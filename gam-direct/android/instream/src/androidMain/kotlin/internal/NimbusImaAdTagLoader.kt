@file:UnstableApi

package com.adsbynimbus.solutions.gamdirect.instream.internal

import android.content.*
import android.os.*
import android.os.SystemClock
import android.view.*
import androidx.annotation.*
import androidx.media3.common.*
import androidx.media3.common.Player.*
import androidx.media3.common.util.*
import androidx.media3.common.util.Util.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.source.ads.AdsLoader
import androidx.media3.exoplayer.source.ads.AdsMediaSource.*
import com.adsbynimbus.request.*
import com.adsbynimbus.solutions.gamdirect.instream.*
import com.adsbynimbus.solutions.gamdirect.instream.internal.NimbusImaAdTagLoader.Companion.IMA_AD_STATE_NONE
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.AdErrorEvent.*
import com.google.ads.interactivemedia.v3.api.AdEvent.*
import com.google.ads.interactivemedia.v3.api.AdsLoader.*
import com.google.ads.interactivemedia.v3.api.player.*
import kotlinx.coroutines.*
import kotlin.math.*

/** Handles loading and playback of a single ad tag.  */
internal class NimbusImaAdTagLoader(
    context: Context,
    adViewGroup: ViewGroup?,
    private val configuration: NimbusImaAdLoader,
    private val supportedMimeTypes: List<String>,
    private val adTagDataSpec: DataSpec,
    private val adsId: Any,
) : Listener, RequestManager {
    private val period: Timeline.Period = Timeline.Period()
    private val handler: Handler = createHandler(Looper.getMainLooper(), null)
    private val componentListener: ComponentListener = ComponentListener()
    private val contentPlaybackAdapter: ContentPlaybackAdapter = ContentPlaybackAdapter()
    private val eventListeners: MutableList<AdsLoader.EventListener> = mutableListOf()
    private val adCallbacks: MutableList<VideoAdPlayer.VideoAdPlayerCallback> =
        ArrayList<VideoAdPlayer.VideoAdPlayerCallback>(1).apply {
            configuration.videoAdPlayerCallback?.let { add(it) }
        }
    private val adLoadTimeoutRunnable: Runnable = Runnable { handleAdLoadTimeout() }
    private val updateAdProgressRunnable: Runnable = Runnable { updateAdProgress() }
    private val adInfoByAdMediaInfo: MutableMap<AdMediaInfo, AdInfo> = mutableMapOf()

    /** Returns the IMA SDK ad display container.  */
    val adDisplayContainer: AdDisplayContainer = with(ImaFactory) {
        val playerImpl = VideoAdPlayerImpl()
        if (adViewGroup != null) createAdDisplayContainer(adViewGroup, playerImpl) else {
            createAudioAdDisplayContainer(context, playerImpl)
        }
    }.apply {
        configuration.companionAdSlots?.let { setCompanionSlots(it) }
    }

    inline val imaSdkSettings: ImaSdkSettings
        get() = (configuration.imaSdkSettings ?: ImaFactory.createImaSdkSettings()).apply {
            if (configuration.debugModeEnabled) {
                isDebugMode = true
            }
            setPlayerType(IMA_SDK_SETTINGS_PLAYER_TYPE)
            setPlayerVersion(IMA_SDK_SETTINGS_PLAYER_VERSION)
        }

    /** Returns the underlying IMA SDK ads loader.  */
    val adsLoader: com.google.ads.interactivemedia.v3.api.AdsLoader =
        requestAds(context, imaSdkSettings, adDisplayContainer)

    private var pendingAdRequestContext: Any? = null
    private var player: Player? = null
    private var lastContentProgress: VideoProgressUpdate = VideoProgressUpdate.VIDEO_TIME_NOT_READY
    private var lastAdProgress: VideoProgressUpdate = VideoProgressUpdate.VIDEO_TIME_NOT_READY
    private var lastVolumePercent = 0

    private var adsManager: AdsManager? = null
    private var isAdsManagerInitialized = false
    private var pendingAdLoadError: AdLoadException? = null
    private var timeline: Timeline = Timeline.EMPTY
    private var contentDurationMs: Long = C.TIME_UNSET
    private var adPlaybackState: AdPlaybackState = AdPlaybackState.NONE
    private var released = false

    // Fields tracking IMA's state.
    /** Whether IMA has sent an ad event to pause content since the last resume content event.  */
    private var imaPausedContent = false

    /** The current ad playback state.  */

    private var imaAdState: @ImaAdState Int = 0

    /** The current ad media info, or `null` if in state [IMA_AD_STATE_NONE].  */
    private var imaAdMediaInfo: AdMediaInfo? = null

    /** The current ad info, or `null` if in state [IMA_AD_STATE_NONE].  */
    private var imaAdInfo: AdInfo? = null

    /** Whether IMA has been notified that playback of content has finished.  */
    private var sentContentComplete = false

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
     * If a content period has finished but IMA has not yet called `ComponentListener.playAd`,
     * stores the value of [SystemClock.elapsedRealtime] when the content stopped playing.
     * This can be used to determine a fake, increasing content position. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressElapsedRealtimeMs: Long = C.TIME_UNSET

    /**
     * If [.fakeContentProgressElapsedRealtimeMs] is set, stores the offset from which the
     * content progress should increase. [C.TIME_UNSET] otherwise.
     */
    private var fakeContentProgressOffsetMs: Long = C.TIME_UNSET

    /** Stores the pending content position when a seek operation was intercepted to play an ad.  */
    private var pendingContentPositionMs: Long = C.TIME_UNSET

    /**
     * Whether `ComponentListener.getContentProgress` has sent `.pendingContentPositionMs` to IMA.
     */
    private var sentPendingContentPositionMs = false

    /**
     * Stores the real time in milliseconds at which the player started buffering, possibly due to not
     * having preloaded an ad, or [C.TIME_UNSET] if not applicable.
     */
    private var waitingForPreloadElapsedRealtimeMs: Long = C.TIME_UNSET

    /** Skips the current skippable ad, if there is one.  */
    fun skipAd() {
        adsManager?.skip()
    }

    /**
     * Moves UI focus to the skip button (or other interactive elements), if currently shown. See
     * [AdsManager.focus].
     */
    fun focusSkipButton() {
        adsManager?.focus()
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
        } else adsManager?.run {
            adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(adCuePoints))
            updateAdPlaybackState()
        }
        for (overlayInfo in adViewProvider.adOverlayInfos) {
            adDisplayContainer.registerFriendlyObstruction(
                ImaFactory.createFriendlyObstruction(
                    overlayInfo.view,
                    getFriendlyObstructionPurpose(overlayInfo.purpose),
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

        val playWhenReady = player.playWhenReady
        onTimelineChanged(player.currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        val adsManager = this.adsManager
        if ((AdPlaybackState.NONE != adPlaybackState) && adsManager != null && imaPausedContent) {
            // Check whether the current ad break matches the expected ad break based on the current
            // position. If not, discard the current ad break so that the correct ad break can load.
            val contentPositionMs: Long = getContentPeriodPositionMs(player, timeline, period)
            val adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                msToUs(contentPositionMs), msToUs(contentDurationMs),
            )
            if (adGroupForPositionIndex != C.INDEX_UNSET
                && imaAdInfo?.let { it.adGroupIndex != adGroupForPositionIndex } == true
            ) {
                if (configuration.debugModeEnabled) Log.d(TAG, "Discarding preloaded ad $imaAdInfo")
                adsManager.discardAdBreak()
            }
            if (playWhenReady) {
                adsManager.resume()
            }
        }
    }

    /** Deactivates playback.  */
    fun deactivate() {
        // Post deactivation behind any already queued Player.Listener events to ensure that
        // any pending events are processed before the listener is removed and the ads manager paused.
        player?.let { handler.post { deactivateInternal(it) } }
    }

    /**
     * Deactivates playback internally, after the Listener.onEvents() cycle completes so the complete
     * state change picture is clear. For example, if an error caused the deactivation, the error
     * callback can be handled first.
     */
    private fun deactivateInternal(player: Player) {
        if (adPlaybackState != AdPlaybackState.NONE && imaPausedContent && player.playerError == null) {
            // Only need to pause and store resume position if not in error state.
            adsManager?.pause()
            adPlaybackState = adPlaybackState.withAdResumePositionUs(
                if (playingAd) msToUs(player.currentPosition) else 0,
            )
        }
        lastVolumePercent = playerVolumePercent
        lastAdProgress = adVideoProgressUpdate
        lastContentProgress = contentVideoProgressUpdate
        player.removeListener(this)
        this.player = null
    }

    /** Stops passing of events from this instance and unregisters obstructions.  */
    fun removeListener(eventListener: AdsLoader.EventListener) {
        eventListeners.remove(eventListener)
        if (eventListeners.isEmpty()) {
            adDisplayContainer.unregisterAllFriendlyObstructions()
        }
    }

    /** Releases all resources used by the ad tag loader.  */
    fun release() {
        if (released) return
        released = true
        pendingAdRequestContext = null
        destroyAdsManager()
        adsLoader.removeAdsLoadedListener(componentListener)
        adsLoader.removeAdErrorListener(componentListener)
        configuration.adErrorListener?.let { adsLoader.removeAdErrorListener(it) }
        adsLoader.release()
        imaPausedContent = false
        imaAdState = IMA_AD_STATE_NONE
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
        if (configuration.debugModeEnabled) Log.d(TAG, "Prepared ad $adInfo")

        adInfoByAdMediaInfo.getKey(adInfo)?.let {
            for (i in adCallbacks.indices) {
                adCallbacks[i].onAdProgress(it, adVideoProgressUpdate)
            }
        } ?: run {
            Log.w(TAG, "Unexpected prepared ad $adInfo")
        }
    }

    /** Notifies the IMA SDK that the specified ad has failed to prepare for playback.  */
    fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: java.io.IOException?) {
        if (player == null) return else try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception)
        } catch (e: RuntimeException) {
            maybeNotifyInternalError("handlePrepareError", e)
        }
    }

    // Player.Listener implementation.
    override fun onTimelineChanged(timeline: Timeline, @TimelineChangeReason reason: Int) {
        val player = this.player.takeUnless { timeline.isEmpty } ?: return
        this.timeline = timeline
        val contentDurationUs = timeline.getPeriod(player.currentPeriodIndex, period).durationUs
        contentDurationMs = usToMs(contentDurationUs)
        if (contentDurationUs != adPlaybackState.contentDurationUs) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs)
            updateAdPlaybackState()
        }
        val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
        handleTimelineOrPositionChanged()
    }

    override fun onPositionDiscontinuity(
        oldPosition: PositionInfo,
        newPosition: PositionInfo,
        @DiscontinuityReason reason: Int,
    ) {
        handleTimelineOrPositionChanged()
    }

    override fun onPlaybackStateChanged(@State playbackState: Int) {
        val player = this.player.takeUnless { adsManager == null } ?: return
        if (playbackState == STATE_BUFFERING && !player.isPlayingAd && isWaitingForFirstAdToPreload) {
            waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
        } else if (playbackState == STATE_READY) {
            waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        }

        handlePlayerStateChanged(player.playWhenReady, playbackState)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        @PlayWhenReadyChangeReason reason: Int,
    ) {
        when (imaAdState) {
            IMA_AD_STATE_PLAYING -> if (!playWhenReady) adsManager?.pause()
            IMA_AD_STATE_PAUSED -> if (playWhenReady) adsManager?.resume()
            else -> player?.let { handlePlayerStateChanged(playWhenReady, it.playbackState) }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (imaAdState != IMA_AD_STATE_NONE && player?.isPlayingAd == true) {
            val adMediaInfo = imaAdMediaInfo ?: return
            for (i in adCallbacks.indices) {
                adCallbacks[i].onError(adMediaInfo)
            }
        }
    }

    private fun requestAds(
        context: Context,
        imaSdkSettings: ImaSdkSettings,
        adDisplayContainer: AdDisplayContainer,
    ): com.google.ads.interactivemedia.v3.api.AdsLoader {
        val adsLoader = ImaFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer).apply {
            addAdErrorListener(componentListener)
            configuration.adErrorListener?.let { addAdErrorListener(it) }
            addAdsLoadedListener(componentListener)
        }
        val request: AdsRequest =
            if (adTagDataSpec.uri.scheme == C.CSAI_SCHEME
                && adTagDataSpec.uri.authority == IMA_AUTHORITY
            ) {
                createAdsRequest(adTagDataSpec.uri)
            } else {
                try {
                    getAdsRequestForAdTagDataSpec(adTagDataSpec)
                } catch (e: java.io.IOException) {
                    adPlaybackState = AdPlaybackState(adsId)
                    updateAdPlaybackState()
                    pendingAdLoadError = AdLoadException.createForAllAds(e)
                    maybeNotifyPendingAdLoadError()
                    return adsLoader
                }
            }
        pendingAdRequestContext = configuration.nimbusRequest?.let { "direct" } ?: Any()
        request.setUserRequestContext(pendingAdRequestContext!!)
        if (configuration.enableContinuousPlayback != null) {
            request.setContinuousPlayback(configuration.enableContinuousPlayback)
        }
        if (configuration.vastLoadTimeoutMs != -1) {
            request.setVastLoadTimeout(configuration.vastLoadTimeoutMs.toFloat())
        }
        request.setContentProgressProvider(contentPlaybackAdapter)
        adsLoader.requestAds(request)
        return adsLoader
    }

    private fun maybeInitializeAdsManager(contentPositionMs: Long, contentDurationMs: Long) {
        val adsManager = adsManager ?: return
        if (!isAdsManagerInitialized) {
            isAdsManagerInitialized = true
            val adsRenderingSettings = setupAdsRendering(contentPositionMs, contentDurationMs)
            if (adsRenderingSettings == null) {
                // There are no ads to play.
                destroyAdsManager()
            } else {
                adsManager.init(adsRenderingSettings)
                adsManager.start()
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Initialized with ads rendering settings: $adsRenderingSettings")
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
        val adsRenderingSettings = ImaFactory.createAdsRenderingSettings()
        adsRenderingSettings.enablePreloading = true
        adsRenderingSettings.enableCustomTabs = configuration.enableCustomTabs
        adsRenderingSettings.setMimeTypes(configuration.adMediaMimeTypes ?: supportedMimeTypes)
        if (configuration.mediaLoadTimeoutMs != -1) {
            adsRenderingSettings.setLoadVideoTimeout(configuration.mediaLoadTimeoutMs)
        }
        if (configuration.mediaBitrate != -1) {
            adsRenderingSettings.bitrateKbps = configuration.mediaBitrate / 1000
        }
        adsRenderingSettings.focusSkipButtonWhenAvailable = configuration.focusSkipButtonWhenAvailable
        if (configuration.adUiElements != null) {
            adsRenderingSettings.setUiElements(configuration.adUiElements)
        }

        // Skip ads based on the start position as required.
        var adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
            msToUs(contentPositionMs), msToUs(contentDurationMs),
        )
        if (adGroupForPositionIndex != C.INDEX_UNSET) {
            val playAdWhenStartingPlayback =
                adPlaybackState.getAdGroup(adGroupForPositionIndex).timeUs == msToUs(contentPositionMs)
                    || configuration.playAdBeforeStartPosition
            if (!playAdWhenStartingPlayback) {
                adGroupForPositionIndex++
            } else if (hasMidrollAdGroups(adPlaybackState)) {
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
                    // ads, we signal that no ads will render so the caller can destroy the AdManager.
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
            val player = player
            val hasContentDuration = contentDurationMs != C.TIME_UNSET
            val contentPositionMs: Long = when {
                pendingContentPositionMs != C.TIME_UNSET -> pendingContentPositionMs.also {
                    sentPendingContentPositionMs = true
                }
                player == null -> return lastContentProgress
                fakeContentProgressElapsedRealtimeMs != C.TIME_UNSET ->
                    fakeContentProgressOffsetMs + SystemClock.elapsedRealtime() - fakeContentProgressElapsedRealtimeMs
                imaAdState == IMA_AD_STATE_NONE && !playingAd && hasContentDuration ->
                    getContentPeriodPositionMs(player, timeline, period)
                else -> return VideoProgressUpdate.VIDEO_TIME_NOT_READY
            }
            val contentDurationMs = if (hasContentDuration) contentDurationMs else IMA_DURATION_UNSET
            return VideoProgressUpdate(contentPositionMs, contentDurationMs)
        }

    private val adVideoProgressUpdate: VideoProgressUpdate
        get() = player?.let {
            if (imaAdState == IMA_AD_STATE_NONE || !playingAd || it.duration == C.TIME_UNSET) {
                VideoProgressUpdate.VIDEO_TIME_NOT_READY
            } else VideoProgressUpdate(it.currentPosition, it.duration)
        } ?: lastAdProgress


    private fun updateAdProgress() {
        val videoProgressUpdate = this.adVideoProgressUpdate
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "Ad progress: ${videoProgressUpdate.updateString}")
        }

        val adMediaInfo = imaAdMediaInfo ?: return
        for (i in adCallbacks.indices) {
            adCallbacks[i].onAdProgress(adMediaInfo, videoProgressUpdate)
        }
        handler.removeCallbacks(updateAdProgressRunnable)
        handler.postDelayed(updateAdProgressRunnable, AD_PROGRESS_UPDATE_INTERVAL_MS.toLong())
    }

    private fun stopUpdatingAdProgress() {
        handler.removeCallbacks(updateAdProgressRunnable)
    }

    private val playerVolumePercent: Int
        get() {
            val player = this.player
            return when {
                player == null -> lastVolumePercent
                player.isCommandAvailable(COMMAND_GET_VOLUME) -> (player.volume * 100).toInt()
                player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO) -> 100
                else -> 0
            }
        }

    private fun handleAdEvent(adEvent: AdEvent) {
        // Drop events after release.
        if (adsManager == null) return
        when (adEvent.type) {
            AdEventType.AD_BREAK_FETCH_ERROR -> {
                val adGroupTimeSecondsString = adEvent.adData["adBreakTime"] ?: return
                if (configuration.debugModeEnabled) {
                    Log.d(TAG, "Fetch error for ad at $adGroupTimeSecondsString seconds")
                }
                val adGroupTimeSeconds = adGroupTimeSecondsString.toDouble()
                val adGroupIndex = if (adGroupTimeSeconds == -1.0)
                    adPlaybackState.adGroupCount - 1
                else
                    getAdGroupIndexForCuePointTimeSeconds(adGroupTimeSeconds)
                markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
            }
            AdEventType.CONTENT_PAUSE_REQUESTED -> {
                // After CONTENT_PAUSE_REQUESTED, IMA will playAd/pauseAd/stopAd to show one or more ads
                // before sending CONTENT_RESUME_REQUESTED.
                imaPausedContent = true
                pauseContentInternal()
            }
            AdEventType.TAPPED -> eventListeners.forEach { it.onAdTapped() }
            AdEventType.CLICKED -> eventListeners.forEach { it.onAdClicked() }
            AdEventType.CONTENT_RESUME_REQUESTED -> {
                imaPausedContent = false
                resumeContentInternal()
            }
            AdEventType.LOG -> Log.i(TAG, "AdEvent: $adEvent.adData")
            else -> return
        }
    }

    private fun pauseContentInternal() {
        imaAdState = IMA_AD_STATE_NONE
        if (sentPendingContentPositionMs) {
            pendingContentPositionMs = C.TIME_UNSET
            sentPendingContentPositionMs = false
        }
    }

    private fun resumeContentInternal() {
        imaAdInfo?.run {
            // Mark current ad group as skipped if it hasn't finished yet. This could for example
            // happen after a load timeout where we receive CONTENT_RESUME_REQUESTED instead of
            // loadAd. See [Internal: b/330750756].
            adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
            updateAdPlaybackState()
        }
    }

    /**
     * Returns whether this instance is expecting the first ad in the upcoming ad group to load
     * within the `Configuration.adPreloadTimeoutMs`.
     */
    private val isWaitingForFirstAdToPreload: Boolean
        get() {
            val player = player.takeUnless { loadingAdGroupIndex == C.INDEX_UNSET } ?: return false
            val adGroup = adPlaybackState.getAdGroup(loadingAdGroupIndex).takeIf {
                it.count > 0 && it.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE
            } ?: return false
            val timeUntilAdMs = usToMs(adGroup.timeUs) - getContentPeriodPositionMs(player, timeline, period)
            return timeUntilAdMs < configuration.adPreloadTimeoutMs
        }

    private val isWaitingForCurrentAdToLoad: Boolean
        get() {
            val player = this.player ?: return false
            val adGroupIndex = player.currentAdGroupIndex
            if (adGroupIndex == C.INDEX_UNSET) {
                return false
            }
            if (adGroupIndex >= adPlaybackState.adGroupCount) {
                return true
            }
            val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
            val adIndexInAdGroup = player.currentAdIndexInAdGroup
            if (adGroup.count == C.LENGTH_UNSET || adGroup.count <= adIndexInAdGroup) {
                return true
            }
            return adGroup.states[adIndexInAdGroup] == AdPlaybackState.AD_STATE_UNAVAILABLE
        }

    private fun handlePlayerStateChanged(playWhenReady: Boolean, @State playbackState: @State Int) {
        if (playingAd && imaAdState == IMA_AD_STATE_PLAYING) {
            if (!bufferingAd && playbackState == STATE_BUFFERING) {
                bufferingAd = true
                imaAdMediaInfo?.run {
                    adCallbacks.forEach { it.onBuffering(this) }
                    stopUpdatingAdProgress()
                }
            } else if (bufferingAd && playbackState == STATE_READY) {
                bufferingAd = false
                updateAdProgress()
            }
        }

        if (imaAdState == IMA_AD_STATE_NONE && playWhenReady
            && (playbackState == STATE_BUFFERING || playbackState == STATE_ENDED)
        ) {
            ensureSentContentCompleteIfAtEndOfStream()
        } else if (imaAdState != IMA_AD_STATE_NONE && playbackState == STATE_ENDED) {
            val adMediaInfo = imaAdMediaInfo
            if (adMediaInfo == null) {
                Log.w(TAG, "onEnded without ad media info")
            } else {
                adCallbacks.forEach { it.onEnded(adMediaInfo) }
            }
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "VideoAdPlayerCallback.onEnded in onPlaybackStateChanged")
            }
        }
    }

    private fun handleTimelineOrPositionChanged() {
        val player = this.player.takeUnless { adsManager == null } ?: return
        if (!playingAd && !player.isPlayingAd) {
            ensureSentContentCompleteIfAtEndOfStream()
            if (!sentContentComplete && !timeline.isEmpty) {
                val positionMs: Long = getContentPeriodPositionMs(player, timeline, period)
                timeline.getPeriod(player.currentPeriodIndex, period)
                val newAdGroupIndex = period.getAdGroupIndexForPositionUs(msToUs(positionMs))
                if (newAdGroupIndex != C.INDEX_UNSET) {
                    sentPendingContentPositionMs = false
                    pendingContentPositionMs = positionMs
                }
            }
        }

        val wasPlayingAd = playingAd
        val oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup
        playingAd = player.isPlayingAd
        playingAdIndexInAdGroup = if (playingAd) player.currentAdIndexInAdGroup else C.INDEX_UNSET
        val adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup
        if (adFinished) {
            imaAdMediaInfo?.let { adMediaInfo ->
                adInfoByAdMediaInfo[adMediaInfo]?.let { adInfo ->
                    if (playingAdIndexInAdGroup == C.INDEX_UNSET
                        || adInfo.adIndexInAdGroup < playingAdIndexInAdGroup
                    ) {
                        adCallbacks.forEach { it.onEnded(adMediaInfo) }
                    }
                }
            } ?: run {
                Log.w(TAG, "onEnded without ad info")
            }
        }
        if (!sentContentComplete && !wasPlayingAd && playingAd && imaAdState == IMA_AD_STATE_NONE) {
            val adGroup = adPlaybackState.getAdGroup(player.currentAdGroupIndex)
            if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
                sendContentComplete()
            } else {
                // IMA hasn't called playAd yet, so fake the content position.
                fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
                fakeContentProgressOffsetMs = usToMs(adGroup.timeUs)
                if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                    fakeContentProgressOffsetMs = contentDurationMs
                }
            }
        }
        if (isWaitingForCurrentAdToLoad) {
            handler.removeCallbacks(adLoadTimeoutRunnable)
            handler.postDelayed(adLoadTimeoutRunnable, configuration.adPreloadTimeoutMs)
        }
    }

    private fun loadAdInternal(adMediaInfo: AdMediaInfo, adPodInfo: AdPodInfo) {
        // Drop events after release.
        if (adsManager == null) {
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "loadAd after release ${adMediaInfo.infoString}, ad pod $adPodInfo")
            }
            return
        }

        val adGroupIndex = getAdGroupIndexForAdPod(adPodInfo)
        val adIndexInAdGroup = adPodInfo.adPosition - 1
        val adInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        // The ad URI may already be known, so force put to update it if needed.
        adInfoByAdMediaInfo[adMediaInfo] = adInfo
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "loadAd ${adMediaInfo.infoString}")
        }
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) {
            // We have already marked this ad as having failed to load, so ignore the request.
            // IMA will time out after its media load timeout.
            return
        }

        // The ad count may increase on successive loads of ads in the same ad pod, for example, due to
        // separate requests for ad tags with multiple ads within the ad pod completing after an earlier
        // ad has loaded. See also https://github.com/google/ExoPlayer/issues/7477.
        var adGroup = adPlaybackState.getAdGroup(adInfo.adGroupIndex)
        adPlaybackState = adPlaybackState.withAdCount(
            adInfo.adGroupIndex, max(adPodInfo.totalAds, adGroup.states.size),
        )
        adGroup = adPlaybackState.getAdGroup(adInfo.adGroupIndex)
        for (i in 0..<adIndexInAdGroup) {
            // Any preceding ads that haven't loaded are not going to load.
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex,  /* adIndexInAdGroup= */i)
            }
        }

        val adMediaItem = MediaItem.Builder().setUri(adMediaInfo.url)
        // Use the video MIME type if it is provided.
        // Demuxed streams may contain an audio MIME type, however it should only be used to set the
        // audio MIME type or compose the audio codec string, when/if ExoPlayer introduces support for
        // demuxed streams functionality. Even audio-only streams should only use the video MIME type as
        // they are not demuxed. It is possible that the video MIME type is not provided, in which case,
        // we do not set the MIME type of the MediaItem. However, if an audio MIME type is provided, it
        // is most likely that the video MIME type is also provided (though not the other way around).
        val videoMimeType = adMediaInfo.videoMimeType
        if (videoMimeType != null) adMediaItem.setMimeType(videoMimeType)

        adPlaybackState = adPlaybackState.withAvailableAdMediaItem(
            adInfo.adGroupIndex, adInfo.adIndexInAdGroup, adMediaItem.build(),
        )
        updateAdPlaybackState()
    }

    private fun playAdInternal(adMediaInfo: AdMediaInfo) {
        if (configuration.debugModeEnabled) {
            Log.d(TAG, "playAd ${adMediaInfo.infoString}")
        }
        if (adsManager == null) {
            // Drop events after release.
            return
        }

        if (imaAdState == IMA_AD_STATE_PLAYING) {
            // IMA does not always call stopAd before resuming content.
            // See [Internal: b/38354028].
            Log.w(TAG, "Unexpected playAd without stopAd")
        }

        if (imaAdState == IMA_AD_STATE_NONE) {
            // IMA is requesting to play the ad, so stop faking the content position.
            fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
            fakeContentProgressOffsetMs = C.TIME_UNSET
            imaAdState = IMA_AD_STATE_PLAYING
            imaAdMediaInfo = adMediaInfo
            imaAdInfo = adInfoByAdMediaInfo[adMediaInfo]
            adCallbacks.forEach { it.onPlay(adMediaInfo) }
            if (pendingAdPrepareErrorAdInfo != null && pendingAdPrepareErrorAdInfo == imaAdInfo) {
                pendingAdPrepareErrorAdInfo = null
                adCallbacks.forEach { it.onError(adMediaInfo) }
            }
            updateAdProgress()
        } else {
            imaAdState = IMA_AD_STATE_PLAYING
            require(adMediaInfo == imaAdMediaInfo)
            adCallbacks.forEach { it.onResume(adMediaInfo) }
        }
        // Either this loader hasn't been activated yet, or the player is paused now.
        if (player?.playWhenReady == false) adsManager?.pause()
    }

    private fun pauseAdInternal(adMediaInfo: AdMediaInfo) {
        if (configuration.debugModeEnabled) Log.d(TAG, "pauseAd ${adMediaInfo.infoString}")

        // Drop event after release.
        if (adsManager == null || imaAdState == IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the loaded ad won't play due to
            // a seek to a different position, so drop the event. See also [Internal: b/159111848].
            return
        }
        if (configuration.debugModeEnabled && adMediaInfo != imaAdMediaInfo) {
            Log.w(TAG, "Unexpected pauseAd for ${adMediaInfo.infoString}, expected ${imaAdMediaInfo?.infoString}")
        }
        imaAdState = IMA_AD_STATE_PAUSED
        adCallbacks.forEach { it.onPlay(adMediaInfo) }
    }

    private fun stopAdInternal(adMediaInfo: AdMediaInfo) {
        if (configuration.debugModeEnabled) Log.d(TAG, "stopAd ${adMediaInfo.infoString}")

        // Drop event after release.
        if (adsManager == null) return

        if (imaAdState == IMA_AD_STATE_NONE) {
            // This method is called if loadAd has been called but the preloaded ad won't play due
            // to a seek to a different position, so drop the event and discard the ad. See also
            // [Internal:b/159111848].
            adInfoByAdMediaInfo[adMediaInfo]?.let {
                adPlaybackState = adPlaybackState.withSkippedAd(it.adGroupIndex, it.adIndexInAdGroup)
                updateAdPlaybackState()
            }
            return
        }
        imaAdState = IMA_AD_STATE_NONE
        stopUpdatingAdProgress()
        // TODO: Handle the skipped event so the ad can be marked as skipped rather than played.
        val adGroupIndex = imaAdInfo!!.adGroupIndex
        val adIndexInAdGroup = imaAdInfo!!.adIndexInAdGroup

        // We have already marked this ad as having failed to load, so ignore the request.
        if (adPlaybackState.isAdInErrorState(adGroupIndex, adIndexInAdGroup)) return

        adPlaybackState = adPlaybackState
            .withPlayedAd(adGroupIndex, adIndexInAdGroup)
            .withAdResumePositionUs(0)

        updateAdPlaybackState()
        if (!playingAd) {
            imaAdMediaInfo = null
            imaAdInfo = null
        }
    }

    private fun handleAdGroupLoadError(error: Exception) {
        val adGroupIndex = loadingAdGroupIndex.takeUnless { it == C.INDEX_UNSET } ?: run {
            Log.w(TAG, "Unable to determine ad group index for ad group load error", error)
            return
        }
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
        if (pendingAdLoadError == null) {
            pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex)
        }
    }

    private fun handleAdLoadTimeout() {
        // We started the timeout when we were first waiting for the current ad to load. Check if we are
        // still waiting after the timeout before triggering the error event.
        if (!isWaitingForCurrentAdToLoad) return

        // IMA got stuck and didn't load an ad in time, so skip the entire group.
        handleAdGroupLoadError(java.io.IOException("Ad loading timed out"))
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
                    Log.d(TAG, "Removing ad $i in ad group $adGroupIndex")
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
            Log.d(TAG, "Prepare error for ad $adIndexInAdGroup in group $adGroupIndex", exception)
        }
        if (adsManager == null) {
            Log.w(TAG, "Ignoring ad prepare error after release")
            return
        }
        if (imaAdState == IMA_AD_STATE_NONE) {
            // Send IMA a content position at the ad group so that it will try to play it, at which point
            // we can notify that it failed to load.
            fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
            fakeContentProgressOffsetMs = usToMs(adPlaybackState.getAdGroup(adGroupIndex).timeUs)
            if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                fakeContentProgressOffsetMs = contentDurationMs
            }
            pendingAdPrepareErrorAdInfo = AdInfo(adGroupIndex, adIndexInAdGroup)
        } else {
            val adMediaInfo = imaAdMediaInfo ?: return
            // We're already playing an ad.
            if (adIndexInAdGroup > playingAdIndexInAdGroup) {
                // Mark the playing ad as ended so we can notify the error on the next ad and remove
                //  it, which means that the ad after will load (if any).
                adCallbacks.forEach { it.onEnded(adMediaInfo) }
            }
            playingAdIndexInAdGroup = adPlaybackState.getAdGroup(adGroupIndex).firstAdIndexToPlay
            adCallbacks.forEach { it.onError(adMediaInfo) }
        }
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    private fun ensureSentContentCompleteIfAtEndOfStream() {
        if (sentContentComplete
            || contentDurationMs == C.TIME_UNSET
            || pendingContentPositionMs != C.TIME_UNSET
        ) return

        val contentPeriodPositionMs = getContentPeriodPositionMs(player!!, timeline, period)
        if (contentPeriodPositionMs + THRESHOLD_END_OF_CONTENT_MS < contentDurationMs) return

        val pendingAdGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(
            msToUs(contentPeriodPositionMs), msToUs(contentDurationMs),
        )
        // Pending mid-roll ad that needs to be played before marking the content complete.
        if (pendingAdGroupIndex != C.INDEX_UNSET && adPlaybackState.getAdGroup(pendingAdGroupIndex)
                .let { it.timeUs != C.TIME_END_OF_SOURCE && it.shouldPlayAdGroup() }
        ) return

        sendContentComplete()
    }

    private fun sendContentComplete() {
        adCallbacks.forEach { it.onContentComplete() }
        sentContentComplete = true

        if (configuration.debugModeEnabled) Log.d(TAG, "adsLoader.contentComplete")

        for (i in 0..<adPlaybackState.adGroupCount) {
            if (adPlaybackState.getAdGroup(i).timeUs != C.TIME_END_OF_SOURCE) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
            }
        }
        updateAdPlaybackState()
    }

    private fun updateAdPlaybackState() {
        eventListeners.forEach { it.onAdPlaybackState(adPlaybackState) }
    }

    private fun maybeNotifyPendingAdLoadError() {
        pendingAdLoadError?.let { pendingError ->
            eventListeners.forEach { it.onAdLoadError(pendingError, adTagDataSpec) }
            pendingAdLoadError = null
        }
    }

    private fun maybeNotifyInternalError(name: String, cause: Exception?) {
        val message = "Internal error in $name"
        Log.e(TAG, message, cause)
        // We can't recover from an unexpected error in general, so skip all remaining ads.

        for (i in 0..<adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
        eventListeners.forEach {
            it.onAdLoadError(
                AdLoadException.createForUnexpected(RuntimeException(message, cause)),
                adTagDataSpec,
            )
        }
    }

    private fun getAdGroupIndexForAdPod(adPodInfo: AdPodInfo): Int =
        if (adPodInfo.podIndex == -1) adPlaybackState.adGroupCount - 1 else {
            // adPodInfo.podIndex may be 0-based or 1-based, so for now look up the cue point instead.
            getAdGroupIndexForCuePointTimeSeconds(adPodInfo.timeOffset)
        }

    /**
     * Returns the index of the ad group that will preload next, or [C.INDEX_UNSET] if there is
     * no such ad group.
     */
    private val loadingAdGroupIndex: Int
        get() {
            val player = player ?: return C.INDEX_UNSET
            val playerPositionUs = msToUs(getContentPeriodPositionMs(player, timeline, period))
            var adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, msToUs(contentDurationMs))
            if (adGroupIndex == C.INDEX_UNSET) {
                adGroupIndex = adPlaybackState.getAdGroupIndexAfterPositionUs(
                    playerPositionUs, msToUs(contentDurationMs),
                )
            }
            return adGroupIndex
        }

    private fun getAdGroupIndexForCuePointTimeSeconds(cuePointTimeSeconds: Double): Int {
        // We receive initial cue points from IMA SDK as floats. This code replicates the same
        // calculation used to populate adGroupTimesUs (having truncated input back to float, to avoid
        // failures if the behavior of the IMA SDK changes to provide greater precision).
        val cuePointTimeSecondsFloat = cuePointTimeSeconds.toFloat()
        val adPodTimeUs = (cuePointTimeSecondsFloat.toDouble() * C.MICROS_PER_SECOND).roundToInt()
        for (adGroupIndex in 0..<adPlaybackState.adGroupCount) {
            val adGroupTimeUs = adPlaybackState.getAdGroup(adGroupIndex).timeUs
            if (adGroupTimeUs != C.TIME_END_OF_SOURCE
                && abs(adGroupTimeUs - adPodTimeUs) < THRESHOLD_AD_MATCH_US
            ) {
                return adGroupIndex
            }
        }
        throw IllegalStateException("Failed to find cue point")
    }

    private fun destroyAdsManager() {
        adsManager?.run {
            removeAdErrorListener(componentListener)
            configuration.adErrorListener?.let { removeAdErrorListener(it) }
            removeAdEventListener(componentListener)
            configuration.adEventListener?.let { removeAdEventListener(it) }
            destroy()
            adsManager = null
        }
    }

    private inner class ContentPlaybackAdapter : ContentProgressProvider {
        override fun getContentProgress(): VideoProgressUpdate {
            val videoProgressUpdate: VideoProgressUpdate = contentVideoProgressUpdate
            if (configuration.debugModeEnabled) {
                Log.d(TAG, "Content progress: ${videoProgressUpdate.updateString}")
            }

            if (waitingForPreloadElapsedRealtimeMs != C.TIME_UNSET) {
                // IMA is polling the player position, but we are buffering for an ad to preload,
                // so playback may be stuck. Detect this case and signal an error if applicable.
                val stuckElapsedRealtimeMs =
                    SystemClock.elapsedRealtime() - waitingForPreloadElapsedRealtimeMs
                if (stuckElapsedRealtimeMs >= configuration.adPreloadTimeoutMs) {
                    waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
                    handleAdGroupLoadError(java.io.IOException("Ad preloading timed out"))
                    maybeNotifyPendingAdLoadError()
                }
            } else if (pendingContentPositionMs != C.TIME_UNSET && player?.playbackState == STATE_BUFFERING && isWaitingForFirstAdToPreload) {
                // Prepare to time out the load of an ad for the pending seek operation.
                waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
            }

            return videoProgressUpdate
        }
    }

    private inner class ComponentListener : AdsLoadedListener, AdEventListener, AdErrorListener {
        override fun onAdsManagerLoaded(adsManagerLoadedEvent: AdsManagerLoadedEvent) {
            // The same AdsLoader may be used for both Client-side ads and SSAI ads at the same time.
            // In this scenario, it may emit an `AdsManagerLoadedEvent` which should be handled by the
            // `ImaServerSideAdInsertionMediaSource` instead of the `AdTagLoader`.
            // It's safe to ignore that event.
            adsManagerLoadedEvent.adsManager?.apply {
                if (pendingAdRequestContext != adsManagerLoadedEvent.userRequestContext) {
                    destroy()
                    return
                }
                pendingAdRequestContext = null
                this@NimbusImaAdTagLoader.adsManager = this
                addAdErrorListener(this@ComponentListener)
                configuration.adErrorListener?.let { addAdErrorListener(it) }
                addAdEventListener(this@ComponentListener)
                configuration.adEventListener?.let { addAdEventListener(it) }
                try {
                    adPlaybackState =
                        AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(adCuePoints))
                    updateAdPlaybackState()
                } catch (e: RuntimeException) {
                    maybeNotifyInternalError("onAdsManagerLoaded", e)
                }

            }
        }

        override fun onAdEvent(adEvent: AdEvent) {
            if (configuration.debugModeEnabled && adEvent.type != AdEventType.AD_PROGRESS) {
                Log.d(TAG, "onAdEvent: ${adEvent.type}")
            }
            try {
                handleAdEvent(adEvent)
            } catch (e: RuntimeException) {
                maybeNotifyInternalError("onAdEvent", e)
            }
        }

        inline val requestManager: RequestManager
            get() = configuration.requestManager ?: this@NimbusImaAdTagLoader

        override fun onAdError(adErrorEvent: AdErrorEvent) {
            if (adErrorEvent.userRequestContext == "direct" && configuration.nimbusRequest != null) {
                (MainScope() + CoroutineName("NimbusDirect")).launch {
                    runCatching {
                        requestManager.makeRequest(configuration.context, configuration.nimbusRequest)
                    }.onFailure {
                        handleError(adErrorEvent)
                    }.onSuccess { nimbusResponse ->
                        val nimbusImaRequest = ImaFactory.createAdsRequest().apply {
                            setAdsResponse(nimbusResponse.bid.markup)
                            pendingAdRequestContext = Any().also { setUserRequestContext(it) }
                            configuration.enableContinuousPlayback?.let { setContinuousPlayback(it) }
                            if (configuration.vastLoadTimeoutMs > -1) {
                                setVastLoadTimeout(configuration.vastLoadTimeoutMs.toFloat())
                            }
                            setContentProgressProvider(contentPlaybackAdapter)
                        }
                        adsLoader.requestAds(nimbusImaRequest)
                    }
                }
            }
            handleError(adErrorEvent)
        }

        private fun handleError(adErrorEvent: AdErrorEvent) {
            val error = adErrorEvent.error
            if (configuration.debugModeEnabled) Log.d(TAG, "onAdError", error)
            if (adsManager == null) {
                // No ads were loaded, so allow playback to start without any ads.
                pendingAdRequestContext = null
                adPlaybackState = AdPlaybackState(adsId)
                updateAdPlaybackState()
            } else if (error.isGroupLoadError) {
                try {
                    handleAdGroupLoadError(error)
                } catch (e: RuntimeException) {
                    maybeNotifyInternalError("onAdError", e)
                }
            }
            if (pendingAdLoadError == null) {
                pendingAdLoadError = AdLoadException.createForAllAds(error)
            }
            maybeNotifyPendingAdLoadError()
        }
    }

    inner class VideoAdPlayerImpl : VideoAdPlayer {
        override fun addCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback) {
            adCallbacks.add(videoAdPlayerCallback)
        }

        override fun removeCallback(videoAdPlayerCallback: VideoAdPlayer.VideoAdPlayerCallback) {
            adCallbacks.remove(videoAdPlayerCallback)
        }

        override fun getAdProgress(): VideoProgressUpdate {
            throw IllegalStateException("Unexpected call to getAdProgress when using preloading")
        }

        override fun getVolume(): Int = playerVolumePercent

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

        override fun stopAd(adMediaInfo: AdMediaInfo) {
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
    private data class AdInfo(val adGroupIndex: Int, val adIndexInAdGroup: Int)

    companion object {
        private const val TAG = "AdTagLoader"

        private const val IMA_SDK_SETTINGS_PLAYER_TYPE = "google/exo.ext.ima"
        private const val IMA_SDK_SETTINGS_PLAYER_VERSION = MediaLibraryInfo.VERSION

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
        private const val IMA_DURATION_UNSET = -1L

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
         * The ad playback state when IMA has called `ComponentListener.playAd` and not
         * `ComponentListener.pauseAd`.
         */
        private const val IMA_AD_STATE_PLAYING = 1

        /**
         * The ad playback state when IMA has called `ComponentListener.pauseAd` while
         * playing an ad.
         */
        private const val IMA_AD_STATE_PAUSED = 2

        private fun getContentPeriodPositionMs(
            player: Player, timeline: Timeline, period: Timeline.Period,
        ): Long {
            val contentWindowPositionMs = player.contentPosition
            return if (timeline.isEmpty) {
                contentWindowPositionMs
            } else {
                (contentWindowPositionMs - timeline.getPeriod(player.currentPeriodIndex, period).positionInWindowMs)
            }
        }

        private fun hasMidrollAdGroups(adPlaybackState: AdPlaybackState): Boolean =
            when (adPlaybackState.adGroupCount) {
                1 -> adPlaybackState.getAdGroup(0).timeUs.let {
                    it != 0L && it != C.TIME_END_OF_SOURCE
                }
                2 -> adPlaybackState.getAdGroup(0).timeUs != 0L
                    || adPlaybackState.getAdGroup(1).timeUs != C.TIME_END_OF_SOURCE
                else -> true
            }
    }

    val AdMediaInfo.infoString: String
        get() = "AdMediaInfo[$url, ${adInfoByAdMediaInfo[this]}]"

    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
    @IntDef(IMA_AD_STATE_NONE, IMA_AD_STATE_PLAYING, IMA_AD_STATE_PAUSED)
    private annotation class ImaAdState
}
