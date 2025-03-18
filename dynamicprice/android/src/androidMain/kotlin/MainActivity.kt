package adsbynimbus.solutions.dynamicprice

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import kotlin.time.TimeSource.Monotonic

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply {
            val preloadBanner = adCache["banner"] ?: return
            val startTime = Monotonic.markNow()
            // Must set the listener before attaching to the parent view
            preloadBanner.adView.adListener = object : AdListener() {
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
            addView(preloadBanner.adView)
        })
    }
}
