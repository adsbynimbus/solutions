//
//  DynamicPriceView+Examples.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/8/25.
//

import DTBiOSSDK
import DynamicPrice
import GoogleMobileAds
import NimbusKit

/// Fill in with your own price mapping
public let priceMapping = LinearPriceMapping(
    LinearPriceGranularity(min: 0, max: 500, step: 1)
)

extension DynamicPriceView {

    /// Convenience initializer for running Amazon and Nimbus as parallel bidders
    @inlinable public convenience init(
        adSize: GoogleMobileAds.AdSize,
        adUnitId: String,
        apsRequest: APSAdRequest,
        nimbusRequest: InlineAd,
    ) {
        self.init(
            adSize: adSize,
            adUnitId: adUnitId,
            bidders: [apsRequest.asBidder(), nimbusRequest.asBidder()]
        )
    }

    /// Creates a 320x50  footer banner
    @inlinable public static func footerBannerAd(
        googleAdUnitId: String,
        amazonSlotId: String,
        nimbusPosition: String,
    ) -> DynamicPriceView {
        DynamicPriceView(
            adSize: AdSizeBanner,
            adUnitId: googleAdUnitId,
            apsRequest: APSAdRequest(slotId: amazonSlotId, format: .banner),
            nimbusRequest: Nimbus.bannerAd(
                position: nimbusPosition,
                size: .banner,
                adPosition: .footer,
            )
        )
    }

    /// Creates an inline 300x250  banner
    @inlinable public static func inlineMrecAd(
        googleAdUnitId: String,
        amazonSlotId: String,
        nimbusPosition: String,
    ) -> DynamicPriceView {
        DynamicPriceView(
            adSize: AdSizeMediumRectangle,
            adUnitId: googleAdUnitId,
            apsRequest: APSAdRequest(slotId: amazonSlotId, format: .MREC),
            nimbusRequest: Nimbus.bannerAd(
                position: nimbusPosition,
                size: .mrec,
            )
        )
    }
}

@inlinable @MainActor public func loadInterstitialWithBidders(
    googleAdUnitId: String,
    amazonSlotId: String,
    nimbusPosition: String,
    appEventDelegate: AppEventDelegate,
    fullScreenDelegate: FullScreenContentDelegate?,
) async -> AdManagerInterstitialAd? {
    let bids = await [
        APSAdRequest(slotId: amazonSlotId, format: .interstitial).asBidder(),
        Nimbus.interstitialAd(position: nimbusPosition).asBidder(),
    ].auction()

    let request = AdManagerRequest()
    request.customTargeting = [:]

    bids.forEach { $0.applyTargeting(to: request, priceMapping: priceMapping) }

    guard let ad = try? await AdManagerInterstitialAd.load(with: googleAdUnitId, request: request) else {
        return nil
    }

    ad.fullScreenContentDelegate = fullScreenDelegate
    ad.appEventDelegate = appEventDelegate

    return ad
}

class GoogleInterstitialListener: NSObject, FullScreenContentDelegate, AppEventDelegate {

    public func adView(
        _ interstitialAd: GoogleMobileAds.InterstitialAd,
        didReceiveAppEvent name: String, with info: String?
    ) {
        interstitialAd.handleEventForNimbus(name: name, info: info)
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("ad:didFailToPresentFullScreenContentWithError: \(error.localizedDescription)")
    }

    func adDidRecordImpression(_ ad: FullScreenPresentingAd) {
        print("adDidRecordImpression")
    }

    func adDidRecordClick(_ ad: FullScreenPresentingAd) {
        print("adDidRecordClick")
    }

    func adWillPresentFullScreenContent(_ ad: FullScreenPresentingAd) {
        print("ad:adWillPresentFullScreenContent")
    }

    func adWillDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        print("ad:adWillDismissFullScreenContent")
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        print("ad:adDidDismissFullScreenContent")
    }
}

class GoogleAdListener: NSObject, BannerViewDelegate {
    public func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        Task { @MainActor in print("Ad Loaded \(bannerView.adUnitID ?? "")") }
    }

    public func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        Task { @MainActor in print("Ad Error \(bannerView.adUnitID ?? "") \(error.localizedDescription)") }
    }

    public func bannerViewDidRecordImpression(_ bannerView: BannerView) {
        Task { @MainActor in print("Ad Impression \(bannerView.adUnitID ?? "")") }
    }

    public func bannerViewDidRecordClick(_ bannerView: BannerView) {
        Task { @MainActor in print("Ad Clicked \(bannerView.adUnitID ?? "")") }
    }
}

extension APSAdRequest {
    @inlinable public convenience init(slotId: String, format: APSAdFormat) {
        self.init(slotUUID: slotId, adNetworkInfo: .init(networkName: .googleAdManager))
        setAdFormat(format)
    }
}
