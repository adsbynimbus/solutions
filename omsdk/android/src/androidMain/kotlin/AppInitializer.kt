package com.adsbynimbus.android.omsdk

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.startup.Initializer
import com.adsbynimbus.IABVerificationProvider
import com.adsbynimbus.Nimbus
import com.adsbynimbus.ViewabilityProvider
import kotlin.time.measureTime

class AppInitializer : Initializer<Nimbus> {
    override fun create(context: Context): Nimbus = Nimbus.apply {
        val startupTime = measureTime {
            initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            testMode = true
            addLogger(Nimbus.Logger.Default(level = Log.VERBOSE))
        }

        ViewabilityProvider.verificationProviders.add(IABVerificationProvider)

        Log.i("Nimbus", "Startup Time: $startupTime")

        WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
