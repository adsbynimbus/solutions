package adsbynimbus.solutions.app.mediators

import adsbynimbus.solutions.bidding.*
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbynimbus.google.*
import com.adsbynimbus.openrtb.request.*
import com.adsbynimbus.request.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.*
import kotlinx.coroutines.launch

@Composable
fun AdManagerView(
    adUnit: String,
    adSizes: Collection<AdSize>,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdManagerAdView(context).apply {
                adUnitId = adUnit
                setAdSizes(*adSizes.toTypedArray())
                coroutineScope.launch {
                    setOnPaidEventListener {
                        Log.i("Dynamic Price", "PAID EVENT: ${it.valueMicros * 1000000f}")
                    }
                    setAppEventListener { name, info ->
                        handleEventForNimbus(name, info)
                    }
                    adListener = object : AdListener() { }
                    runCatching {
                        loadAd(
                            request = AdManagerAdRequest.Builder(),
                            bidders = listOf(
                                NimbusBidder { NimbusRequest.forBannerAd("MREC", Format.MREC) }
                            )
                        )
                    }
                }
            }
        },
        onRelease = { it.destroy() },
    )
}
