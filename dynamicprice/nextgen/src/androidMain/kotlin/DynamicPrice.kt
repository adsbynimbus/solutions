package adsbynimbus.solutions.dynamicprice.nextgen.app

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.collection.LruCache
import androidx.core.view.allViews
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.lineitem.Mapping
import com.adsbynimbus.lineitem.targetingMap
import com.adsbynimbus.openrtb.request.BidRequest
import com.adsbynimbus.render.AdController
import com.adsbynimbus.render.AdEvent
import com.adsbynimbus.render.Renderer
import com.adsbynimbus.request.NimbusResponse
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.BaseAdRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.net.HttpURLConnection
import java.net.URL

fun NimbusResponse.applyDynamicPrice(
    request: BaseAdRequestBuilder<*>,
    mapping: Mapping,
) {
    nimbusAdCache.put(auctionId, this)
    targetingMap(mapping).forEach { request.putCustomTargeting(it.key, it.value) }
}

inline fun handleEventForNimbus(name: String, info: String?, receiver: (DynamicPriceRenderer) -> Unit) =
    info?.takeIf { name == "na_render" }?.runCatching {
        jsonSerializer.decodeFromString<DynamicPriceRenderer>(serializer(), this)
    }?.onSuccess(receiver)?.onFailure {
        Log.w("Ads", it.message ?: "")
    }

@Serializable
data class DynamicPriceRenderer(
    @SerialName("na_id") val auctionId: String,
    @SerialName("ga_click") val googleClickEvent: String,
    @kotlinx.serialization.Transient var adController: AdController? = null,
    @kotlinx.serialization.Transient var adListener: AdEventCallback? = null,
    @kotlinx.serialization.Transient val connectionProvider: (String) -> HttpURLConnection = {
        URL(it).openConnection() as HttpURLConnection
    },
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
                    val request = connectionProvider(googleClickEvent).apply {
                        connectTimeout = 60000
                    }
                    when (request.responseCode) {
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

private val nimbusAdCache = LruCache<String, NimbusAd>(10)

val jsonSerializer = BidRequest.lenientSerializer
