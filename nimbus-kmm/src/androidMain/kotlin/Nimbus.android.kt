package adsbynimbus.solutions.kmm

import android.annotation.*
import android.content.Context
import android.util.*
import com.adsbynimbus.Nimbus
import com.adsbynimbus.request.*

@SuppressLint("StaticFieldLeak")
lateinit var context: Context

actual typealias Nimbus = Nimbus

actual fun Nimbus.initialize(publisherKey: String, apiKey: String) =
    initialize(context, publisherKey, apiKey)

actual var Nimbus.endpointOverride: String?
    get() = RequestManager.getRequestUrl()
    set(value) { RequestManager.setRequestUrl(value) }

internal var logger: Nimbus.Logger? = null

actual var Nimbus.enableLogs: Boolean
    get() = logger != null
    set(value) {
        when {
            value && logger != null -> return
            value -> logger = Nimbus.Logger.Default(Log.VERBOSE).also { addLogger(it) }
            else -> logger?.let { removeLogger(it) }
        }
    }
