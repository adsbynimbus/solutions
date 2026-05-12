@file:UnstableApi

package adsbynimbus.solutions.gamdirect

import android.content.Context
import android.os.*
import android.os.SystemClock
import android.view.*
import androidx.annotation.*
import androidx.annotation.IntRange
import androidx.media3.common.*
import androidx.media3.common.util.*
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ima.ImaAdTagUriBuilder
import androidx.media3.exoplayer.source.ads.*
import androidx.media3.exoplayer.source.ads.AdsLoader
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.AdErrorListener
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener
import com.google.ads.interactivemedia.v3.api.player.*
import com.google.common.base.Preconditions
import com.google.common.collect.*
import java.io.IOException
import java.util.*
import kotlin.math.*

/**
 * [AdsLoader] using the IMA SDK. All methods must be called on the main thread.
 *
 * The player instance that will play the loaded ads must be set before playback using [ ][.setPlayer]. If the ads loader is no longer required, it must be released by calling
 * [.release].
 *
 * See [IMA's
 * Support and compatibility page](https://developers.google.com/interactive-media-ads/docs/sdks/android/compatibility) for information on compatible ad tag formats. Pass the ad tag
 * URI when setting media item playback properties (if using the media item API) or as a [ ] when constructing the [AdsMediaSource] (if using media sources directly). For the
 * latter case, please note that this implementation delegates loading of the data spec to the IMA
 * SDK, so range and headers specifications will be ignored in ad tag URIs. Literal ads responses
 * can be encoded as data scheme data specs, for example, by constructing the data spec using a URI
 * generated via [Util.getDataUriForString].
 *
 * The IMA SDK can report obstructions to the ad view for accurate viewability measurement. This
 * means that any overlay views that obstruct the ad overlay but are essential for playback need to
 * be registered via the [AdViewProvider] passed to the [AdsMediaSource]. See the [IMA
 * SDK Open Measurement documentation](https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/omsdk) for more information.
 */
class NimbusDirectAdLoader(context: Context, val builder: Builder) : AdsLoader {

    /** Builder for [NimbusDirectAdLoader].  */
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

        /**
         * Sets the IMA SDK settings. The provided settings instance's player type and version fields
         * may be overwritten.
         *
         *
         * If this method is not called the default settings will be used.
         *
         * @param imaSdkSettings The [ImaSdkSettings].
         * @return This builder, for convenience.
         */
        fun setImaSdkSettings(imaSdkSettings: ImaSdkSettings): Builder  {
            this.imaSdkSettings = imaSdkSettings
            return this
        }

        /**
         * Sets a listener for ad errors that will be passed to [com.google.ads.interactivemedia.v3.api.AdsLoader.addAdErrorListener] and
         * [AdsManager.addAdErrorListener].
         *
         * @param adErrorListener The ad error listener.
         * @return This builder, for convenience.
         */
        fun setAdErrorListener(adErrorListener: AdErrorListener): Builder {
            this.adErrorListener = adErrorListener
            return this
        }

        /**
         * Sets a listener for ad events that will be passed to [AdsManager.addAdEventListener].
         *
         * @param adEventListener The ad event listener.
         * @return This builder, for convenience.
         */
        fun setAdEventListener(adEventListener: AdEventListener): Builder {
            this.adEventListener = adEventListener
            return this
        }

        /**
         * Sets a callback to receive video ad player events. Note that these events are handled
         * internally by the IMA SDK and this ads loader. For analytics and diagnostics, new
         * implementations should generally use events from the top-level [Player] listeners
         * instead of setting a callback via this method.
         *
         * @param videoAdPlayerCallback The callback to receive video ad player events.
         * @return This builder, for convenience.
         * @see VideoAdPlayer.VideoAdPlayerCallback
         */
        fun setVideoAdPlayerCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback): Builder {
            this.videoAdPlayerCallback = videoAdPlayerCallback
            return this
        }

        /**
         * Sets the ad UI elements to be rendered by the IMA SDK.
         *
         * @param adUiElements The ad UI elements to be rendered by the IMA SDK.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setUiElements
         */
        fun setAdUiElements(adUiElements: MutableSet<UiElement>): Builder {
            this.adUiElements = adUiElements
            return this
        }

        /**
         * Sets the slots to use for companion ads, if they are present in the loaded ad.
         *
         * @param companionAdSlots The slots to use for companion ads.
         * @return This builder, for convenience.
         * @see AdDisplayContainer.setCompanionSlots
         */
        fun setCompanionAdSlots(companionAdSlots: MutableCollection<CompanionAdSlot>): Builder {
            this.companionAdSlots = companionAdSlots
            return this
        }

        /**
         * Sets the MIME types to prioritize for linear ad media. If not specified, MIME types supported
         * by the adMediaSourceFactory used to construct the ImaAdLoader
         *
         * @param adMediaMimeTypes The MIME types to prioritize for linear ad media. May contain [MimeTypes.APPLICATION_MPD], [MimeTypes.APPLICATION_M3U8], [     ][MimeTypes.VIDEO_MP4], [MimeTypes.VIDEO_WEBM], [MimeTypes.VIDEO_H263], [     ][MimeTypes.AUDIO_MP4] and [MimeTypes.AUDIO_MPEG].
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setMimeTypes
         */
        fun setAdMediaMimeTypes(adMediaMimeTypes: MutableList<String>): Builder {
            this.adMediaMimeTypes = adMediaMimeTypes
            return this
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
        fun setEnableContinuousPlayback(enableContinuousPlayback: Boolean): Builder {
            this.enableContinuousPlayback = enableContinuousPlayback
            return this
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
        fun setAdPreloadTimeoutMs(adPreloadTimeoutMs: Long): Builder {
            require(adPreloadTimeoutMs == C.TIME_UNSET || adPreloadTimeoutMs > 0)
            this.adPreloadTimeoutMs = adPreloadTimeoutMs
            return this
        }

        /**
         * Sets the VAST load timeout, in milliseconds.
         *
         * @param vastLoadTimeoutMs The VAST load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRequest.setVastLoadTimeout
         */
        fun setVastLoadTimeoutMs(@IntRange(from = 1) vastLoadTimeoutMs: Int): Builder {
            require(vastLoadTimeoutMs > 0)
            this.vastLoadTimeoutMs = vastLoadTimeoutMs
            return this
        }

        /**
         * Sets the ad media load timeout, in milliseconds.
         *
         * @param mediaLoadTimeoutMs The ad media load timeout, in milliseconds.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setLoadVideoTimeout
         */
        fun setMediaLoadTimeoutMs(@IntRange(from = 1) mediaLoadTimeoutMs: Int): Builder {
            require(mediaLoadTimeoutMs > 0)
            this.mediaLoadTimeoutMs = mediaLoadTimeoutMs
            return this
        }

        /**
         * Sets whether to enable custom tabs for the ad click-through URLs. The default value is `false`.
         *
         * @param enableCustomTabs Whether to enable custom tabs for the ad click-through URLs.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setEnableCustomTabs
         */
        fun setEnableCustomTabs(enableCustomTabs: Boolean): Builder {
            this.enableCustomTabs = enableCustomTabs
            return this
        }

        /**
         * Sets the media maximum recommended bitrate for ads, in bps.
         *
         * @param bitrate The media maximum recommended bitrate for ads, in bps.
         * @return This builder, for convenience.
         * @see AdsRenderingSettings.setBitrateKbps
         */
        fun setMaxMediaBitrate(@IntRange(from = 1) bitrate: Int): Builder {
            require(bitrate > 0)
            this.mediaBitrate = bitrate
            return this
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
        fun setFocusSkipButtonWhenAvailable(focusSkipButtonWhenAvailable: Boolean): Builder {
            this.focusSkipButtonWhenAvailable = focusSkipButtonWhenAvailable
            return this
        }

        /**
         * Sets whether to play an ad before the start position when beginning playback. If `true`, an ad will be played if there is one at or before the start position. If `false`, an ad will be played only if there is one exactly at the start position. The default
         * setting is `true`.
         *
         * @param playAdBeforeStartPosition Whether to play an ad before the start position when
         * beginning playback.
         * @return This builder, for convenience.
         */
        fun setPlayAdBeforeStartPosition(playAdBeforeStartPosition: Boolean): Builder {
            this.playAdBeforeStartPosition = playAdBeforeStartPosition
            return this
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
        fun setDebugModeEnabled(debugModeEnabled: Boolean): Builder {
            this.debugModeEnabled = debugModeEnabled
            return this
        }

        /** Returns a new [NimbusDirectAdLoader].  */
        fun build(): NimbusDirectAdLoader {
            if (vastLoadTimeoutMs != -1 && adPreloadTimeoutMs < vastLoadTimeoutMs) {
                adPreloadTimeoutMs = vastLoadTimeoutMs.toLong()
            }
            return NimbusDirectAdLoader(context, this)
        }
    }

    private val context = context.applicationContext
    private val playerListener: PlayerListenerImpl = PlayerListenerImpl()
    private val adTagLoaderByAdsId: MutableMap<Any, NimbusDirectAdTagLoader> = mutableMapOf()
    private val adTagLoaderByAdsMediaSource: MutableMap<AdsMediaSource, NimbusDirectAdTagLoader> = mutableMapOf()
    private val period: Timeline.Period = Timeline.Period()
    private val window: Timeline.Window = Timeline.Window()

    private var wasSetPlayerCalled = false
    private var nextPlayer: Player? = null
    private var supportedMimeTypes: MutableList<String> = mutableListOf()
    private var player: Player? = null
    private var currentAdTagLoader: NimbusDirectAdTagLoader? = null

    /**
     * Returns the underlying [com.google.ads.interactivemedia.v3.api.AdsLoader] wrapped by this
     * instance, or `null` if ads have not been requested yet.
     */
    val adsLoader: AdsLoader?
        get() = if (currentAdTagLoader != null) currentAdTagLoader.getAdsLoader() else null

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
        get() = if (currentAdTagLoader != null) currentAdTagLoader.getAdDisplayContainer() else null

    /**
     * Requests ads, if they have not already been requested. Must be called on the main thread.
     *
     *
     * Ads will be requested automatically when the player is prepared if this method has not been
     * called, so it is only necessary to call this method if you want to request ads before preparing
     * the player.
     *
     * @param adTagDataSpec The data specification of the ad tag to load. See class javadoc for
     * information about compatible ad tag formats.
     * @param adsId A opaque identifier for the ad playback state across start/stop calls.
     * @param adViewGroup A [ViewGroup] on top of the player that will show any ad UI, or `null` if playing audio-only ads.
     */
    @UnstableApi
    fun requestAds(adTagDataSpec: DataSpec, adsId: Any, adViewGroup: ViewGroup?) {
        if (!adTagLoaderByAdsId.containsKey(adsId)) {
            val adTagLoader =
                NimbusDirectAdTagLoader(
                    context,
                    builder,
                    supportedMimeTypes,
                    adTagDataSpec,
                    adsId,
                    adViewGroup,
                )
            adTagLoaderByAdsId[adsId] = adTagLoader
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
        if (currentAdTagLoader != null) {
            currentAdTagLoader.skipAd()
        }
    }

    /**
     * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
     * [AdsManager.focus].
     */
    @UnstableApi
    fun focusSkipButton() {
        if (currentAdTagLoader != null) {
            currentAdTagLoader.focusSkipButton()
        }
    }

    // AdsLoader implementation.
    override fun setPlayer(player: Player?) {
        Preconditions.checkState(Looper.myLooper() == ImaUtil.getImaLooper())
        Preconditions.checkState(player == null || player.getApplicationLooper() == ImaUtil.getImaLooper())
        nextPlayer = player
        wasSetPlayerCalled = true
    }

    @UnstableApi
    override fun setSupportedContentTypes(@C.ContentType vararg contentTypes: Int) {
        val supportedMimeTypes: MutableList<String?> = ArrayList<String?>()
        for (@C.ContentType contentType: @C.ContentType Int in contentTypes) {
            // IMA does not support Smooth Streaming ad media.
            if (contentType == C.CONTENT_TYPE_DASH) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_MPD)
            } else if (contentType == C.CONTENT_TYPE_HLS) {
                supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8)
            } else if (contentType == C.CONTENT_TYPE_OTHER) {
                supportedMimeTypes.addAll(
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
        this.supportedMimeTypes = Collections.unmodifiableList<String?>(supportedMimeTypes)
    }

    @UnstableApi
    override fun start(
        adsMediaSource: AdsMediaSource,
        adTagDataSpec: DataSpec,
        adsId: Any,
        adViewProvider: AdViewProvider,
        eventListener: AdsLoader.EventListener,
    ) {
        Preconditions.checkState(
            wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player.",
        )
        if (adTagLoaderByAdsMediaSource.isEmpty()) {
            player = nextPlayer
            val player = this.player
            if (player == null) {
                return
            }
            player.addListener(playerListener)
        }

        var adTagLoader = adTagLoaderByAdsId[adsId]
        if (adTagLoader == null) {
            requestAds(adTagDataSpec, adsId, adViewProvider.getAdViewGroup())
            adTagLoader = adTagLoaderByAdsId[adsId]
        }
        adTagLoaderByAdsMediaSource.put(adsMediaSource, Preconditions.checkNotNull<AdTagLoader?>(adTagLoader))
        adTagLoader.addListenerWithAdView(eventListener, adViewProvider)
        maybeUpdateCurrentAdTagLoader()
    }

    @UnstableApi
    override fun stop(adsMediaSource: AdsMediaSource, eventListener: AdsLoader.EventListener) {
        val removedAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource)
        maybeUpdateCurrentAdTagLoader()
        if (removedAdTagLoader != null) {
            removedAdTagLoader.removeListener(eventListener)
        }

        if (player != null && adTagLoaderByAdsMediaSource.isEmpty()) {
            player!!.removeListener(playerListener)
            player = null
        }
    }

    override fun release() {
        if (player != null) {
            player!!.removeListener(playerListener)
            player = null
            maybeUpdateCurrentAdTagLoader()
        }
        nextPlayer = null

        for (adTagLoader in adTagLoaderByAdsMediaSource.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsMediaSource.clear()

        for (adTagLoader in adTagLoaderByAdsId.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsId.clear()
    }

    @UnstableApi
    override fun handlePrepareComplete(
        adsMediaSource: AdsMediaSource, adGroupIndex: Int, adIndexInAdGroup: Int,
    ) {
        if (player == null) {
            return
        }
        Preconditions.checkNotNull<AdTagLoader?>(adTagLoaderByAdsMediaSource.get(adsMediaSource))
            .handlePrepareComplete(adGroupIndex, adIndexInAdGroup)
    }

    @UnstableApi
    override fun handlePrepareError(
        adsMediaSource: AdsMediaSource,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: IOException,
    ) {
        if (player == null) {
            return
        }
        Preconditions.checkNotNull<AdTagLoader?>(adTagLoaderByAdsMediaSource.get(adsMediaSource))
            .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception)
    }

    // Internal methods.
    private fun maybeUpdateCurrentAdTagLoader() {
        val oldAdTagLoader = currentAdTagLoader
        val newAdTagLoader: AdTagLoader? = getCurrentAdTagLoader()
        if (oldAdTagLoader != newAdTagLoader) {
            if (oldAdTagLoader != null) {
                oldAdTagLoader.deactivate()
            }
            currentAdTagLoader = newAdTagLoader
            if (newAdTagLoader != null) {
                newAdTagLoader.activate(Preconditions.checkNotNull<Player?>(player))
            }
        }
    }

    private fun getCurrentAdTagLoader(): AdTagLoader? {
        val player = this.player
        if (player == null) {
            return null
        }
        val timeline = player.getCurrentTimeline()
        if (timeline.isEmpty()) {
            return null
        }
        val periodIndex = player.getCurrentPeriodIndex()
        val adsId = timeline.getPeriod(periodIndex, period).getAdsId()
        if (adsId == null) {
            return null
        }
        val adTagLoader = adTagLoaderByAdsId.get(adsId)
        if (adTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(adTagLoader)) {
            return null
        }
        return adTagLoader
    }

    private fun maybePreloadNextPeriodAds() {
        val player = this@NimbusDirectAdLoader.player
        if (player == null) {
            return
        }
        val timeline = player.getCurrentTimeline()
        if (timeline.isEmpty()) {
            return
        }
        val nextPeriodIndex =
            timeline.getNextPeriodIndex(
                player.getCurrentPeriodIndex(),
                period,
                window,
                player.getRepeatMode(),
                player.getShuffleModeEnabled(),
            )
        if (nextPeriodIndex == C.INDEX_UNSET) {
            return
        }
        timeline.getPeriod(nextPeriodIndex, period)
        val nextAdsId = period.getAdsId()
        if (nextAdsId == null) {
            return
        }
        val nextAdTagLoader = adTagLoaderByAdsId.get(nextAdsId)
        if (nextAdTagLoader == null || nextAdTagLoader == currentAdTagLoader) {
            return
        }
        val periodPositionUs =
            timeline.getPeriodPositionUs(
                window, period, period.windowIndex,  /* windowPositionUs= */C.TIME_UNSET,
            )
                .second
        nextAdTagLoader.maybePreloadAds(Util.usToMs(periodPositionUs), Util.usToMs(period.durationUs))
    }

    private inner class PlayerListenerImpl : Player.Listener {
        override fun onTimelineChanged(
            timeline: Timeline,
            @Player.TimelineChangeReason reason: @Player.TimelineChangeReason Int,
        ) {
            if (timeline.isEmpty()) {
                // The player is being reset or contains no media.
                return
            }
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            @Player.DiscontinuityReason reason: @Player.DiscontinuityReason Int,
        ) {
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            maybePreloadNextPeriodAds()
        }

        override fun onRepeatModeChanged(@Player.RepeatMode repeatMode: @Player.RepeatMode Int) {
            maybePreloadNextPeriodAds()
        }
    }

    /**
     * Default [ImaUtil.ImaFactory] for non-test usage, which delegates to [ ].
     */
    private class DefaultImaFactory : ImaFactory {
        override fun createImaSdkSettings(): ImaSdkSettings {
            val settings = ImaSdkFactory.getInstance().createImaSdkSettings()
            settings.setLanguage(Util.getSystemLanguageCodes()[0])
            return settings
        }

        override fun createAdsRenderingSettings(): AdsRenderingSettings {
            return ImaSdkFactory.getInstance().createAdsRenderingSettings()
        }

        override fun createAdDisplayContainer(container: ViewGroup, player: VideoAdPlayer): AdDisplayContainer {
            return ImaSdkFactory.createAdDisplayContainer(container, player)
        }

        override fun createAudioAdDisplayContainer(context: Context, player: VideoAdPlayer): AdDisplayContainer {
            return ImaSdkFactory.createAudioAdDisplayContainer(context, player)
        }

        // The reasonDetail parameter to createFriendlyObstruction is annotated @Nullable but the
        // annotation is not kept in the obfuscated dependency.
        override fun createFriendlyObstruction(
            view: View,
            friendlyObstructionPurpose: FriendlyObstructionPurpose,
            reasonDetail: String?,
        ): FriendlyObstruction {
            return ImaSdkFactory.getInstance()
                .createFriendlyObstruction(view, friendlyObstructionPurpose, reasonDetail)
        }

        override fun createAdsRequest(): AdsRequest {
            return ImaSdkFactory.getInstance().createAdsRequest()
        }

        override fun createAdsLoader(
            context: Context, imaSdkSettings: ImaSdkSettings, adDisplayContainer: AdDisplayContainer,
        ): com.google.ads.interactivemedia.v3.api.AdsLoader {
            return ImaSdkFactory.getInstance()
                .createAdsLoader(context, imaSdkSettings, adDisplayContainer)
        }
    }
}

/** Handles loading and playback of a single ad tag.  */ /* package */
internal class NimbusDirectAdTagLoader(
    context: Context,
    private val configuration: NimbusDirectAdLoader.Builder,
    supportedMimeTypes: MutableList<String>,
    adTagDataSpec: DataSpec,
    adsId: Any,
    adViewGroup: ViewGroup?,
) : Player.Listener {
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
    @IntDef([androidx.media3.exoplayer.ima.AdTagLoader.IMA_AD_STATE_NONE, androidx.media3.exoplayer.ima.AdTagLoader.IMA_AD_STATE_PLAYING, androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PAUSED])
    private annotation class ImaAdState

    private val supportedMimeTypes: MutableList<String?>?
    private val adTagDataSpec: DataSpec
    private val adsId: Any
    private val period: Timeline.Period
    private val handler: Handler
    private val componentListener: androidx.media3.exoplayer.ima.AdTagLoader.ComponentListener
    private val contentPlaybackAdapter: ContentPlaybackAdapter
    private val eventListeners: MutableList<AdsLoader.EventListener?>
    private val adCallbacks: MutableList<VideoAdPlayer.VideoAdPlayerCallback?>
    private val updateAdProgressRunnable: Runnable
    private val adInfoByAdMediaInfo: BiMap<AdMediaInfo?, AdInfo?>

    /** Returns the IMA SDK ad display container.  */
    val adDisplayContainer: AdDisplayContainer

    /** Returns the underlying IMA SDK ads loader.  */
    val adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader
    private val adLoadTimeoutRunnable: Runnable

    private var pendingAdRequestContext: Any? = null
    private var player: Player? = null
    private var lastContentProgress: VideoProgressUpdate
    private var lastAdProgress: VideoProgressUpdate
    private var lastVolumePercent = 0

    private var adsManager: AdsManager? = null
    private var isAdsManagerInitialized = false
    private var pendingAdLoadError: AdsMediaSource.AdLoadException? = null
    private var timeline: Timeline
    private var contentDurationMs: Long
    private var adPlaybackState: AdPlaybackState

    private var released = false

    // Fields tracking IMA's state.
    /** Whether IMA has sent an ad event to pause content since the last resume content event.  */
    private var imaPausedContent = false

    /** The current ad playback state.  */
    private var imaAdState: @androidx.media3.exoplayer.ima.AdTagLoader.ImaAdState Int = 0

    /** The current ad media info, or `null` if in state [.IMA_AD_STATE_NONE].  */
    private var imaAdMediaInfo: AdMediaInfo? = null

    /** The current ad info, or `null` if in state [.IMA_AD_STATE_NONE].  */
    private var imaAdInfo: AdInfo? = null

    /** Whether IMA has been notified that playback of content has finished.  */
    private var sentContentComplete = false

    // Fields tracking the player/loader state.
    /** Whether the player is playing an ad.  */
    private var playingAd = false

    /** Whether the player is buffering an ad.  */
    private var bufferingAd = false

    /**
     * If the player is playing an ad, stores the ad index in its ad group. [C.INDEX_UNSET]
     * otherwise.
     */
    private var playingAdIndexInAdGroup = 0

    /**
     * The ad info for a pending ad for which the media failed preparation, or `null` if no
     * pending ads have failed to prepare.
     */
    private var pendingAdPrepareErrorAdInfo: AdInfo? = null

    /**
     * If a content period has finished but IMA has not yet called [ ][ComponentListener.playAd], stores the value of [ ][SystemClock.elapsedRealtime] when the content stopped playing. This can be used to determine
     * a fake, increasing content position. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressElapsedRealtimeMs: Long

    /**
     * If [.fakeContentProgressElapsedRealtimeMs] is set, stores the offset from which the
     * content progress should increase. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressOffsetMs: Long

    /** Stores the pending content position when a seek operation was intercepted to play an ad.  */
    private var pendingContentPositionMs: Long

    /**
     * Whether [ComponentListener.getContentProgress] has sent [ ][.pendingContentPositionMs] to IMA.
     */
    private var sentPendingContentPositionMs = false

    /**
     * Stores the real time in milliseconds at which the player started buffering, possibly due to not
     * having preloaded an ad, or [C.TIME_UNSET] if not applicable.
     */
    private var waitingForPreloadElapsedRealtimeMs: Long

    /** Creates a new ad tag loader, starting the ad request if the ad tag is valid.  */
    init {
        var imaSdkSettings = configuration.imaSdkSettings
        if (imaSdkSettings == null) {
            imaSdkSettings = imaFactory.createImaSdkSettings()
            if (configuration.debugModeEnabled) {
                imaSdkSettings.setDebugMode(true)
            }
        }
        imaSdkSettings.setPlayerType(androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_SDK_SETTINGS_PLAYER_TYPE)
        imaSdkSettings.setPlayerVersion(androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_SDK_SETTINGS_PLAYER_VERSION)
        this.supportedMimeTypes = supportedMimeTypes
        this.adTagDataSpec = adTagDataSpec
        this.adsId = adsId
        period = Timeline.Period()
        handler = Util.createHandler(ImaUtil.getImaLooper(),  /* callback= */null)
        componentListener = androidx.media3.exoplayer.ima.AdTagLoader.ComponentListener()
        contentPlaybackAdapter = ContentPlaybackAdapter()
        eventListeners = ArrayList<AdsLoader.EventListener?>()
        adCallbacks = ArrayList<VideoAdPlayer.VideoAdPlayerCallback?>( /* initialCapacity= */1)
        if (configuration.applicationVideoAdPlayerCallback != null) {
            adCallbacks.add(configuration.applicationVideoAdPlayerCallback)
        }
        updateAdProgressRunnable = Runnable { this.updateAdProgress() }
        adInfoByAdMediaInfo = HashBiMap.create<AdMediaInfo?, AdInfo?>()
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
        fakeContentProgressOffsetMs = C.TIME_UNSET
        pendingContentPositionMs = C.TIME_UNSET
        waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        contentDurationMs = C.TIME_UNSET
        timeline = Timeline.EMPTY
        adPlaybackState = AdPlaybackState.NONE
        adLoadTimeoutRunnable = Runnable { this.handleAdLoadTimeout() }
        val videoAdPlayerImpl = VideoAdPlayerImpl()
        if (adViewGroup != null) {
            adDisplayContainer =
                imaFactory.createAdDisplayContainer(adViewGroup,  /* player= */videoAdPlayerImpl)
        } else {
            adDisplayContainer =
                imaFactory.createAudioAdDisplayContainer(context,  /* player= */videoAdPlayerImpl)
        }
        if (configuration.companionAdSlots != null) {
            adDisplayContainer.setCompanionSlots(configuration.companionAdSlots)
        }
        adsLoader = requestAds(context, imaSdkSettings, adDisplayContainer)
    }

    /** Skips the current skippable ad, if there is one.  */
    fun skipAd() {
        if (adsManager != null) {
            adsManager!!.skip()
        }
    }

    /**
     * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
     * [AdsManager.focus].
     */
    fun focusSkipButton() {
        if (adsManager != null) {
            adsManager!!.focus()
        }
    }

    /**
     * Starts passing events from this instance (including any pending ad playback state) and
     * registers obstructions.
     */
    fun addListenerWithAdView(eventListener: AdsLoader.EventListener, adViewProvider: AdViewProvider) {
        val isStarted = !eventListeners.isEmpty()
        eventListeners.add(eventListener)
        if (isStarted) {
            if (AdPlaybackState.NONE != adPlaybackState) {
                // Pass the existing ad playback state to the new listener.
                eventListener.onAdPlaybackState(adPlaybackState)
            }
            return
        }
        lastVolumePercent = 0
        lastAdProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        lastContentProgress = VideoProgressUpdate.VIDEO_TIME_NOT_READY
        maybeNotifyPendingAdLoadError()
        if (AdPlaybackState.NONE != adPlaybackState) {
            // Pass the ad playback state to the player, and resume ads if necessary.
            eventListener.onAdPlaybackState(adPlaybackState)
        } else if (adsManager != null) {
            adPlaybackState =
                AdPlaybackState(adsId, *ImaUtil.getAdGroupTimesUsForCuePoints(adsManager!!.getAdCuePoints()))
            updateAdPlaybackState()
        }
        for (overlayInfo in adViewProvider.getAdOverlayInfos()) {
            adDisplayContainer.registerFriendlyObstruction(
                imaFactory.createFriendlyObstruction(
                    overlayInfo.view,
                    ImaUtil.getFriendlyObstructionPurpose(overlayInfo.purpose),
                    overlayInfo.reasonDetail,
                ),
            )
        }
    }

    /**
     * Populates the ad playback state with loaded cue points, if available. Any preroll will be
     * paused immediately while waiting for this instance to be [activated][.activate].
     */
    fun maybePreloadAds(contentPositionMs: Long, contentDurationMs: Long) {
        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
    }

    /** Activates playback.  */
    fun activate(player: Player) {
        this.player = player
        player.addListener(this)

        val playWhenReady = player.getPlayWhenReady()
        onTimelineChanged(player.getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        val adsManager = this.adsManager
        if ((AdPlaybackState.NONE != adPlaybackState) && adsManager != null && imaPausedContent) {
            // Check whether the current ad break matches the expected ad break based on the current
            // position. If not, discard the current ad break so that the correct ad break can load.
            val contentPositionMs: Long =
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(player, timeline, period)
            val adGroupForPositionIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(
                    Util.msToUs(contentPositionMs), Util.msToUs(contentDurationMs),
                )
            if (adGroupForPositionIndex != C.INDEX_UNSET && imaAdInfo != null && imaAdInfo!!.adGroupIndex != adGroupForPositionIndex) {
                if (configuration.debugModeEnabled) {
                    Log.d(
                        androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                        "Discarding preloaded ad " + imaAdInfo,
                    )
                }
                adsManager.discardAdBreak()
            }
            if (playWhenReady) {
                adsManager.resume()
            }
        }
    }

    /** Deactivates playback.  */
    fun deactivate() {
        val player = Preconditions.checkNotNull<Player?>(this.player)
        // Post deactivation behind any already queued Player.Listener events to ensure that
        // any pending events are processed before the listener is removed and the ads manager paused.
        handler.post(Runnable { deactivateInternal(player) })
    }

    /**
     * Deactivates playback internally, after the Listener.onEvents() cycle completes so the complete
     * state change picture is clear. For example, if an error caused the deactivation, the error
     * callback can be handled first.
     */
    private fun deactivateInternal(player: Player) {
        if ((adPlaybackState != AdPlaybackState.NONE) && imaPausedContent
            && player.getPlayerError() == null
        ) {
            // Only need to pause and store resume position if not in error state.
            if (adsManager != null) {
                adsManager!!.pause()
            }
            adPlaybackState =
                adPlaybackState.withAdResumePositionUs(
                    if (playingAd) Util.msToUs(player.getCurrentPosition()) else 0,
                )
        }
        lastVolumePercent = this.playerVolumePercent
        lastAdProgress = this.adVideoProgressUpdate
        lastContentProgress = this.contentVideoProgressUpdate
        player.removeListener(this)
        this.player = null
    }

    /** Stops passing of events from this instance and unregisters obstructions.  */
    fun removeListener(eventListener: AdsLoader.EventListener?) {
        eventListeners.remove(eventListener)
        if (eventListeners.isEmpty()) {
            adDisplayContainer.unregisterAllFriendlyObstructions()
        }
    }

    /** Releases all resources used by the ad tag loader.  */
    fun release() {
        if (released) {
            return
        }
        released = true
        pendingAdRequestContext = null
        destroyAdsManager()
        adsLoader.removeAdsLoadedListener(componentListener)
        adsLoader.removeAdErrorListener(componentListener)
        if (configuration.applicationAdErrorListener != null) {
            adsLoader.removeAdErrorListener(configuration.applicationAdErrorListener!!)
        }
        adsLoader.release()
        imaPausedContent = false
        imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE
        imaAdMediaInfo = null
        stopUpdatingAdProgress()
        imaAdInfo = null
        pendingAdLoadError = null
        // No more ads will play once the loader is released, so mark all ad groups as skipped.
        for (i in 0..<adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
    }

    /** Notifies the IMA SDK that the specified ad has been prepared for playback.  */
    fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int) {
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        if (configuration.debugModeEnabled) {
            Log.d(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "Prepared ad " + adInfo)
        }
        val adMediaInfo = adInfoByAdMediaInfo.inverse().get(adInfo)
        if (adMediaInfo != null) {
            for (i in adCallbacks.indices) {
                adCallbacks.get(i)!!.onLoaded(adMediaInfo)
            }
        } else {
            Log.w(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "Unexpected prepared ad " + adInfo)
        }
    }

    /** Notifies the IMA SDK that the specified ad has failed to prepare for playback.  */
    fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?) {
        if (player == null) {
            return
        }
        try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception)
        } catch (e: RuntimeException) {
            maybeNotifyInternalError("handlePrepareError", e)
        }
    }

    // Player.Listener implementation.
    override fun onTimelineChanged(
        timeline: Timeline,
        @Player.TimelineChangeReason reason: @Player.TimelineChangeReason Int,
    ) {
        if (timeline.isEmpty() || player == null) {
            // The player is being reset or contains no media.
            return
        }
        val player = this.player
        this.timeline = timeline
        val contentDurationUs = timeline.getPeriod(player!!.getCurrentPeriodIndex(), period).durationUs
        contentDurationMs = Util.usToMs(contentDurationUs)
        if (contentDurationUs != adPlaybackState.contentDurationUs) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs)
            updateAdPlaybackState()
        }
        val contentPositionMs: Long =
            androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(player, timeline, period)
        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
        handleTimelineOrPositionChanged()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        @Player.DiscontinuityReason reason: @Player.DiscontinuityReason Int,
    ) {
        handleTimelineOrPositionChanged()
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: @Player.State Int) {
        val player = this.player
        if (adsManager == null || player == null) {
            return
        }

        if (playbackState == Player.STATE_BUFFERING && !player.isPlayingAd() && this.isWaitingForFirstAdToPreload) {
            waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
        } else if (playbackState == Player.STATE_READY) {
            waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        }

        handlePlayerStateChanged(player.getPlayWhenReady(), playbackState)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean, @Player.PlayWhenReadyChangeReason reason: @Player.PlayWhenReadyChangeReason Int,
    ) {
        if (adsManager == null || player == null) {
            return
        }

        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PLAYING && !playWhenReady) {
            adsManager!!.pause()
            return
        }

        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PAUSED && playWhenReady) {
            adsManager!!.resume()
            return
        }
        handlePlayerStateChanged(playWhenReady, player!!.getPlaybackState())
    }

    override fun onPlayerError(error: PlaybackException) {
        if (imaAdState != androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE && Preconditions.checkNotNull<Player?>(
                player,
            ).isPlayingAd()
        ) {
            val adMediaInfo = Preconditions.checkNotNull<AdMediaInfo?>(imaAdMediaInfo)
            for (i in adCallbacks.indices) {
                adCallbacks.get(i)!!.onError(adMediaInfo)
            }
        }
    }

    // Internal methods.
    private fun requestAds(
        context: Context, imaSdkSettings: ImaSdkSettings, adDisplayContainer: AdDisplayContainer,
    ): com.google.ads.interactivemedia.v3.api.AdsLoader {
        val adsLoader = imaFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer)
        adsLoader.addAdErrorListener(componentListener)
        if (configuration.applicationAdErrorListener != null) {
            adsLoader.addAdErrorListener(configuration.applicationAdErrorListener!!)
        }
        adsLoader.addAdsLoadedListener(componentListener)
        val request: AdsRequest?
        if (adTagDataSpec.uri.getScheme() == C.CSAI_SCHEME
            && adTagDataSpec.uri.getAuthority() == ImaAdTagUriBuilder.IMA_AUTHORITY
        ) {
            request = ImaAdTagUriBuilder.createAdsRequest(imaFactory, adTagDataSpec.uri)
        } else {
            try {
                request = ImaUtil.getAdsRequestForAdTagDataSpec(imaFactory, adTagDataSpec)
            } catch (e: IOException) {
                adPlaybackState = AdPlaybackState(adsId)
                updateAdPlaybackState()
                pendingAdLoadError = AdsMediaSource.AdLoadException.createForAllAds(e)
                maybeNotifyPendingAdLoadError()
                return adsLoader
            }
        }
        pendingAdRequestContext = Any()
        request.setUserRequestContext(pendingAdRequestContext!!)
        if (configuration.enableContinuousPlayback != null) {
            request.setContinuousPlayback(configuration.enableContinuousPlayback!!)
        }
        if (configuration.vastLoadTimeoutMs != ImaUtil.TIMEOUT_UNSET) {
            request.setVastLoadTimeout(configuration.vastLoadTimeoutMs.toFloat())
        }
        request.setContentProgressProvider(contentPlaybackAdapter)
        adsLoader.requestAds(request)
        return adsLoader
    }

    private fun maybeInitializeAdsManager(contentPositionMs: Long, contentDurationMs: Long) {
        val adsManager = this.adsManager
        if (!isAdsManagerInitialized && adsManager != null) {
            isAdsManagerInitialized = true
            val adsRenderingSettings =
                setupAdsRendering(contentPositionMs, contentDurationMs)
            if (adsRenderingSettings == null) {
                // There are no ads to play.
                destroyAdsManager()
            } else {
                adsManager.init(adsRenderingSettings)
                adsManager.start()
                if (configuration.debugModeEnabled) {
                    Log.d(
                        androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                        "Initialized with ads rendering settings: " + adsRenderingSettings,
                    )
                }
            }
            updateAdPlaybackState()
        }
    }

    /**
     * Configures ads rendering for starting playback, returning the settings for the IMA SDK or
     * `null` if no ads should play.
     */
    private fun setupAdsRendering(contentPositionMs: Long, contentDurationMs: Long): AdsRenderingSettings? {
        val adsRenderingSettings = imaFactory.createAdsRenderingSettings()
        adsRenderingSettings.setEnablePreloading(true)
        adsRenderingSettings.setEnableCustomTabs(configuration.enableCustomTabs)
        adsRenderingSettings.setMimeTypes(
            (if (configuration.adMediaMimeTypes != null)
                configuration.adMediaMimeTypes
            else
                supportedMimeTypes)!!,
        )
        if (configuration.mediaLoadTimeoutMs != ImaUtil.TIMEOUT_UNSET) {
            adsRenderingSettings.setLoadVideoTimeout(configuration.mediaLoadTimeoutMs)
        }
        if (configuration.mediaBitrate != ImaUtil.BITRATE_UNSET) {
            adsRenderingSettings.setBitrateKbps(configuration.mediaBitrate / 1000)
        }
        adsRenderingSettings.setFocusSkipButtonWhenAvailable(
            configuration.focusSkipButtonWhenAvailable,
        )
        if (configuration.adUiElements != null) {
            adsRenderingSettings.setUiElements(configuration.adUiElements!!)
        }

        // Skip ads based on the start position as required.
        var adGroupForPositionIndex =
            adPlaybackState.getAdGroupIndexForPositionUs(
                Util.msToUs(contentPositionMs), Util.msToUs(contentDurationMs),
            )
        if (adGroupForPositionIndex != C.INDEX_UNSET) {
            val playAdWhenStartingPlayback =
                adPlaybackState.getAdGroup(adGroupForPositionIndex).timeUs == Util.msToUs(contentPositionMs)
                    || configuration.playAdBeforeStartPosition
            if (!playAdWhenStartingPlayback) {
                adGroupForPositionIndex++
            } else if (androidx.media3.exoplayer.ima.AdTagLoader.Companion.hasMidrollAdGroups(adPlaybackState)) {
                // Provide the player's initial position to trigger loading and playing the ad. If there are
                // no midrolls, we are playing a preroll and any pending content position wouldn't be
                // cleared.
                pendingContentPositionMs = contentPositionMs
            }
            if (adGroupForPositionIndex > 0) {
                for (i in 0..<adGroupForPositionIndex) {
                    adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
                }
                if (adGroupForPositionIndex == adPlaybackState.adGroupCount) {
                    // We don't need to play any ads. Because setPlayAdsAfterTime does not discard non-VMAP
                    // ads, we signal that no ads will render so the caller can destroy the ads manager.
                    return null
                }
                val adGroupForPositionTimeUs = adPlaybackState.getAdGroup(adGroupForPositionIndex).timeUs
                val adGroupBeforePositionTimeUs =
                    adPlaybackState.getAdGroup(adGroupForPositionIndex - 1).timeUs
                if (adGroupForPositionTimeUs == C.TIME_END_OF_SOURCE) {
                    // Play the postroll by offsetting the start position just past the last non-postroll ad.
                    adsRenderingSettings.setPlayAdsAfterTime(
                        adGroupBeforePositionTimeUs.toDouble() / C.MICROS_PER_SECOND + 1.0,
                    )
                } else {
                    // Play ads after the midpoint between the ad to play and the one before it, to avoid
                    // issues with rounding one of the two ad times.
                    val midpointTimeUs = (adGroupForPositionTimeUs + adGroupBeforePositionTimeUs) / 2.0
                    adsRenderingSettings.setPlayAdsAfterTime(midpointTimeUs / C.MICROS_PER_SECOND)
                }
            }
        }
        return adsRenderingSettings
    }

    private val contentVideoProgressUpdate: VideoProgressUpdate
        get() {
            val hasContentDuration = contentDurationMs != C.TIME_UNSET
            val contentPositionMs: Long
            if (pendingContentPositionMs != C.TIME_UNSET) {
                sentPendingContentPositionMs = true
                contentPositionMs = pendingContentPositionMs
            } else if (player == null) {
                return lastContentProgress
            } else if (fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET) {
                val elapsedSinceEndMs = SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs
                contentPositionMs = fakeContentProgressOffsetMs + elapsedSinceEndMs
            } else if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE && !playingAd && hasContentDuration) {
                contentPositionMs = androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(
                    player,
                    timeline,
                    period,
                )
            } else {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY
            }
            val contentDurationMs =
                if (hasContentDuration) this.contentDurationMs else androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_DURATION_UNSET
            return VideoProgressUpdate(contentPositionMs, contentDurationMs)
        }

    private val adVideoProgressUpdate: VideoProgressUpdate
        get() {
            if (player == null) {
                return lastAdProgress
            } else if (imaAdState != androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE && playingAd) {
                val adDuration = player!!.getDuration()
                return if (adDuration == C.TIME_UNSET)
                    VideoProgressUpdate.VIDEO_TIME_NOT_READY
                else
                    VideoProgressUpdate(player!!.getCurrentPosition(), adDuration)
            } else {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY
            }
        }

    private fun updateAdProgress() {
        val videoProgressUpdate = this.adVideoProgressUpdate
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "Ad progress: " + ImaUtil.getStringForVideoProgressUpdate(videoProgressUpdate),
            )
        }

        val adMediaInfo = Preconditions.checkNotNull<AdMediaInfo?>(imaAdMediaInfo)
        for (i in adCallbacks.indices) {
            adCallbacks.get(i)!!.onAdProgress(adMediaInfo, videoProgressUpdate)
        }
        handler.removeCallbacks(updateAdProgressRunnable)
        handler.postDelayed(
            updateAdProgressRunnable,
            androidx.media3.exoplayer.ima.AdTagLoader.Companion.AD_PROGRESS_UPDATE_INTERVAL_MS.toLong(),
        )
    }

    private fun stopUpdatingAdProgress() {
        handler.removeCallbacks(updateAdProgressRunnable)
    }

    private val playerVolumePercent: Int
        get() {
            val player = this.player
            if (player == null) {
                return lastVolumePercent
            }

            if (player.isCommandAvailable(Player.COMMAND_GET_VOLUME)) {
                return (player.getVolume() * 100).toInt()
            }

            // Check for a selected track using an audio renderer.
            return if (player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)) 100 else 0
        }

    private fun handleAdEvent(adEvent: AdEvent) {
        if (adsManager == null) {
            // Drop events after release.
            return
        }
        when (adEvent.getType()) {
            AdEvent.AdEventType.AD_BREAK_FETCH_ERROR -> {
                val adGroupTimeSecondsString =
                    Preconditions.checkNotNull<String?>(adEvent.getAdData().get("adBreakTime"))
                if (configuration.debugModeEnabled) {
                    Log.d(
                        androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                        "Fetch error for ad at " + adGroupTimeSecondsString + " seconds",
                    )
                }
                val adGroupTimeSeconds = adGroupTimeSecondsString.toDouble()
                val adGroupIndex =
                    if (adGroupTimeSeconds == -1.0)
                        adPlaybackState.adGroupCount - 1
                    else
                        getAdGroupIndexForCuePointTimeSeconds(adGroupTimeSeconds)
                markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
            }
            AdEvent.AdEventType.CONTENT_PAUSE_REQUESTED -> {
                // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
                // before sending CONTENT_RESUME_REQUESTED.
                imaPausedContent = true
                pauseContentInternal()
            }
            AdEvent.AdEventType.TAPPED -> {
                val i = 0
                while (i < eventListeners.size) {
                    eventListeners.get(i)!!.onAdTapped()
                    i++
                }
            }
            AdEvent.AdEventType.CLICKED -> {
                val i = 0
                while (i < eventListeners.size) {
                    eventListeners.get(i)!!.onAdClicked()
                    i++
                }
            }
            AdEvent.AdEventType.CONTENT_RESUME_REQUESTED -> {
                imaPausedContent = false
                resumeContentInternal()
            }
            AdEvent.AdEventType.LOG -> {
                val adData = adEvent.getAdData()
                val message = "AdEvent: " + adData
                Log.i(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, message)
            }
            else -> {}
        }
    }

    private fun pauseContentInternal() {
        imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE
        if (sentPendingContentPositionMs) {
            pendingContentPositionMs = C.TIME_UNSET
            sentPendingContentPositionMs = false
        }
    }

    private fun resumeContentInternal() {
        if (imaAdInfo != null) {
            // Mark current ad group as skipped if it hasn't finished yet. This could for example happen
            // after a load timeout where we receive CONTENT_RESUME_REQUESTED instead of loadAd.
            // See [Internal: b/330750756].
            adPlaybackState =
                adPlaybackState.withSkippedAdGroup(Preconditions.checkNotNull<AdInfo?>(imaAdInfo).adGroupIndex)
            updateAdPlaybackState()
        }
    }

    private val isWaitingForFirstAdToPreload: Boolean
        /**
         * Returns whether this instance is expecting the first ad in an the upcoming ad group to load
         * within the [preload timeout][ImaUtil.Configuration.adPreloadTimeoutMs].
         */
        get() {
            val player = this.player
            if (player == null) {
                return false
            }
            val adGroupIndex = this.loadingAdGroupIndex
            if (adGroupIndex == C.INDEX_UNSET) {
                return false
            }
            val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
            if (adGroup.count != C.LENGTH_UNSET && adGroup.count != 0 && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
                // An ad is available already.
                return false
            }
            val adGroupTimeMs = Util.usToMs(adGroup.timeUs)
            val contentPositionMs: Long =
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(player, timeline, period)
            val timeUntilAdMs = adGroupTimeMs - contentPositionMs
            return timeUntilAdMs < configuration.adPreloadTimeoutMs
        }

    private val isWaitingForCurrentAdToLoad: Boolean
        get() {
            val player = this.player
            if (player == null) {
                return false
            }
            val adGroupIndex = player.getCurrentAdGroupIndex()
            if (adGroupIndex == C.INDEX_UNSET) {
                return false
            }
            if (adGroupIndex >= adPlaybackState.adGroupCount) {
                return true
            }
            val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
            val adIndexInAdGroup = player.getCurrentAdIndexInAdGroup()
            if (adGroup.count == C.LENGTH_UNSET || adGroup.count <= adIndexInAdGroup) {
                return true
            }
            return adGroup.states[adIndexInAdGroup] == AdPlaybackState.AD_STATE_UNAVAILABLE
        }

    private fun handlePlayerStateChanged(playWhenReady: Boolean, @Player.State playbackState: @Player.State Int) {
        if (playingAd && imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PLAYING) {
            if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
                bufferingAd = true
                val adMediaInfo = Preconditions.checkNotNull<AdMediaInfo?>(imaAdMediaInfo)
                for (i in adCallbacks.indices) {
                    adCallbacks.get(i)!!.onBuffering(adMediaInfo)
                }
                stopUpdatingAdProgress()
            } else if (bufferingAd && playbackState == Player.STATE_READY) {
                bufferingAd = false
                updateAdProgress()
            }
        }

        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_ENDED)
            && playWhenReady
        ) {
            ensureSentContentCompleteIfAtEndOfStream()
        } else if (imaAdState != androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE && playbackState == Player.STATE_ENDED) {
            val adMediaInfo = imaAdMediaInfo
            if (adMediaInfo == null) {
                Log.w(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "onEnded without ad media info")
            } else {
                for (i in adCallbacks.indices) {
                    adCallbacks.get(i)!!.onEnded(adMediaInfo)
                }
            }
            if (configuration.debugModeEnabled) {
                Log.d(
                    androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                    "VideoAdPlayerCallback.onEnded in onPlaybackStateChanged",
                )
            }
        }
    }

    private fun handleTimelineOrPositionChanged() {
        val player = this.player
        if (adsManager == null || player == null) {
            return
        }
        if (!playingAd && !player.isPlayingAd()) {
            ensureSentContentCompleteIfAtEndOfStream()
            if (!sentContentComplete && !timeline.isEmpty()) {
                val positionMs: Long = androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(
                    player,
                    timeline,
                    period,
                )
                timeline.getPeriod(player.getCurrentPeriodIndex(), period)
                val newAdGroupIndex = period.getAdGroupIndexForPositionUs(Util.msToUs(positionMs))
                if (newAdGroupIndex != C.INDEX_UNSET) {
                    sentPendingContentPositionMs = false
                    pendingContentPositionMs = positionMs
                }
            }
        }

        val wasPlayingAd = playingAd
        val oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup
        playingAd = player.isPlayingAd()
        playingAdIndexInAdGroup = if (playingAd) player.getCurrentAdIndexInAdGroup() else C.INDEX_UNSET
        val adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup
        if (adFinished) {
            // IMA is waiting for the ad playback to finish so invoke the callback now.
            // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
            val adMediaInfo = imaAdMediaInfo
            if (adMediaInfo == null) {
                Log.w(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "onEnded without ad media info")
            } else {
                val adInfo = adInfoByAdMediaInfo.get(adMediaInfo)
                if (playingAdIndexInAdGroup == C.INDEX_UNSET
                    || (adInfo != null && adInfo.adIndexInAdGroup < playingAdIndexInAdGroup)
                ) {
                    for (i in adCallbacks.indices) {
                        adCallbacks.get(i)!!.onEnded(adMediaInfo)
                    }
                    if (configuration.debugModeEnabled) {
                        Log.d(
                            androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                            "VideoAdPlayerCallback.onEnded in onTimelineChanged/onPositionDiscontinuity",
                        )
                    }
                }
            }
        }
        if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE) {
            val adGroup = adPlaybackState.getAdGroup(player.getCurrentAdGroupIndex())
            if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
                sendContentComplete()
            } else {
                // IMA hasn't called playAd yet, so fake the content position.
                fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
                fakeContentProgressOffsetMs = Util.usToMs(adGroup.timeUs)
                if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                    fakeContentProgressOffsetMs = contentDurationMs
                }
            }
        }
        if (this.isWaitingForCurrentAdToLoad) {
            handler.removeCallbacks(adLoadTimeoutRunnable)
            handler.postDelayed(adLoadTimeoutRunnable, configuration.adPreloadTimeoutMs)
        }
    }

    private fun loadAdInternal(adMediaInfo: AdMediaInfo, adPodInfo: AdPodInfo) {
        if (adsManager == null) {
            // Drop events after release.
            if (configuration.debugModeEnabled) {
                Log.d(
                    androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                    "loadAd after release " + getAdMediaInfoString(adMediaInfo) + ", ad pod " + adPodInfo,
                )
            }
            return
        }

        val adGroupIndex = getAdGroupIndexForAdPod(adPodInfo)
        val adIndexInAdGroup = adPodInfo.getAdPosition() - 1
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        // The ad URI may already be known, so force put to update it if needed.
        adInfoByAdMediaInfo.forcePut(adMediaInfo, adInfo)
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "loadAd " + getAdMediaInfoString(adMediaInfo),
            )
        }
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request. IMA will
            // timeout after its media load timeout.
            return
        }

        // The ad count may increase on successive loads of ads in the same ad pod, for example, due to
        // separate requests for ad tags with multiple ads within the ad pod completing after an earlier
        // ad has loaded. See also https://github.com/google/ExoPlayer/issues/7477.
        var adGroup = adPlaybackState.getAdGroup(adInfo.adGroupIndex)
        adPlaybackState =
            adPlaybackState.withAdCount(
                adInfo.adGroupIndex, max(adPodInfo.getTotalAds(), adGroup.states.size),
            )
        adGroup = adPlaybackState.getAdGroup(adInfo.adGroupIndex)
        for (i in 0..<adIndexInAdGroup) {
            // Any preceding ads that haven't loaded are not going to load.
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex,  /* adIndexInAdGroup= */i)
            }
        }

        val adMediaItem = MediaItem.Builder().setUri(adMediaInfo.getUrl())
        // Use the video MIME type if it is provided.
        // Demuxed streams may contain an audio MIME type, however it should only be used to set the
        // audio MIME type or compose the audio codec string, when/if ExoPlayer introduces support for
        // demuxed streams functionality. Even audio-only streams should only use the video MIME type as
        // they are not demuxed. It is possible that the video MIME type is not provided, in which case,
        // we do not set the MIME type of the MediaItem. However, if an audio MIME type is provided, it
        // is most likely that the video MIME type is also provided (though not the other way around).
        val videoMimeType = adMediaInfo.getVideoMimeType()
        if (videoMimeType != null) {
            adMediaItem.setMimeType(videoMimeType)
        }

        adPlaybackState =
            adPlaybackState.withAvailableAdMediaItem(
                adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adMediaItem.build(),
            )
        updateAdPlaybackState()
    }

    private fun playAdInternal(adMediaInfo: AdMediaInfo) {
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "playAd " + getAdMediaInfoString(adMediaInfo),
            )
        }
        if (adsManager == null) {
            // Drop events after release.
            return
        }

        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PLAYING) {
            // IMA does not always call stopAd before resuming content.
            // See [Internal: b/38354028].
            Log.w(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "Unexpected playAd without stopAd")
        }

        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE) {
            // IMA is requesting to play the ad, so stop faking the content position.
            fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
            fakeContentProgressOffsetMs = C.TIME_UNSET
            imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PLAYING
            imaAdMediaInfo = adMediaInfo
            imaAdInfo = Preconditions.checkNotNull<AdInfo?>(adInfoByAdMediaInfo.get(adMediaInfo))
            for (i in adCallbacks.indices) {
                adCallbacks.get(i)!!.onPlay(adMediaInfo)
            }
            if (pendingAdPrepareErrorAdInfo != null && pendingAdPrepareErrorAdInfo == imaAdInfo) {
                pendingAdPrepareErrorAdInfo = null
                for (i in adCallbacks.indices) {
                    adCallbacks.get(i)!!.onError(adMediaInfo)
                }
            }
            updateAdProgress()
        } else {
            imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PLAYING
            Preconditions.checkState(adMediaInfo == imaAdMediaInfo)
            for (i in adCallbacks.indices) {
                adCallbacks.get(i)!!.onResume(adMediaInfo)
            }
        }
        if (player == null || !player!!.getPlayWhenReady()) {
            // Either this loader hasn't been activated yet, or the player is paused now.
            Preconditions.checkNotNull<AdsManager?>(adsManager).pause()
        }
    }

    private fun pauseAdInternal(adMediaInfo: AdMediaInfo) {
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "pauseAd " + getAdMediaInfoString(adMediaInfo),
            )
        }
        if (adsManager == null) {
            // Drop event after release.
            return
        }
        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the loaded ad won't play due to a seek
            // to a different position, so drop the event. See also [Internal: b/159111848].
            return
        }
        if (configuration.debugModeEnabled && adMediaInfo != imaAdMediaInfo) {
            Log.w(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                ("Unexpected pauseAd for "
                    + getAdMediaInfoString(adMediaInfo)
                    + ", expected "
                    + getAdMediaInfoString(imaAdMediaInfo)),
            )
        }
        imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_PAUSED
        for (i in adCallbacks.indices) {
            adCallbacks.get(i)!!.onPause(adMediaInfo)
        }
    }

    private fun stopAdInternal(adMediaInfo: AdMediaInfo?) {
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "stopAd " + getAdMediaInfoString(adMediaInfo),
            )
        }
        if (adsManager == null) {
            // Drop event after release.
            return
        }
        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the preloaded ad won't play due to a
            // seek to a different position, so drop the event and discard the ad. See also [Internal:
            // b/159111848].
            val adInfo = adInfoByAdMediaInfo.get(adMediaInfo)
            if (adInfo != null) {
                adPlaybackState =
                    adPlaybackState.withSkippedAd(adInfo.adGroupIndex, adInfo.adIndexInAdGroup)
                updateAdPlaybackState()
            }
            return
        }
        imaAdState = androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE
        stopUpdatingAdProgress()
        // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
        Preconditions.checkNotNull<AdInfo?>(imaAdInfo)
        val adGroupIndex = imaAdInfo!!.adGroupIndex
        val adIndexInAdGroup = imaAdInfo!!.adIndexInAdGroup
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request.
            return
        }
        adPlaybackState =
            adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup).withAdResumePositionUs(0)
        updateAdPlaybackState()
        if (!playingAd) {
            imaAdMediaInfo = null
            imaAdInfo = null
        }
    }

    private fun handleAdGroupLoadError(error: Exception) {
        val adGroupIndex = this.loadingAdGroupIndex
        if (adGroupIndex == C.INDEX_UNSET) {
            Log.w(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "Unable to determine ad group index for ad group load error",
                error,
            )
            return
        }
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
        if (pendingAdLoadError == null) {
            pendingAdLoadError = AdsMediaSource.AdLoadException.createForAdGroup(error, adGroupIndex)
        }
    }

    private fun handleAdLoadTimeout() {
        // We started the timeout when we were first waiting for the current ad to load. Check if we are
        // still waiting after the timeout before triggering the error event.
        if (!this.isWaitingForCurrentAdToLoad) {
            return
        }
        // IMA got stuck and didn't load an ad in time, so skip the entire group.
        handleAdGroupLoadError(IOException("Ad loading timed out"))
        maybeNotifyPendingAdLoadError()
    }

    private fun markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex: Int) {
        // Update the ad playback state so all ads in the ad group are in the error state.
        var adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        if (adGroup.count == C.LENGTH_UNSET) {
            adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, max(1, adGroup.states.size))
            adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        }
        for (i in 0..<adGroup.count) {
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                if (configuration.debugModeEnabled) {
                    Log.d(
                        androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                        "Removing ad " + i + " in ad group " + adGroupIndex,
                    )
                }
                adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i)
            }
        }
        updateAdPlaybackState()
        // Clear any pending content position that triggered attempting to load the ad group.
        pendingContentPositionMs = C.TIME_UNSET
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
    }

    private fun handleAdPrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: Exception?) {
        if (configuration.debugModeEnabled) {
            Log.d(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                "Prepare error for ad " + adIndexInAdGroup + " in group " + adGroupIndex,
                exception,
            )
        }
        if (adsManager == null) {
            Log.w(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "Ignoring ad prepare error after release")
            return
        }
        if (imaAdState == androidx.media3.exoplayer.ima.AdTagLoader.Companion.IMA_AD_STATE_NONE) {
            // Send IMA a content position at the ad group so that it will try to play it, at which point
            // we can notify that it failed to load.
            fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
            fakeContentProgressOffsetMs = Util.usToMs(adPlaybackState.getAdGroup(adGroupIndex).timeUs)
            if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                fakeContentProgressOffsetMs = contentDurationMs
            }
            pendingAdPrepareErrorAdInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        } else {
            val adMediaInfo = Preconditions.checkNotNull<AdMediaInfo?>(imaAdMediaInfo)
            // We're already playing an ad.
            if (adIndexInAdGroup > playingAdIndexInAdGroup) {
                // Mark the playing ad as ended so we can notify the error on the next ad and remove it,
                // which means that the ad after will load (if any).
                for (i in adCallbacks.indices) {
                    adCallbacks.get(i)!!.onEnded(adMediaInfo)
                }
            }
            playingAdIndexInAdGroup = adPlaybackState.getAdGroup(adGroupIndex).getFirstAdIndexToPlay()
            for (i in adCallbacks.indices) {
                adCallbacks.get(i)!!.onError(Preconditions.checkNotNull<AdMediaInfo?>(adMediaInfo))
            }
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    private fun ensureSentContentCompleteIfAtEndOfStream() {
        if (sentContentComplete
            || contentDurationMs == C.TIME_UNSET || pendingContentPositionMs != C.TIME_UNSET
        ) {
            return
        }
        val contentPeriodPositionMs: Long =
            androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(
                Preconditions.checkNotNull<Player?>(
                    player,
                ),
                timeline, period,
            )
        if (contentPeriodPositionMs + androidx.media3.exoplayer.ima.AdTagLoader.Companion.THRESHOLD_END_OF_CONTENT_MS < contentDurationMs) {
            return
        }
        val pendingAdGroupIndex =
            adPlaybackState.getAdGroupIndexForPositionUs(
                Util.msToUs(contentPeriodPositionMs), Util.msToUs(contentDurationMs),
            )
        if (pendingAdGroupIndex != C.INDEX_UNSET && adPlaybackState.getAdGroup(pendingAdGroupIndex).timeUs != C.TIME_END_OF_SOURCE && adPlaybackState.getAdGroup(
                pendingAdGroupIndex,
            ).shouldPlayAdGroup()
        ) {
            // Pending mid-roll ad that needs to be played before marking the content complete.
            return
        }
        sendContentComplete()
    }

    private fun sendContentComplete() {
        for (i in adCallbacks.indices) {
            adCallbacks.get(i)!!.onContentComplete()
        }
        sentContentComplete = true
        if (configuration.debugModeEnabled) {
            Log.d(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "adsLoader.contentComplete")
        }
        for (i in 0..<adPlaybackState.adGroupCount) {
            if (adPlaybackState.getAdGroup(i).timeUs != C.TIME_END_OF_SOURCE) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup( /* adGroupIndex= */i)
            }
        }
        updateAdPlaybackState()
    }

    private fun updateAdPlaybackState() {
        for (i in eventListeners.indices) {
            eventListeners.get(i)!!.onAdPlaybackState(adPlaybackState)
        }
    }

    private fun maybeNotifyPendingAdLoadError() {
        if (pendingAdLoadError != null) {
            for (i in eventListeners.indices) {
                eventListeners.get(i)!!.onAdLoadError(pendingAdLoadError!!, adTagDataSpec)
            }
            pendingAdLoadError = null
        }
    }

    private fun maybeNotifyInternalError(name: String, cause: Exception?) {
        val message = "Internal error in " + name
        Log.e(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, message, cause)
        // We can't recover from an unexpected error in general, so skip all remaining ads.
        for (i in 0..<adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
        for (i in eventListeners.indices) {
            eventListeners
                .get(i)!!
                .onAdLoadError(
                    AdsMediaSource.AdLoadException.createForUnexpected(RuntimeException(message, cause)),
                    adTagDataSpec,
                )
        }
    }

    private fun getAdGroupIndexForAdPod(adPodInfo: AdPodInfo): Int {
        if (adPodInfo.getPodIndex() == -1) {
            // This is a postroll ad.
            return adPlaybackState.adGroupCount - 1
        }

        // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
        return getAdGroupIndexForCuePointTimeSeconds(adPodInfo.getTimeOffset())
    }

    private val loadingAdGroupIndex: Int
        /**
         * Returns the index of the ad group that will preload next, or [C.INDEX_UNSET] if there is
         * no such ad group.
         */
        get() {
            if (player == null) {
                return C.INDEX_UNSET
            }
            val playerPositionUs = Util.msToUs(
                androidx.media3.exoplayer.ima.AdTagLoader.Companion.getContentPeriodPositionMs(
                    player,
                    timeline,
                    period,
                ),
            )
            var adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, Util.msToUs(contentDurationMs))
            if (adGroupIndex == C.INDEX_UNSET) {
                adGroupIndex =
                    adPlaybackState.getAdGroupIndexAfterPositionUs(
                        playerPositionUs, Util.msToUs(contentDurationMs),
                    )
            }
            return adGroupIndex
        }

    private fun getAdGroupIndexForCuePointTimeSeconds(cuePointTimeSeconds: Double): Int {
        // We receive initial cue points from IMA SDK as floats. This code replicates the same
        // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
        // failures if the behavior of the IMA SDK changes to provide greater precision).
        val cuePointTimeSecondsFloat = cuePointTimeSeconds.toFloat()
        val adPodTimeUs = Math.round(cuePointTimeSecondsFloat.toDouble() * C.MICROS_PER_SECOND)
        for (adGroupIndex in 0..<adPlaybackState.adGroupCount) {
            val adGroupTimeUs = adPlaybackState.getAdGroup(adGroupIndex).timeUs
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                && abs(adGroupTimeUs - adPodTimeUs) < androidx.media3.exoplayer.ima.AdTagLoader.Companion.THRESHOLD_AD_MATCH_US
            ) {
                return adGroupIndex
            }
        }
        throw IllegalStateException("Failed to find cue point")
    }

    private fun getAdMediaInfoString(adMediaInfo: AdMediaInfo?): String {
        val adInfo = adInfoByAdMediaInfo.get(adMediaInfo)
        return ("AdMediaInfo["
            + (if (adMediaInfo == null) "null" else adMediaInfo.getUrl())
            + ", "
            + adInfo
            + "]")
    }

    private fun destroyAdsManager() {
        if (adsManager != null) {
            adsManager!!.removeAdErrorListener(componentListener)
            if (configuration.applicationAdErrorListener != null) {
                adsManager!!.removeAdErrorListener(configuration.applicationAdErrorListener!!)
            }
            adsManager!!.removeAdEventListener(componentListener)
            if (configuration.applicationAdEventListener != null) {
                adsManager!!.removeAdEventListener(configuration.applicationAdEventListener!!)
            }
            adsManager!!.destroy()
            adsManager = null
        }
    }

    private inner class ContentPlaybackAdapter : ContentProgressProvider {
        override fun getContentProgress(): VideoProgressUpdate {
            val videoProgressUpdate: VideoProgressUpdate = this.contentVideoProgressUpdate
            if (configuration.debugModeEnabled) {
                Log.d(
                    androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG,
                    "Content progress: " + ImaUtil.getStringForVideoProgressUpdate(videoProgressUpdate),
                )
            }

            if (waitingForPreloadElapsedRealtimeMs != C.TIME_UNSET) {
                // IMA is polling the player position but we are buffering for an ad to preload, so playback
                // may be stuck. Detect this case and signal an error if applicable.
                val stuckElapsedRealtimeMs =
                    SystemClock.elapsedRealtime() - waitingForPreloadElapsedRealtimeMs
                if (stuckElapsedRealtimeMs >= configuration.adPreloadTimeoutMs) {
                    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
                    handleAdGroupLoadError(IOException("Ad preloading timed out"))
                    maybeNotifyPendingAdLoadError()
                }
            } else if (pendingContentPositionMs != C.TIME_UNSET && player != null && player!!.getPlaybackState() == Player.STATE_BUFFERING && this.isWaitingForFirstAdToPreload) {
                // Prepare to timeout the load of an ad for the pending seek operation.
                waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
            }

            return videoProgressUpdate
        }
    }

    private inner class ComponentListener

        : AdsLoadedListener, AdEventListener, AdErrorListener {
        // AdsLoader.AdsLoadedListener implementation.
        override fun onAdsManagerLoaded(adsManagerLoadedEvent: AdsManagerLoadedEvent) {
            val adsManager = adsManagerLoadedEvent.getAdsManager()
            if (adsManager == null) {
                // The same AdsLoader may be used for both Client-side ads and SSAI ads at the same time.
                // In this scenario, it may emit an `AdsManagerLoadedEvent` which should be handled by the
                // `ImaServerSideAdInsertionMediaSource` instead of the `AdTagLoader`. It's safe to ignore
                // that event.
                return
            }
            if (pendingAdRequestContext != adsManagerLoadedEvent.getUserRequestContext()) {
                adsManager.destroy()
                return
            }
            pendingAdRequestContext = null
            this@AdTagLoader.adsManager = adsManager
            adsManager.addAdErrorListener(this)
            if (configuration.applicationAdErrorListener != null) {
                adsManager.addAdErrorListener(configuration.applicationAdErrorListener!!)
            }
            adsManager.addAdEventListener(this)
            if (configuration.applicationAdEventListener != null) {
                adsManager.addAdEventListener(configuration.applicationAdEventListener!!)
            }
            try {
                adPlaybackState =
                    AdPlaybackState(adsId, *ImaUtil.getAdGroupTimesUsForCuePoints(adsManager.getAdCuePoints()))
                updateAdPlaybackState()
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("onAdsManagerLoaded", e)
            }
        }

        // AdEvent.AdEventListener implementation.
        override fun onAdEvent(adEvent: AdEvent) {
            val adEventType = adEvent.getType()
            if (configuration.debugModeEnabled && adEventType != AdEvent.AdEventType.AD_PROGRESS) {
                Log.d(androidx.media3.exoplayer.ima.AdTagLoader.TAG, "onAdEvent: " + adEventType)
            }
            try {
                handleAdEvent(adEvent)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("onAdEvent", e)
            }
        }

        // AdErrorEvent.AdErrorListener implementation.
        override fun onAdError(adErrorEvent: AdErrorEvent) {
            val error = adErrorEvent.getError()
            if (configuration.debugModeEnabled) {
                Log.d(androidx.media3.exoplayer.ima.AdTagLoader.Companion.TAG, "onAdError", error)
            }
            if (adsManager == null) {
                // No ads were loaded, so allow playback to start without any ads.
                pendingAdRequestContext = null
                adPlaybackState = AdPlaybackState(adsId)
                updateAdPlaybackState()
            } else if (ImaUtil.isAdGroupLoadError(error)) {
                try {
                    handleAdGroupLoadError(error)
                } catch (e: RuntimeException) {
                    maybeNotifyInternalError("onAdError", e)
                }
            }
            if (pendingAdLoadError == null) {
                pendingAdLoadError = AdsMediaSource.AdLoadException.createForAllAds(error)
            }
            maybeNotifyPendingAdLoadError()
        }
    }

    internal inner class VideoAdPlayerImpl : VideoAdPlayer {
        override fun addCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback?) {
            adCallbacks.add(videoAdPlayerCallback)
        }

        override fun removeCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback?) {
            adCallbacks.remove(videoAdPlayerCallback)
        }

        override fun getAdProgress(): VideoProgressUpdate {
            throw IllegalStateException("Unexpected call to getAdProgress when using preloading")
        }

        override fun getVolume(): Int {
            return this.playerVolumePercent
        }

        override fun loadAd(adMediaInfo: AdMediaInfo, adPodInfo: AdPodInfo) {
            try {
                loadAdInternal(adMediaInfo, adPodInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("loadAd", e)
            }
        }

        override fun playAd(adMediaInfo: AdMediaInfo) {
            try {
                playAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("playAd", e)
            }
        }

        override fun pauseAd(adMediaInfo: AdMediaInfo) {
            try {
                pauseAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("pauseAd", e)
            }
        }

        override fun stopAd(adMediaInfo: AdMediaInfo?) {
            try {
                stopAdInternal(adMediaInfo)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("stopAd", e)
            }
        }

        override fun release() {
            // Do nothing.
        }
    }

    // TODO: Consider moving this into AdPlaybackState.
    private class AdInfo(val adGroupIndex: Int, val adIndexInAdGroup: Int) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val adInfo: AdInfo = o as AdInfo
            if (adGroupIndex != adInfo.adGroupIndex) {
                return false
            }
            return adIndexInAdGroup == adInfo.adIndexInAdGroup
        }

        override fun hashCode(): Int {
            var result = adGroupIndex
            result = 31 * result + adIndexInAdGroup
            return result
        }

        override fun toString(): String {
            return "(" + adGroupIndex + ", " + adIndexInAdGroup + ')'
        }
    }

    companion object {
        private const val TAG = "AdTagLoader"

        private const val IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima"
        private val IMA_SDK_SETTINGS_PLAYER_VERSION = MediaLibraryInfo.VERSION

        /**
         * Interval at which ad progress updates are provided to the IMA SDK, in milliseconds. 200 ms is
         * the interval recommended by the Media Rating Council (MRC) for minimum polling of viewable
         * video impressions.
         * http://www.mediaratingcouncil.org/063014%20Viewable%20Ad%20Impression%20Guideline_Final.pdf.
         *
         * @see VideoAdPlayer.VideoAdPlayerCallback
         */
        private const val AD_PROGRESS_UPDATE_INTERVAL_MS = 200

        /** The value used in [VideoProgressUpdate]s to indicate an unset duration.  */
        private val IMA_DURATION_UNSET = -1L

        /**
         * Threshold before the end of content at which IMA is notified that content is complete if the
         * player buffers, in milliseconds.
         */
        private const val THRESHOLD_END_OF_CONTENT_MS: Long = 5000

        /** The threshold below which ad cue points are treated as matching, in microseconds.  */
        private const val THRESHOLD_AD_MATCH_US: Long = 1000

        /** The ad playback state when IMA is not playing an ad.  */
        private const val IMA_AD_STATE_NONE = 0

        /**
         * The ad playback state when IMA has called [ComponentListener.playAd] and not
         * [ComponentListener..pauseAd].
         */
        private const val IMA_AD_STATE_PLAYING = 1

        /**
         * The ad playback state when IMA has called [ComponentListener.pauseAd] while
         * playing an ad.
         */
        private const val IMA_AD_STATE_PAUSED = 2

        private fun getContentPeriodPositionMs(
            player: Player, timeline: Timeline, period: Timeline.Period,
        ): Long {
            val contentWindowPositionMs = player.getContentPosition()
            if (timeline.isEmpty()) {
                return contentWindowPositionMs
            } else {
                return (contentWindowPositionMs
                    - timeline.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs())
            }
        }

        private fun hasMidrollAdGroups(adPlaybackState: AdPlaybackState): Boolean {
            val count = adPlaybackState.adGroupCount
            if (count == 1) {
                val adGroupTimeUs = adPlaybackState.getAdGroup(0).timeUs
                return adGroupTimeUs != 0L && adGroupTimeUs != C.TIME_END_OF_SOURCE
            } else if (count == 2) {
                return adPlaybackState.getAdGroup(0).timeUs != 0L
                    || adPlaybackState.getAdGroup(1).timeUs != C.TIME_END_OF_SOURCE
            } else {
                // There's at least one midroll ad group, as adPlaybackState is never empty.
                return true
            }
        }
    }
}
