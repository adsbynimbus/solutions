@file:OptIn(ExperimentalForeignApi::class)

package adsbynimbus.solutions.kmm

import kotlinx.cinterop.*
import platform.NimbusKit.Nimbus as SDK

actual object Nimbus {
    actual var testMode: Boolean
        get() = SDK.testMode()
        set(value) = SDK.setTestMode(value)
    actual val version: String = SDK.sdkVersion()
}

actual fun Nimbus.initialize(publisherKey: String, apiKey: String) =
    SDK.initializeWithPublisher(publisherKey, apiKey)

actual var Nimbus.endpointOverride: String?
    get() = SDK.endpointOverride()
    set(value) = SDK.setEndpointOverride(value)

actual var Nimbus.enableLogs: Boolean
    get() = SDK.enableLogs()
    set(value) { SDK.setEnableLogs(value) }
