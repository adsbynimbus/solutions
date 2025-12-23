package com.adsbynimbus.solutions.nextgen

import android.content.Context
import com.adsbynimbus.Nimbus
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.solutions.nextgen.internal.*
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerSignalRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialSignalRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeSignalRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedSignalRequest

/**
 * Include Google Bidding Banners in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun NimbusRequest.withAdMobBanner(adUnitId: String): NimbusRequest = apply {
    val adSize = when(request.imp[0].banner?.h) {
        50 -> AdSize.BANNER
        90 -> AdSize.LEADERBOARD
        250 -> AdSize.MEDIUM_RECTANGLE
        else -> throw IllegalArgumentException()
    }
    interceptors.removeAll { it is AdMobNextGenRequestInterceptor }
    interceptors += AdMobNextGenRequestInterceptor(
        signalRequest = BannerSignalRequest.Builder(signalType = "requester_type_2")
            .setAdSize(adSize)
            .setRequiredParams(adUnitId)
            .build()
    )
}

/**
 * Include Google Bidding Adaptive Banners in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 * @param context optional context, defaults to application context
 * @param width optional max width in density pixels; defaults to full screen width
 */
public fun NimbusRequest.withAdMobAdaptiveBanner(
    adUnitId: String,
    context: Context = Nimbus.applicationContext,
    width: Int = context.screenWidthDp,
): NimbusRequest = apply {
    val adSize = when(request.imp[0].banner?.h) {
        50, 90 -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width)
        else -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, width)
    }
    interceptors.removeAll { it is AdMobNextGenRequestInterceptor }
    interceptors += AdMobNextGenRequestInterceptor(
        signalRequest = BannerSignalRequest.Builder(signalType = "requester_type_2")
            .setAdSize(adSize)
            .setRequiredParams(adUnitId)
            .build()
    )
}

/**
 * Include Google Bidding Native in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun NimbusRequest.withAdMobInterstitial(adUnitId: String): NimbusRequest = apply {
    interceptors.removeAll { it is AdMobNextGenRequestInterceptor }
    interceptors += AdMobNextGenRequestInterceptor(
        signalRequest = InterstitialSignalRequest.Builder(signalType = "requester_type_2")
            .setRequiredParams(adUnitId)
            .build()
    )
}

/**
 * Include Google Bidding Native in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun NimbusRequest.withAdMobRewarded(adUnitId: String): NimbusRequest = apply {
    interceptors.removeAll { it is AdMobNextGenRequestInterceptor }
    interceptors += AdMobNextGenRequestInterceptor(
        signalRequest = RewardedSignalRequest.Builder(signalType = "requester_type_2")
            .setRequiredParams(adUnitId)
            .build()
    )
}

/**
 * Include Google Bidding Native in the Nimbus Request
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun NimbusRequest.withAdMobNative(adUnitId: String): NimbusRequest = apply {
    interceptors.removeAll { it is AdMobNextGenRequestInterceptor }
    interceptors += AdMobNextGenRequestInterceptor(
        signalRequest = NativeSignalRequest.Builder(signalType = "requester_type_2")
            .setRequiredParams(adUnitId)
            .build()
    )
}
