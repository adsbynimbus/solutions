import Foundation
import NimbusKit
import UIKit

typealias sdk = NimbusKit.Nimbus

@objcMembers public class Nimbus: NSObject {

    public static func initialize(publisher: String, apiKey: String) {
        sdk.shared.initialize(publisher: publisher, apiKey: apiKey)
        sdk.shared.adVisibilityMinPercentage = 0
    }

    public static var testMode: Bool {
        get { sdk.shared.testMode }
        set { sdk.shared.testMode = newValue }
    }

    public static let sdkVersion: String = sdk.shared.version

    @objc(showAd:container:viewController:onAdReady:) @MainActor
    public static func showAd(
        request: NimbusRequestWrapper,
        container: UIView,
        viewController: UIViewController? = nil
    ) async -> NimbusAdController? {
        let manager = NimbusRequestManager()
        guard let response = try? await manager.makeRequest(request: request.wrapped) else {
            return nil
        }
        return NimbusAdController(
            adController: sdk.load(
                ad: response.nimbusAd,
                container: container,
                adPresentingViewController: viewController ?? container.parentViewController,
                delegate: nil
            ),
            response: response
        )
    }

    @objc(loadBlockingAd:viewController:onAdReady:) @MainActor
    public static func showBlockingAd(
        request: NimbusRequestWrapper,
        viewController: UIViewController
    ) async -> NimbusAdController? {
        let manager = NimbusRequestManager()
        guard let response = try? await manager.makeRequest(request: request.wrapped) else {
            return nil
        }
        guard let interstitial: AdController = try? sdk.loadBlocking(
            ad: response.nimbusAd,
            presentingViewController: viewController,
            delegate: nil
        ) else {
            return nil
        }
        // Start interstitail ad after returning
        Task {
            interstitial.start()
        }
        return NimbusAdController(
            adController: interstitial,
            response: response
        )
    }

    public static var endpointOverride: String? {
        get { NimbusRequestManager.requestUrl?.absoluteString ?? "" }
        set {
            if let url = newValue {
                NimbusRequestManager.requestUrl = URL(string: url)
            } else {
                NimbusRequestManager.requestUrl = nil
            }
        }
    }

    public static var enableLogs: Bool {
        get { sdk.shared.logLevel == .debug }
        set { sdk.shared.logLevel = newValue ? .debug : .off }
    }
}

extension NimbusRequestManager {

    class RequestListener: NimbusRequestManagerDelegate {

        private let continuation: UnsafeContinuation<NimbusResponse, Error>

        public init(_ continuation: UnsafeContinuation<NimbusResponse, Error>) {
            self.continuation = continuation
        }

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation.resume(returning: NimbusResponse(ad))
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation.resume(throwing: error)
        }
    }

    func makeRequest(request: NimbusRequest) async throws -> NimbusResponse {
        var listener: RequestListener? = nil
        let result = try await withUnsafeThrowingContinuation { continuation in
            listener = RequestListener(continuation)
            delegate = listener
            performRequest(request: request)
        }
        delegate = nil
        return result
    }
}

public class NimbusRequestWrapper: NSObject {

    internal let wrapped: NimbusRequest

    internal init(request: NimbusRequest) {
        self.wrapped = request
    }

    @objc(forBannerAd:width:height:)
    public static func forBannerAd(
        position: String,
        width: Int,
        height: Int
    ) -> NimbusRequestWrapper {
        .init(
            request: NimbusRequest.forBannerAd(
                position: position,
                format: NimbusAdFormat(width: width, height: height)
            )
        )
    }

    @objc(forInterstitialAd:)
    public static func forInterstitialAd(position: String) -> NimbusRequestWrapper {
        .init(request: NimbusRequest.forInterstitialAd(position: position))
    }
}

@objcMembers public class NimbusResponse: NSObject {

    internal let nimbusAd: NimbusAd

    /// This unique auction id. Represented as a GUID
    public var auctionId: String { nimbusAd.auctionId }

    /// The position name of the originating request
    public var position: String { nimbusAd.position }

    /// The type of creative returned
    public var type: String { nimbusAd.auctionType.rawValue }

    /// The winning auction's precise winning bid
    public var bidRaw: Double { nimbusAd.bidRaw }

    /// This winning auction's bid in cents
    public var bidInCents: Int { nimbusAd.bidInCents }

    /// The network that won this auction
    public var network: String { nimbusAd.network }

    /// The width of the ad
    public var width: Int { nimbusAd.adDimensions?.width ?? 0 }

    /// The height of the ad
    public var height: Int { nimbusAd.adDimensions?.height ?? 0 }

    public init(_ response: NimbusAd) {
        self.nimbusAd = response
        super.init()
    }
}

@objc
public class NimbusAdController: NSObject, AdController {

    @objc public let ad: NimbusResponse

    public let controller: AdController

    public init(adController: AdController, response: NimbusResponse) {
        ad = response
        controller = adController
    }

    @objc public func start() { controller.start() }

    @objc public func destroy() { controller.destroy() }

    public var delegate: (any AdControllerDelegate)? {
        get { controller.delegate }
        set { controller.delegate = newValue }
    }

    /// Volume for the ad from 0-100
    @objc public var volume: Int {
        get { controller.volume }
        set { controller.volume = newValue }
    }

    /// Ad view for the controller
    @objc public var adView: UIView? { controller.adView }

    /// Duration of the ad
    @objc public var adDuration: CGFloat { controller.adDuration }

    @objc public func stop() { controller.stop() }

    @objc public var isClickProtectionEnabled: Bool {
        get { controller.isClickProtectionEnabled }
        set { controller.isClickProtectionEnabled = newValue }
    }

    public var friendlyObstructions: [UIView]? {
        controller.friendlyObstructions
    }
}
