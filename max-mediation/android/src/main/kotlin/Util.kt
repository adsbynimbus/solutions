package com.applovin.enterprise.apps.demoapp

import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener

class DefaultMaxMediationListener : MaxAdViewAdListener, MaxRewardedAdListener {
    override fun onAdLoaded(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdLoaded")
    }

    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
        Log.i("DefaultMaxMediationListener", "onAdLoadFailed")
    }

    override fun onAdHidden(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdHidden")
    }

    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
        Log.i("DefaultMaxMediationListener", "onAdDisplayFailed")
    }

    override fun onAdDisplayed(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdDisplayed")
    }

    override fun onAdClicked(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdClicked")
    }

    override fun onAdExpanded(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdExpanded")
    }

    override fun onAdCollapsed(ad: MaxAd) {
        Log.i("DefaultMaxMediationListener", "onAdCollapsed")
    }

    override fun onUserRewarded(p0: MaxAd, p1: MaxReward) {
        Log.i("DefaultMaxMediationListener", "onUserRewarded")
    }
}
