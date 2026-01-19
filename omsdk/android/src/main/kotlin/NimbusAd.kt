package com.adsbynimbus.android.omsdk

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbynimbus.NimbusAdManager
import com.adsbynimbus.render.AdController
import com.adsbynimbus.request.NimbusRequest

@Composable
fun NimbusAd(
    request: NimbusRequest,
    modifier: Modifier = Modifier,
    adManager: NimbusAdManager = remember { NimbusAdManager() },
    listener: AdController.Listener? = null,
) {
    var controller: AdController? by remember { mutableStateOf(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                adManager.showAd(request, this) {
                    controller = it.apply {
                        if (listener != null) listeners.add(listener)
                    }
                }
            }
        },
        onRelease = { controller?.destroy() },
    )
}
