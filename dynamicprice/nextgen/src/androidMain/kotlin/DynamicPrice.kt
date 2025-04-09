package adsbynimbus.solutions.dynamicprice.nextgen

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.collection.LruCache
import androidx.core.view.allViews
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.lineitem.Mapping
import com.adsbynimbus.lineitem.targetingMap
import com.adsbynimbus.openrtb.request.BidRequest
import com.adsbynimbus.render.*
import com.adsbynimbus.render.Renderer.Companion.loadBlockingAd
import com.adsbynimbus.request.NimbusResponse
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.use

fun NimbusResponse.applyDynamicPrice(
    request: BaseAdRequestBuilder<*>,
    mapping: Mapping,
) {
    nimbusAdCache.put(auctionId, this)
    targetingMap(mapping).forEach { request.putCustomTargeting(it.key, it.value) }
}

inline fun handleEventForNimbus(name: String, data: String?, receiver: (DynamicPriceRenderer) -> Unit) =
    data?.takeIf { name == "na_render" }?.runCatching {
        jsonSerializer.decodeFromString<DynamicPriceRenderer>(serializer(), this)
    }?.onSuccess(receiver)?.onFailure {
        Log.w("Ads", it.message ?: "")
    }

fun InterstitialAd.handleEventForNimbus(name: String, data: String?) {
    data?.takeIf { name == "na_render" }?.runCatching {
        val event = jsonSerializer.decodeFromString<DynamicPriceEvent>(serializer(), this)
        val activity = Platform.currentActivity.get()!!
        val ad = nimbusAdCache[event.auctionId]!!
        Log.i("Ads", "Loading Nimbus Interstitial")
        activity.runOnUiThread {
            activity.loadBlockingAd(ad)?.apply {
                Log.i("Ads", "Loaded Nimbus Interstitial")
                listeners.add(object : AdController.Listener {
                    override fun onError(error: NimbusError) {
                        destroy()
                        activity.destroy()
                    }

                    override fun onAdEvent(adEvent: AdEvent) {
                        when (adEvent) {
                            AdEvent.CLICKED -> {
                                event.fireClickTracker()
                                adEventCallback?.onAdClicked()
                            }
                            AdEvent.DESTROYED -> {
                                activity.destroy()
                                adEventCallback?.onAdDismissedFullScreenContent()
                            }
                            else -> return
                        }
                    }
                })
            }?.start()
        }
    }?.onFailure {
        Log.w("Ads", "Rendering Failure ${it.message}")
    }
}

@Serializable
data class DynamicPriceEvent(
    @SerialName("na_id") val auctionId: String,
    @SerialName("ga_click") val googleClickEvent: String,
)

fun DynamicPriceEvent.fireClickTracker() = CoroutineScope(Dispatchers.IO).launch {
    when (OneShotConnection(googleClickEvent).use { it.responseCode }) {
        in 200..399 -> Log.v("DynamicPrice", "Fired Google click tracker")
        else -> Log.w("DynamicPrice", "Error firing Google click tracker")
    }
}

@Serializable
data class DynamicPriceRenderer(
    @SerialName("na_id") val auctionId: String,
    @SerialName("ga_click") val googleClickEvent: String,
    @kotlinx.serialization.Transient var adController: AdController? = null,
    @kotlinx.serialization.Transient var adListener: AdEventCallback? = null,
) : AdController.Listener, Renderer.Listener {
    override fun onError(error: NimbusError) {
        adListener?.onAdFailedToShowFullScreenContent(
            FullScreenContentError(MEDIATION_SHOW_ERROR, error.message ?: "", null)
        )
        adController?.destroy()
        adController = null
    }

    override fun onAdRendered(controller: AdController) {
        adController = controller.apply { listeners.add(this@DynamicPriceRenderer) }
    }

    override fun onAdEvent(adEvent: AdEvent) {
        when(adEvent) {
            AdEvent.CLICKED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    when (OneShotConnection(googleClickEvent).use { it.responseCode }) {
                        in 200..399 -> Log.v("DynamicPrice", "Fired Google click tracker")
                        else -> Log.w("DynamicPrice", "Error firing Google click tracker")
                    }
                }
                adListener?.onAdClicked()
            }
            AdEvent.DESTROYED -> adListener?.onAdDismissedFullScreenContent()
            else -> return
        }
    }
}

fun DynamicPriceRenderer.loadAd(view: View) = runCatching {
    Renderer.loadAd(
        ad = nimbusAdCache[auctionId]!!,
        container = view.allViews.filterIsInstance<WebView>().first().parent as ViewGroup,
        listener = this@loadAd,
    )
}

@Suppress("Deprecation")
fun Activity.destroy() {
    finish()
    overridePendingTransition(0, 0)
}

val nimbusAdCache = LruCache<String, NimbusAd>(10)

val jsonSerializer = BidRequest.lenientSerializer
