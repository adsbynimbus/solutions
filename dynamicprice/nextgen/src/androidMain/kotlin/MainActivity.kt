package adsbynimbus.solutions.dynamicprice.nextgen

import adsbynimbus.solutions.dynamicprice.nextgen.BuildConfig.ADMANAGER_ADUNIT_ID
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import adsbynimbus.solutions.dynamicprice.nextgen.databinding.ActivityMainBinding
import com.adsbynimbus.NimbusError
import com.adsbynimbus.render.AdController
import com.adsbynimbus.render.AdEvent
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.launch
import kotlin.time.TimeSource.Monotonic

class MainActivity : ComponentActivity() {

    var interstitial: InterstitialAd? = null
    val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
    }
    val delegate: AdEventCallback = object : AdEventCallback {
        override fun onAdClicked() {
            Log.i("Ads", "Clicked")
        }

        override fun onAdDismissedFullScreenContent() {
            Log.i("Ads", "Dismissed FullScreen")
        }

        override fun onAdFailedToShowFullScreenContent(
            fullScreenContentError: FullScreenContentError,
        ) {
            Log.w("Ads", "Failed: ${fullScreenContentError.message}")
        }

        override fun onAdImpression() {
            Log.i("Ads", "Impression")
        }

        override fun onAdShowedFullScreenContent() {
            Log.i("Ads", "Showed FullScreen")
        }
    }
    val interstitialCallback: InterstitialAdEventCallback
        get() = object : InterstitialAdEventCallback, AdEventCallback by delegate { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.interstitial.apply {
            setOnClickListener {
                interstitial?.show(this@MainActivity) ?: lifecycleScope.launch {
                    interstitial = loadInterstitial(
                        builder = AdRequest.Builder(ADMANAGER_ADUNIT_ID),
                        bidders = interstitialBidders,
                        eventCallback = interstitialCallback,
                    )?.also {
                        text = "Show Interstitial"
                    }
                }
            }
        }
        lifecycleScope.launch {
            val startTime = Monotonic.markNow()
            loadRefreshingBanner(
                activity = this@MainActivity,
                container = binding.adFrame,
                builder = { BannerAdRequest.Builder(ADMANAGER_ADUNIT_ID, AdSize.BANNER) },
                bidders = bannerBidders,
                eventCallback = object : BannerAdEventCallback, AdEventCallback by delegate { },
                preloadBanner = adCache["banner"]?.await()?.apply {
                    // Must set the listener before attaching to the parent view
                    adEventCallback = object : BannerAdEventCallback, AdEventCallback by delegate {
                        override fun onAdImpression() {
                            Log.i("Ads", "Impression: ${startTime.elapsedNow()}")
                        }

                        // Listeners must call BannerAd.handleEventForNimbus
                        override fun onAppEvent(name: String, data: String?) {
                            handleEventForNimbus(
                                name = name,
                                data = data,
                                listener = object : AdController.Listener {
                                    override fun onAdEvent(adEvent: AdEvent) {
                                        Log.i("Nimbus Ads", adEvent.name)
                                    }

                                    override fun onError(error: NimbusError) {
                                        Log.w("Nimbus Ads", "${error.message}")
                                    }
                                },
                            )
                        }
                    }
                }
            )
        }
    }
}
