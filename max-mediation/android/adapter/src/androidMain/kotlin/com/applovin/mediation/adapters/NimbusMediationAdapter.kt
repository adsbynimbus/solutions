package com.applovin.mediation.adapters

import android.app.*
import androidx.collection.LruCache
import com.adsbynimbus.*
import com.applovin.mediation.*
import com.applovin.mediation.adapter.*
import com.applovin.mediation.adapter.MaxAdapter.*
import com.applovin.mediation.adapter.MaxAdapterError.*
import com.applovin.mediation.adapter.listeners.*
import com.applovin.mediation.adapter.parameters.*
import com.applovin.sdk.*
import kotlinx.coroutines.*


public class GoogleMediationAdapter @JvmOverloads constructor(
    sdk: AppLovinSdk,
    private val scope: CoroutineScope = MainScope(),
): MediationAdapterBase(sdk), MaxAdViewAdapter, MaxInterstitialAdapter, MaxRewardedAdapter,
    MaxSignalProvider, AdEvent.Listener, NimbusError.Listener {

    public companion object {
        private val cache = LruCache<String, NimbusResponse>(10)
    }

    private lateinit var maxAdFormat: MaxAdFormat
    private var bannerListener: MaxAdViewAdapterListener? = null
    private var interstitialListener: MaxInterstitialAdapterListener? = null
    private var rewardedListener: MaxRewardedAdapterListener? = null

    private var inlineAd: InlineAd? = null
    private var fullscreenAd: FullscreenAd? = null
    private var loaded = false

    override fun initialize(
        parameters: MaxAdapterInitializationParameters,
        activity: Activity?,
        onCompletionListener: OnCompletionListener,
    ) {
        onCompletionListener.onCompletion(InitializationStatus.DOES_NOT_APPLY, null)
    }

    override fun getSdkVersion(): String = "25.5.0"
    override fun getAdapterVersion(): String = "25.5.0.0"

    override fun onDestroy() {
        inlineAd?.destroy(); inlineAd = null
        fullscreenAd?.destroy(); fullscreenAd = null
        bannerListener = null; interstitialListener = null; rewardedListener = null
    }

    override fun loadAdViewAd(
        parameters: MaxAdapterResponseParameters,
        adFormat: MaxAdFormat,
        activity: Activity?,
        listener: MaxAdViewAdapterListener,
    ) {
        val auctionId = parameters.serverParameters.getString("na_id") ?: run {
            log("Invalid auction id")
            listener.onAdViewAdLoadFailed(INVALID_CONFIGURATION)
            return
        }
        val response = cache[auctionId] ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId")
            listener.onAdViewAdLoadFailed(INVALID_LOAD_STATE)
            return
        }
        bannerListener = listener
        maxAdFormat = adFormat
        scope.launch {
            runCatching {
                inlineAd = Nimbus.inlineAd(response)
                    .onEvent(this@GoogleMediationAdapter)
                    .onError(this@GoogleMediationAdapter)
                    .load()
            }.onFailure {
                log("Failed to load banner ad: ${it.message}")
                listener.onAdViewAdLoadFailed(UNSPECIFIED)
            }
        }
    }

    override fun loadInterstitialAd(parameters: MaxAdapterResponseParameters, activity: Activity?, listener: MaxInterstitialAdapterListener) {
        val auctionId = parameters.serverParameters.getString("na_id") ?: run {
            log("Invalid auction id")
            listener.onInterstitialAdLoadFailed(INVALID_CONFIGURATION)
            return
        }
        val response = cache[auctionId] ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId")
            listener.onInterstitialAdLoadFailed(INVALID_LOAD_STATE)
            return
        }
        maxAdFormat = MaxAdFormat.INTERSTITIAL
        interstitialListener = listener
        scope.launch {
            runCatching {
                fullscreenAd = Nimbus.interstitialAd(response)
                    .onEvent(this@GoogleMediationAdapter)
                    .onError(this@GoogleMediationAdapter)
                    .load()
            }.onFailure {
                log("Failed to load interstitial ad: ${it.message}")
                listener.onInterstitialAdLoadFailed(UNSPECIFIED)
            }
        }
    }

    override fun showInterstitialAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxInterstitialAdapterListener,
    ) {
        log("Showing interstitial ad...")
        val activity = activity ?: run {
            listener.onInterstitialAdDisplayFailed(
                MaxAdapterError(AD_DISPLAY_FAILED, MISSING_ACTIVITY.code, MISSING_ACTIVITY.message)
            )
            return
        }
        val ad = fullscreenAd ?: run {
            log("Interstitial ad is null")
            listener.onInterstitialAdDisplayFailed(
                MaxAdapterError(AD_DISPLAY_FAILED, INVALID_LOAD_STATE.code, INVALID_LOAD_STATE.message)
            )
            return
        }
        scope.launch {
            runCatching {
                ad.show(activity)
                log("Interstitial ad shown")
            }.onFailure {
                log("Failed to show interstitial ad: ${it.message}")
                listener.onInterstitialAdDisplayFailed(UNSPECIFIED)
            }
        }
    }

    override fun loadRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        val auctionId = parameters.serverParameters.getString("na_id") ?: run {
            log("Invalid auction id")
            listener.onRewardedAdLoadFailed(INVALID_CONFIGURATION)
            return
        }
        configureReward(parameters)
        val response = cache[auctionId] ?: run {
            log("Unable to find Nimbus response for auction id: $auctionId")
            listener.onRewardedAdLoadFailed(INVALID_LOAD_STATE)
            return
        }
        maxAdFormat = MaxAdFormat.REWARDED
        rewardedListener = listener
        scope.launch {
            runCatching {
                fullscreenAd = Nimbus.rewardedAd(response)
                    .onEvent(this@GoogleMediationAdapter)
                    .onError(this@GoogleMediationAdapter)
                    .load()
            }.onFailure {
                log("Failed to load rewarded ad: ${it.message}")
                listener.onRewardedAdLoadFailed(UNSPECIFIED)
            }
        }
    }

    override fun showRewardedAd(
        parameters: MaxAdapterResponseParameters,
        activity: Activity?,
        listener: MaxRewardedAdapterListener,
    ) {
        log("Showing rewarded ad...")
        val activity = activity ?: run {
            listener.onRewardedAdDisplayFailed(
                MaxAdapterError(AD_DISPLAY_FAILED, MISSING_ACTIVITY.code, MISSING_ACTIVITY.message)
            )
            return
        }
        val ad = fullscreenAd ?: run {
            log("Rewarded ad is null")
            listener.onRewardedAdDisplayFailed(
                MaxAdapterError(AD_DISPLAY_FAILED, INVALID_LOAD_STATE.code, INVALID_LOAD_STATE.message)
            )
            return
        }
        scope.launch {
            runCatching {
                ad.show(activity)
                log("Rewarded ad shown")
            }.onFailure {
                log("Failed to show rewarded ad: ${it.message}")
                listener.onRewardedAdDisplayFailed(UNSPECIFIED)
            }
        }
    }

    override fun onAdEvent(adEvent: AdEvent) {
        when (adEvent) {
            AdEvent.Loaded -> {
                bannerListener?.runCatching {
                    onAdViewAdLoaded(inlineAd!!.adView!!, null)
                }?.onFailure {
                    bannerListener?.onAdViewAdLoadFailed(UNSPECIFIED)
                    return
                }
                interstitialListener?.onInterstitialAdLoaded()
                rewardedListener?.onRewardedAdLoaded()
                loaded = true
                log("${maxAdFormat.label} ad loaded")
            }
            AdEvent.Impression -> {
                log("${maxAdFormat.label} ad impression")
                bannerListener?.onAdViewAdDisplayed()
                interstitialListener?.onInterstitialAdDisplayed()
                rewardedListener?.onRewardedAdDisplayed()
            }
            AdEvent.Clicked -> {
                log("${maxAdFormat.label} ad clicked")
                bannerListener?.onAdViewAdClicked()
                interstitialListener?.onInterstitialAdClicked()
                rewardedListener?.onRewardedAdClicked()
            }
            AdEvent.RewardEarned -> {
                log("Reward earned")
                if (!shouldAlwaysRewardUser()) rewardedListener?.onUserRewarded(reward)
            }
            AdEvent.Completed -> {
                log("${maxAdFormat.label} ad completed")
            }
            AdEvent.Destroyed -> {
                log("${maxAdFormat.label} ad destroyed")
                interstitialListener?.onInterstitialAdHidden()
                rewardedListener?.onRewardedAdHidden()
            }
            else -> return
        }
    }

    override fun onError(error: NimbusError) {
        if (loaded) {
            bannerListener?.onAdViewAdDisplayFailed(UNSPECIFIED)
            interstitialListener?.onInterstitialAdDisplayFailed(UNSPECIFIED)
            rewardedListener?.onRewardedAdDisplayFailed(UNSPECIFIED)
        } else {
            bannerListener?.onAdViewAdLoadFailed(UNSPECIFIED)
            interstitialListener?.onInterstitialAdLoadFailed(UNSPECIFIED)
            rewardedListener?.onRewardedAdLoadFailed(UNSPECIFIED)
        }
    }

    override fun collectSignal(
        p0: MaxAdapterSignalCollectionParameters?,
        p1: Activity?,
        p2: MaxSignalCollectionListener?,
    ) {
        p2?.onSignalCollected("")
    }
}
