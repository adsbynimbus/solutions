package com.adsbynimbus.dynamicprice.nextgen.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.core.view.allViews
import androidx.webkit.WebViewCompat
import com.adsbynimbus.NimbusError
import com.adsbynimbus.dynamicprice.nextgen.*
import com.adsbynimbus.internal.*
import com.adsbynimbus.render.*
import com.adsbynimbus.render.Renderer.Companion.loadBlockingAd
import com.adsbynimbus.request.NimbusResponse
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR
import com.google.android.libraries.ads.mobile.sdk.rewarded.*

internal class DynamicPriceRewardedAd(
    val googleAd: RewardedAd,
    val nimbusAd: NimbusResponse,
    val listener: AdController.Listener?
) : RewardedAd by googleAd, AdController.Listener {

    var rewardListener: OnUserEarnedRewardListener? = null

    fun Context.createController(): AdController? = loadBlockingAd(nimbusAd)?.apply {
        Log.i("Debugger", "Dynamic Price Create Controller")
        googleAd.dynamicPriceAd = DynamicPriceAd(adController = this)
        listeners.add(this@DynamicPriceRewardedAd)
        if (listener != null) listeners.add(listener)
    }

    init {
        Log.i("Debugger", "Dynamic Price Rewarded Thread ${Thread.currentThread().name}")
        if (googleAd.isNimbusWin) application.createController()
    }

    override fun show(activity: Activity, onUserEarnedRewardListener: OnUserEarnedRewardListener) {
        if (!googleAd.isNimbusWin) googleAd.show(activity, onUserEarnedRewardListener) else {
            (googleAd.dynamicPriceAd?.adController ?: activity.createController())?.run {
                Log.i("Debugger", "Dynamic Price Show Thread ${Thread.currentThread().name}")
                rewardListener = onUserEarnedRewardListener
                googleAd.show(activity) { }
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
                Platform.doOnNextActivity {
                    Log.i("Debugger", "Dynamic Price Activity Thread ${Thread.currentThread().name}")
                    start()
                }
            } ?: googleAd.adEventCallback?.onAdFailedToShowFullScreenContent(
                FullScreenContentError(
                    code = MEDIATION_SHOW_ERROR,
                    message = "Nimbus controller failed to show",
                )
            )
        }
    }

    override fun destroy() {
        googleAd.dynamicPriceAd?.destroy()
        googleAd.dynamicPriceAd = null
        rewardListener = null
        DynamicPriceRenderer.maybeClearInterstitial()
        googleAd.destroy()
    }

    override fun onAdEvent(adEvent: AdEvent) {
        Log.i("Debugger", "Dynamic Price AdEvent[${adEvent.name}] Thread ${Thread.currentThread().name}")
        when (adEvent) {
            AdEvent.CLICKED -> googleAd.adEventCallback?.onAdClicked()
            AdEvent.COMPLETED -> rewardListener?.onUserEarnedReward(googleAd.getRewardItem())
            AdEvent.DESTROYED -> destroy()
            else -> return
        }
    }

    override fun onError(error: NimbusError) {
        destroy()
    }
}
