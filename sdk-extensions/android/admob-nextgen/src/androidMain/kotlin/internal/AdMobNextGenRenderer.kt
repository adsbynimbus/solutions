package com.adsbynimbus.solutions.nextgen.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.adsbynimbus.NimbusAd
import com.adsbynimbus.NimbusError
import com.adsbynimbus.NimbusError.ErrorType.*
import com.adsbynimbus.internal.*
import com.adsbynimbus.render.*
import com.adsbynimbus.render.internal.trackEvent
import com.adsbynimbus.request.internal.RenderType
import com.adsbynimbus.request.internal.renderType
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.interstitial.*
import com.google.android.libraries.ads.mobile.sdk.nativead.*
import com.google.android.libraries.ads.mobile.sdk.rewarded.*
import kotlinx.coroutines.launch

public class AdMobNextGenRenderer : Renderer, Renderer.Blocking, Component {
    override fun install() {
        Renderer.INLINE.put("admob", this)
        Renderer.BLOCKING.put("admob", this)
    }

    override fun <T> render(
        ad: NimbusAd,
        container: ViewGroup,
        listener: T,
    ) where T : Renderer.Listener, T : NimbusError.Listener {
        val controller = AdMobNextGenAdController(ad, container as NimbusAdView)
        listener.onAdRendered(controller)
        when (ad.renderType) {
            RenderType.Native -> NativeAdLoader.loadFromAdResponse(
                adResponse = ad.markup(),
                adLoadCallback = NextGenAdLoaderCallback<NativeAd>(controller),
            )
            else -> container.addView(AdView(container.context, null, 0, 0).apply {
                loadFromAdResponse(
                    adResponse = ad.markup(),
                    adLoadCallback = NextGenAdLoaderCallback(controller),
                )
            })
        }
    }

    override fun render(
        ad: NimbusAd,
        context: Context,
    ): AdController = AdMobNextGenAdController(ad).also {
        nimbusScope.launch {
            when (ad.renderType) {
                RenderType.Rewarded -> RewardedAd.loadFromAdResponse(
                    adResponse = ad.markup(),
                    adLoadCallback = NextGenAdLoaderCallback(it),
                )
                else -> InterstitialAd.loadFromAdResponse(
                    adResponse = ad.markup(),
                    adLoadCallback = NextGenAdLoaderCallback(it)
                )
            }
        }
    }

    public companion object {
        public fun interface Delegate {
            /**
             * Override this method to provide a custom layout for a AdMob NativeAd.
             *
             * The View returned from this method should have all the data set from the native ad
             * on children views such as the call to action, image data, title, privacy icon etc.
             * The view returned from this method should not be attached to the container passed in as
             * it will be attached at a later time during the rendering process.
             *
             * @param container the container the layout will be attached to
             * @param nativeAd the AdMob native ad with the relevant ad information
             * @return A custom ad layout that will be attached to the container
             */
            public fun customViewForRendering(container: ViewGroup, nativeAd: NativeAd): View
        }

        /**
         * A rendering delegate for customizing the AdMob Native Ad rendering.
         *
         * The delegate is stored in a static variable so care should be taken to ensure an
         * implementation of the delegate does not hold a reference to an Activity or Fragment
         * context.
         */
        public lateinit var delegate: Delegate
    }
}

@JvmInline
internal value class NextGenAdLoaderCallback<T: Ad>(val controller: AdMobNextGenAdController):
    AdLoadCallback<T>, NativeAdLoaderCallback {

    override fun onAdFailedToLoad(adError: LoadAdError) {
        controller.onAdFailedToLoad(adError)
        controller.destroy()
    }

    override fun onAdLoaded(ad: T) {
        controller.onAdLoaded(ad)
    }

    override fun onNativeAdLoaded(nativeAd: NativeAd) {
        controller.view!!.apply {
            addView(AdMobNextGenRenderer.delegate.customViewForRendering(this, nativeAd))
        }
        controller.onAdLoaded(nativeAd)
    }
}

internal class AdMobNextGenAdController(
    val nimbusAd: NimbusAd,
    override val view: NimbusAdView? = null,
) : AdController(), AdEventCallback, AdLoadCallback<Ad> {

    inline val fullscreen: Boolean
        get() = view == null

    init {
        started = !fullscreen
    }

    lateinit var googleAd: Ad

    override fun onAdFailedToLoad(adError: LoadAdError) {
        dispatchError(NimbusError(
            errorType = RENDERER_ERROR,
            message = "Error loading AdMob for ${nimbusAd.position()}",
            cause = RuntimeException(adError.message),
        ))
    }

    override fun onAdLoaded(ad: Ad) {
        googleAd = ad
        dispatchAdEvent(AdEvent.LOADED)
        if (started) start()
    }

    override fun start() {
        started = true
        if (state == AdState.READY) when (val ad = googleAd) {
            is InterstitialAd -> ad.show(Platform.currentActivity.get()!!)
            is RewardedAd -> ad.show(Platform.currentActivity.get()!!) {
                dispatchAdEvent(AdEvent.COMPLETED)
            }
        }
    }

    override fun stop() { started = false }

    override fun destroy() {
        if (state != AdState.DESTROYED) {
            if (state != AdState.LOADING) {
                googleAd.destroy()
                view?.destroy()
            }
            dispatchAdEvent(AdEvent.DESTROYED)
        }
    }

    override fun onAdClicked() {
        nimbusAd.trackEvent(AdEvent.CLICKED)
        dispatchAdEvent(AdEvent.CLICKED)
    }

    override fun onAdImpression() {
        nimbusAd.trackEvent(AdEvent.IMPRESSION)
        dispatchAdEvent(AdEvent.IMPRESSION)
    }

    override fun onAdDismissedFullScreenContent() {
        if (view == null) destroy()
    }

    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
        dispatchError(NimbusError(CONTROLLER_ERROR, fullScreenContentError.message, null))
    }
}
