@file:OptIn(ExperimentalTime::class)

package adsbynimbus.solutions.dynamicprice

import adsbynimbus.solutions.dynamicprice.BuildConfig.AMAZON_BANNER_SLOT_ID
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit
import com.adsbynimbus.google.handleEventForNimbus
import com.adsbynimbus.request.NimbusRequest.Companion.forInterstitialAd
import com.amazon.aps.ads.ApsAdNetworkInfo
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.model.ApsAdFormat.INTERSTITIAL
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.admanager.AppEventListener
import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

var SharedPreferences.interstitialFrequencyCapTime: Instant
    get() = when (val ts = getLong("na_instl_ts", 0)) {
        0L -> Instant.DISTANT_PAST
        else -> Instant.fromEpochSeconds(ts)
    }
    set(value) {
        edit { putLong("na_instl_ts", value.epochSeconds) }
    }

var SharedPreferences.interstitialCount: Int
    get() = getInt("na_instl_imp", 0)
    set(value) {
        edit { putInt("na_instl_imp", value) }
    }

lateinit var application: Application

val OTPublishersHeadlessSDK.bestGuessLocation: String
    get() = lastDataDownloadedLocation?.country ?: lastUserConsentedLocation?.country ?: ""

// This can be replaced by another instance of OTPublishersHeadlessSDK if available
val userLocation by lazy { OTPublishersHeadlessSDK(application).bestGuessLocation }

// Use the default shared preferences to prevent additional disk i/o
val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(application) }

/**
 * Check to see if a user is in a targetable geo and is not frequency capped. All 3 parameters use
 * default values but can be replaced at runtime by passing values obtained from a remote config
 * such as Firebase
 *
 * @param maxInterstitials number of interstitial impressions to show per frequency cap period
 * @param duration length of the frequency cap; defaults to 1 day
 * @param validLocations list of valid countries; be sure to check that the country names match the output of the OneTrust SDK
 */
fun userIsEligibleForNimbusInterstitial(
    maxInterstitials: Int = 1,
    duration: Duration = 24.hours,
    validLocations: Collection<String> = "USA,CAN,MEX,AUS".split(","),
): Boolean = validLocations.contains(userLocation) && sharedPreferences.run {
    val lastFrequencyCap = sharedPreferences.interstitialFrequencyCapTime
    when {
        // User has never been frequency capped
        lastFrequencyCap == Instant.DISTANT_PAST -> true
        // User was previously capped and duration has elapsed, reset the impression count to 0
        Clock.System.now() - lastFrequencyCap > duration -> true.also { interstitialCount = 0 }
        else -> interstitialCount < maxInterstitials
    }

}

/**
 * Loads an interstitial ad with optional pre‑bidder targeting.
 *
 * The method filters the supplied bidders to include Nimbus bidders only when the
 * `allowNimbus` predicate returns `true`. It then performs a parallel auction
 * to obtain bids from the remaining bidders, builds an `AdManagerAdRequest` with
 * all targeting information, and requests an interstitial ad from Google Ad
 * Manager.  When the ad loads successfully, an `AppEventListener` is attached
 * to capture Nimbus win events and set up the Dynamic Price renderer.
 *
 * Example Usage with Remote Config where:
 *   nimbus_instl_cap = 2; number of interstitials per 24hour period
 *   nimbus_instl_duration_hours = 24; frequency cap duration in hours
 *   nimbus_instl_countries = "USA,CAN,MEX,AUS"; CSV list of eligible country names
 * ```kotlin
 * loadInterstitial(
 *  context = context,
 *  adUnitId = "/1234/dynamic-price",
 *  allowNimbus = {
 *      userIsEligibleForNimbusInterstitial(
 *          maxInterstitials = remoteConfig.getInt("nimbus_instl_cap"),
 *          duration: Duration = remoteConfig.getInt("nimbus_instl_duration_hours").hours,
 *          validLocations: Collection<String> = remoteConfig.getString(nimbus_instl_countries").split(","),
 *      )
 *  })
 * ```
 *
 *
 * @param context the application context used to load the ad.
 * @param adUnitId the Ad Manager ad unit identifier.
 * @param bidders the collection of bidders to participate in the auction.
 *        Default bidders include Nimbus and Amazon Pacing Service (APS) bidders.
 * @param allowNimbus a predicate that determines whether Nimbus bidders are
 *        allowed for this request
 * @return an [AdManagerInterstitialAd] instance that is ready for display.
 * @throws RuntimeException if the Ad Manager ad request fails.
 */
@Throws(RuntimeException::class)
suspend fun loadInterstitial(
    context: Context,
    adUnitId: String,
    bidders: Collection<Bidder<*>> = listOf(
        NimbusBidder { forInterstitialAd("Interstitial") },
        ApsBidder {
            ApsAdRequest(AMAZON_BANNER_SLOT_ID, INTERSTITIAL, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
        }
    ),
    allowNimbus: () -> Boolean = { userIsEligibleForNimbusInterstitial() }
): AdManagerInterstitialAd {
    // Allow non Nimbus bidders and eligible users in geo and not frequency capped
    val bids = bidders.filter { it !is NimbusBidder || allowNimbus() }.auction()
    val request = AdManagerAdRequest.Builder().apply {
        bids.forEach { it.applyTargeting(this) }
    }.build()

    val interstitialAd = suspendCancellableCoroutine { continuation ->
        AdManagerInterstitialAd.load(context, adUnitId, request,
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    continuation.takeIf { it.isActive }?.resume(ad)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    continuation.takeIf { it.isActive }?.resumeWithException(RuntimeException(p0.message))
                }
            }
        )
    }
    // Set the appEventListener before returning the AdManagerInterstitialAd
    return interstitialAd.apply {
        // handleEventForNimbus is called when Nimbus wins the auction
        appEventListener = AppEventListener { name, info ->
            if (handleEventForNimbus(name, info)) {
                // Nimbus won the auction, increment the interstitial counter
                sharedPreferences.interstitialCount += 1
            }
        }
    }
}
