package com.adsbynimbus.solutions.nextgen.internal

import android.content.Context
import androidx.core.os.bundleOf
import com.adsbynimbus.request.internal.*
import com.adsbynimbus.request.internal.NimbusRequestDelta.UserDelta
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.BaseSignalRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.signal.*

internal class AdMobNextGenRequestInterceptor(val signalRequest: SignalRequest) : NimbusRequestInterceptor {
    override suspend fun modifyRequest(request: NimbusRequest): List<NimbusRequestDelta>? =
        when (val result = MobileAds.generateSignal(signalRequest)) {
            is SignalGenerationResult.Success -> listOf(UserDelta("admob_gde_signals", result.signal.signalString))
            is SignalGenerationResult.Failure -> null
        }
}

internal inline val Context.screenWidthDp: Int
    get() = resources.displayMetrics.let { (it.widthPixels / it.density).toInt() }

internal val signalBundle = bundleOf("query_info_type" to "requester_type_2")

internal inline fun <reified T : BaseSignalRequestBuilder<T>>
    BaseSignalRequestBuilder<T>.setRequiredParams(adUnitId: String): T = apply {
        setAdUnitId(adUnitId)
        setRequestAgent("nimbus")
        setGoogleExtrasBundle(signalBundle)
    } as T

