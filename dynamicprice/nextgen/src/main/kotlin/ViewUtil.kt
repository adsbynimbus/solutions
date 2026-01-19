package adsbynimbus.solutions.dynamicprice.nextgen

import android.graphics.Rect
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewTreeObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sets minimum width and height to 1 if values are less than 0.
 *
 * Used in conjunction with [waitUntilVisible] to prevent [View.getGlobalVisibleRect] from returning
 * false due to the width or height measuring as 0.
 */
fun View.requestVisibleLayout() = apply {
    if (minimumWidth <= 0) minimumWidth = 1
    if (minimumHeight <= 0) minimumHeight = 1
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
