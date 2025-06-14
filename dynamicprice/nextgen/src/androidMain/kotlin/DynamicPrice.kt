package adsbynimbus.solutions.dynamicprice.nextgen

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.WorkerThread
import androidx.collection.LruCache
import androidx.core.view.allViews
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.internal.lifecycleOrNimbusScope
import com.adsbynimbus.internal.log
import com.adsbynimbus.lineitem.Mapping
import com.adsbynimbus.lineitem.targetingMap
import com.adsbynimbus.render.*
import com.adsbynimbus.render.Renderer.Companion.loadBlockingAd
import com.adsbynimbus.request.NimbusResponse
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.lang.AutoCloseable
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.use

/** Appends Nimbus Key Values to the Ad Manager request and caches the ad for rendering */
fun <T: BaseAdRequestBuilder<T>> BaseAdRequestBuilder<T>.applyDynamicPrice(
    nimbusAd: NimbusResponse,
    mapping: Mapping,
) {
    nimbusAdCache.put(nimbusAd.auctionId, nimbusAd)
    nimbusAd.targetingMap(mapping).forEach { putCustomTargeting(it.key, it.value) }
}

/**
 * Renders a Nimbus ad when the na_render app event is called
 *
 * @param name passed from onAppEvent
 * @param data passed from onAppEvent
 * @param eventListener optional listener for Nimbus Ad events and errors
 * @param activity optional context the ad is loaded in; current activity used as the default
 */
fun BannerAd.handleEventForNimbus(
    name: String,
    data: String?,
    eventListener: AdController.Listener? = null,
    activity: Activity? = Platform.currentActivity.get(),
) {
    data?.takeIf { name == "na_render" }?.runCatching {
        val event = jsonSerializer.decodeFromString<RenderEvent>(serializer(), this)
        activity!!.lifecycleOrNimbusScope.launch(Dispatchers.Main.immediate) {
            val adView = getView(activity)
            val controller = event.nimbusAd.renderInline(
                container = adView.allViews.filterIsInstance<WebView>().first().parent as ViewGroup
            )
            controller.attachListener(
                googleClickTracker = event.clickTracker,
                onClick = { adEventCallback?.onAdClicked() },
                onDestroy = { destroy() }
            )
            eventListener?.let { controller.listeners.add(it) }
            adView.doOnAttach {
                adView.controllerJob = it.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        awaitCancellation()
                    } finally {
                        controller.destroy()
                    }
                }
            }
        }
    }
}

/**
 * Renders a Nimbus Interstitial ad when the na_render app event is called
 *
 * @param name passed from onAppEvent
 * @param data passed from onAppEvent
 * @param eventListener optional listener for Nimbus Ad events and errors
 * @param activity optional context the ad is loaded in; current activity used as the default
 */
fun InterstitialAd.handleEventForNimbus(
    name: String,
    data: String?,
    eventListener: AdController.Listener? = null,
    activity: Activity? = Platform.currentActivity.get(),
) {
    data?.takeIf { name == "na_render" }?.runCatching {
        val event = jsonSerializer.decodeFromString<RenderEvent>(serializer(), this)
        activity!!.runOnUiThread {
            val controller = activity.loadBlockingAd(event.nimbusAd)!!
            controller.attachListener(
                googleClickTracker = event.clickTracker,
                onClick = { adEventCallback?.onAdClicked() },
                onDestroy = { activity.destroy() }
            )
            eventListener?.let { controller.listeners.add(it) }
            controller.start()
        }
    }?.onFailure {
        adEventCallback?.onAdFailedToShowFullScreenContent(
            FullScreenContentError(
                code = MEDIATION_SHOW_ERROR,
                message = "Nimbus Rendering Failure ${it.message}",
                mediationAdError = null,
            )
        )
        activity.takeIf { it is AdActivity }?.destroy()
    }
}

/** Searches all children for a Nimbus Ad job and cancels it, destroying the AdController */
fun ViewGroup.destroyNimbusAds() = allViews.firstOrNull { it.controllerJob != null }?.let {
    it.controllerJob?.cancel()
    it.controllerJob = null
}

/** Stores a BannerAd LifeCycle job using View tags */
internal inline var View.controllerJob: Job?
    get() = getTag(com.adsbynimbus.render.R.id.controller) as Job?
    set(value) { setTag(com.adsbynimbus.render.R.id.controller, value) }

@Serializable
internal data class RenderEvent(
    @SerialName("na_id") val auctionId: String,
    @SerialName("ga_click") val clickTracker: String,
)

internal inline val RenderEvent.nimbusAd: NimbusAd
    get() = nimbusAdCache[auctionId]!!

internal inline fun AdController.attachListener(
    googleClickTracker: String,
    crossinline onClick: () -> Unit,
    crossinline onDestroy: () -> Unit,
) = listeners.add(object : AdController.Listener {

    override fun onAdEvent(adEvent: AdEvent) {
        when (adEvent) {
            AdEvent.CLICKED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    when (OneShotConnection(googleClickTracker).use { it.responseCode }) {
                        in 200..399 -> log(Log.VERBOSE, "Fired Google click tracker")
                        else -> log(Log.WARN, "Error firing Google click tracker")
                    }
                }
                onClick()
            }
            AdEvent.DESTROYED -> onDestroy()
            else -> return
        }
    }

    override fun onError(error: NimbusError) {
        destroy()
    }
})

@JvmInline @WorkerThread
value class OneShotConnection(val connection: HttpURLConnection): AutoCloseable {

    constructor(url: String, timeout: Duration = 30.seconds) : this(
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout.inWholeMilliseconds.toInt()
        },
    )

    override fun close() { connection.disconnect() }

    inline val responseCode: Int get() = runCatching { connection.responseCode }.getOrDefault(-1)
}

/** Renders a Nimbus Ad into the provided ViewGroup */
suspend inline fun NimbusAd.renderInline(container: ViewGroup): AdController =
    suspendCancellableCoroutine { continuation ->
        Renderer.loadAd(this, container, object : Renderer.Listener, NimbusError.Listener {
            override fun onAdRendered(controller: AdController) {
                if (continuation.isActive) continuation.resume(controller) else controller.destroy()
            }

            override fun onError(error: NimbusError) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        })
    }

private fun Activity.destroy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        finish()
    } else {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

internal val nimbusAdCache = LruCache<String, NimbusAd>(10)

@OptIn(ExperimentalSerializationApi::class)
internal val jsonSerializer = Json {
    coerceInputValues = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
