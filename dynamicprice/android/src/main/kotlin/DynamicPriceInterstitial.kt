package adsbynimbus.solutions.dynamicprice

import adsbynimbus.solutions.dynamicprice.BuildConfig.AMAZON_BANNER_SLOT_ID
import android.content.Context
import com.adsbynimbus.google.handleEventForNimbus
import com.adsbynimbus.request.NimbusRequest.Companion.forInterstitialAd
import com.amazon.aps.ads.*
import com.amazon.aps.ads.model.ApsAdFormat.INTERSTITIAL
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.*

val interstitialBidders = listOf(
    NimbusBidder { forInterstitialAd("Interstitial") },
    ApsBidder {
        ApsAdRequest(AMAZON_BANNER_SLOT_ID, INTERSTITIAL, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
    },
)

@Throws(RuntimeException::class)
suspend fun Context.loadInterstitial(
    adUnitId: String,
    bidders: Collection<Bidder<*>>,
): AdManagerInterstitialAd {
    val bids = bidders.auction()
    val request = AdManagerAdRequest.Builder().apply {
        bids.forEach { it.applyTargeting(this) }
    }.build()

    val interstitialAd = suspendCancellableCoroutine { continuation ->
        AdManagerInterstitialAd.load(this, adUnitId, request,
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    if (continuation.isActive) continuation.resume(ad)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    if (continuation.isActive) continuation.resumeWithException(RuntimeException(p0.message))
                }
            },
        )
    }
    // Set the appEventListener before returning the AdManagerInterstitialAd
    return interstitialAd.apply {
        // handleEventForNimbus is called when Nimbus wins the auction
        appEventListener = AppEventListener { name, info -> handleEventForNimbus(name, info) }
    }
}
