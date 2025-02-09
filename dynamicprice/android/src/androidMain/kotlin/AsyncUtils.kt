package adsbynimbus.solutions.dynamicprice.app

import android.graphics.Rect
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewTreeObserver
import com.amazon.device.ads.AdError
import com.amazon.device.ads.DTBAdCallback
import com.amazon.device.ads.DTBAdRequest
import com.amazon.device.ads.DTBAdResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Loads an APS ad using a coroutine */
suspend fun DTBAdRequest.loadAsync(): DTBAdResponse = suspendCancellableCoroutine { coroutine ->
    val callback = object : DTBAdCallback {
        override fun onFailure(p0: AdError) {
            if (coroutine.isActive) coroutine.resumeWithException(RuntimeException(p0.message))
        }

        override fun onSuccess(p0: DTBAdResponse) {
            if (coroutine.isActive) coroutine.resume(p0)
        }
    }
    loadAd(callback)
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
