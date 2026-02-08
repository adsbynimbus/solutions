package com.adsbynimbus.android.omsdk

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adsbynimbus.*
import com.adsbynimbus.openrtb.enumerations.PlacementType.IN_STREAM
import com.adsbynimbus.render.*
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusResponse
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Throws suspend fun ViewGroup.doublePreroll(
    adManager: NimbusAdManager = NimbusAdManager(),
    loadTimeout: Duration = 3.seconds,
    onVideoResponse: (Int, NimbusResponse) -> Unit = { _, _ -> },
) {
    // There is a bug in the SDK that prevents the 2nd video from starting playback
    // which can be fixed by the following line of code.
    Renderer.INLINE.remove("test_demand")
    var currentVideo: AdController? = null
    try {
        coroutineScope {
            val request = NimbusRequest.forVideoAd("preroll").apply {
                request.imp[0].video?.apply {
                    maxduration = 15
                    // The following two parameters indicate this video request is preroll
                    placement = IN_STREAM
                    startdelay = -1
                }
            }

            // Request and render the first pre-roll video; playback will start once the ViewGroup
            // is at least 25% visible on screen.
            val firstBid = withContext(Dispatchers.IO) { adManager.makeRequest(context, request) }
            onVideoResponse(0, firstBid)
            currentVideo = withContext(Dispatchers.Main) { render(ad = firstBid) }
            // As a safeguard we add a timeout to ensure the LOADED event fires
            withTimeout(loadTimeout) { currentVideo?.waitFor(AdEvent.LOADED) }

            // Next we want to wait for the video to progress before requesting the next ad; the
            // third quartile will leave ~3-4 seconds for the next video to load
            currentVideo.waitFor(event = AdEvent.THIRD_QUARTILE)

            // Start the request for the 2nd video async but do not block
            val secondBid = async {
                withContext(Dispatchers.IO) { adManager.makeRequest(context, request) }.also {
                    onVideoResponse(1, it)
                }
            }
            currentVideo.waitFor(event = AdEvent.COMPLETED)

            currentVideo = withContext(Dispatchers.Main) {
                currentVideo?.destroy()
                render(ad = secondBid.await())
            }

            withTimeout(loadTimeout) { currentVideo.waitFor(AdEvent.IMPRESSION) }

            // Finally, we suspend the coroutine until the 2nd video finishes
            currentVideo.waitFor(event = AdEvent.COMPLETED)
        }
    } finally {
        currentVideo?.destroy()
    }
}

/** Coroutine implementation of Nimbus render method */
suspend inline fun ViewGroup.render(ad: NimbusAd) = suspendCancellableCoroutine { coroutine ->
    Renderer.loadAd(ad, this@render, object : Renderer.Listener, NimbusError.Listener {
        override fun onAdRendered(controller: AdController) {
            if (coroutine.isActive) coroutine.resume(controller) { _, unusedController, _ ->
                unusedController.destroy()
            }
        }

        override fun onError(error: NimbusError) {
            if (coroutine.isActive) coroutine.resumeWithException(error)
        }
    })
}

/**
 * Suspends a coroutine until an AdController event has fired or resumes with an exception if
 * the AdController is destroyed early or an error occurs.
 */
suspend inline fun AdController.waitFor(event: AdEvent): AdController =
    suspendCancellableCoroutine { coroutine ->
        val waitForListener = object : AdController.Listener {
            override fun onAdEvent(adEvent: AdEvent) {
                when (adEvent) {
                    event if (coroutine.isActive) -> coroutine.resume(this@waitFor)
                    AdEvent.DESTROYED if (coroutine.isActive) -> coroutine.resumeWithException(
                        Throwable("Ad Destroyed while waiting for event")
                    )
                    else -> return
                }
            }

            override fun onError(error: NimbusError) {
                if (coroutine.isActive) coroutine.resumeWithException(error)
            }
        }
        coroutine.invokeOnCancellation {
            listeners.remove(waitForListener)
        }
        listeners.add(waitForListener)
}

@Composable
fun DoublePreroll(
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                coroutineScope.launch {
                    try {
                        doublePreroll { index, ad ->
                            Log.i("DoublePreroll", "Preroll[$index]: ${ad.bid.bid_raw}")
                        }
                    } catch (e: Exception) {
                        Log.d("DoublePreroll", e.message ?: "Preroll canceled")
                    }
                }
            }
        },
        onRelease = { coroutineScope.cancel() },
    )
}
