package com.adsbynimbus.dynamicprice.nextgen

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.WorkerThread
import androidx.core.os.BundleCompat.getSerializable
import androidx.core.view.allViews
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.dynamicprice.nextgen.internal.DynamicPriceRenderer
import com.adsbynimbus.dynamicprice.nextgen.internal.renderInline
import com.adsbynimbus.dynamicprice.nextgen.internal.webViewParent
import com.adsbynimbus.internal.Platform
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
                DynamicPriceRenderer.maybeClearInterstitial(activity)
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
