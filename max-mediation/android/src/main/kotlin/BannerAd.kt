package com.applovin.enterprise.apps.demoapp

import android.graphics.*
import android.view.*
import android.view.View.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.viewinterop.*
import com.applovin.mediation.*
import com.applovin.mediation.ads.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Composable
fun BannerAdScreen(modifier: Modifier = Modifier) {
    Box {
        MaxInlineAd(
            adUnitId = "5c524ca3afafbecf",
            adFormat = MaxAdFormat.BANNER,
            listener = DefaultMaxMediationListener(),
            modifier = modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun BannerVideoScreen(modifier: Modifier = Modifier) {
    MaxInlineAd(
        adUnitId = "fa8e3a4d5564d0a",
        adFormat = MaxAdFormat.MREC,
        listener = DefaultMaxMediationListener(),
        modifier = modifier,
    )
}

@Composable
fun MaxInlineAd(
    adUnitId: String,
    adFormat: MaxAdFormat,
    listener: MaxAdViewAdListener,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box { Text(text = "MAX Ads preview banner.", modifier.align(Alignment.Center)) }
        return
    }

    val adView = remember {
        MaxAdView(adUnitId, adFormat).apply {
            setListener(listener)
            loadAd()
        }
    }

    AndroidView(modifier = modifier.wrapContentSize(), factory = { adView })

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
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
