package adsbynimbus.solutions.dynamicprice.nextgen

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adsbynimbus.openrtb.enumerations.Position
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.amazon.aps.ads.ApsAdNetworkInfo
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.model.ApsAdFormat.BANNER
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadResult
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
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

lateinit var appContext: Context

class DynamicPriceAd(
    val adUnitId: String,
    val adSize: AdSize,
    val bidders: Collection<Bidder<*>>,
    preloadTimeout: Duration = 3000.milliseconds,
) {
    private val adScope = CoroutineScope(Dispatchers.Default) + CoroutineName("Ad: $adUnitId")
    private val bannerAd = MutableSharedFlow<BannerAd>(replay = 1)
    var lastRequestTime = TimeSource.Monotonic.markNow()
        private set

    inline val timeSinceLoad
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

        if (!AdInitializer.completed) yield()
        val request = BannerAdRequest.Builder(adUnitId, adSize).apply {
            bids.forEach { it.applyTargeting(this) }
        }
        val (adResponse, requestTime) = measureTimedValue {
            BannerAd.load(request.build())
        }
        Log.v("Ads", "[$adUnitId] AdRequest Duration: $requestTime")

        when (adResponse) {
            is AdLoadResult.Success<BannerAd> -> adResponse.ad.let {
                it.adEventCallback = object : BannerAdEventCallback by adListener {
                    override fun onAppEvent(name: String, data: String?) {
                        Log.v("Ads", "[$adUnitId] AppEvent Received: $timeSinceLoad")
                        handleEventForNimbus(name, data) {
                            nimbusRenderer?.adController?.destroy()
                            nimbusRenderer = it
                        }
                        adListener?.onAppEvent(name, data)
                    }
                }
                bannerAd.emit(it)
            }
            is AdLoadResult.Failure<*> -> Log.i("Ads", "[$adUnitId] ${adResponse.error.message}")
        }
    }

    fun attach(
        activity: Activity,
        view: ViewGroup,
        layoutParams: ViewGroup.LayoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    ) {
        view.doOnAttach {
            val lifecycleOwner = view.findViewTreeLifecycleOwner() ?: throw Exception()
            lifecycleOwner.lifecycleScope.launch {
                // Start normal refreshing tied using the LifecycleScope
                try {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        /* The while loop here enables refreshing the ad tied to the lifecycle */
                        while (isActive) {
                            val waitingPeriod = (30.seconds - timeSinceLoad).coerceAtLeast(ZERO)
                            Log.v("Ads", "[$adUnitId] Refreshing in $waitingPeriod")
                            delay(waitingPeriod)
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
                    }, layoutParams)
                    Log.i("Ads", "[$adUnitId] Ad Rendered: $timeSinceLoad")
                }
            }
        }
    }
}
