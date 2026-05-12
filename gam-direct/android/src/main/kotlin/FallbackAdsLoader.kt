package adsbynimbus.solutions.gamdirect

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.ads.*
import androidx.core.net.toUri
import java.io.IOException

/**
 * A wrapper [AdsLoader] that delegates to a primary [ImaAdsLoader].
 * In case of failure, it allows for potential fallback logic.
 */
@UnstableApi
class FallbackAdsLoader(
    private val primaryLoader: ImaAdsLoader,
    private val fallbackAdTag: String,
    private val fallbackMarkup: String? = null
) : AdsLoader {

    private var isFallbackUsed = false
    private var player: Player? = null

    override fun setPlayer(player: Player?) {
        this.player = player
        primaryLoader.setPlayer(player)
    }

    override fun release() {
        primaryLoader.release()
    }

    override fun setSupportedContentTypes(vararg type: Int) {
        primaryLoader.setSupportedContentTypes(*type)
    }

    override fun start(
        adsMediaSource: AdsMediaSource,
        dataSpec: DataSpec,
        adsId: Any,
        adViewProvider: AdViewProvider,
        eventListener: AdsLoader.EventListener
    ) {
        primaryLoader.start(adsMediaSource, dataSpec, adsId, adViewProvider, eventListener)
    }

    override fun stop(adsMediaSource: AdsMediaSource, eventListener: AdsLoader.EventListener) {
        primaryLoader.stop(adsMediaSource, eventListener)
    }

    override fun handleContentTimelineChanged(adsMediaSource: AdsMediaSource, timeline: Timeline): Boolean =
        primaryLoader.handleContentTimelineChanged(adsMediaSource, timeline)

    override fun handlePrepareComplete(adsMediaSource: AdsMediaSource, adGroupIndex: Int, adIndexInAdGroup: Int) {
        primaryLoader.handlePrepareComplete(adsMediaSource, adGroupIndex, adIndexInAdGroup)
    }

    override fun handlePrepareError(
        adsMediaSource: AdsMediaSource,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: IOException,
    ) {
        if (!isFallbackUsed) {
            isFallbackUsed = true

            val currentPlayer = player ?: run {
                primaryLoader.handlePrepareError(adsMediaSource, adGroupIndex, adIndexInAdGroup, exception)
                return
            }

            val currentUri = adsMediaSource.mediaItem.localConfiguration?.uri ?: run {
                primaryLoader.handlePrepareError(adsMediaSource, adGroupIndex, adIndexInAdGroup, exception)
                return
            }

            // If markup is provided, we use setAdsResponse to bypass the ad request and force playback with fallback logic.
            // Otherwise, we fall back to a new request with the fallbackAdTag.
            if (fallbackMarkup != null) {
                val fallbackMediaItem = MediaItem.Builder()
                    .setUri(currentUri)
                    .setAdsConfiguration(AdsConfiguration.Builder(fallbackAdTag.toUri()).build())
                    .build()

                currentPlayer.setMediaItem(fallbackMediaItem)
                currentPlayer.prepare()
            } else {
                val fallbackMediaItem = MediaItem.Builder()
                    .setUri(currentUri)
                    .setAdsConfiguration(AdsConfiguration.Builder(fallbackAdTag.toUri()).build())
                    .build()

                currentPlayer.setMediaItem(fallbackMediaItem)
                currentPlayer.prepare()
            }
        } else {
            primaryLoader.handlePrepareError(adsMediaSource, adGroupIndex, adIndexInAdGroup, exception)
        }
    }
}
