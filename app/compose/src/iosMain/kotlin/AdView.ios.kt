package adsbynimbus.solutions.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.*
import kotlinx.cinterop.*
import platform.NimbusKit.*
import platform.UIKit.*

@Composable @OptIn(ExperimentalForeignApi::class)
actual fun AdView(modifier: Modifier, position: String, size: Pair<Int, Int>) {
    var adController: NimbusAdController? by remember { mutableStateOf(null) }
    UIKitView(
        modifier = modifier.size(size.first.dp, size.second.dp),
        factory = {
            UIView().apply {
                Nimbus.showAd(
                    request = NimbusRequestWrapper.forBannerAd(position,
                        size.first.toLong(), size.second.toLong()),
                    container = this as objcnames.classes.UIView,
                    onAdReady = { adController = it },
                )
            }
        },
        onRelease = { adController?.destroy() },
    )
}
