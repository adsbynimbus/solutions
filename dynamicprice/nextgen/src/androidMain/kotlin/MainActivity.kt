package adsbynimbus.solutions.dynamicprice.nextgen

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import kotlin.time.TimeSource.Monotonic

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply {
            val preloadBanner = adCache["banner"] ?: return
            val startTime = Monotonic.markNow()
            // Must set the listener before attaching to the parent view
            preloadBanner.adListener = object : BannerAdEventCallback {
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    Log.w("Ads", "Failed: ${fullScreenContentError.message}")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.i("Ads", "Impressions: ${Monotonic.markNow() - startTime}")
                }
            }
            preloadBanner.attach(this@MainActivity, this)
        })
    }
}
