package adsbynimbus.solutions.dynamicprice.nextgen

import adsbynimbus.solutions.dynamicprice.nextgen.BuildConfig.AMAZON_BANNER_SLOT_ID
import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.lifecycle.*
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
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadResult
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

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
 * This example uses a 1 second timeout for the auction in cases where a low banner impression
 * time is critical due to users quickly exiting the application.
 *
 * @param bidders Bidders participating in the auction
 * @param timeout Auction timeout; should not be set below 1 second
 */
fun preloadBanner(
    bidders: Collection<Bidder<*>>,
    timeout: Duration = 1.seconds,
): Deferred<BannerAd?> = MainScope().async {
    loadBanner(
        builder = BannerAdRequest.Builder(BuildConfig.ADMANAGER_ADUNIT_ID, AdSize.BANNER),
        bidders = bidders,
        bidderTimeout = timeout,
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
suspend fun loadBanner(
    builder: BannerAdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: BannerAdEventCallback? = null,
    bidderTimeout: Duration = 3.seconds
): BannerAd? = supervisorScope {
    val (bids, auctionTime) = measureTimedValue { bidders.auction(timeout = bidderTimeout) }

    val request = builder.run {
        bids.forEach { it.applyTargeting(this) }
        build()
    }

    val (adResponse, requestTime) = measureTimedValue { BannerAd.load(request) }

    Log.v("Ads", "[${request.adUnitId}] Auction: $auctionTime AdRequest: $requestTime")

    when (adResponse) {
        is AdLoadResult.Success<BannerAd> -> adResponse.ad.apply {
            adEventCallback = object : BannerAdEventCallback by eventCallback {
                override fun onAppEvent(name: String, data: String?) {
                    Log.v("Ads", "[${request.adUnitId}] AppEvent Received")
                    handleEventForNimbus(name, data)
                    eventCallback?.onAppEvent(name, data)
                }
            }
        }
        is AdLoadResult.Failure<*> -> null.also {
            Log.i("Ads", "[${request.adUnitId}] ${adResponse.error.message}")
        }
    }
}

/**
 * Refreshes the BannerAd every 30 seconds while the container is in view.
 *
 * @param activity The hosting activity of the refreshing banner
 * @param container A ViewGroup for hosting the banner; FrameLayout is preferred
 * @param builder Closure that runs on each ad load to create a new Builder instance
 * @param bidders Bidders participating in the auction
 * @param eventCallback listener for BannerAd events fired from Google
 * @param preloadBanner An existing BannerAd that is attached to the container on first load
 */
fun loadRefreshingBanner(
    activity: Activity,
    container: ViewGroup,
    builder: () -> BannerAdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: BannerAdEventCallback? = null,
    preloadBanner: BannerAd? = null,
) = container.doOnAttach {
    val lifecycleOwner = it.findViewTreeLifecycleOwner() ?: throw Exception()
    var lastRequestTime = TimeSource.Monotonic.markNow()
    var bannerAd: BannerAd? = preloadBanner?.apply { container.addView(getView(activity)) }
    if (bannerAd == null) lastRequestTime -= 30.seconds
    container.requestVisibleLayout()
    lifecycleOwner.lifecycleScope.launch {
        try {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // The while loop enables refreshing the ad tied to the lifecycle
                while (isActive) {
                    delay(30.seconds - lastRequestTime.elapsedNow())
                    container.waitUntilVisible()
                    if (isActive) {
                        lastRequestTime = TimeSource.Monotonic.markNow()
                        bannerAd = loadBanner(
                            builder = builder(),
                            bidders = bidders,
                            eventCallback = eventCallback,
                        )?.apply {
                            bannerAd?.destroy()
                            container.destroyNimbusAds()
                            container.removeAllViews()
                            container.addView(getView(activity))
                        }
                    }
                }
            }
        } finally {
            bannerAd?.destroy()
            container.destroyNimbusAds()
            container.removeAllViews()
        }
    }
}

/**
 * Runs an auction with a 3 second timeout and loads an Interstitial ad.
 *
 * @param builder A new instance should be used for each request
 * @param bidders Bidders participating in the auction
 * @param eventCallback listener for Interstitial events fired from Google
 */
suspend fun loadInterstitial(
    builder: AdRequest.Builder,
    bidders: Collection<Bidder<*>>,
    eventCallback: InterstitialAdEventCallback? = null,
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
                    eventCallback?.onAppEvent(name, data)
                }
            }
        }
        is AdLoadResult.Failure<*> -> null.also {
            Log.i("Ads", "[${request.adUnitId}] ${adResponse.error.message}")
        }
    }
}
