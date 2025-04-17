package adsbynimbus.solutions.dynamicprice.nextgen

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.adsbynimbus.Nimbus
import com.amazon.device.ads.AdRegistration
import com.amazon.device.ads.MRAIDPolicy
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.*
import kotlin.time.TimeSource.Monotonic
import kotlin.time.measureTime

class AdInitializer : Initializer<Map<String, Deferred<BannerAd?>>> {

    inline val adManagerConfig
        get() = InitializationConfig.Builder(BuildConfig.ADMANAGER_APP_ID).build()

    @SuppressLint("MissingPermission")
    override fun create(context: Context): Map<String, Deferred<BannerAd?>> {
        val nimbusInit = measureTime {
            Nimbus.initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            Nimbus.testMode = true
        }

        val amazonInit = measureTime {
            AdRegistration.getInstance(BuildConfig.AMAZON_APP_KEY, context)
            AdRegistration.setMRAIDSupportedVersions(arrayOf("1.0", "2.0", "3.0"))
            AdRegistration.setMRAIDPolicy(MRAIDPolicy.DFP)
            AdRegistration.enableTesting(true)
            //AdRegistration.enableLogging(true)
        }

        adCache.put("banner", preloadBanner(bidders = bannerBidders))

        val googleStart = Monotonic.markNow()
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(context, adManagerConfig) {
                completed = true
                val googleInit = Monotonic.markNow() - googleStart
                Log.i("Ads", "Nimbus: $nimbusInit, Amazon: $amazonInit, Google: $googleInit, Google Reported: ${it.totalLatency}")
            }
        }

        return adCache
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()

    companion object {
        @Volatile @JvmField
        var completed = false
    }
}
