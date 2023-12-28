package adsbynimbus.solutions.bidding

import android.*
import androidx.annotation.*
import androidx.core.view.*
import androidx.lifecycle.*
import com.adsbynimbus.*
import com.adsbynimbus.lineitem.*
import com.adsbynimbus.request.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.*

@RequiresPermission(Manifest.permission.INTERNET)
inline fun AdManagerAdView.loadAd(
    request: AdManagerAdRequest.Builder,
    bidders: () -> Collection<Bidder<*>>,
) = with(bidders()) {
    doOnAttach {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: throw Exception(
            "View must be hosted as a child of a ComponentActivity or Fragment"
        )

        lifecycleOwner.lifecycleScope.launch { loadAd(request, this@with) }
    }
}

@RequiresPermission(Manifest.permission.INTERNET)
suspend inline fun AdManagerAdView.loadAd(
    request: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
    bidders: Collection<Bidder<*>>,
) {
    val (bids, auctionTime) = measureTimedValue {
        withContext(Dispatchers.IO) { bidders.auction() }
    }

    val loadTime = measureTime {
        loadAdAsync(request.apply(bids::targeting).build())
    }
}

@RequiresPermission(Manifest.permission.INTERNET)
suspend inline fun AdManagerAdView.loadAdAsync(
    request: AdManagerAdRequest,
    listener: AdListener = adListener
) = suspendCancellableCoroutine { adLoad ->
    adListener = object : AdListener() {
        override fun onAdFailedToLoad(p0: LoadAdError) {
            adLoad.resumeWithException(Exception(p0.message))
            listener.onAdFailedToLoad(p0)
        }

        override fun onAdLoaded() = adLoad.resume(this).also { listener.onAdLoaded() }
    }

    adLoad.invokeOnCancellation { adListener = listener }

    loadAd(request)
}

fun Collection<Bid<*>>.targeting(builder: AdManagerAdRequest.Builder) = forEach {
    when(it.response) {
        is NimbusResponse -> it.response.applyDynamicPrice(builder)
    }
}.also { if (Nimbus.testMode) builder.addCustomTargeting("na_test", "force") }
