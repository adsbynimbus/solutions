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
public class GAMDirectView : UIView {

    private let adLoader: AdLoader
    private let adSizes: [AdSize]
    private let adManagerRequestProvider: @Sendable () -> AdManagerRequest
    private let apsRequestProvider: @Sendable () -> APSAdRequest
    private let nimbusRequestProvider: @Sendable () -> NimbusRequest
    private var lastRequestTime: Date = Date.distantPast
    private var refreshTask: Task<Void, Error>?

    /// Set this delegate to receive events from the GAMBannerView
    public weak var googleDelegate: BannerViewDelegate?
    public weak var nimbusDelegate: AdControllerDelegate?

    /// Set to true for banner ads if they refresh when not on the screen
    public var useOnScreenCheck = false
    
    public init(
        adUnitId: String,
        adManagerAdSizes: [AdSize],
        adManagerRequest: @autoclosure @escaping @Sendable () -> AdManagerRequest,
        apsRequest: @autoclosure @escaping @Sendable () -> APSAdRequest,
        nimbusRequest: @autoclosure @escaping @Sendable () -> NimbusRequest,
    ) {
        self.adLoader = AdLoader(
            adUnitID: adUnitId,
            rootViewController: nil,
            adTypes: [ .adManagerBanner ],
            options: nil,
        )
        self.adSizes = adManagerAdSizes
        self.adManagerRequestProvider = adManagerRequest
        self.apsRequestProvider = apsRequest
        self.nimbusRequestProvider = nimbusRequest
        super.init(frame: .zero)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public func loadAd() async {
        lastRequestTime = Date()
        
        // If the ad load is successful there will be 2 children with the old ad at index 0
        defer {
            if subviews.count > 1 {
                let view = subviews[0]
                view.removeFromSuperview()
                (view as? NimbusAdView)?.destroy()
            }
        }

        let adManagerRequest = adManagerRequestProvider()
        // This key value will be used for targeting in GAM in lieu of different ad units and
        // you should check with your ad ops team on what the actual value should be
        adManagerRequest.customTargeting = ["nimbus":"true"]
        
        if let directAd = try? await adLoader.loadAd(request: adManagerRequest, sizes: adSizes) {
            addSubview(directAd)
            directAd.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                directAd.centerXAnchor.constraint(equalTo: centerXAnchor),
                directAd.centerYAnchor.constraint(equalTo: centerYAnchor),
            ])
            return
        }
        
        let nimbusRequest = nimbusRequestProvider()
        
        if let apsAd = try? await apsRequestProvider().loadAd() {
            nimbusRequest.addAPSResponse(apsAd)
        }
        
        if let nimbusAd = try? await nimbusRequest.fetchAd() {
            _ = Nimbus.load(
                ad: nimbusAd,
                container: self,
                adPresentingViewController: parentViewController() ?? UIApplication.rootViewController!,
                delegate: nimbusDelegate
            )
        }
    }

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
    
    public override func willMove(toSuperview newSuperview: UIView?) {
        NSLayoutConstraint.deactivate(constraints)
    }
    
    public override func didMoveToSuperview() {
        guard let parent = superview else { return }
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            topAnchor.constraint(equalTo: parent.topAnchor),
            bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            leftAnchor.constraint(equalTo: parent.leftAnchor),
            rightAnchor.constraint(equalTo: parent.rightAnchor)
        ])
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
