package adsbynimbus.solutions.dynamicprice.app

import android.content.Context
import android.util.Log
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adsbynimbus.google.handleEventForNimbus
import com.adsbynimbus.openrtb.enumerations.Position
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.amazon.device.ads.DTBAdNetwork
import com.amazon.device.ads.DTBAdNetworkInfo
import com.amazon.device.ads.DTBAdRequest
import com.amazon.device.ads.DTBAdSize
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.lang.System.currentTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

lateinit var appContext: Context

val adCache = mutableMapOf<String, DynamicPriceAd>()

val preloadScope = MainScope() + CoroutineName("AdPreLoader")

fun preloadBanner(
    nimbusRequest: NimbusRequest = forBannerAd("Top Banner", Format.BANNER_320_50, Position.HEADER),
    amazonRequest: () -> DTBAdRequest = {
        DTBAdRequest(DTBAdNetworkInfo(DTBAdNetwork.GOOGLE_AD_MANAGER)).apply {
            setSizes(DTBAdSize(320, 50, BuildConfig.AMAZON_BANNER_SLOT_ID))
        }
    },
    bidders: List<Bidder<*>> = listOf(NimbusBidder(nimbusRequest), ApsBidder(amazonRequest)),
    timeout: Duration = 1000.milliseconds
) = DynamicPriceAd(
    adUnitId = BuildConfig.ADMANAGER_ADUNIT_ID,
    adSize = AdSize.BANNER,
    bidders = bidders,
    preloadBids = preloadScope.async { bidders.auction(timeout) }
)

class DynamicPriceAd(
    adUnitId: String,
    adSize: AdSize,
    bidders: Collection<Bidder<*>>,
    context: Context = appContext,
    preloadBids: Deferred<List<Bid<*>>>? = null,
) {
    private var lastRequestTime = 0L
    /** Must attach a listener prior to attaching to a parent view */
    val adView by lazy {
        AdManagerAdView(context).apply {
            setAdUnitId(adUnitId)
            setAdSize(adSize)
            setAppEventListener { name, info -> handleEventForNimbus(name, info) }
            doOnAttach {
                val lifecycleOwner = findViewTreeLifecycleOwner() ?: throw Exception()
                lifecycleOwner.lifecycleScope.launch {
                    // If a preloaded bid exists load it when attached to the view hierarchy
                    preloadBids?.let {
                        lastRequestTime = currentTimeMillis()
                        val (bids, auctionTime) = measureTimedValue { it.await() }
                        val request = AdManagerAdRequest.Builder().apply {
                            bids.forEach { it.applyTargeting(this) }
                        }.build()
                        loadAd(request)
                        Log.i("Ads", "Preload Time: $auctionTime")
                    }
                    // Start normal refreshing tied using the LifecycleScope
                    try {
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            /* The while loop here enables refreshing the ad tied to the lifecycle */
                            while (isActive) {
                                delay(30_000 - (currentTimeMillis() - lastRequestTime))
                                waitUntilVisible()
                                if (!isActive) return@repeatOnLifecycle
                                lastRequestTime = currentTimeMillis()
                                val adRequest = AdManagerAdRequest.Builder().apply {
                                    bidders.auction().forEach { it.applyTargeting(this) }
                                }
                                if (isActive) loadAd(adRequest.build())
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
