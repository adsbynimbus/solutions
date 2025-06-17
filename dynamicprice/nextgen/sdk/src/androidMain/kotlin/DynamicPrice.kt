package com.adsbynimbus.dynamicprice.nextgen

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.WorkerThread
import androidx.collection.LruCache
import androidx.core.os.BundleCompat.getSerializable
import androidx.core.view.allViews
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.internal.Platform
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
    DynamicPriceRenderer.adCache.put(nimbusAd.auctionId, nimbusAd)
    nimbusAd.targetingMap(mapping).forEach { putCustomTargeting(it.key, it.value) }
}

/**
 * Renders a Nimbus ad when the na_render app event is called
 *
 * @param name passed from onAppEvent
 * @param data passed from onAppEvent
 * @param listener optional listener for Nimbus Ad events and errors
 * @param activity optional context the ad is loaded in; current activity used as the default
 */
fun BannerAd.handleEventForNimbus(
    name: String,
    data: String?,
    listener: AdController.Listener? = null,
    activity: Activity? = Platform.currentActivity.get(),
) {
    if (name == "na_render") DynamicPriceRenderer.render(this, data, listener) { nimbusAd ->
        nimbusAd.renderInline(getView(activity!!).webViewParent)
    }
}

/**
 * Renders a Nimbus Interstitial ad when the na_render app event is called
 *
 * @param name passed from onAppEvent
 * @param data passed from onAppEvent
 * @param listener optional listener for Nimbus Ad events and errors
 * @param activity optional context the ad is loaded in; current activity used as the default
 */
fun InterstitialAd.handleEventForNimbus(
    name: String,
    data: String?,
    listener: AdController.Listener? = null,
    activity: Activity? = Platform.currentActivity.get(),
) {
    when (name) {
        "na_render" -> DynamicPriceRenderer.render(this, data, listener) { nimbusAd ->
            activity!!.loadBlockingAd(nimbusAd)!!
        }
        "na_show" -> DynamicPriceRenderer.renderScope.launch(Dispatchers.Main) {
            dynamicPriceAd?.adController?.start() ?: run {
                adEventCallback?.onAdFailedToShowFullScreenContent(
                    FullScreenContentError(
                        code = MEDIATION_SHOW_ERROR,
                        message = "Nimbus controller failed to show",
                        mediationAdError = null,
                    )
                )
                maybeClearInterstitial(activity)
            }
        }
    }
}

@JvmInline
value class DynamicPriceAd(val adController: AdController) : java.io.Serializable {
    fun destroy() = adController.destroy()
}

inline var Ad.dynamicPriceAd: DynamicPriceAd?
    get() = getSerializable(getResponseInfo().responseExtras, "na_render", DynamicPriceAd::class.java)
    internal set(value) {
        getResponseInfo().responseExtras.apply {
            if (value == null) remove("na_render") else putSerializable("na_render", value)
        }
    }

internal inline val View.webViewParent: ViewGroup
    get() = allViews.filterIsInstance<WebView>().first().parent as ViewGroup

internal fun maybeClearInterstitial(activity: Activity? = Platform.currentActivity.get()) {
    when {
        activity !is AdActivity -> return
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> activity.run {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            finish()
        }
        else -> activity.run {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}


@Serializable
internal class DynamicPriceRenderer(
    @SerialName("na_id") val auctionId: String,
    @SerialName("ga_click") val clickTracker: String,
) {
    companion object {
        fun render(
            ad: Ad,
            data: String?,
            publisherListener: AdController.Listener?,
            render: suspend (NimbusAd) -> AdController,
        ) {
            renderScope.launch {
                runCatching {
                    val renderer = jsonSerializer.decodeFromString(serializer(), data!!)
                    val nimbusAd = adCache[renderer.auctionId]!!
                    ad.dynamicPriceAd = DynamicPriceAd(
                        adController = withContext(Dispatchers.Main) { render(nimbusAd) }.apply {
                            attachDynamicPriceListener(ad, renderer.clickTracker, renderScope)
                            publisherListener?.let { listeners.add(it) }
                        }
                    )
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        publisherListener?.onError(
                            NimbusError(
                                errorType = NimbusError.ErrorType.RENDERER_ERROR,
                                message = "Failed to render dynamic price ad",
                                cause = it,
                            )
                        )
                    }
                }
            }
        }

        fun AdController.attachDynamicPriceListener(
            googleAd: Ad,
            googleClickTracker: String,
            coroutineScope: CoroutineScope,
        ) = listeners.add(object : AdController.Listener {

            val adEventCallback: AdEventCallback?
                get() = when(googleAd) {
                    is BannerAd -> googleAd.adEventCallback
                    is InterstitialAd -> googleAd.adEventCallback
                    else -> null
                }

            override fun onAdEvent(adEvent: AdEvent) {
                when (adEvent) {
                    AdEvent.CLICKED -> {
                        coroutineScope.launch(Dispatchers.IO) {
                            when (OneShotConnection(googleClickTracker).use { it.responseCode }) {
                                in 200..399 -> log(Log.VERBOSE, "Fired Google click tracker")
                                else -> log(Log.WARN, "Error firing Google click tracker")
                            }
                        }
                        adEventCallback?.onAdClicked()
                    }
                    AdEvent.DESTROYED -> {
                        googleAd.dynamicPriceAd = null
                        if (googleAd is InterstitialAd) maybeClearInterstitial()
                    }
                    else -> return
                }
            }

            override fun onError(error: NimbusError) {
                destroy()
            }
        })

        val adCache = LruCache<String, NimbusAd>(10)

        @OptIn(ExperimentalSerializationApi::class)
        val jsonSerializer = Json {
            coerceInputValues = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

        val renderScope = CoroutineScope(Dispatchers.Default) + CoroutineName("DynamicPrice")
    }
}

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
suspend inline fun NimbusAd.renderInline(container: ViewGroup): AdController {
    return suspendCancellableCoroutine { continuation ->
        Renderer.loadAd(
            this, container,
            object : Renderer.Listener, NimbusError.Listener {
                override fun onAdRendered(controller: AdController) {
                    if (continuation.isActive) continuation.resume(controller) else controller.destroy()
                }

                override fun onError(error: NimbusError) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        )
    }
}
