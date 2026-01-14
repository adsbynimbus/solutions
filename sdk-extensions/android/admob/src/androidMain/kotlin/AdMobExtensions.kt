package com.adsbynimbus.solutions.admob

import android.content.Context
import androidx.core.os.bundleOf
import com.adsbynimbus.Nimbus
import com.adsbynimbus.NimbusError
import com.adsbynimbus.NimbusError.ErrorType.NETWORK_ERROR
import com.adsbynimbus.openrtb.request.User
import com.adsbynimbus.render.AdMobRenderer.admob
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusResponse
import com.adsbynimbus.request.internal.AsyncInterceptor
import com.adsbynimbus.request.internal.NimbusRequestChange
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdFormat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.query.QueryInfo
import com.google.android.gms.ads.query.QueryInfoGenerationCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Include AdMob anchored adaptive banner in Nimbus auction.
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun NimbusRequest.withAdMobAnchoredBanner(adUnitId: String): NimbusRequest = apply {
    interceptors.removeIf { it is AdMobAdaptiveDemandProvider }
    interceptors += AdMobAdaptiveDemandProvider(adUnitId)
}

internal class AdMobAdaptiveDemandProvider(val adUnitId: String) : AsyncInterceptor {
    override suspend fun interceptRequest(request: NimbusRequest): NimbusRequestChange? =
        NimbusRequestChange(userChanges = User.Extension(admob_gde_signals = gdeAdaptiveSignals()))

    override fun onAdResponse(nimbusResponse: NimbusResponse) {
        if (nimbusResponse.network() == admob) nimbusResponse.placementIdOverride = adUnitId
    }
}

internal inline val Context.screenWidthDp: Int
    get() = resources.displayMetrics.let { (it.widthPixels / it.density).toInt() }

internal suspend fun AdMobAdaptiveDemandProvider.gdeAdaptiveSignals(): String =
    suspendCancellableCoroutine { continuation ->
        val adaptiveSize = Nimbus.applicationContext.let {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(it, it.screenWidthDp)
        }

        val googleRequest = AdRequest.Builder()
            .setRequestAgent("nimbus")
            .addNetworkExtrasBundle(AdMobAdapter::class.java, bundleOf(
                "query_info_type" to "requester_type_2",
                "adaptive_banner_w" to adaptiveSize.width,
                "adaptive_banner_h" to adaptiveSize.height,
            ))
            .build()

        QueryInfo.generate(Nimbus.applicationContext, AdFormat.BANNER, googleRequest, adUnitId,
            object : QueryInfoGenerationCallback() {
                override fun onFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(
                        NimbusError(NETWORK_ERROR, error, null)
                    )
                }

                override fun onSuccess(queryInfo: QueryInfo) {
                    if (continuation.isActive) continuation.resume(queryInfo.query)
                }
            },
        )
    }
