package com.adsbynimbus.solutions.max

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.adsbynimbus.Nimbus
import com.adsbynimbus.NimbusResponse
import com.adsbynimbus.internal.Platform
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.sdk.AppLovinSdk
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*

class NimbusMediationAdapterTest : StringSpec({
    val sdk = mockk<AppLovinSdk>(relaxed = true)
    val adapter = NimbusMediationAdapter(sdk)

    beforeTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        mockkObject(Platform)
    }

    "NimbusMediationAdapter initialize success" {
        val params = mockk<MaxAdapterInitializationParameters>(relaxed = true)
        val listener = mockk<MaxAdapter.OnCompletionListener>(relaxed = true)
        adapter.initialize(params, null, listener)
        verify { listener.onCompletion(any(), null) }
    }

    "NimbusMediationAdapter getSdkVersion returns Nimbus version" {
        adapter.getSdkVersion() shouldBe Nimbus.version
    }

    "NimbusMediationAdapter getAdapterVersion returns BuildConfig version" {
        val version = adapter.getAdapterVersion()
        version shouldNotBe null
    }

    "NimbusMediationAdapter cacheNimbusResponse caches response" {
        val response = mockk<NimbusResponse>(relaxed = true)
        NimbusMediationAdapter.cacheNimbusResponse(response)
    }

    "NimbusMediationAdapter loadAdViewAd fails with invalid auction id" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        every { params.serverParameters.getString("nimbus_auction_id") } returns null
        val listener = mockk<MaxAdViewAdapterListener>(relaxed = true)
        adapter.loadAdViewAd(params, MaxAdFormat.BANNER, null, listener)
        verify { listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION) }
    }

    "NimbusMediationAdapter loadAdViewAd fails when response not cached" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        every { params.serverParameters.getString("nimbus_auction_id") } returns "unknown"
        val listener = mockk<MaxAdViewAdapterListener>(relaxed = true)
        adapter.loadAdViewAd(params, MaxAdFormat.BANNER, null, listener)
        verify { listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_LOAD_STATE) }
    }

    "NimbusMediationAdapter loadInterstitialAd fails with invalid auction id" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        val bundle = mockk<Bundle>(relaxed = true)
        every { params.serverParameters } returns bundle
        every { bundle.getString("nimbus_auction_id") } returns null
        val listener = mockk<MaxInterstitialAdapterListener>(relaxed = true)
        adapter.loadInterstitialAd(params, null, listener)
        verify { listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION) }
    }

    "NimbusMediationAdapter showInterstitialAd fails when activity is null" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        val listener = mockk<MaxInterstitialAdapterListener>(relaxed = true)
        adapter.showInterstitialAd(params, null, listener)
        verify { listener.onInterstitialAdDisplayFailed(any()) }
    }

    "NimbusMediationAdapter showInterstitialAd fails when ad is null" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true)
        val listener = mockk<MaxInterstitialAdapterListener>(relaxed = true)
        adapter.showInterstitialAd(params, activity, listener)
        verify { listener.onInterstitialAdDisplayFailed(any()) }
    }

    "NimbusMediationAdapter loadRewardedAd fails with invalid auction id" {
        val params = mockk<MaxAdapterResponseParameters>(relaxed = true)
        every { params.serverParameters.getString("nimbus_auction_id") } returns null
        val listener = mockk<MaxRewardedAdapterListener>(relaxed = true)
        adapter.loadRewardedAd(params, mockk(), listener)
        verify { listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION) }
    }

    "NimbusMediationAdapter onDestroy cleans up" {
        adapter.onDestroy()
    }

    afterTest {
        unmockkStatic(SystemClock::class)
        unmockkObject(Nimbus)
    }
})
