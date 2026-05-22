@file:UnstableApi

package com.adsbynimbus.solutions.gamdirect.instream

import android.content.*
import android.os.*
import android.view.*
import androidx.annotation.IntRange
import androidx.media3.common.*
import androidx.media3.common.Player.*
import androidx.media3.common.util.*
import androidx.media3.common.util.Util.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.source.ads.*
import androidx.media3.exoplayer.source.ads.AdsLoader
import com.adsbynimbus.openrtb.enumerations.*
import com.adsbynimbus.request.*
import com.adsbynimbus.solutions.gamdirect.instream.internal.NimbusImaAdTagLoader
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.*
import com.google.ads.interactivemedia.v3.api.AdEvent.*
import com.google.ads.interactivemedia.v3.api.player.*
import kotlin.collections.*

/**
 * [AdsLoader] using the IMA SDK. All methods must be called on the main thread.
 *
 * The player instance that will play the loaded ads must be set before playback using [setPlayer].
 * If the ads loader is no longer required, it must be released by calling [release].
 *
 * See [IMA's Support and compatibility page](https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility)
 * for information on compatible ad tag formats. Pass the ad tag URI when setting media item
 * playback properties (if using the media item API) when constructing the [AdsMediaSource] (if
 * using media sources directly). For the  latter case, please note that this implementation
 * delegates loading of the data spec to the IMA SDK, so range and headers specifications will be
 * ignored in ad tag URIs. Literal ads responses can be encoded as data scheme data specs, for
 * example, by constructing the data spec using a URI generated via [Util.getDataUriForString].
 *
 * The IMA SDK can report obstructions to the ad view for accurate viewability measurement. This
 * means that any overlay views that obstruct the ad overlay but are essential for playback need to
 * be registered via the [AdViewProvider] passed to the [AdsMediaSource]. See the
 * [IMA SDK Open Measurement documentation](https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/omsdk)
 * for more information.
 */
class NimbusImaAdLoader(
    internal val context: Context,
    internal val imaSdkSettings: ImaSdkSettings?,
    internal val adErrorListener: AdErrorListener?,
    internal val adEventListener: AdEventListener?,
    internal val videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback?,
    internal val adMediaMimeTypes: List<String>?,
    internal val adUiElements: Set<UiElement>?,
    internal val companionAdSlots: Collection<CompanionAdSlot>?,
    internal val enableContinuousPlayback: Boolean?,
    internal val adPreloadTimeoutMs: Long,
    internal val vastLoadTimeoutMs: Int,
    internal val mediaLoadTimeoutMs: Int,
    internal val mediaBitrate: Int,
    internal val focusSkipButtonWhenAvailable: Boolean,
    internal val playAdBeforeStartPosition: Boolean,
    internal val debugModeEnabled: Boolean,
    internal val enableCustomTabs: Boolean,
    internal val nimbusRequest: NimbusRequest?,
    internal val requestManager: RequestManager?,
) : AdsLoader {

    /** Builder for [NimbusImaAdLoader].  */
    @Suppress("UNUSED")
    class Builder(val context: Context) {
        private var imaSdkSettings: ImaSdkSettings? = null
        private var adErrorListener: AdErrorListener? = null
        private var adEventListener: AdEventListener? = null
        private var videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback? = null
        private var adMediaMimeTypes: MutableList<String>? = null
        private var adUiElements: MutableSet<UiElement>? = null
        private var companionAdSlots: MutableCollection<CompanionAdSlot>? = null
        private var enableContinuousPlayback: Boolean? = null
        private var adPreloadTimeoutMs: Long = 10 * C.MILLIS_PER_SECOND
        private var vastLoadTimeoutMs: Int = -1
        private var mediaLoadTimeoutMs: Int = -1
        private var mediaBitrate: Int = -1
        private var focusSkipButtonWhenAvailable = true
        private var playAdBeforeStartPosition = true
        private var debugModeEnabled = false
        private var enableCustomTabs = false
        private var nimbusRequest: NimbusRequest? = null
        private var requestManager: RequestManager? = null

        /**
         * Sets the instream video NimbusRequest object
         */
        fun setNimbusRequest(videoRequest: NimbusRequest): Builder = apply {
            nimbusRequest = videoRequest.apply {
                requireNotNull(request.imp[0].video)
                require(request.imp[0].banner == null && request.imp[0].native == null) {
                    "Only video objects are supported for Instream "
                }
                request.imp[0].video?.placement = PlacementType.IN_FEED
                configureViewability("Google", MediaLibraryInfo.VERSION)
            }
        }

        fun setNimbusRequestManager(requestManager: RequestManager): Builder = apply {
            this.requestManager = requestManager
        }

        /**
         * Sets the IMA SDK settings. The provided settings instance's player type and version fields
         * may be overwritten.
         *
         * If this method is not called the default settings will be used.
         *
         * @param imaSdkSettings The [ImaSdkSettings].
         * @return This builder, for convenience.
         */
        fun setImaSdkSettings(imaSdkSettings: ImaSdkSettings): Builder = apply {
            this.imaSdkSettings = imaSdkSettings
        }

        /**
         * Sets a listener for ad errors that will be passed to [com.google.ads.interactivemedia.v3.api.AdsLoader.addAdErrorListener] and
         * [AdsManager.addAdErrorListener].
         *
         * @param adErrorListener The ad error listener.
         * @return This builder, for convenience.
         */
        fun setAdErrorListener(adErrorListener: AdErrorListener): Builder = apply {
            this.adErrorListener = adErrorListener
        }

        /**
         * Sets a listener for ad events that will be passed to [AdsManager.addAdEventListener].
         *
         * @param adEventListener The ad event listener.
         * @return This builder, for convenience.
         */
        fun setAdEventListener(adEventListener: AdEventListener): Builder = apply {
            this.adEventListener = adEventListener
        }

        /**
         * Sets a callback to receive video ad player events. Note that these events are handled
         * internally by the IMA SDK and this ad loader. For analytics and diagnostics, new
         * implementations should generally use events from the top-level [Player] listeners
         * instead of setting a callback via this method.
         *
         * @param videoAdPlayerCallback The callback to receive video ad player events.
         * @return This builder, for convenience.
         * @see VideoAdPlayer.VideoAdPlayerCallback
         */
        fun setVideoAdPlayerCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback): Builder = apply {
            this.videoAdPlayerCallback = videoAdPlayerCallback
        }

        /**
         * Sets the ad UI elements to be rendered by the IMA SDK.
         *
         * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setUiElements
         */
        fun setAdUiElements(adUiElements: MutableSet<UiElement>): Builder = apply {
            this.adUiElements = adUiElements
        }

        /**
         * Sets the slots to use for companion ads, if they are present in the loaded ad.
         *
         * @param companionAdSlots The slots to use for companion ads.
         * @return This builder, for convenience.
         * @see AdDisplayContainer.setCompanionSlots
         */
        fun setCompanionAdSlots(companionAdSlots: MutableCollection<CompanionAdSlot>): Builder = apply {
            this.companionAdSlots = companionAdSlots
        }

        /**
         * Sets the MIME types to prioritize for linear ad media. If not specified, MIME types supported
         * by the adMediaSourceFactory used to construct the ImaAdLoader
         *
         * @param adMediaMimeTypes The MIME types to prioritize for linear ad media. May contain [MimeTypes.APPLICATION_MPD], [MimeTypes.APPLICATION_M3U8], [     ][MimeTypes.VIDEO_MP4], [MimeTypes.VIDEO_WEBM], [MimeTypes.VIDEO_H263], [     ][MimeTypes.AUDIO_MP4] and [MimeTypes.AUDIO_MPEG].
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setMimeTypes
         */
        fun setAdMediaMimeTypes(adMediaMimeTypes: MutableList<String>): Builder = apply {
            this.adMediaMimeTypes = adMediaMimeTypes
        }

        /**
         * Sets whether to enable continuous playback. Pass `true` if content videos will be
         * played continuously, similar to a TV broadcast. This setting may modify the ads request but
         * does not affect ad playback behavior. The requested value is unknown by default.
         *
         * @param enableContinuousPlayback Whether to enable continuous playback.
         * @return This builder, for convenience.
         * @see AdsRequest.setContinuousPlayback
         */
        fun setEnableContinuousPlayback(enableContinuousPlayback: Boolean): Builder = apply {
            this.enableContinuousPlayback = enableContinuousPlayback
        }

        /**
         * Sets the duration in milliseconds for which the player must buffer while preloading an ad
         * group before that ad group is skipped and marked as having failed to load. Pass [ ][C.TIME_UNSET] if there should be no such timeout. The default value is [ ][.DEFAULT_AD_PRELOAD_TIMEOUT_MS] ms.
         *
         * The purpose of this timeout is to avoid playback getting stuck in the unexpected case that
         * the IMA SDK does not load an ad break based on the player's reported content position.
         *
         * The value will be adjusted to be greater or equal to the one in [ ][.setVastLoadTimeoutMs] if provided.
         *
         * @param adPreloadTimeoutMs The timeout buffering duration in milliseconds, or [     ][C.TIME_UNSET] for no timeout.
         * @return This builder, for convenience.
         */
        fun setAdPreloadTimeoutMs(adPreloadTimeoutMs: Long): Builder = apply {
            require(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0)
            this.adPreloadTimeoutMs = adPreloadTimeoutMs
        }

        /**
         * Sets the VAST load timeout, in milliseconds.
         *
         * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRequest.setVastLoadTimeout
         */
        fun setVastLoadTimeoutMs(@IntRange(from = 1) vastLoadTimeoutMs: Int): Builder = apply {
            require(vastLoadTimeoutMs > 0)
            this.vastLoadTimeoutMs = vastLoadTimeoutMs
        }

        /**
         * Sets the ad media load timeout, in milliseconds.
         *
         * @param mediaLoadTimeoutMs The ad media load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setLoadVideoTimeout
         */
        fun setMediaLoadTimeoutMs(@IntRange(from = 1) mediaLoadTimeoutMs: Int): Builder = apply {
            require(mediaLoadTimeoutMs > 0)
            this.mediaLoadTimeoutMs = mediaLoadTimeoutMs
        }

        /**
         * Sets whether to enable custom tabs for the ad click-through URLs. The default value is `false`.
         *
         * @param enableCustomTabs Whether to enable custom tabs for the ad click-through URLs.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setEnableCustomTabs
         */
        fun setEnableCustomTabs(enableCustomTabs: Boolean): Builder = apply {
            this.enableCustomTabs = enableCustomTabs
        }

        /**
         * Sets the media maximum recommended bitrate for ads, in bps.
         *
         * @param bitrate The media maximum recommended bitrate for ads, in bps.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setBitrateKbps
         */
        fun setMaxMediaBitrate(@IntRange(from = 1) bitrate: Int): Builder = apply {
            require(bitrate > 0)
            this.mediaBitrate = bitrate
        }

        /**
         * Sets whether to focus the skip button (when available) on Android TV devices. The default
         * setting is `true`.
         *
         * @param focusSkipButtonWhenAvailable Whether to focus the skip button (when available) on
         * Android TV devices.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setFocusSkipButtonWhenAvailable
         */
        fun setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable: Boolean): Builder = apply {
            this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable
        }

        /**
         * Sets whether to play an ad before the start position when beginning playback. If `true`, an ad will be played if there is one at or before the start position. If `false`, an ad will be played only if there is one exactly at the start position. The default
         * setting is `true`.
         *
         * @param playAdBeforeStartPosition Whether to play an ad before the start position when
         * beginning playback.
         * @return This builder, for convenience.
         */
        fun setPlayAdBeforeStartPosition(playAdBeforeStartPosition: Boolean): Builder = apply {
            this.playAdBeforeStartPosition = playAdBeforeStartPosition
        }

        /**
         * Sets whether to enable outputting verbose logs for the IMA extension and IMA SDK. The default
         * value is `false`. This setting is intended for debugging only, and should not be
         * enabled in production applications.
         *
         * @param debugModeEnabled Whether to enable outputting verbose logs for the IMA extension and
         * IMA SDK.
         * @return This builder, for convenience.
         * @see ImaSdkSettings.setDebugMode
         */
        fun setDebugModeEnabled(debugModeEnabled: Boolean): Builder = apply {
            this.debugModeEnabled = debugModeEnabled
        }

        /** Returns a new [NimbusImaAdLoader].  */
        fun build(): NimbusImaAdLoader {
            if (vastLoadTimeoutMs != -1 && adPreloadTimeoutMs < vastLoadTimeoutMs) {
                adPreloadTimeoutMs = vastLoadTimeoutMs.toLong()
            }
            return NimbusImaAdLoader(
                context = context.applicationContext,
                imaSdkSettings = imaSdkSettings,
                adErrorListener = adErrorListener,
                adEventListener = adEventListener,
                videoAdPlayerCallback = videoAdPlayerCallback,
                adMediaMimeTypes = adMediaMimeTypes ?: emptyList(),
                adUiElements = adUiElements ?: emptySet(),
                companionAdSlots = companionAdSlots ?: emptyList(),
                enableContinuousPlayback = enableContinuousPlayback,
                adPreloadTimeoutMs = adPreloadTimeoutMs,
                vastLoadTimeoutMs = vastLoadTimeoutMs,
                mediaLoadTimeoutMs = mediaLoadTimeoutMs,
                mediaBitrate = mediaBitrate,
                focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable,
                playAdBeforeStartPosition = playAdBeforeStartPosition,
                debugModeEnabled = debugModeEnabled,
                enableCustomTabs = enableCustomTabs,
                nimbusRequest = nimbusRequest,
                requestManager = requestManager,
            )
        }
    }

    private val playerListener: PlayerListenerImpl = PlayerListenerImpl()
    private val adTagLoaderByAdsId: MutableMap<Any, NimbusImaAdTagLoader> = mutableMapOf()
    private val adTagLoaderByAdsMediaSource: MutableMap<AdsMediaSource, NimbusImaAdTagLoader> = mutableMapOf()
    private val period: Timeline.Period = Timeline.Period()
    private val window: Timeline.Window = Timeline.Window()

    private var wasSetPlayerCalled = false
    private var nextPlayer: Player? = null
    private var supportedMimeTypes: List<String> = listOf()
    private var player: Player? = null
    private var currentAdTagLoader: NimbusImaAdTagLoader? = null

    /**
     * Returns the underlying [com.google.ads.interactivemedia.v3.api.AdsLoader] wrapped by this
     * instance, or `null` if ads have not been requested yet.
     */
    val adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader?
        get() = currentAdTagLoader?.adsLoader

    /**
     * Returns the [AdDisplayContainer] used by this loader, or `null` if ads have not
     * been requested yet.
     *
     *
     * Note: any video controls overlays registered via [ ][AdDisplayContainer.registerFriendlyObstruction] will be unregistered
     * automatically when the media source detaches from this instance. It is therefore necessary to
     * re-register views each time the ads loader is reused. Alternatively, provide overlay views via
     * the [AdViewProvider] when creating the media source to benefit from automatic
     * registration.
     */
    val adDisplayContainer: AdDisplayContainer?
        get() = currentAdTagLoader?.adDisplayContainer

    /**
     * Requests ads, if they have not already been requested. Must be called on the main thread.
     *
     *
     * Ads will be requested automatically when the player is prepared if this method has not been
     * called, so it is only necessary to call this method if you want to request ads before preparing
     * the player.
     *
     * @param adTagDataSpec The data specification of the ad tag to load.
     * @param adsId An opaque identifier for the ad playback state across start/stop calls.
     * @param adViewGroup A [ViewGroup] on top of the player that will show any ad UI, or `null` if playing audio-only ads.
     */
    @UnstableApi
    fun requestAds(adTagDataSpec: DataSpec, adsId: Any, adViewGroup: ViewGroup?) {
        if (!adTagLoaderByAdsId.containsKey(adsId)) {
            adTagLoaderByAdsId[adsId] = NimbusImaAdTagLoader(
                context = context,
                adViewGroup = adViewGroup,
                configuration = this,
                supportedMimeTypes = supportedMimeTypes,
                adTagDataSpec = adTagDataSpec,
                adsId = adsId,
            )
        }
    }

    /**
     * Skips the current ad.
     *
     *
     * This method is intended for apps that play audio-only ads and so need to provide their own
     * UI for users to skip skippable ads. Apps showing video ads should not call this method, as the
     * IMA SDK provides the UI to skip ads in the ad view group passed via [AdViewProvider].
     */
    @UnstableApi
    fun skipAd() {
        currentAdTagLoader?.skipAd()
    }

    /**
     * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
     * [AdsManager.focus].
     */
    @UnstableApi
    fun focusSkipButton() {
        currentAdTagLoader?.focusSkipButton()
    }

    override fun setPlayer(player: Player?) {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "Looper mismatch: expected ImaLooper, got ${Looper.myLooper()}"
        }
        require(player?.applicationLooper == Looper.getMainLooper()) {
            "Player is null or player's looper does not match ImaLooper"
        }
        nextPlayer = player
        wasSetPlayerCalled = true
    }

    @UnstableApi
    override fun setSupportedContentTypes(@C.ContentType vararg contentTypes: Int) {
        this.supportedMimeTypes = buildList {
            for (@C.ContentType contentType: @C.ContentType Int in contentTypes) {
                // IMA does not support Smooth Streaming ad media.
                when (contentType) {
                    C.CONTENT_TYPE_DASH -> add(MimeTypes.APPLICATION_MPD)
                    C.CONTENT_TYPE_HLS -> add(MimeTypes.APPLICATION_M3U8)
                    C.CONTENT_TYPE_OTHER -> addAll(
                        listOf(
                            MimeTypes.VIDEO_MP4,
                            MimeTypes.VIDEO_WEBM,
                            MimeTypes.VIDEO_H263,
                            MimeTypes.AUDIO_MP4,
                            MimeTypes.AUDIO_MPEG,
                        ),
                    )
                }
            }
        }
    }

    @UnstableApi
    override fun start(
        adsMediaSource: AdsMediaSource,
        adTagDataSpec: DataSpec,
        adsId: Any,
        adViewProvider: AdViewProvider,
        eventListener: AdsLoader.EventListener,
    ) {
        require(wasSetPlayerCalled) { "Set player using adsLoader.setPlayer before preparing the player." }
        if (adTagLoaderByAdsMediaSource.isEmpty()) {
            player = nextPlayer
            player?.addListener(playerListener)
        }

        val adTagLoader = adTagLoaderByAdsId[adsId] ?: run {
            requestAds(adTagDataSpec, adsId, adViewProvider.adViewGroup)
            adTagLoaderByAdsId[adsId]
        }
        requireNotNull(adTagLoader)
        adTagLoaderByAdsMediaSource[adsMediaSource] = adTagLoader.apply {
            addListenerWithAdView(eventListener, adViewProvider)
        }
        maybeUpdateCurrentAdTagLoader()
    }

    @UnstableApi
    override fun stop(adsMediaSource: AdsMediaSource, eventListener: AdsLoader.EventListener) {
        val removedAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource)
        maybeUpdateCurrentAdTagLoader()
        removedAdTagLoader?.removeListener(eventListener)

        player?.apply {
            if (adTagLoaderByAdsMediaSource.isEmpty()) removeListener(playerListener)
            player = null
        }
    }

    override fun release() {
        player?.run {
            removeListener(playerListener)
            player = null
            maybeUpdateCurrentAdTagLoader()
        }
        nextPlayer = null

        adTagLoaderByAdsMediaSource.values.forEach { it.release() }
        adTagLoaderByAdsMediaSource.clear()
        adTagLoaderByAdsId.values.forEach { it.release() }
        adTagLoaderByAdsId.clear()
    }

    @UnstableApi
    override fun handlePrepareComplete(
        adsMediaSource: AdsMediaSource, adGroupIndex: Int, adIndexInAdGroup: Int,
    ) {
        if (player == null) return
        adTagLoaderByAdsMediaSource[adsMediaSource]?.handlePrepareComplete(adGroupIndex, adIndexInAdGroup)
    }

    @UnstableApi
    override fun handlePrepareError(
        adsMediaSource: AdsMediaSource,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: java.io.IOException,
    ) {
        if (player == null) return
        adTagLoaderByAdsMediaSource[adsMediaSource]?.handlePrepareError(adGroupIndex, adIndexInAdGroup, exception)
    }

    // Internal methods.
    private fun maybeUpdateCurrentAdTagLoader() {
        val oldAdTagLoader = currentAdTagLoader
        val newAdTagLoader = getCurrentAdTagLoader()
        if (oldAdTagLoader != newAdTagLoader) {
            oldAdTagLoader?.deactivate()
            currentAdTagLoader = newAdTagLoader
            player?.let { newAdTagLoader?.activate(it) }
        }
    }

    private fun getCurrentAdTagLoader(): NimbusImaAdTagLoader? =
        player?.takeUnless { it.currentTimeline.isEmpty }?.run {
            val adId = currentTimeline.getPeriod(currentPeriodIndex, period).adsId ?: return null
            adTagLoaderByAdsId[adId]?.takeUnless { !adTagLoaderByAdsMediaSource.containsValue(it) }
        }

    private fun maybePreloadNextPeriodAds() {
        player?.takeUnless { it.currentTimeline.isEmpty }?.run {
            val nextPeriodIndex = currentTimeline.getNextPeriodIndex(
                currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled,
            ).takeUnless { it == C.INDEX_UNSET } ?: return
            val nextAdsId = currentTimeline.getPeriod(nextPeriodIndex, period).adsId ?: return
            adTagLoaderByAdsId[nextAdsId]?.takeUnless { it == currentAdTagLoader }?.maybePreloadAds(
                usToMs(currentTimeline.getPeriodPositionUs(window, period, period.windowIndex, C.TIME_UNSET).second),
                usToMs(period.durationUs),
            )
        }

    }

    private inner class PlayerListenerImpl : Listener {
        override fun onTimelineChanged(timeline: Timeline, @TimelineChangeReason reason: Int) {
            // The player is being reset or contains no media.
            if (timeline.isEmpty) return

            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onPositionDiscontinuity(
            oldPosition: PositionInfo,
            newPosition: PositionInfo,
            @DiscontinuityReason reason: Int,
        ) {
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            maybePreloadNextPeriodAds()
        }

        override fun onRepeatModeChanged(@RepeatMode repeatMode: Int) {
            maybePreloadNextPeriodAds()
        }
    }
}
