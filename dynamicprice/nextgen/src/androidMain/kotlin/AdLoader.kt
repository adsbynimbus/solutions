package adsbynimbus.solutions.dynamicprice.nextgen.app

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.allViews
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.openrtb.enumerations.Position
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.amazon.device.ads.DTBAdNetwork
import com.amazon.device.ads.DTBAdNetworkInfo
import com.amazon.device.ads.DTBAdRequest
import com.amazon.device.ads.DTBAdSize
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

lateinit var appContext: Context

val adCache = mutableMapOf<String, DynamicPriceAd>()

fun preloadBanner(
    nimbusRequest: NimbusRequest = forBannerAd("Top Banner", Format.BANNER_320_50, Position.HEADER),
    amazonRequest: () -> DTBAdRequest = {
        DTBAdRequest(DTBAdNetworkInfo(DTBAdNetwork.GOOGLE_AD_MANAGER)).apply {
            setSizes(DTBAdSize(320, 50, BuildConfig.AMAZON_BANNER_SLOT_ID))
        }
    },
    timeout: Duration = 1000.milliseconds,
) = DynamicPriceAd(
    adUnitId = BuildConfig.ADMANAGER_ADUNIT_ID,
    adSize = AdSize.BANNER,
    bidders = listOf(NimbusBidder(nimbusRequest), ApsBidder(amazonRequest)),
    preloadTimeout = timeout,
)

class DynamicPriceAd(
    val adUnitId: String,
    val adSize: AdSize,
    val bidders: Collection<Bidder<*>>,
    preloadTimeout: Duration = 3000.milliseconds,
) {
    private val adScope = CoroutineScope(Dispatchers.Default) + CoroutineName("Ad: $adUnitId")
    private val bannerAd = MutableSharedFlow<BannerAd>(replay = 1)
    private var lastRequestTime = TimeSource.Monotonic.markNow()

    private inline val timeSinceLoad
        get() = TimeSource.Monotonic.markNow() - lastRequestTime

    init {
        adScope.launch {
            loadAd(timeout = preloadTimeout)
        }
    }

    var adListener: BannerAdEventCallback? = null
    var nimbusRenderer: DynamicPriceRenderer? = null

    suspend fun loadAd(timeout: Duration) = supervisorScope {
        lastRequestTime = TimeSource.Monotonic.markNow()
        val (bids, auctionTime) = measureTimedValue {
            bidders.auction(timeout = timeout)
        }
        Log.v("Ads", "[$adUnitId] Auction Duration: $auctionTime")

        if (!MobileAds.isInitialized()) yield()
        val request = BannerAdRequest.Builder(adUnitId, adSize).apply {
            bids.forEach { it.applyTargeting(this) }
        }
        val (adResponse, requestTime) = measureTimedValue {
            runCatching { request.loadAsync() }
        }

        Log.v("Ads", "[$adUnitId] AdRequest Duration: $requestTime")
        adResponse.onSuccess {
            it.adEventCallback = object : BannerAdEventCallback by adListener {
                override fun onAppEvent(name: String, p1: String?) {
                    Log.v("Ads", "[$adUnitId] AppEvent Received: $timeSinceLoad")
                    handleEventForNimbus(name, p1) {
                        nimbusRenderer?.adController?.destroy()
                        nimbusRenderer = it
                    }
                }
            }
            bannerAd.emit(it)
        }.onFailure {
            Log.i("Ads", "[$adUnitId] ${it.message}" )
        }
    }

    fun attach(activity: Activity, view: ViewGroup) {
        view.doOnAttach {
            val lifecycleOwner = view.findViewTreeLifecycleOwner() ?: throw Exception()
            lifecycleOwner.lifecycleScope.launch {
                // Start normal refreshing tied using the LifecycleScope
                try {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        /* The while loop here enables refreshing the ad tied to the lifecycle */
                        while (isActive) {
                            Log.v("Ads", "[$adUnitId] Starting Refresh")
                            delay(30_000 - timeSinceLoad.toLong(DurationUnit.MILLISECONDS))
                            view.waitUntilVisible()
                            if (!isActive) return@repeatOnLifecycle
                            loadAd(timeout = 3000.milliseconds)
                        }
                    }
                } finally {
                    Log.v("Ads", "[$adUnitId] Refresh Canceled")
                    bannerAd.firstOrNull()?.destroy()
                }
            }
            lifecycleOwner.lifecycleScope.launch {
                bannerAd.collect {
                    view.removeAllViews()
                    view.addView(it.getView(activity).apply {
                        nimbusRenderer?.loadAd(this)
                    })
                    Log.i("Ads", "[$adUnitId] Ad Rendered: $timeSinceLoad")
                }
            }
        }
    }
}
