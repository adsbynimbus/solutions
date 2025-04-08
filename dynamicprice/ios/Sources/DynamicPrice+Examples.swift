//
//  DynamicPriceView+Examples.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/8/25.
//

import DTBiOSSDK
import GoogleMobileAds
@preconcurrency import NimbusGAMKit

/// Fill in with your own price mapping
public let priceMapping = NimbusGAMLinearPriceMapping(granularities: [])

@MainActor
public let nimbusRequestManager = NimbusRequestManager()

extension DynamicPriceView {

    /// Convenience initializer for running Amazon and Nimbus as parallel bidders
    @inlinable public convenience init(
        adSize: GADAdSize,
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
            adSize: GADAdSizeBanner,
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
            adSize: GADAdSizeMediumRectangle,
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
    appEventDelegate: GADAppEventDelegate,
    fullScreenDelegate: GADFullScreenContentDelegate?
) async -> GAMInterstitialAd? {
    let bids = await [
        APSAdRequest(slotId: amazonSlotId, format: .interstitial).asBidder(),
        NimbusRequest.forInterstitialAd(position: nimbusPosition).asBidder()
    ].auction()

    let request = GAMRequest()
    request.customTargeting = [:]

    var nimbusBid: NimbusAd? = nil
    bids.forEach {
        switch $0 {
        case let .nimbus(response):
            response.applyDynamicPrice(into: request, mapping: priceMapping)
            nimbusBid = response
            break;
        case let .aps(response):
            response.customTargeting?.forEach {
                request.customTargeting?[$0.key] = $0.value
            }
        }
    }

    return try? await withUnsafeThrowingContinuation { continuation in
        GAMInterstitialAd.load(withAdManagerAdUnitID: googleAdUnitId, request: request) { ad, error in
            if let error = error {
                continuation.resume(throwing: error)
                return
            }

            guard let ad = ad else { return }

            ad.fullScreenContentDelegate = fullScreenDelegate
            ad.appEventDelegate = appEventDelegate
            if let nimbusBid = nimbusBid {
                ad.applyDynamicPrice(
                    ad: nimbusBid,
                    requestManager: nimbusRequestManager,
                    delegate: fullScreenDelegate)
            }

            Task { @MainActor in
                continuation.resume(returning: ad)
            }
        }
    }
}


class GoogleInterstitialListener: NSObject, GADFullScreenContentDelegate, GADAppEventDelegate {

    public func interstitialAd(_ interstitialAd: GADInterstitialAd,
                               didReceiveAppEvent name: String, withInfo info: String?) {
        interstitialAd.handleEventForNimbus(name: name, info: info)
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("ad:didFailToPresentFullScreenContentWithError: \(error.localizedDescription)")
    }

       func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
           print("adDidRecordImpression")
       }

       func adDidRecordClick(_ ad: GADFullScreenPresentingAd) {
           print("adDidRecordClick")
       }

       func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
           print("ad:adWillPresentFullScreenContent")
       }

       func adWillDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
           print("adWillDismissFullScreenContent")
       }

       func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
           print("adDidDismissFullScreenContent")
       }
}

class GoogleAdListener: NSObject, GADBannerViewDelegate {
    public func bannerViewDidReceiveAd(_ bannerView: GADBannerView) {
        Task { @MainActor in print("Ad Loaded \(bannerView.adUnitID ?? "")") }
    }

    public func bannerView(_ bannerView: GADBannerView, didFailToReceiveAdWithError error: Error) {
        Task { @MainActor in  print("Ad Error \(bannerView.adUnitID ?? "") \(error.localizedDescription)") }
    }

    public func bannerViewDidRecordImpression(_ bannerView: GADBannerView) {
        Task { @MainActor in print("Ad Impression \(bannerView.adUnitID ?? "")") }
    }

    public func bannerViewDidRecordClick(_ bannerView: GADBannerView) {
        Task { @MainActor in print("Ad Clicked \(bannerView.adUnitID ?? "")") }
    }
}

extension APSAdRequest {
    @inlinable public convenience init(slotId: String, format: APSAdFormat) {
        self.init(slotUUID: slotId)
        setAdFormat(format)
    }
}
