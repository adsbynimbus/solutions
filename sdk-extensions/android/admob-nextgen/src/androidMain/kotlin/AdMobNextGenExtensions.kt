package com.adsbynimbus.solutions.nextgen

import android.content.Context
import com.adsbynimbus.AdSize.Banner
import com.adsbynimbus.AdSize.Leaderboard
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.request.DemandBuilder
import com.adsbynimbus.request.DemandHandler
import com.adsbynimbus.solutions.nextgen.internal.*
import com.google.android.libraries.ads.mobile.sdk.banner.*

/**
 * Include Google Bidding Adaptive Banners in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 * @param context optional context, defaults to application context
 * @param width optional max width in density pixels; defaults to full screen width
 */
public fun DemandBuilder.withAdMobAdaptiveBanner(
    adUnitId: String,
    adSize: com.adsbynimbus.AdSize,
    context: Context = Platform.currentActivity.get()!!,
    width: Int = context.screenWidthDp,
) {
    val adSize = when (adSize) {
        Banner, Leaderboard -> AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, width)
        else -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, width)
    }
    demandHandlers += DemandHandler {
        it.add(AdMobNextGenRequestInterceptor(
            signalRequest = BannerSignalRequest.Builder(signalType = "requester_type_2")
                .setAdSize(adSize)
                .setRequiredParams(adUnitId)
                .build(),
        ))
    }
}
