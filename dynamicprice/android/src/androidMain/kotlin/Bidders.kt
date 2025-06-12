package adsbynimbus.solutions.dynamicprice

import com.adsbynimbus.NimbusAdManager
import com.adsbynimbus.lineitem.DEFAULT_BANNER
import com.adsbynimbus.lineitem.applyDynamicPrice
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusResponse
import com.amazon.device.ads.AdError
import com.amazon.device.ads.DTBAdCallback
import com.amazon.device.ads.DTBAdRequest
import com.amazon.device.ads.DTBAdResponse
import com.amazon.device.ads.DTBAdUtil
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Interface representing a bidder that runs before the AdManager request */
sealed interface Bidder<T> {
    suspend fun fetchBid(): Bid<T>
}

/** A response from a successful call to a [Bidder] */
@JvmInline
value class Bid<T>(val response: T)

/** Runs a parallel auction with enforced timeout for a collection of bidders */
suspend inline fun Collection<Bidder<*>>.auction(
    timeout: Duration = 3000.milliseconds,
): List<Bid<*>> = supervisorScope {
    val bidRequests = map {
        async { withTimeout(timeout) { it.fetchBid() } }
    }
    yield()
    bidRequests.mapNotNull {
        runCatching { it.await() }.getOrNull()
    }
}

/** A global instance of the NimbusAdManager for loading all Nimbus ads */
val nimbusAdManager by lazy { NimbusAdManager() }

/** Price Mapping used by Nimbus, should be replaced by Publisher specific price mapping */
val linearPriceMapping = DEFAULT_BANNER

/** Loads a bid from Nimbus using the global NimbusAdManager instance */
@JvmInline
value class NimbusBidder(private val adRequest: NimbusRequest) : Bidder<NimbusResponse> {
    constructor(adRequest: () -> NimbusRequest) : this(adRequest())
    // appContext is defined in DynamicPriceAd.kt and set in the AdInitializer
    override suspend fun fetchBid(): Bid<NimbusResponse> =
        Bid(nimbusAdManager.makeRequest(appContext, adRequest))
}

/** Loads a bid from Amazon using a coroutine [loadAsync] defined in AsyncUtils */
@JvmInline
value class ApsBidder(private val adRequest: () -> DTBAdRequest) : Bidder<DTBAdResponse> {
    override suspend fun fetchBid(): Bid<DTBAdResponse> = Bid(adRequest().loadAsync())
}

/** Loads an APS ad using a coroutine */
suspend fun DTBAdRequest.loadAsync(): DTBAdResponse = suspendCancellableCoroutine { coroutine ->
    val callback = object : DTBAdCallback {
        override fun onFailure(p0: AdError) {
            if (coroutine.isActive) coroutine.resumeWithException(RuntimeException(p0.message))
        }

        override fun onSuccess(p0: DTBAdResponse) {
            if (coroutine.isActive) coroutine.resume(p0)
        }
    }
    loadAd(callback)
}

/** Applies targeting values from a Bid to an AdManagerAdRequest.Builder */
inline fun <reified T> Bid<out T>.applyTargeting(request: AdManagerAdRequest.Builder) {
    when (response) {
        is NimbusResponse -> request.applyDynamicPrice(ad = response, mapping = linearPriceMapping)
        is DTBAdResponse -> DTBAdUtil.INSTANCE.loadDTBParams(request, response)
    }
}
