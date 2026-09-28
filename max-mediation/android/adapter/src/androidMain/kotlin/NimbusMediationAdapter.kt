package com.adsbynimbus.solutions.max

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.collection.LruCache
import com.adsbynimbus.AdEvent
import com.adsbynimbus.FullscreenAd
import com.adsbynimbus.InlineAd
import com.adsbynimbus.Nimbus
import com.adsbynimbus.NimbusResponse
import com.adsbynimbus.android.BuildConfig
import com.adsbynimbus.internal.log
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdViewAdapter
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapter.InitializationStatus
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.mediation.adapters.MediationAdapterBase
import com.applovin.sdk.AppLovinSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

public class NimbusMediationAdapter(sdk: AppLovinSdk) : MediationAdapterBase(sdk),
    MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter {

    public companion object {
        private val nimbusResponseCache = LruCache<String, NimbusResponse>(10)
        private val cacheLock = Any()
        private const val CACHE_CLEANUP_DELAY_SECONDS = 300L

        public fun cacheNimbusResponse(response: NimbusResponse) {
            nimbusResponseCache.put(response.id, response)
            log(Log.VERBOSE, "Cached Nimbus response for auction id: ${response.id}")
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var inlineAd: InlineAd? = null
    private var fullscreenAd: FullscreenAd? = null
    private var bannerListener: MaxAdViewAdapterListener? = null
    private var interstitialListener: MaxInterstitialAdapterListener? = null
    private var rewardedListener: MaxRewardedAdapterListener? = null

    override fun initialize(parameters: MaxAdapterInitializationParameters, activity: Activity?, onCompletionListener: MaxAdapter.OnCompletionListener) {
        log("Initializing Nimbus SDK...")
        runCatching {
            onCompletionListener.onCompletion(InitializationStatus.INITIALIZED_SUCCESS, null)
        }.onFailure {
            log("Nimbus initialization failed: ${it.message}")
            onCompletionListener.onCompletion(InitializationStatus.INITIALIZED_FAILURE, it.message)
        }
    }

    override fun getSdkVersion(): String = Nimbus.version
    override fun getAdapterVersion(): String = BuildConfig.VERSION_NAME

    override fun onDestroy() {
        inlineAd?.destroy(); inlineAd = null
        fullscreenAd?.destroy(); fullscreenAd = null
        bannerListener = null; interstitialListener = null; rewardedListener = null
    }

    override fun loadAdViewAd(parameters: MaxAdapterResponseParameters, adFormat: MaxAdFormat, activity: Activity?, listener: MaxAdViewAdapterListener) {
        val auctionId = parameters.serverParameters.getString("nimbus_auction_id") ?: run {
            log("Invalid auction id"); listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION); return
        }
        val response = getFromCache(auctionId) ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId"); listener.onAdViewAdLoadFailed(MaxAdapterError.INVALID_LOAD_STATE); return
        }
        bannerListener = listener
        coroutineScope.launch {
            runCatching {
                Nimbus.inlineAd(response).apply {
                    onEvent { handleAdEvent(it, listener, adFormat, true) }
                    onError { log("Nimbus ad error: ${it.message}"); listener.onAdViewAdLoadFailed(MaxAdapterError.UNSPECIFIED) }
                    inlineAd = this
                    load()
                }
            }.onFailure {
                log("Failed to load banner ad: ${it.message}")
                listener.onAdViewAdLoadFailed(MaxAdapterError.UNSPECIFIED)
            }.onSuccess {
                val adView = inlineAd?.adView ?: return@launch
                Bundle().apply { putString("auction_id", auctionId) }.let { listener.onAdViewAdLoaded(adView, it) }
                log("${adFormat.label} ad view ad loaded")
            }
        }
    }

    override fun loadInterstitialAd(parameters: MaxAdapterResponseParameters, activity: Activity?, listener: MaxInterstitialAdapterListener) {
        val auctionId = parameters.serverParameters.getString("nimbus_auction_id") ?: run {
            log("Invalid auction id"); listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION); return
        }
        val response = getFromCache(auctionId) ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId")
            listener.onInterstitialAdLoadFailed(MaxAdapterError.INVALID_LOAD_STATE); return
        }
        interstitialListener = listener
        coroutineScope.launch {
            runCatching {
                Nimbus.interstitialAd(response).apply {
                    onEvent { handleAdEvent(it, listener, MaxAdFormat.INTERSTITIAL, false) }
                    onError { log("Nimbus interstitial error: ${it.message}"); listener.onInterstitialAdLoadFailed(MaxAdapterError.UNSPECIFIED) }
                    fullscreenAd = this
                    load()
                }
            }.onFailure {
                log("Failed to load interstitial ad: ${it.message}")
                listener.onInterstitialAdLoadFailed(MaxAdapterError.UNSPECIFIED)
            }.onSuccess {
                listener.onInterstitialAdLoaded()
                log("Interstitial ad loaded")
            }
        }
    }

    override fun showInterstitialAd(parameters: MaxAdapterResponseParameters, activity: Activity?, listener: MaxInterstitialAdapterListener) {
        log("Showing interstitial ad...")
        val activity = activity ?: run {
            listener.onInterstitialAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, MaxAdapterError.MISSING_ACTIVITY.getCode(), MaxAdapterError.MISSING_ACTIVITY.getMessage())); return
        }
        val ad = fullscreenAd ?: run {
            log("Interstitial ad is null")
            listener.onInterstitialAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, MaxAdapterError.INVALID_LOAD_STATE.getCode(), MaxAdapterError.INVALID_LOAD_STATE.getMessage())); return
        }
        coroutineScope.launch {
            runCatching { ad.show(activity); log("Interstitial ad shown") }
                .onFailure { log("Failed to show interstitial ad: ${it.message}"); listener.onInterstitialAdDisplayFailed(MaxAdapterError.UNSPECIFIED) }
        }
    }

    override fun loadRewardedAd(parameters: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedAdapterListener) {
        val auctionId = parameters.serverParameters.getString("nimbus_auction_id") ?: run {
            log("Invalid auction id"); listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_CONFIGURATION); return
        }
        configureReward(parameters)
        val response = getFromCache(auctionId) ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId")
            listener.onRewardedAdLoadFailed(MaxAdapterError.INVALID_LOAD_STATE); return
        }
        rewardedListener = listener
        coroutineScope.launch {
            runCatching {
                Nimbus.rewardedAd(response).apply {
                    onEvent { handleRewardedAdEvent(it, listener) }
                    onError { log("Nimbus rewarded error: ${it.message}"); listener.onRewardedAdLoadFailed(MaxAdapterError.UNSPECIFIED) }
                    fullscreenAd = this
                    load()
                }
            }.onFailure {
                log("Failed to load rewarded ad: ${it.message}")
                listener.onRewardedAdLoadFailed(MaxAdapterError.UNSPECIFIED)
            }.onSuccess {
                listener.onRewardedAdLoaded()
                log("Rewarded ad loaded")
            }
        }
    }

    override fun showRewardedAd(parameters: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedAdapterListener) {
        log("Showing rewarded ad...")
        val activity = activity ?: run {
            listener.onRewardedAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, MaxAdapterError.MISSING_ACTIVITY.getCode(), MaxAdapterError.MISSING_ACTIVITY.getMessage())); return
        }
        val ad = fullscreenAd ?: run {
            log("Rewarded ad is null")
            listener.onRewardedAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, MaxAdapterError.INVALID_LOAD_STATE.getCode(), MaxAdapterError.INVALID_LOAD_STATE.getMessage())); return
        }
        coroutineScope.launch {
            runCatching { ad.show(activity); log("Rewarded ad shown") }
                .onFailure { log("Failed to show rewarded ad: ${it.message}"); listener.onRewardedAdDisplayFailed(MaxAdapterError.UNSPECIFIED) }
        }
    }

    private fun getFromCache(auctionId: String): NimbusResponse? = synchronized(cacheLock) {
        nimbusResponseCache.remove(auctionId)?.also { scheduleCacheCleanup(auctionId) }
    }

    private fun scheduleCacheCleanup(auctionId: String) {
        coroutineScope.launch {
            delay(TimeUnit.SECONDS.toMillis(CACHE_CLEANUP_DELAY_SECONDS))
            nimbusResponseCache.remove(auctionId)
        }
    }

    private fun handleAdEvent(event: AdEvent, listener: Any, adFormat: MaxAdFormat, isBanner: Boolean) {
        when (event) {
            AdEvent.Impression -> {
                log("${adFormat.label} ad impression")
                if (isBanner) (listener as MaxAdViewAdapterListener).onAdViewAdDisplayed()
                else (listener as MaxInterstitialAdapterListener).onInterstitialAdDisplayed()
            }
            AdEvent.Clicked -> {
                log("${adFormat.label} ad clicked")
                if (isBanner) (listener as MaxAdViewAdapterListener).onAdViewAdClicked()
                else (listener as MaxInterstitialAdapterListener).onInterstitialAdClicked()
            }
            AdEvent.Destroyed -> {
                log("${adFormat.label} ad destroyed")
                if (!isBanner) (listener as MaxInterstitialAdapterListener).onInterstitialAdHidden()
            }
            else -> {}
        }
    }

    private fun handleRewardedAdEvent(event: AdEvent, listener: MaxRewardedAdapterListener) {
        when (event) {
            AdEvent.Impression -> { log("Rewarded ad impression"); listener.onRewardedAdDisplayed() }
            AdEvent.Clicked -> { log("Rewarded ad clicked"); listener.onRewardedAdClicked() }
            AdEvent.RewardEarned -> {
                log("Reward earned");
                if (!shouldAlwaysRewardUser()) listener.onUserRewarded(reward)
            }
            AdEvent.Completed -> {
                log("Rewarded ad completed")
                if (shouldAlwaysRewardUser()) listener.onUserRewarded(reward)
                listener.onRewardedAdHidden()
            }
            AdEvent.Destroyed -> { log("Rewarded ad destroyed"); listener.onRewardedAdHidden() }
            else -> {}
        }
    }
}
