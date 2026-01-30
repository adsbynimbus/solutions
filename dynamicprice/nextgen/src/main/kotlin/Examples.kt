package adsbynimbus.solutions.dynamicprice.nextgen

import adsbynimbus.solutions.dynamicprice.nextgen.BuildConfig.AMAZON_BANNER_SLOT_ID
import android.app.Activity
import android.util.Log
import androidx.lifecycle.*
import com.adsbynimbus.dynamicprice.nextgen.dynamicPriceAd
import com.adsbynimbus.dynamicprice.nextgen.handleEventForNimbus
import com.adsbynimbus.openrtb.enumerations.Position
import com.adsbynimbus.openrtb.request.Format.Companion.BANNER_320_50
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.adsbynimbus.request.NimbusRequest.Companion.forInterstitialAd
import com.amazon.aps.ads.ApsAdNetworkInfo
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.model.ApsAdFormat.BANNER
import com.amazon.aps.ads.model.ApsAdFormat.INTERSTITIAL
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/** Caches ads loaded in one activity for use in another */
val adCache = mutableMapOf<String, Deferred<BannerAd?>>()

val interstitialBidders = listOf(
    NimbusBidder { forInterstitialAd("Interstitial") },
    ApsBidder {
        ApsAdRequest(AMAZON_BANNER_SLOT_ID, INTERSTITIAL, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
    }
)

val bannerBidders = listOf(
    NimbusBidder {
        forBannerAd(
            position = "Top Banner",
            format = BANNER_320_50,
            screenPosition = Position.FOOTER,
        )
    },
    ApsBidder {
        ApsAdRequest(AMAZON_BANNER_SLOT_ID, BANNER, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
    }
)

/**
 * Starts the auction of a BannerAd request for use at app startup.
 *
 * This example uses a 1-second timeout for the auction in cases where a low banner impression
 * time is critical due to users quickly exiting the application.
 *
 * @param bidders Bidders participating in the auction
 * @param timeout Auction timeout; should not be set below 1 second
 */
fun preloadBanner(
    bidders: Collection<Bidder<*>>,
    timeout: Duration = 1.seconds,
): Deferred<BannerAd?> = MainScope().async {
    val (bids, auctionTime) = measureTimedValue { bidders.auction(timeout = timeout) }

    val request = BannerAdRequest.Builder(BuildConfig.ADMANAGER_ADUNIT_ID, AdSize.BANNER).run {
        bids.forEach { it.applyTargeting(this) }
        build()
    }

    val (bannerAd, requestTime) = measureTimedValue { BannerAdPreloader.loadAdAsync(request) }

    Log.v("Ads", "[${request.adUnitId}] Auction: $auctionTime AdRequest: $requestTime, Params: ${request.keyValues}")

    return@async bannerAd
}

suspend fun BannerAdPreloader.Companion.loadAdAsync(adRequest: BannerAdRequest): BannerAd? =
    pollAd(adRequest.adUnitId) ?: suspendCancellableCoroutine {
        start(
            preloadId = adRequest.adUnitId,
            preloadConfiguration = PreloadConfiguration(request = adRequest, bufferSize = 1),
            preloadCallback = object : PreloadCallback {
                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    if (it.isActive) it.resume(null)
                }

                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    if (it.isActive) it.resume(pollAd(adRequest.adUnitId))
                }

                override fun onAdsExhausted(preloadId: String) { }
            }
        )
    }

/**
 * Runs an auction with the specified timeout and loads a Banner ad.
 *
 * The bidderTimeout of 3 seconds should not be altered as it accounts for transient device
 * conditions such as poor network connectivity or other application related hangups. In most
 * cases all bidders will return between 200-500ms.
 *
 * @param builder A new instance should be used for each request
 * @param bidders Bidders participating in the auction
 * @param eventCallback listener for BannerAd events fired from Google
 * @param bidderTimeout 3 seconds is ideal for handling transient device conditions
 */
suspend fun AdView.loadBanner(
    builder: BannerAdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: BannerAdEventCallback,
    bidderTimeout: Duration = 3.seconds,
): BannerAd? = supervisorScope {
    val (bids, auctionTime) = measureTimedValue { bidders.auction(timeout = bidderTimeout) }

    val request = builder.run {
        bids.forEach { it.applyTargeting(this) }
        build()
    }

    val (adResponse, requestTime) = measureTimedValue {
        suspendCancellableCoroutine { continuation ->
            val callback = object : AdLoadCallback<BannerAd> {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    continuation.resume(null)
                }

                override fun onAdLoaded(ad: BannerAd) {
                    ad.adEventCallback = object : BannerAdEventCallback by eventCallback {
                        override fun onAppEvent(name: String, data: String?) {
                            Log.v("Ads", "[${request.adUnitId}] AppEvent Received")
                            ad.handleEventForNimbus(name, data)
                            eventCallback.onAppEvent(name, data)
                        }
                    }
                    continuation.resume(ad)
                }
            }
            loadAd(adRequest = request, adLoadCallback = callback)
        }
    }

    Log.v("Ads", "[${request.adUnitId}] Auction: $auctionTime AdRequest: $requestTime, Params: ${request.keyValues}")

    return@supervisorScope adResponse
}

/**
 * Refreshes the BannerAd every 30 seconds while the container is in view.
 *
 * @param activity The hosting activity of the refreshing banner
 * @param builder Closure that runs on each ad load to create a new Builder instance
 * @param bidders Bidders participating in the auction
 * @param eventCallback listener for BannerAd events fired from Google
 * @param preloadBanner An existing BannerAd that is attached to the container on first load
 */
fun AdView.loadRefreshingBanner(
    activity: Activity,
    builder: () -> BannerAdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: BannerAdEventCallback,
    preloadBanner: BannerAd? = null,
    lifecycleOwner: LifecycleOwner? = findViewTreeLifecycleOwner()
) {
    val lifecycleOwner = lifecycleOwner ?: throw Exception()
    var lastRequestTime = TimeSource.Monotonic.markNow()
    var bannerAd: BannerAd? = preloadBanner?.apply { registerBannerAd(this, activity) }
    if (bannerAd == null) lastRequestTime -= 30.seconds
    requestVisibleLayout()
    lifecycleOwner.lifecycleScope.launch {
        try {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // The while loop enables refreshing the ad tied to the lifecycle
                while (isActive) {
                    delay(30.seconds - lastRequestTime.elapsedNow())
                    waitUntilVisible()
                    if (isActive) {
                        lastRequestTime = TimeSource.Monotonic.markNow()
                        loadBanner(
                            builder = builder(),
                            bidders = bidders,
                            eventCallback = eventCallback,
                        )?.run {
                            bannerAd?.dynamicPriceAd?.destroy()
                            bannerAd = this
                        }
                    }
                }
            }
        } finally {
            bannerAd?.dynamicPriceAd?.destroy()
            destroy() // AdView.destroy()
        }
    }
}

/**
 * Runs an auction with a 3-second timeout and loads an Interstitial ad.
 *
 * @param builder A new instance should be used for each request
 * @param bidders Bidders participating in the auction
 * @param eventCallback listener for Interstitial events fired from Google
 */
suspend fun loadInterstitial(
    builder: AdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: InterstitialAdEventCallback,
): InterstitialAd? = supervisorScope {
    val (bids, auctionTime) = measureTimedValue { bidders.auction() }

    val request = builder.run {
        bids.forEach { it.applyTargeting(this) }
        build()
    }
    val (adResponse, requestTime) = measureTimedValue { InterstitialAd.load(request) }

    Log.v("Ads", "[${request.adUnitId}] Auction: $auctionTime AdRequest: $requestTime")

    when (adResponse) {
        is AdLoadResult.Success<InterstitialAd> -> adResponse.ad.apply {
            adEventCallback = object : InterstitialAdEventCallback by eventCallback {
                override fun onAppEvent(name: String, data: String?) {
                    Log.v("Ads", "[${request.adUnitId}] AppEvent Received")
                    handleEventForNimbus(name, data)
                    eventCallback.onAppEvent(name, data)
                }
            }
        }
        is AdLoadResult.Failure<*> -> null.also {
            Log.i("Ads", "[${request.adUnitId}] ${adResponse.error.message}")
        }
    }
}
