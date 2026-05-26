@file:OptIn(ExperimentalTime::class)

package adsbynimbus.solutions.gamdirect

import android.content.*
import android.util.*
import android.widget.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import com.adsbynimbus.*
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.render.*
import com.adsbynimbus.request.*
import com.amazon.aps.ads.*
import com.amazon.aps.ads.listeners.*
import com.amazon.aps.ads.model.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.*
import com.google.android.gms.ads.formats.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@JvmInline
value class AdManagerAdUnit(val id: String) {
    val adSize: AdSize
        get() = when (id) {
            BuildConfig.ADMANAGER_ADUNIT_ID -> AdSize.BANNER
            else -> AdSize.MEDIUM_RECTANGLE
        }
}

@Composable
fun GAMDirectView(
    adManagerAdUnit: AdManagerAdUnit,
    nimbusAdSize: Format,
    admobBiddingId: String = "",
    apsSlotId: String = "",
    googleListener: AdListener? = null,
    nimbusListener: AdController.Listener? = null,
) {
    AndroidView(
        factory = { GAMDirectView(it) },
        update = {
            it.apsSlotId = apsSlotId
            it.admobBiddingId = admobBiddingId
            it.adManagerAdUnit = adManagerAdUnit
            it.googleListener = googleListener
            it.nimbusAdSize = nimbusAdSize
            it.nimbusListener = nimbusListener
        }
    )
}

class GAMDirectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), Renderer.Listener, NimbusError.Listener {

    private var lastRequestTime = TimeSource.Monotonic.markNow() - 60.seconds
    private lateinit var adLoader: SuspendAdLoader

    var apsSlotId: String = ""
    var admobBiddingId: String = ""

    var adManagerAdUnit: AdManagerAdUnit = AdManagerAdUnit("")
        set(value) {
            field = value
            adLoader = SuspendAdLoader(context, value.id, value.adSize)
        }
    var nimbusAdSize: Format = Format.BANNER_320_50

    var adController: AdController? = null
    var googleListener: AdListener? = null
    var nimbusListener: AdController.Listener? = null
    var refreshJob: Job? = null
    val requestManager by lazy { NimbusAdManager() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshJob = findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            while (isActive) {
                delay(30.seconds - lastRequestTime.elapsedNow())
                waitUntilVisible()
                if (isActive) loadAd()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshJob?.cancel()
    }

    suspend fun loadAd() {
        lastRequestTime = TimeSource.Monotonic.markNow()

        val directAdView = runCatching {
            // Additional targeting should be appended before building
            val adManagerAdRequest = AdManagerAdRequest.Builder()
                .addCustomTargeting("nimbus", "true")
                .build()

            adLoader.loadAd(adManagerAdRequest).apply {
                googleListener?.let { adListener = it }
            }
        }.onSuccess {
            adController?.destroy()
            adController = null
            removeAllViews()
            addView(it)
        }

        if (directAdView.isFailure) {
            val nimbusRequest = NimbusRequest.forBannerAd(
                position = adManagerAdUnit.id,
                format = nimbusAdSize,
            )
            if (apsSlotId.isNotEmpty()) runCatching {
                nimbusRequest.addApsResponse(
                    ApsAdRequest(apsSlotId, ApsAdNetworkInfo(ApsAdNetwork.NIMBUS)).loadAsync(),
                )
            }
            if (admobBiddingId.isNotEmpty()) nimbusRequest.withAdMobBanner(admobBiddingId)

            runCatching {
                requestManager.makeRequest(context, nimbusRequest)
            }.onSuccess {
                Renderer.loadAd(it, this, this)
            }
        }
    }

    override fun onAdRendered(controller: AdController) {
        adController?.destroy()
        adController = controller
        nimbusListener?.let { controller.listeners.add(it) }
        if (childCount > 1) removeViews(0, childCount - 1)
    }

    override fun onError(error: NimbusError) {}
}

/** Loads an APS ad using a coroutine */
suspend inline fun ApsAdRequest.loadAsync(): ApsAd = suspendCancellableCoroutine { coroutine ->
    loadAd(
        object : ApsAdRequestListener {
            override fun onFailure(p0: ApsAdError) {
                if (coroutine.isActive) coroutine.resumeWithException(RuntimeException(p0.message))
            }

            override fun onSuccess(p0: ApsAd) {
                if (coroutine.isActive) coroutine.resume(p0)
            }
        },
    )
}

class SuspendAdLoader(
    context: Context,
    adUnitId: String,
    vararg adSizes: AdSize,
) : AdListener(), OnAdManagerAdViewLoadedListener {
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
