//
//  DynamicPriceView+Examples.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/8/25.
//

import DTBiOSSDK
@preconcurrency import GoogleMobileAds
@preconcurrency import NimbusGAMKit

/// Fill in with your own price mapping
public let priceMapping = NimbusGAMLinearPriceMapping(granularities: [])

public let nimbusRequestManager = NimbusRequestManager()

extension DynamicPriceView {

    /// Convenience initializer for running Amazon and Nimbus as parallel bidders
    @inlinable public convenience init(
        adSize: AdSize,
        adUnitId: String,
        apsRequest: APSAdRequest,
        nimbusRequest: NimbusRequest
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
        nimbusPosition: String
    ) -> DynamicPriceView {
        DynamicPriceView(
            adSize: AdSizeBanner,
            adUnitId: googleAdUnitId,
            apsRequest: APSAdRequest(slotId: amazonSlotId, format: .banner),
            nimbusRequest: .forBannerAd(
                position: nimbusPosition,
                format: .banner320x50,
                adPosition: .footer
            )
        )
    }

    /// Creates an inline 300x250  banner
    @inlinable public static func inlineMrecAd(
        googleAdUnitId: String,
        amazonSlotId: String,
        nimbusPosition: String
    ) -> DynamicPriceView {
        DynamicPriceView(
            adSize: AdSizeMediumRectangle,
            adUnitId: googleAdUnitId,
            apsRequest: APSAdRequest(slotId: amazonSlotId, format: .MREC),
            nimbusRequest: .forBannerAd(
                position: nimbusPosition,
                format: .letterbox,
                adPosition: .unknown
            )
        )
    }
}

@inlinable @MainActor public func loadInterstitialWithBidders(
    googleAdUnitId: String,
    amazonSlotId: String,
    nimbusPosition: String,
    appEventDelegate: AppEventDelegate,
    fullScreenDelegate: FullScreenContentDelegate?
) async -> AdManagerInterstitialAd? {
    let bids = await [
        APSAdRequest(slotId: amazonSlotId, format: .interstitial).asBidder(),
        NimbusRequest.forInterstitialAd(position: nimbusPosition).asBidder()
    ].auction()

    let request = AdManagerRequest()
    request.customTargeting = [:]

    bids.forEach { $0.applyTargeting(to: request, priceMapping: priceMapping) }

    return try? await withUnsafeThrowingContinuation { continuation in
        AdManagerInterstitialAd.load(with: googleAdUnitId, request: request) { ad, error in
            if let error = error {
                continuation.resume(throwing: error)
                return
            }

            guard let ad = ad else { return }

            ad.fullScreenContentDelegate = fullScreenDelegate
            ad.appEventDelegate = appEventDelegate
            bids.forEach {
                if case .nimbus(let nimbusBid) = $0 {
                    ad.applyDynamicPrice(
                        ad: nimbusBid,
                        requestManager: nimbusRequestManager,
                        delegate: fullScreenDelegate)
                }
            }

            Task { @MainActor in
                continuation.resume(returning: ad)
            }
        }
    }
}


class GoogleInterstitialListener: NSObject, FullScreenContentDelegate, AppEventDelegate {

    public func adView(_ interstitialAd: InterstitialAd,
                       didReceiveAppEvent name: String, with info: String?) {
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
           print("adWillDismissFullScreenContent")
       }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
           print("adDidDismissFullScreenContent")
       }
}

class GoogleAdListener: NSObject, BannerViewDelegate {
    public func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        Task { @MainActor in print("Ad Loaded \(bannerView.adUnitID ?? "")") }
    }

    public func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        Task { @MainActor in  print("Ad Error \(bannerView.adUnitID ?? "") \(error.localizedDescription)") }
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
