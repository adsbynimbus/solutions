package adsbynimbus.solutions.dynamicprice.nextgen

import com.adsbynimbus.Nimbus
import com.adsbynimbus.NimbusAdManager
import com.adsbynimbus.dynamicprice.nextgen.applyDynamicPrice
import com.adsbynimbus.lineitem.DEFAULT_BANNER
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusResponse
import com.amazon.aps.ads.*
import com.amazon.aps.ads.listeners.ApsAdRequestListener
import com.amazon.device.ads.AdType
import com.google.android.libraries.ads.mobile.sdk.common.BaseAdRequestBuilder
import com.google.android.libraries.ads.mobile.sdk.common.BaseRequest
import kotlinx.coroutines.*
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
    yield() // Allows other work to proceed while requests are in-flight
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
    /** Convenience constructor for using lambda notation */
    constructor(adRequest: () -> NimbusRequest) : this(adRequest())

    override suspend fun fetchBid(): Bid<NimbusResponse> =
        Bid(nimbusAdManager.makeRequest(Nimbus.applicationContext, adRequest))
}

/** Loads a bid from Amazon using a coroutine [loadAsync] defined in AsyncUtils */
@JvmInline
value class ApsBidder(private val adRequest: () -> ApsAdRequest) : Bidder<ApsAd> {
    override suspend fun fetchBid(): Bid<ApsAd> = Bid(adRequest().loadAsync())
}

/** Loads an APS ad using a coroutine */
suspend inline fun ApsAdRequest.loadAsync(): ApsAd = suspendCancellableCoroutine { coroutine ->
    loadAd(object : ApsAdRequestListener {
        override fun onFailure(p0: ApsAdError) {
            if (coroutine.isActive) coroutine.resumeWithException(RuntimeException(p0.message))
        }

        override fun onSuccess(p0: ApsAd) {
            if (coroutine.isActive) coroutine.resume(p0)
        }
    })
}

/** Returns a map of key-value parameters used by APS */
inline val ApsAd.adManagerParams: Map<String, List<String>>
    get() = when (dtbAds.first().dtbAdType) {
        AdType.VIDEO -> defaultVideoAdsRequestCustomParams.mapValues { listOf(it.value) }
        else -> defaultDisplayAdsRequestCustomParams
    }

/** Applies targeting values from a Bid to an AdManagerAdRequest.Builder */
inline fun <reified T> Bid<out T>.applyTargeting(request: BaseAdRequestBuilder<*>) {
    when (response) {
        is NimbusResponse -> request.applyDynamicPrice(response, linearPriceMapping)
        is ApsAd if response.adCount > 0 -> response.adManagerParams.forEach {
            request.putCustomTargeting(it.key, it.value)
        }
    }
}

inline val BaseRequest.keyValues: String
    get() = customTargeting.entries.joinToString(
        separator = ",\n",
        prefix = "[\n",
        postfix = "]",
    )
