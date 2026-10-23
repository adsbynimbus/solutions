package com.adsbynimbus.android.omsdk

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.startup.Initializer
import com.adsbynimbus.*
import com.adsbynimbus.Nimbus.Logger
import com.iab.omid.library.adsbynimbus.adsession.VerificationScriptResource
import java.net.URL
import kotlin.time.measureTime

class AppInitializer : Initializer<Nimbus> {
    override fun create(context: Context): Nimbus = Nimbus.apply {
        val startupTime = measureTime {
            initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            configuration.testMode = true
            Logger.add(Logger.Default(level = Log.VERBOSE))
        }

        //configuration.verificationProviders

        Log.i("Nimbus", "Startup Time: $startupTime")

        WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}

object IABVerificationProvider : Configuration.VerificationProvider {
    override fun verificationMarkup(ad: NimbusResponse): String =
        """<script src="${BuildConfig.IAB_VERIFICATION_URL}" type="text/javascript"></script>"""

    override fun verificationResource(ad: NimbusResponse): VerificationScriptResource =
        VerificationScriptResource.createVerificationScriptResourceWithParameters(
            "iabtechlab.com-omid", URL(BuildConfig.IAB_VERIFICATION_URL), "iabtechlab-Adsbynimbus",
        )
}
