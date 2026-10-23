package com.adsbynimbus.solutions.admob

import android.content.Context
import androidx.core.os.bundleOf
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.request.*
import com.adsbynimbus.request.internal.*
import com.adsbynimbus.request.internal.NimbusRequestDelta.UserDelta
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.*
import com.google.android.gms.ads.query.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.*

/**
 * Include AdMob anchored adaptive banner in Nimbus auction.
 *
 * NOTE: This API is pre-release and subject to change when moved to the Nimbus SDK
 *
 * @param adUnitId ad unit id obtained from the AdMob dashboard
 */
public fun DemandBuilder.withAdMobAnchoredBanner(adUnitId: String) {
    demandHandlers += DemandHandler { it.add(AdMobAdaptiveDemandProvider(adUnitId)) }
}

internal class AdMobAdaptiveDemandProvider(val adUnitId: String) : NimbusRequestInterceptor {
    override suspend fun modifyRequest(request: NimbusRequest): List<NimbusRequestDelta>? =
        runCatching { gdeAdaptiveSignals() }.getOrNull()?.let {
            listOf(UserDelta("admob_gde_signals", it))
        }
}

internal inline val Context.screenWidthDp: Int
    get() = resources.displayMetrics.let { (it.widthPixels / it.density).toInt() }

@Throws(Exception::class)
internal suspend fun AdMobAdaptiveDemandProvider.gdeAdaptiveSignals(): String =
    suspendCancellableCoroutine { continuation ->
        val context = Platform.currentActivity.get() ?: run {
            if (continuation.isActive) continuation.resumeWithException(RuntimeException())
            return@suspendCancellableCoroutine
        }
        val adaptiveSize =
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, context.screenWidthDp)

        val googleRequest = AdRequest.Builder()
            .setRequestAgent("nimbus")
            .addNetworkExtrasBundle(AdMobAdapter::class.java, bundleOf(
                "query_info_type" to "requester_type_2",
                "adaptive_banner_w" to adaptiveSize.width,
                "adaptive_banner_h" to adaptiveSize.height,
            ))
            .build()

        QueryInfo.generate(context, AdFormat.BANNER, googleRequest, adUnitId,
            object : QueryInfoGenerationCallback() {
                override fun onFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(
                        RuntimeException(),
                    )
                }

                override fun onSuccess(queryInfo: QueryInfo) {
                    if (continuation.isActive) continuation.resume(queryInfo.query)
                }
            },
        )
    }
