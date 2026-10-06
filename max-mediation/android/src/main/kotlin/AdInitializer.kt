package com.applovin.enterprise.apps.demoapp

import android.content.*
import androidx.startup.*
import com.adsbynimbus.*
import com.applovin.sdk.*
import com.google.android.gms.ads.identifier.*
import kotlinx.coroutines.*
import kotlin.time.*

const val key = "05TMDQ5tZabpXQ45_UTbmEGNUtVAzSTzT6KmWQc5_CuWdzccS4DCITZoL3yIWUG3bbq60QC_d4WF28tUC4gVTF"

class AdInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val nimbusStartup = measureTime {
            Nimbus.initialize(context, BuildConfig.PUBLISHER_KEY, BuildConfig.API_KEY)
            Nimbus.configuration.testMode = true
        }

        MainScope().launch(Dispatchers.IO) {
            // Initialize the AppLovin SDK
            with(AppLovinSdk.getInstance(context)) {
                initialize(
                    AppLovinSdkInitializationConfiguration.builder(key).run {
                        mediationProvider = AppLovinMediationProvider.MAX
                        AdvertisingIdClient.getAdvertisingIdInfo(context).id?.let {
                            testDeviceAdvertisingIds = listOf(it)
                        }
                        build()
                    }
                ) {
                    // AppLovin SDK is initialized, start loading ads now or later if ad gate is reached
                }
                settings.setVerboseLogging(true)
            }
        }
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
