package adsbynimbus.solutions.dynamicprice.app

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.adsbynimbus.Nimbus
import com.amazon.device.ads.AdRegistration
import com.amazon.device.ads.MRAIDPolicy
import com.google.android.gms.ads.MobileAds
import kotlin.time.TimeSource.Monotonic
import kotlin.time.measureTime

@Suppress("unused")
class AdInitializer : Initializer<Map<String, DynamicPriceAd>> {
    override fun create(context: Context): Map<String, DynamicPriceAd> {
        // Set appContext in AdLoader.kt to use for preloading ads
        appContext = context
        val nimbusStartup = measureTime {
            Nimbus.initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            Nimbus.testMode = true
        }

        val amazonStartup = measureTime {
            AdRegistration.getInstance(BuildConfig.AMAZON_APP_KEY, context)
            AdRegistration.setMRAIDSupportedVersions(arrayOf("1.0", "2.0", "3.0"))
            AdRegistration.setMRAIDPolicy(MRAIDPolicy.DFP)
            AdRegistration.enableTesting(true)
            //AdRegistration.enableLogging(true)
        }

        adCache.put("banner", preloadBanner())

        val googleStart = Monotonic.markNow()
        MobileAds.initialize(context) {
            val googleStartup = Monotonic.markNow() - googleStart
            Log.i("Ads", "Nimbus: $nimbusStartup, Amazon: $amazonStartup, Google: $googleStartup")
        }
        return adCache
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
