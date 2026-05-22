@file:UnstableApi

package com.adsbynimbus.solutions.gamdirect.instream.internal

import android.content.*
import android.net.*
import android.view.*
import androidx.annotation.*
import androidx.media3.common.*
import androidx.media3.common.util.*
import androidx.media3.datasource.*
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.player.*
import java.io.*
import kotlin.math.*

val AdError.isGroupLoadError: Boolean
    get() = errorCode == AdError.AdErrorCode.VAST_LINEAR_ASSET_MISMATCH ||
        errorCode == AdError.AdErrorCode.UNKNOWN_ERROR

val VideoProgressUpdate.updateString: String
    get() = if (this == VideoProgressUpdate.VIDEO_TIME_NOT_READY) "not ready" else
        Util.formatInvariant("%d ms of %d ms", currentTimeMs, durationMs)

fun <K, V> Map<K, V>.getKey(value: V) = entries.firstOrNull { it.value == value }?.key

/** Types of video orientations.  */
@IntDef(
    value = [
        IMA_ORIENTATION_UNSET,
        IMA_ORIENTATION_LANDSCAPE,
        IMA_ORIENTATION_PORTRAIT,
        IMA_ORIENTATION_SQUARE,
    ],
)
@Retention(AnnotationRetention.SOURCE)
annotation class ImaOrientationType

const val IMA_AUTHORITY: String = "ima.google.com"
const val IMA_ORIENTATION_UNSET: Int = 0
const val IMA_ORIENTATION_LANDSCAPE: Int = 1
const val IMA_ORIENTATION_PORTRAIT: Int = 2
const val IMA_ORIENTATION_SQUARE: Int = 3
private const val URI = "uri"
private const val VIDEO_ORIENTATION_KEY = "videoOrientation"

fun createAdsRequest(uri: Uri): AdsRequest = ImaFactory.createAdsRequest().apply {
    uri.getQueryParameter(URI)?.let {
        setAdTagUrl(it)
    }
    uri.getQueryParameter(VIDEO_ORIENTATION_KEY)?.let {
        preferredLinearOrientation = fromImaOrientation(it.toInt())
    }
}

/**
 * Conversion from the [ImaOrientationType] enum to the enum used
 * by the IMA SDK.
 */
private fun fromImaOrientation(@ImaOrientationType imaOrientation: Int): VideoOrientation =
    when (imaOrientation) {
        IMA_ORIENTATION_LANDSCAPE -> VideoOrientation.LANDSCAPE
        IMA_ORIENTATION_PORTRAIT -> VideoOrientation.PORTRAIT
        IMA_ORIENTATION_SQUARE -> VideoOrientation.SQUARE
        else -> VideoOrientation.UNSET
    }

/** Returns an [AdsRequest] based on the specified ad tag [DataSpec].  */
@Throws(IOException::class)
fun getAdsRequestForAdTagDataSpec(adTagDataSpec: DataSpec): AdsRequest = ImaFactory.createAdsRequest().apply {
    if (DataSchemeDataSource.SCHEME_DATA == adTagDataSpec.uri.scheme) {
        DataSchemeDataSource().run {
            try {
                open(adTagDataSpec)
                setAdsResponse(Util.fromUtf8Bytes(DataSourceUtil.readToEnd(this)))
            } finally {
                close()
            }
        }
    } else {
        setAdTagUrl(adTagDataSpec.uri.toString())
    }
}

/**
 * Returns the microsecond ad group timestamps corresponding to the specified cue points.
 *
 * @param cuePoints The cue points of the ads in seconds, provided by the IMA SDK.
 * @return The corresponding microsecond ad group timestamps.
 */
fun getAdGroupTimesUsForCuePoints(cuePoints: MutableList<Float?>): LongArray =
    if (cuePoints.isEmpty()) longArrayOf(0L) else {
        val count = cuePoints.size
        LongArray(count).apply {
            var adGroupIndex = 0
            for (i in 0..<count) {
                val cuePoint = cuePoints[i]!!.toDouble()
                if (cuePoint == -1.0) {
                    this[count - 1] = C.TIME_END_OF_SOURCE
                } else {
                    this[adGroupIndex++] = (C.MICROS_PER_SECOND * cuePoint).roundToLong()
                }
            }
            // Cue points may be out of order, so sort them.
            sort()
        }
    }

/**
 * Returns the IMA [FriendlyObstructionPurpose] corresponding to the given [AdOverlayInfo.purpose].
 */
fun getFriendlyObstructionPurpose(purpose: @AdOverlayInfo.Purpose Int): FriendlyObstructionPurpose =
    when (purpose) {
        AdOverlayInfo.PURPOSE_CONTROLS -> FriendlyObstructionPurpose.VIDEO_CONTROLS
        AdOverlayInfo.PURPOSE_CLOSE_AD -> FriendlyObstructionPurpose.CLOSE_AD
        AdOverlayInfo.PURPOSE_NOT_VISIBLE -> FriendlyObstructionPurpose.NOT_VISIBLE
        AdOverlayInfo.PURPOSE_OTHER -> FriendlyObstructionPurpose.OTHER
        else -> FriendlyObstructionPurpose.OTHER
    }

object ImaFactory {
    fun createImaSdkSettings(): ImaSdkSettings = ImaSdkFactory.getInstance().createImaSdkSettings().apply {
        setLanguage(Util.getSystemLanguageCodes()[0])
    }

    fun createAdsRenderingSettings(): AdsRenderingSettings =
        ImaSdkFactory.getInstance().createAdsRenderingSettings()

    fun createAdDisplayContainer(container: ViewGroup, player: VideoAdPlayer): AdDisplayContainer =
        ImaSdkFactory.createAdDisplayContainer(container, player)


    fun createAudioAdDisplayContainer(context: Context, player: VideoAdPlayer): AdDisplayContainer =
        ImaSdkFactory.createAudioAdDisplayContainer(context, player)

    // The reasonDetail parameter to createFriendlyObstruction is annotated @Nullable but the
    // annotation is not kept in the obfuscated dependency.
    fun createFriendlyObstruction(
        view: View,
        friendlyObstructionPurpose: FriendlyObstructionPurpose,
        reasonDetail: String?,
    ): FriendlyObstruction = ImaSdkFactory.getInstance()
        .createFriendlyObstruction(view, friendlyObstructionPurpose, reasonDetail)

    fun createAdsRequest(): AdsRequest = ImaSdkFactory.getInstance().createAdsRequest()

    fun createAdsLoader(
        context: Context,
        imaSdkSettings: ImaSdkSettings,
        adDisplayContainer: AdDisplayContainer,
    ): AdsLoader = ImaSdkFactory.getInstance().createAdsLoader(context, imaSdkSettings, adDisplayContainer)
}
