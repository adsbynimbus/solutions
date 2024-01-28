import Foundation
import NimbusKit
import NimbusRenderStaticKit
import NimbusRenderVideoKit
import UIKit

typealias sdk = NimbusKit.Nimbus

@objcMembers public class Nimbus : NSObject {

    public static func initialize(publisher: String, apiKey: String) {
        sdk.shared.initialize(publisher: publisher, apiKey: apiKey)
        sdk.shared.renderers = [
            .forAuctionType(.static): NimbusStaticAdRenderer(),
            .forAuctionType(.video): NimbusVideoAdRenderer(),
        ]
        sdk.shared.adVisibilityMinPercentage = 0
    }

    public static var testMode: Bool {
        get { sdk.shared.testMode }
        set { sdk.shared.testMode = newValue }
    }

    public static let sdkVersion: String = sdk.shared.version

    @objc(showAd:container:onAdReady:)
    public static func showAd(
        request: NimbusRequestWrapper,
        container: UIView
    ) async -> NimbusAdController? {
        guard let nimbusAd = try? await NimbusRequestManager().makeRequest(request: request.wrapped) else {
            return nil
        }
        let adView = await NimbusAdView(adPresentingViewController: nil)
        await container.addSubview(adView)
        await adView.render(ad: nimbusAd.nimbusAd)
        return NimbusAdController(nimbusAdView: adView, response: nimbusAd)
    }

    public static var endpointOverride: String? {
        get { NimbusRequestManager.requestUrl?.absoluteString ?? "" }
        set {
            if let url = newValue { NimbusRequestManager.requestUrl = URL(string: url) } else {
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

    class RequestListener : NimbusRequestManagerDelegate {

        private let continuation: CheckedContinuation<NimbusResponse, Error>

        public init(_ continuation: CheckedContinuation<NimbusResponse, Error>) {
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
        let result = try await withCheckedThrowingContinuation { continuation in
            listener = RequestListener(continuation)
            delegate = listener
            performRequest(request: request)
        }
        delegate = nil
        return result
    }
}

public class NimbusRequestWrapper : NSObject {

    internal let wrapped: NimbusRequest

    internal init(request: NimbusRequest) {
        self.wrapped = request
    }

    @objc(forBannerAd:width:height:) public static func forBannerAd(position: String, width: Int, height: Int) -> NimbusRequestWrapper {
        .init(request: NimbusRequest.forBannerAd(position: position, format: NimbusAdFormat(width: width, height: height)))
    }
}

@objcMembers public class NimbusResponse : NSObject {

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

public class NimbusAdController : NSObject, AdController {

    @objc public let ad: NimbusResponse
    @objc public var adView: UIView? { view }

    public var internalDelegate: AdControllerDelegate? = nil
    public var delegate: AdControllerDelegate? = nil
    public var friendlyObstructions: [UIView]? = []
    public var isClickProtectionEnabled: Bool = true
    public var volume: Int = 0
    public var adDuration: CGFloat { CGFloat(ad.nimbusAd.duration ?? 0) }

    internal let view: NimbusAdView

    public init(nimbusAdView: NimbusAdView, response: NimbusResponse) {
        ad = response
        view = nimbusAdView
    }

    public func start() { }

    public func stop() { }

    @objc public func destroy() {
        view.destroy()
        view.removeFromSuperview()
    }
}
