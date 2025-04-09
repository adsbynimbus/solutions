package adsbynimbus.solutions.compose.app

import android.util.*
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.*
import com.adsbynimbus.*
import com.adsbynimbus.openrtb.request.*
import com.adsbynimbus.render.*
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import kotlin.Pair

@Composable
actual fun AdView(modifier: Modifier, position: String, size: Pair<Int, Int>) {
    val adManager by remember { mutableStateOf(NimbusAdManager()) }
    var adController: AdController? by remember { mutableStateOf(null) }
    AndroidView(
        modifier = modifier.requiredSizeIn(minWidth = size.first.dp, minHeight = size.second.dp),
        factory = { context ->
            FrameLayout(context).apply {
                adManager.showAd(
                    request = forBannerAd(position = position, format = Format(size.first, size.second)),
                    viewGroup = this,
                    object : NimbusAdManager.Listener {
                        override fun onAdRendered(controller: AdController) {
                            adController = controller
                        }

                        override fun onError(error: NimbusError) {
                            error.message?.let { Log.e(null, it) }
                        }
                    }
                )
            }
        },
        onRelease = { adController?.destroy() },
    )
}
