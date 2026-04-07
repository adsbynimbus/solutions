package com.adsbynimbus.dynamicprice.nextgen.internal

import android.app.Activity
import com.adsbynimbus.NimbusError
import com.adsbynimbus.dynamicprice.nextgen.*
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.render.AdController
import com.adsbynimbus.render.AdEvent
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

    override fun show(activity: Activity, onUserEarnedRewardListener: OnUserEarnedRewardListener) {
        if (!googleAd.isNimbusWin) googleAd.show(activity, onUserEarnedRewardListener) else {
            activity.loadBlockingAd(nimbusAd)?.run {
                googleAd.dynamicPriceAd = DynamicPriceAd(adController = this)
                listeners.add(this@DynamicPriceRewardedAd)
                if (listener != null) listeners.add(listener)
                rewardListener = onUserEarnedRewardListener
                googleAd.show(activity) { }
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
                Platform.doOnNextActivity { start() }
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
