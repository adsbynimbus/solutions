import android.os.SystemClock
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.internal.Platform
import com.adsbynimbus.render.AdController
import com.adsbynimbus.render.AdEvent
import com.adsbynimbus.render.internal.trackEvent
import com.adsbynimbus.solutions.nextgen.internal.*
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import io.kotest.core.spec.style.FreeSpec
import io.mockk.*

class AdMobNextGenControllerTests : FreeSpec({
    val nimbusAd: NimbusAd = mockk(relaxed = true)
    val nimbusListener: AdController.Listener = mockk(relaxed = true)
    var adEventCallbackCapture: AdEventCallback? = null
    lateinit var googleAd: Ad
    lateinit var controller: AdController

    beforeTest {
        mockkStatic(SystemClock::class) {
            every { SystemClock.elapsedRealtime() } returns 0
            mockkObject(Platform)
        }
        every { Platform.currentActivity.get() } returns mockk(relaxed = true)
        AdMobNextGenRenderer.delegate = AdMobNextGenRenderer.Companion.Delegate { _, _ ->
            mockk(relaxed = true)
        }
    }

    beforeEach {
        clearMocks(nimbusAd, nimbusListener)
        controller = AdMobNextGenAdController(
            nimbusAd = nimbusAd,
            view = when (googleAd) {
                is BannerAd, is NativeAd -> mockk(relaxed = true)
                else -> null
            },
        )
        controller.listeners.add(nimbusListener)
        NextGenAdLoaderCallback<Ad>(controller).run {
            googleAd.let { if (it is NativeAd) onNativeAdLoaded(it) else onAdLoaded(it) }
        }
    }

    mapOf(
        "BannerAd" to mockk<BannerAd>(relaxed = true) {
            every { adEventCallback = any() } answers { adEventCallbackCapture = firstArg() }
        },
        "NativeAd" to mockk<NativeAd>(relaxed = true) {
            every { adEventCallback = any() } answers { adEventCallbackCapture = firstArg() }
        },
        "InterstitialAd" to mockk<InterstitialAd>(relaxed = true) {
            every { adEventCallback = any() } answers { adEventCallbackCapture = firstArg() }
        },
        "RewardedAd" to mockk<RewardedAd>(relaxed = true) {
            every { adEventCallback = any() } answers { adEventCallbackCapture = firstArg() }
        },
    ).forEach { (name, testAd) ->
        "AdController for $name" - {
            googleAd = testAd

            "fires LOADED event" {
                verify {
                    nimbusListener.onAdEvent(AdEvent.LOADED)
                }
            }

            "fires IMPRESSION event" {
                adEventCallbackCapture?.onAdImpression()
                verify {
                    nimbusListener.onAdEvent(AdEvent.IMPRESSION)
                    nimbusAd.trackEvent(AdEvent.IMPRESSION)
                }
            }

            "fires CLICKED event" {
                adEventCallbackCapture?.onAdClicked()
                verify {
                    nimbusListener.onAdEvent(AdEvent.CLICKED)
                    nimbusAd.trackEvent(AdEvent.CLICKED)
                }
            }

            "fires DESTROYED event when destroy() called" {
                controller.destroy()
                verify {
                    nimbusListener.onAdEvent(AdEvent.DESTROYED)
                }
            }

            if (googleAd is InterstitialAd || googleAd is RewardedAd) {
                "fires DESTROYED when dismissed" {
                    adEventCallbackCapture?.onAdDismissedFullScreenContent()
                    verify {
                        nimbusListener.onAdEvent(AdEvent.DESTROYED)
                    }
                }
            }

            "calls destroy() on googleAd when destroyed" {
                controller.destroy()
                verify {
                    googleAd.destroy()
                }
            }
        }
    }
})
