@file:OptIn(ExperimentalTime::class)

package adsbynimbus.solutions.gamdirect

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.adsbynimbus.render.AdController
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.addApsResponse
import com.amazon.aps.ads.*
import com.amazon.aps.ads.listeners.ApsAdRequestListener
import com.amazon.aps.ads.model.ApsAdNetwork
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.formats.OnAdManagerAdViewLoadedListener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.*

class GAMDirectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var lastRequestTime: Instant = Instant.DISTANT_PAST
    private lateinit var adLoader: SuspendAdLoader

    var apsSlotId: String = ""
    lateinit var admobBiddingId: String
    lateinit var nimbusRequest: NimbusRequest

    var googleListener: AdListener? = null
    var nimbusListener: AdController.Listener? = null

    suspend fun loadAd() {
        lastRequestTime = Clock.System.now()

        val directAdView = runCatching {
            val adManagerAdRequest = AdManagerAdRequest.Builder().apply {
                // Any targeting values used for Ad Manager should be appended here
                addCustomTargeting("nimbus", "true")
            }.build()

            adLoader.loadAd(adManagerAdRequest).apply {
                googleListener?.let { adListener = it }
            }
        }

        val nextAd = directAdView.getOrElse {
            apsSlotId.takeIf { it.isNotEmpty() }?.let {
                ApsAdRequest(it, ApsAdNetworkInfo(ApsAdNetwork.NIMBUS)).loadAsync().apply {
                    nimbusRequest.addApsResponse(this)
                }
            }
        }
    }
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

class SuspendAdLoader(
    context: Context,
    adUnitId: String,
    vararg adSizes: AdSize,
): AdListener(), OnAdManagerAdViewLoadedListener {
    internal val adLoader: AdLoader = AdLoader.Builder(context, adUnitId)
        .forAdManagerAdView(this, *adSizes)
        .withAdListener(this)
        .build()
    private var continuation: CancellableContinuation<AdManagerAdView>? = null

    override fun onAdManagerAdViewLoaded(p0: AdManagerAdView) {
        continuation?.resume(p0)
        continuation = null
    }

    override fun onAdFailedToLoad(p0: LoadAdError) {
        continuation?.resumeWithException(RuntimeException(p0.message))
        continuation = null
    }

    suspend fun loadAd(adRequest: AdManagerAdRequest): AdManagerAdView {
        if (adLoader.isLoading || continuation != null) {
            throw IllegalStateException("Previous load has not completed")
        }
        return suspendCancellableCoroutine {
            continuation = it
            adLoader.loadAd(adRequest)
        }
    }
}
