//
//  GAMDirectView.swift
//  GAMDirect
//
//  Created by Jason Sznol on 11/26/25.
//

@preconcurrency import DTBiOSSDK
import GoogleMobileAds
@preconcurrency import NimbusKit

fileprivate let refreshInterval: TimeInterval = 30

@MainActor
func exampleBanner(
    googleAdUnitId: String,
    amazonSlotId: String,
) -> GAMDirectView {
    return .init(
        adUnitId: googleAdUnitId,
        adManagerAdSizes: [currentOrientationInlineAdaptiveBanner(width: 320)],
        apsSlotId: amazonSlotId,
        apsSize: .MREC,
        nimbusSizes: [
            NimbusAdFormat(width: 300, height: 250),
            NimbusAdFormat(width: 620, height: 250)
        ]
    )
}

@MainActor
public class GAMDirectView : UIView {

    private let adLoader: AdLoader
    private let adManagerAdSizes: [AdSize]
    private let apsSlotId: String
    private let apsSize: APSAdFormat
    private let nimbusSizes: Set<NimbusAdFormat>
    private var lastRequestTime: Date = Date.distantPast
    private var refreshTask: Task<Void, Error>?

    /// Set this delegate to receive events from the GAMBannerView
    public weak var googleDelegate: BannerViewDelegate?
    /// Set this delegate to receive events from the NimbusAdView
    public weak var nimbusDelegate: AdControllerDelegate?

    /// Set to true for banner ads if they refresh when not on the screen
    public var useOnScreenCheck = false
    
    public init(
        adUnitId: String,
        adManagerAdSizes: [AdSize],
        apsSlotId: String,
        apsSize: APSAdFormat,
        nimbusSizes: Set<NimbusAdFormat>,
    ) {
        self.adLoader = AdLoader(
            adUnitID: adUnitId,
            rootViewController: nil,
            adTypes: [ .adManagerBanner ],
            options: nil,
        )
        self.adManagerAdSizes = adManagerAdSizes
        self.apsSlotId = apsSlotId
        self.apsSize = apsSize
        self.nimbusSizes = nimbusSizes
        super.init(frame: .zero)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public func loadAd() async {
        lastRequestTime = Date()
        
        // If the ad load is successful there will be 2 children with the old ad at index 0 which we should remove
        defer {
            if subviews.count > 1 {
                let view = subviews[0]
                view.removeFromSuperview()
                (view as? NimbusAdView)?.destroy()
            }
        }

        let adManagerRequest = AdManagerRequest()
        // This key value will be used for targeting in GAM in lieu of different ad units and
        // you should check with your ad ops team on what the actual value should be
        adManagerRequest.customTargeting = ["nimbus":"true"]
        
        if let directAd = try? await adLoader.loadAd(request: adManagerRequest, sizes: adManagerAdSizes) {
            directAd.delegate = googleDelegate
            addSubview(directAd)
            directAd.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                directAd.centerXAnchor.constraint(equalTo: centerXAnchor),
                directAd.centerYAnchor.constraint(equalTo: centerYAnchor),
            ])
            return
        }
        
        
        let nimbusRequest = NimbusRequest.forBannerAd(position: adLoader.adUnitID, format: .interstitialPortrait)
        nimbusRequest.impressions[0].banner?.formats = nimbusSizes
        nimbusRequest.impressions[0].banner?.blockedCreativeAttributes = [.userInteractive]
        
        let apsAdRequest = APSAdRequest(slotUUID: apsSlotId, adNetworkInfo: APSAdNetworkInfo(networkName: .nimbus))
        apsAdRequest.setAdFormat(apsSize)
        if let apsAd = try? await apsAdRequest.loadAd() {
            nimbusRequest.addAPSResponse(apsAd)
        }
        
        // Uses an async implementation of the manual request and render flow
        // NimbusAdManager.showAd() = NimbusRequestManager.performRequest + Nimbus.load(ad)
        if let nimbusAd = try? await nimbusRequest.fetchAd() {
            _ = Nimbus.load(
                ad: nimbusAd,
                container: self,
                adPresentingViewController: parentViewController() ?? UIApplication.rootViewController!,
                delegate: nimbusDelegate
            )
        }
    }
    
    // Handles starting and stopping the automatic refresh of ads
    public override func willMove(toWindow newWindow: UIWindow?) {
        removeVisibilityListeners()
        
        guard let newWindow = newWindow else {
            self.refreshTask?.cancel()
            return
        }
        
        onVisibilityChanged(newWindow) { [unowned self] isVisible in
            Task { @MainActor in isVisible ? self.startRefresh() : self.refreshTask?.cancel() }
        }
        
        startRefresh()
    }
    
    private func startRefresh() {
        guard refreshTask == nil || refreshTask?.isCancelled == true else { return }
        
        refreshTask = Task {
            while !Task.isCancelled {
                // If not on screen, sleep for 200 milliseconds and then check again
                guard !useOnScreenCheck || isOnScreen else {
                    await Task.sleep(seconds: 0.2)
                    continue
                }

                await Task.sleep(seconds: refreshInterval - Date().timeIntervalSince(lastRequestTime))
                guard !Task.isCancelled else { break }
                
                await loadAd()
            }
        }
    }
}

public extension UIView {
    
    var isOnScreen: Bool {
        guard let window else { return false }
    
        return !window.bounds.intersection(convert(bounds, to: nil)).isEmpty
    }
    
    @inlinable
    func removeVisibilityListeners() -> Void {
        NotificationCenter.default.removeObserver(
            self, name: UIWindow.didBecomeVisibleNotification, object: window)
        NotificationCenter.default.removeObserver(
            self, name: UIWindow.didBecomeHiddenNotification, object: window)
        NotificationCenter.default.removeObserver(
            self, name: UIApplication.didBecomeActiveNotification, object: UIApplication.shared)
        NotificationCenter.default.removeObserver(
            self, name: UIApplication.didEnterBackgroundNotification, object: UIApplication.shared)
    }
    
    @inlinable
    func onVisibilityChanged(_ target: UIWindow,  _ onChange: @escaping @Sendable (Bool) -> Void) {
        NotificationCenter.default.addObserver(
            forName: UIWindow.didBecomeVisibleNotification,
            object: target,
            queue: nil
        ) { _ in onChange(true) }
        NotificationCenter.default.addObserver(
            forName: UIWindow.didBecomeHiddenNotification,
            object: target,
            queue: nil
        ) { _ in onChange(false) }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: UIApplication.shared,
            queue: nil
        ) { _ in onChange(true) }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: UIApplication.shared,
            queue: nil
        ) { _ in onChange(false) }
    }
    
    func parentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while !(responder is UIViewController) {
            responder = responder?.next
            if nil == responder {
                break
            }
        }
        return (responder as? UIViewController)
    }
}

extension AdLoader {
    final class RequestListener : NSObject, Sendable, AdManagerBannerAdLoaderDelegate {
        nonisolated(unsafe) var continuation: UnsafeContinuation<AdManagerBannerView, Error>?
        let sizes: [AdSize]
        
        init(sizes: [AdSize]) {
            self.sizes = sizes
        }
        
        func adLoader(_ adLoader: AdLoader, didReceive bannerView: AdManagerBannerView) {
            continuation?.resume(returning: bannerView)
            continuation = nil
        }
        
        func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: any Error) {
            continuation?.resume(throwing: error)
            continuation = nil
        }
        
        func validBannerSizes(for adLoader: AdLoader) -> [NSValue] {
            return sizes.map { nsValue(for: $0) }
        }
    }
    
    @MainActor
    func loadAd(request: AdManagerRequest, sizes: [AdSize]) async throws -> AdManagerBannerView {
        let listener = RequestListener(sizes: sizes)
        let adView: AdManagerBannerView = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { c in
                listener.continuation = c
                delegate = listener
                load(request)
            }
        } onCancel: {
            listener.continuation?.resume(throwing: CancellationError())
            listener.continuation = nil
        }
        return adView
    }
}

public extension APSAdRequest {
    @MainActor
    func loadAd() async throws -> APSAd {
        nonisolated(unsafe) var continuation: UnsafeContinuation<APSAd, Error>?
        let response: APSAd = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { c in
                continuation = c
                loadAd { adResponse, error in
                    if let error = error {
                        continuation?.resume(throwing: error)
                    } else {
                        continuation?.resume(returning: adResponse)
                    }
                    continuation = nil
                }
            }
        } onCancel: {
            continuation?.resume(throwing: CancellationError())
            continuation = nil
        }
        return response
    }
}

extension NimbusRequest {
    // Helper method for appending APS params using the new APSAd methods
    public func addAPSResponse(_ response: APSAd) {
        guard let targeting = response.customTargeting else { return }
        
        var ext = impressions[0].extensions ?? [:]
        ext["aps"] = NimbusCodable([targeting])
        impressions[0].extensions = ext
    }
        
    final class RequestListener : NimbusRequestManagerDelegate, Sendable {

        nonisolated(unsafe) var continuation: UnsafeContinuation<NimbusAd, Error>?

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation?.resume(returning: ad)
            continuation = nil
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }

    public func fetchAd() async throws -> NimbusAd {
        let requestManager = NimbusRequestManager()
        let listener = RequestListener()
        requestManager.delegate = listener
        let bid = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { continuation in
                listener.continuation = continuation
                requestManager.performRequest(request: self)
            }
        } onCancel: {
            listener.continuation?.resume(throwing: CancellationError())
            listener.continuation = nil
        }
        return bid
    }
}

extension Task where Success == Never, Failure == Never {
    @inlinable
    static func sleep(seconds: TimeInterval) async {
        if seconds > 0 {
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
        }
    }
}

// Extension to grab the rootViewController if the ad is load unattached from the view hierarchy
extension UIApplication {
    var firstKeyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .first?.keyWindow
    }

    static var rootViewController: UIViewController? {
        shared.firstKeyWindow?.rootViewController
    }
}
