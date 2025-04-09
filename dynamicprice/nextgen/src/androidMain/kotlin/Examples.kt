package adsbynimbus.solutions.dynamicprice.nextgen

import android.util.Log
import com.adsbynimbus.openrtb.enumerations.Position
import com.adsbynimbus.openrtb.request.Format
import com.adsbynimbus.request.NimbusRequest
import com.adsbynimbus.request.NimbusRequest.Companion.forBannerAd
import com.adsbynimbus.request.NimbusRequest.Companion.forInterstitialAd
import com.amazon.aps.ads.ApsAdNetworkInfo
import com.amazon.aps.ads.ApsAdRequest
import com.amazon.aps.ads.model.ApsAdFormat.BANNER
import com.amazon.aps.ads.model.ApsAdFormat.INTERSTITIAL
import com.amazon.aps.ads.model.ApsAdNetwork.GOOGLE_AD_MANAGER
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadResult
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

val adCache = mutableMapOf<String, DynamicPriceAd>()

fun preloadBanner(
    nimbusRequest: NimbusRequest = forBannerAd("Top Banner", Format.BANNER_320_50, Position.HEADER),
    amazonRequest: () -> ApsAdRequest = {
        ApsAdRequest(BuildConfig.AMAZON_BANNER_SLOT_ID, BANNER, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
    },
    timeout: Duration = 1000.milliseconds,
) = DynamicPriceAd(
    adUnitId = BuildConfig.ADMANAGER_ADUNIT_ID,
    adSize = AdSize.BANNER,
    bidders = listOf(NimbusBidder(nimbusRequest), ApsBidder(amazonRequest)),
    preloadTimeout = timeout,
)

suspend fun loadInterstitial(
    adUnitId: String = BuildConfig.ADMANAGER_ADUNIT_ID,
    bidders: Collection<Bidder<*>> = listOf(
        NimbusBidder { forInterstitialAd("Interstitial") },
        ApsBidder {
            ApsAdRequest(BuildConfig.AMAZON_BANNER_SLOT_ID, INTERSTITIAL, ApsAdNetworkInfo(GOOGLE_AD_MANAGER))
        }
    ),
    listener: InterstitialAdEventCallback? = null,
): InterstitialAd? = supervisorScope {
    val (bids, auctionTime) = measureTimedValue {
        bidders.auction()
    }
    Log.v("Ads", "[$adUnitId] Auction Duration: $auctionTime")

    val request = AdRequest.Builder(adUnitId).apply {
        bids.forEach { it.applyTargeting(this) }
    }
    val (adResponse, requestTime) = measureTimedValue {
        InterstitialAd.load(request.build())
    }
    Log.v("Ads", "[$adUnitId] AdRequest Duration: $requestTime")

    when (adResponse) {
        is AdLoadResult.Success<InterstitialAd> -> adResponse.ad.also {
            it.adEventCallback = object : InterstitialAdEventCallback by listener {
                override fun onAppEvent(name: String, data: String?) {
                    Log.v("Ads", "[$adUnitId] AppEvent Received")
                    it.handleEventForNimbus(name, data)
                    listener?.onAppEvent(name, data)
                }
            }
        }
        is AdLoadResult.Failure<*> -> null.also {
            Log.i("Ads", "[$adUnitId] ${adResponse.error.message}")
        }
    }
}
