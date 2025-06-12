package adsbynimbus.solutions.dynamicprice

import android.graphics.Rect
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewTreeObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
