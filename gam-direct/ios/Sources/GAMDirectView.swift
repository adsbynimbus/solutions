//
//  GAMDirectView.swift
//  GAMDirect
//
//  Created by Jason Sznol on 11/26/25.
//

import DTBiOSSDK
import GoogleMobileAds
import NimbusKit
import NimbusAPSKit
import NimbusAdMobKit

fileprivate let refreshInterval: TimeInterval = 30

@MainActor
func exampleBanner(
    gamDirectAdUnitId: String,
    amazonSlotId: String,
    admobBiddingAdUnitId: String,
) -> GAMDirectView {
    return .init(
        directAdUnitId: gamDirectAdUnitId,
        adManagerAdSizes: [currentOrientationInlineAdaptiveBanner(width: 320)],
        apsSlotId: amazonSlotId,
        apsSize: .MREC,
        admobBiddingAdUnitId: admobBiddingAdUnitId
    )
}

@MainActor
public class GAMDirectView : UIView {

    private let adLoader: AdLoader
    private let adManagerAdSizes: [GoogleMobileAds.AdSize]
    private let apsSlotId: String
    private let apsSize: APSAdFormat
    private let admobBiddingId: String
    private let nimbusSizes: Set<RTB.Format>
    private var lastRequestTime: Date = Date.distantPast
    private var refreshTask: Task<Void, Error>?
    private weak var nimbusAd: InlineAd?

    /// Set this delegate to receive events from the AdManagerBannerView
    public weak var googleDelegate: BannerViewDelegate?
    /// Set this callback to receive events from the Nimbus InlineAd
    public var nimbusOnEvent: ((AdEvent) -> Void)? {
        didSet {
            guard let onEvent = nimbusOnEvent else { return }
            nimbusAd?.onEvent(onEvent)
        }
    }
    /// Set this callback to receive errors from the Nimbus InlineAdAd
    public var nimbusOnError: ((NimbusError) -> Void)? {
        didSet {
            guard let onError = nimbusOnError else { return }
            nimbusAd?.onError(onError)
        }
    }

    /// Set to true for banner ads if they refresh when not on the screen
    public var useOnScreenCheck = false

    public init(
        directAdUnitId: String,
        adManagerAdSizes: [GoogleMobileAds.AdSize],
        apsSlotId: String,
        apsSize: APSAdFormat,
        admobBiddingAdUnitId: String,
        nimbusSizes: Set<RTB.Format> = [],
    ) {
        self.adLoader = AdLoader(
            adUnitID: directAdUnitId,
            rootViewController: nil,
            adTypes: [ .adManagerBanner ],
            options: nil,
        )
        self.adManagerAdSizes = adManagerAdSizes
        self.apsSlotId = apsSlotId
        self.apsSize = apsSize
        self.nimbusSizes = nimbusSizes
        self.admobBiddingId = admobBiddingAdUnitId
        super.init(frame: .zero)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public func destroy() {
        guard let nimbusAd else { return }
        nimbusAd.destroy()
    }

    public func loadAd() async {
        lastRequestTime = Date()

        // If the ad load is successful there will be 2 children with the old ad at index 0 which we should remove
        defer {
            if subviews.count > 1 {
                let view = subviews[0]
                view.removeFromSuperview()
            }
        }

        let adManagerRequest = AdManagerRequest()
        // Any targeting values used for Ad Manager should be appended here
        adManagerRequest.customTargeting = ["nimbus":"true"]

        if let directAd = try? await adLoader.loadAd(request: adManagerRequest, sizes: adManagerAdSizes) {
            nimbusAd?.destroy()
            directAd.delegate = googleDelegate
            addSubview(directAd)
            directAd.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                directAd.centerXAnchor.constraint(equalTo: centerXAnchor),
                directAd.centerYAnchor.constraint(equalTo: centerYAnchor),
            ])
            return
        }

        let apsAdRequest = apsSlotId.isEmpty ? nil : APSAdRequest(
            slotUUID: apsSlotId,
            adNetworkInfo: APSAdNetworkInfo(networkName: .nimbus)
        )
        apsAdRequest?.setAdFormat(apsSize)
        let apsAd = try? await apsAdRequest?.loadAd()

        let nextAd = Nimbus.bannerAd(
            position: adLoader.adUnitID,
            size: .interstitialPortrait,
            addFormats: nimbusSizes,
        ) {
            if let apsAd {
                aps(ads: [apsAd])
            }
            // Add AdMob Bidding
            admob(bannerAdUnitId: admobBiddingId)
        }

        if let loadedAd = try? await nextAd.show(in: self) {
            nimbusAd?.destroy()
            nimbusAd = loadedAd
        }
    }

    // Handles starting and stopping the automatic refresh of ads
    public override func willMove(toWindow newWindow: UIWindow?) {
        removeVisibilityListeners()

        guard let newWindow = newWindow else {
            self.refreshTask?.cancel()
            return
        }

        onVisibilityChanged(newWindow) { isVisible in
            Task { @MainActor in
                isVisible ? self.startRefresh() : self.refreshTask?.cancel()
            }
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
        let sizes: [GoogleMobileAds.AdSize]

        init(sizes: [GoogleMobileAds.AdSize]) {
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
    func loadAd(request: AdManagerRequest, sizes: [GoogleMobileAds.AdSize]) async throws -> AdManagerBannerView {
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
