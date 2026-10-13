package adsbynimbus.solutions.mediation.max

import android.graphics.Rect
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Composable
fun BannerAdScreen(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val window = LocalWindowInfo.current
    Box {
        MaxInlineAd(
            onLoadAd = {

            },
            modifier = modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun BannerVideoScreen(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    MaxInlineAd(
        onLoadAd = {

        },
        modifier = modifier,
    )
}

@Composable
fun MaxInlineAd(
    onLoadAd: (FrameLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box { Text(text = "MAX Ads preview banner.", modifier.align(Alignment.Center)) }
        return
    }

    val context = LocalContext.current
    val adView = remember { FrameLayout(context) }

    AndroidView(modifier = modifier.wrapContentSize(), factory = { adView })

    onLoadAd(adView)

    DisposableEffect(Unit) {
        onDispose {  }
    }
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
