package com.adsbynimbus.solutions.nextgen.internal

import android.content.Context
import androidx.core.os.bundleOf
import com.adsbynimbus.openrtb.request.User
import com.adsbynimbus.request.*
import com.adsbynimbus.request.internal.*
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.BaseSignalRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.signal.*

internal class AdMobNextGenRequestInterceptor(val signalRequest: SignalRequest) : AsyncInterceptor {
    override suspend fun interceptRequest(request: NimbusRequest): NimbusRequestChange? =
        when (val result = MobileAds.generateSignal(signalRequest)) {
            is SignalGenerationResult.Success -> NimbusRequestChange(
                userChanges = User.Extension(admob_gde_signals = result.signal.signalString),
            )
            is SignalGenerationResult.Failure -> null
        }

    override fun onAdResponse(nimbusResponse: NimbusResponse) {
        if (nimbusResponse.network() == "admob") nimbusResponse.placementIdOverride = signalRequest.adUnitId
    }
}

internal inline val Context.screenWidthDp: Int
    get() = resources.displayMetrics.let { (it.widthPixels / it.density).toInt() }

internal val signalBundle = bundleOf("query_info_type" to "requester_type_2")

internal inline fun <reified T : BaseSignalRequestBuilder<T>> BaseSignalRequestBuilder<T>.setRequiredParams(adUnitId: String): T = apply {
    setAdUnitId(adUnitId)
    setRequestAgent("nimbus")
    setGoogleExtrasBundle(signalBundle)
} as T

