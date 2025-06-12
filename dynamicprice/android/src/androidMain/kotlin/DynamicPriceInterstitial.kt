package adsbynimbus.solutions.dynamicprice

import adsbynimbus.solutions.dynamicprice.BuildConfig.AMAZON_BANNER_SLOT_ID
import android.content.Context
import com.adsbynimbus.google.handleEventForNimbus
import com.adsbynimbus.request.NimbusRequest.Companion.forInterstitialAd
import com.amazon.aps.ads.ApsAdNetworkInfo
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.model.ApsAdFormat.INTERSTITIAL
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.admanager.AppEventListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val interstitialBidders = listOf(
    NimbusBidder { forInterstitialAd("Interstitial") },
    ApsBidder {
        ApsAdRequest(AMAZON_BANNER_SLOT_ID, INTERSTITIAL, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
    }
)

suspend fun Context.loadInterstitial(
    adUnitId: String,
    bidders: Collection<Bidder<*>>,
): AdManagerInterstitialAd {
    val bids = bidders.auction()
    val request = AdManagerAdRequest.Builder().apply {
        bids.forEach { it.applyTargeting(this) }
    }.build()

    return suspendCoroutine { continuation ->
        AdManagerInterstitialAd.load(this, adUnitId, request,
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    /* Set the appEventListener and call handleEventForNimbus */
                    ad.appEventListener = AppEventListener { name, info ->
                        /* handleEventForNimbus is called when Nimbus wins the auction */
                        ad.handleEventForNimbus(name, info)
                    }
                    continuation.resume(ad)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    continuation.resumeWithException(RuntimeException(p0.message))
                }
            }
        )
    }
}
