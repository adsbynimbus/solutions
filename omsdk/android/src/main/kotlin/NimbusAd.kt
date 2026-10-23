package com.adsbynimbus.android.omsdk

import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbynimbus.AdEvent
import com.adsbynimbus.InlineAd
import com.adsbynimbus.NimbusError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun NimbusAd(
    ad: InlineAd,
    modifier: Modifier = Modifier,
    scope: CoroutineScope = rememberCoroutineScope(),
    onError: NimbusError.Listener = { },
    onEvent: AdEvent.Listener = { },
) {
    val inlineAd: InlineAd = remember { ad.onError(onError).onEvent(onEvent) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                scope.launch { inlineAd.show(this@apply) }
            }
        },
        onRelease = { ad.destroy() },
    )
}
