package adsbynimbus.solutions.dynamicprice.nextgen

import adsbynimbus.solutions.dynamicprice.nextgen.BuildConfig.ADMANAGER_ADUNIT_ID
import adsbynimbus.solutions.dynamicprice.nextgen.databinding.ActivityMainBinding
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout.LayoutParams
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.adsbynimbus.NimbusError
import com.adsbynimbus.dynamicprice.nextgen.handleEventForNimbus
import com.adsbynimbus.render.*
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.interstitial.*
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
        get() = object : InterstitialAdEventCallback, AdEventCallback by delegate {}

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
                        setText(R.string.showInterstitial)
                    }
                }
            }
        }
        lifecycleScope.launch {
            val startTime = Monotonic.markNow()
            val preloadBanner = adCache["banner"]
            AdView(binding.adFrame.context).apply {
                binding.adFrame.addView(this)
                // Ad must be attached or a lifecycleOwner must be passed to loadRefreshingBanner
                loadRefreshingBanner(
                    activity = this@MainActivity,
                    builder = { BannerAdRequest.Builder(ADMANAGER_ADUNIT_ID, AdSize.BANNER) },
                    bidders = bannerBidders,
                    eventCallback = object : BannerAdEventCallback, AdEventCallback by delegate {},
                    preloadBanner = preloadBanner?.await()?.apply {
                        // Must set the listener before attaching to the parent view
                        adEventCallback =
                            object : BannerAdEventCallback, AdEventCallback by delegate {
                                override fun onAdImpression() {
                                    Log.i("Ads", "Impression: ${startTime.elapsedNow()}")
                                }

                                // Listeners must call AdView.handleEventForNimbus
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
                                    )?.let { nimbusAd ->
                                        Log.i("Ads", "Nimbus ${nimbusAd.network()} won auction")
                                    }
                                }
                            }
                    },
                )
            }
            AdView(binding.adFrame.context).apply {
                binding.root.addView(this, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
                loadBanner(
                    builder = BannerAdRequest.Builder(
                        adUnitId = ADMANAGER_ADUNIT_ID,
                        adSize = AdSize(320, 480),
                    ),
                    bidders = dynamicUnitBidders,
                    eventCallback = object : BannerAdEventCallback, AdEventCallback by delegate {},
                )
            }
        }
    }
}
