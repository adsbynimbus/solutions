package adsbynimbus.solutions.bidding

import com.adsbynimbus.*
import com.adsbynimbus.request.*
import com.amazon.device.ads.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

class ApsBidder(val adLoader: () -> DTBAdRequest) : Bidder<DTBAdResponse> {
    override suspend fun fetchBid(): Bid<DTBAdResponse> = suspendCancellableCoroutine { mediator ->
        adLoader().loadAd(object : DTBAdCallback {
            override fun onFailure(error: AdError) {
                if (mediator.isActive) mediator.resumeWithException(Throwable(error.message))
            }

            override fun onSuccess(response: DTBAdResponse) {
                if (mediator.isActive) mediator.resume(Bid(response))
            }
        })
    }
}

class NimbusBidder(
    val manager: RequestManager = NimbusAdManager(),
    val requestProvider: () -> NimbusRequest,
) : Bidder<NimbusResponse> {
    override suspend fun fetchBid(): Bid<NimbusResponse> = suspendCancellableCoroutine { mediator ->
        manager.makeRequest(
            Nimbus.applicationContext, requestProvider(), object : RequestManager.Listener {
                override fun onError(error: NimbusError) {
                    if (mediator.isActive) mediator.resumeWithException(error)
                }

                override fun onAdResponse(nimbusResponse: NimbusResponse) {
                    if (mediator.isActive) mediator.resume(Bid(nimbusResponse))
                }
            }
        )
    }
}
