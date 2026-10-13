package adsbynimbus.solutions.mediation.max

import android.content.Context
import androidx.startup.Initializer
import com.adsbynimbus.Nimbus
import com.amazon.device.ads.*
import kotlin.time.measureTime

lateinit var appContext: Context

class AdInitializer : Initializer<Unit> {
    override fun create(context: Context) {
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
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
