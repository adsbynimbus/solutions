package adsbynimbus.solutions.mediation.max

import android.content.Context
import androidx.startup.Initializer
import com.adsbynimbus.Nimbus
import kotlin.time.measureTime

lateinit var appContext: Context

class AdInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        appContext = context
        val nimbusStartup = measureTime {
            Nimbus.initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            Nimbus.testMode = true
        }
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
