package adsbynimbus.solutions.dynamicprice.nextgen

import android.graphics.Rect
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewTreeObserver
import androidx.annotation.WorkerThread
import com.amazon.aps.ads.ApsAd
import com.amazon.aps.ads.ApsAdError
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.listeners.ApsAdRequestListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.AutoCloseable
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Loads an APS ad using a coroutine */
suspend fun ApsAdRequest.loadAsync(): ApsAd = suspendCancellableCoroutine { coroutine ->
    loadAd(object : ApsAdRequestListener {
        override fun onFailure(p0: ApsAdError) {
            if (coroutine.isActive) coroutine.resumeWithException(RuntimeException(p0.message))
        }

        override fun onSuccess(p0: ApsAd) {
            if (coroutine.isActive) coroutine.resume(p0)
        }
    })
}

/**
 * Suspend the current coroutine until the target View is visible on screen
 *
 * @param rect optional parameter to receive the visible rect when measured on screen
 */
suspend fun View.waitUntilVisible(rect: Rect = Rect()) {
    if (!isAttachedToWindow || !getGlobalVisibleRect(rect)) {
        var layoutListener: OnLayoutChangeListener? = null
        var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null
        try {
            suspendCancellableCoroutine { coroutine ->
                layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (coroutine.isActive && isAttachedToWindow && getGlobalVisibleRect(rect)) {
                        coroutine.resume(Unit)
                    }
                }
                scrollListener = ViewTreeObserver.OnScrollChangedListener {
                    if (coroutine.isActive && isAttachedToWindow && getGlobalVisibleRect(rect)) {
                        coroutine.resume(Unit)
                    }
                }
                viewTreeObserver.addOnScrollChangedListener(scrollListener)
                addOnLayoutChangeListener(layoutListener)
            }
        } finally {
            viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            removeOnLayoutChangeListener(layoutListener)
        }
    }
}

@JvmInline @WorkerThread
value class OneShotConnection(val connection: HttpURLConnection): AutoCloseable {

    constructor(url: String, timeout: Duration = 30.seconds) : this(
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout.inWholeMilliseconds.toInt()
        },
    )

    override fun close() { connection.disconnect() }

    inline val responseCode: Int get() = runCatching { connection.responseCode }.getOrDefault(-1)
}
