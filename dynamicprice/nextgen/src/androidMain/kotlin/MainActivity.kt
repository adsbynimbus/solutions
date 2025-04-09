package adsbynimbus.solutions.dynamicprice.nextgen

import android.os.Bundle
import android.util.Log
import android.view.Gravity.BOTTOM
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.launch
import kotlin.time.TimeSource.Monotonic

class MainActivity : ComponentActivity() {

    var interstitial: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply {
            addView(Button(context).apply {
                text = "Load Interstitial"
                setOnClickListener {
                    interstitial?.show(this@MainActivity) ?: run {
                        lifecycleScope.launch {
                            interstitial = loadInterstitial(listener = object : InterstitialAdEventCallback {
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
                            })?.also {
                                text = "Show Interstitial"
                            }
                        }
                    }
                }
            })
            adCache["banner"]?.let {
                val startTime = Monotonic.markNow()
                // Must set the listener before attaching to the parent view
                it.adListener = object : BannerAdEventCallback {
                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        Log.w("Ads", "Failed: ${fullScreenContentError.message}")
                    }

                    override fun onAdImpression() {
                        Log.i("Ads", "Impression: ${Monotonic.markNow() - startTime}")
                    }
                }
                it.attach(
                    activity = this@MainActivity,
                    view = this,
                    layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, BOTTOM)
                )
            }
        })
    }
}
