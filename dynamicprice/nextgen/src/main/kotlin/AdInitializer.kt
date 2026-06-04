package adsbynimbus.solutions.dynamicprice.nextgen

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.adsbynimbus.Nimbus
import com.adsbynimbus.Nimbus.Logger
import com.adsbynimbus.request.FANDemandProvider
import com.adsbynimbus.request.OkHttpNimbusClient
import com.adsbynimbus.request.RequestManager
import com.adsbynimbus.request.internal.AdUnitType
import com.amazon.device.ads.*
import com.facebook.ads.AdSettings
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.logging.Level
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
        Nimbus.addLogger(Logger.Default(level = Log.VERBOSE))
        FANDemandProvider.initialize(context, "")
        FANDemandProvider.forceTestAd = true
        RequestManager.interceptors.add {
            it.request.imp[0].ext.apply {
                if(adUnitType == AdUnitType.Rewarded.value && facebook_test_ad_type != null) {
                    facebook_test_ad_type = AdSettings.TestAdType.VIDEO_HD_16_9_15S_LINK.adTypeString
                }
            }
        }
        RequestManager.setClient(
            OkHttpNimbusClient(
                OkHttpClient.Builder()
                    .addInterceptor(
                        HttpLoggingInterceptor { Log.d("Ads", it) }
                            .setLevel(HttpLoggingInterceptor.Level.BODY)
                    )
            )
        )

        val amazonInit = measureTime {
            AdRegistration.getInstance(BuildConfig.AMAZON_APP_KEY, context)
            AdRegistration.setMRAIDSupportedVersions(arrayOf("1.0", "2.0", "3.0"))
            AdRegistration.setMRAIDPolicy(MRAIDPolicy.DFP)
            AdRegistration.enableTesting(true)
            //AdRegistration.enableLogging(true)
        }

       // adCache["banner"] = preloadBanner(bidders = bannerBidders)

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
