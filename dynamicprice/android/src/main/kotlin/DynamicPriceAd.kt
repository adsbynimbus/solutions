package adsbynimbus.solutions.dynamicprice

import android.content.Context
import android.util.Log
import androidx.core.view.doOnAttach
import androidx.lifecycle.*
import com.adsbynimbus.google.handleEventForNimbus
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.amazon.device.ads.*
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.*
import kotlinx.coroutines.*
import java.lang.System.currentTimeMillis
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

/** Wrapper for AdManagerAdView that auto-refreshes and manages bidding */
class DynamicPriceAd(
    adUnitId: String,
    adSize: AdSize,
    bidders: Collection<Bidder<*>>,
    context: Context = appContext,
    preloadBids: Deferred<List<Bid<*>>>? = null,
) {
    private var lastRequestTime = 0L

    private suspend fun AdManagerAdView.loadAd(auction: Deferred<List<Bid<*>>>) {
        lastRequestTime = currentTimeMillis()
        val (bids, auctionTime) = measureTimedValue { auction.await() }
        val request = AdManagerAdRequest.Builder().apply {
            bids.forEach { it.applyTargeting(this) }
        }.build()
        if (currentCoroutineContext().isActive) loadAd(request)
        Log.i("Ads", "Auction Time: $auctionTime")
    }

    /** Must attach a listener prior to attaching to a parent view */
    val view by lazy {
        AdManagerAdView(context).apply {
            setAdUnitId(adUnitId)
            setAdSize(adSize)
            setAppEventListener { name, info -> handleEventForNimbus(name, info) }
            doOnAttach {
                val lifecycleOwner = findViewTreeLifecycleOwner() ?: throw Exception()
                lifecycleOwner.lifecycleScope.launch {
                    // If a preloaded bid exists load it when attached to the view hierarchy
                    if (preloadBids != null) loadAd(preloadBids)

                    // Start normal refreshing tied using the LifecycleScope
                    try {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            // The while loop enables refreshing the ad tied to the lifecycle
                            while (isActive) {
                                delay(30_000 - (currentTimeMillis() - lastRequestTime))
                                waitUntilVisible()
                                if (isActive) loadAd(async { bidders.auction() })
                            }
                        }
                    } finally {
                        destroy()
                    }
                }
            }
        }
    }
}

/** Set in AdInitializer.kt and used for preloaded ads */
lateinit var appContext: Context

/** Stores ads for use in an activity that has not been created yet */
val adCache = mutableMapOf<String, DynamicPriceAd>()

/** Returns a named CoroutineScope used for preloading ads */
val String.preloadScope get() = MainScope() + CoroutineName("PreLoad $this")

/** Loads a 320x50 banner on app startup for use on the first screen */
fun preloadBanner(
    adUnitId: String = BuildConfig.ADMANAGER_ADUNIT_ID,
    nimbusRequest: NimbusRequest = forBannerAd("Banner", Format.BANNER_320_50),
    amazonRequest: () -> DTBAdRequest = {
        DTBAdRequest(DTBAdNetworkInfo(DTBAdNetwork.GOOGLE_AD_MANAGER)).apply {
            setSizes(DTBAdSize(320, 50, BuildConfig.AMAZON_BANNER_SLOT_ID))
        }
    },
    bidders: List<Bidder<*>> = listOf(NimbusBidder(nimbusRequest), ApsBidder(amazonRequest)),
    timeout: Duration = 1000.milliseconds,
) = DynamicPriceAd(
    adUnitId = adUnitId,
    adSize = AdSize.BANNER,
    bidders = bidders,
    preloadBids = adUnitId.preloadScope.async { bidders.auction(timeout) },
)
