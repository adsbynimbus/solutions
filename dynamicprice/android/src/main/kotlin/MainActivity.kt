package adsbynimbus.solutions.dynamicprice

import adsbynimbus.solutions.dynamicprice.BuildConfig.ADMANAGER_ADUNIT_ID
import android.os.Bundle
import android.util.Log
import android.view.Gravity.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import kotlinx.coroutines.launch
import kotlin.time.TimeSource.Monotonic

class MainActivity : ComponentActivity() {

    var interstitial: AdManagerInterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).also { frame ->
            frame.fitsSystemWindows = true
            val preloadBanner = adCache["banner"] ?: return
            val startTime = Monotonic.markNow()
            // Must set the listener before attaching to the parent view
            preloadBanner.view.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.i("Ads", "Loaded: ${Monotonic.markNow() - startTime}")
                }

                override fun onAdImpression() {
                    Log.i("Ads", "Impressions: ${Monotonic.markNow() - startTime}")
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.w("Ads", "Failed: ${p0.message}")
                }
            }
            frame.addView(preloadBanner.view, LayoutParams(MATCH_PARENT, WRAP_CONTENT, BOTTOM))

            frame.addView(Button(this).apply {
                text = getString(R.string.loadInterstitial)
                setOnClickListener {
                    interstitial?.show(this@MainActivity) ?: lifecycleScope.launch {
                        runCatching {
                            interstitial = loadInterstitial(
                                adUnitId = ADMANAGER_ADUNIT_ID,
                                bidders = interstitialBidders,
                            )
                            text = getString(R.string.showInterstitial)
                        }
                    }
                }
            }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, TOP or CENTER_HORIZONTAL))
        })
    }
}
