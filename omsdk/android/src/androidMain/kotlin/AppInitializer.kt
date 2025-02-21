package com.adsbynimbus.android.omsdk

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.startup.Initializer
import com.adsbynimbus.IABVerificationProvider
import com.adsbynimbus.Nimbus
import com.adsbynimbus.ViewabilityProvider
import com.adsbynimbus.render.Renderer
import com.adsbynimbus.render.StaticAdRenderer
import com.adsbynimbus.render.VastRenderer
import com.adsbynimbus.request.OkHttpNimbusClient
import kotlin.time.measureTime

@Suppress("unused")
class AppInitializer : Initializer<Nimbus> {
    override fun create(context: Context): Nimbus = Nimbus.apply {
        val videoRenderer = VastRenderer()
        val startupTime = measureTime {
            initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY, components =
                setOf(OkHttpNimbusClient(), StaticAdRenderer(), videoRenderer)
            )
            testMode = true
            addLogger(Nimbus.Logger.Default(level = Log.VERBOSE))
        }

        Renderer.INLINE.put("video", videoRenderer)
        VastRenderer.showMuteButton = true
        ViewabilityProvider.verificationProviders.add(IABVerificationProvider)

        Log.i("Nimbus", "Startup Time: $startupTime")

        WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
