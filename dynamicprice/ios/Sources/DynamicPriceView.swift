//
//  DynamicPriceView.swift
//
//
//  Created by Jason Sznol on 9/4/24.
//

import GoogleMobileAds
@preconcurrency import NimbusGAMKit

fileprivate let refreshInterval: TimeInterval = 30

@MainActor
public class DynamicPriceView : UIView, GADAppEventDelegate {

    private let bidders: [any Bidder]
    private let googleBanner: GAMBannerView
    private var lastRequestTime: Date = Date.distantPast
    private var refreshTask: Task<Void, Error>?

    /// Set this delegate to receive events from the GAMBannerView
    public weak var delegate: GADBannerViewDelegate?

    /// Set to true for banner ads if they refresh when not on the screen
    public var useOnScreenCheck = false
    
    public init(
        adSize: GADAdSize,
        adUnitId: String,
        bidders: [any Bidder]
    ) {
        self.bidders = bidders
        self.googleBanner = GAMBannerView(adSize: adSize)
        super.init(frame: .zero)
        googleBanner.adUnitID = adUnitId
        googleBanner.appEventDelegate = self
        addSubview(googleBanner)
        googleBanner.translatesAutoresizingMaskIntoConstraints = false
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public func loadAd() async {
        lastRequestTime = Date()

        let bids = await bidders.auction()

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

        googleBanner.applyDynamicPrice(
            requestManager: nimbusRequestManager,
            delegate: delegate,
            ad: nimbusBid)

        googleBanner.load(request)
    }

    public nonisolated func adView(
        _ banner: GADBannerView,
        didReceiveAppEvent name: String,
        withInfo info: String?
    ) {
       Task { @MainActor in googleBanner.handleEventForNimbus(name: name, info: info) }
    }

    public override func willMove(toWindow newWindow: UIWindow?) {
        if window != nil { removeVisibilityListeners() }
        
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
            topAnchor.constraint(greaterThanOrEqualTo: parent.topAnchor),
            bottomAnchor.constraint(lessThanOrEqualTo: parent.bottomAnchor),
            leftAnchor.constraint(greaterThanOrEqualTo: parent.leftAnchor),
            rightAnchor.constraint(lessThanOrEqualTo: parent.rightAnchor),
            centerXAnchor.constraint(equalTo: parent.centerXAnchor),
            centerYAnchor.constraint(equalTo: parent.centerYAnchor),
            // Might need to review these
            topAnchor.constraint(equalTo: googleBanner.topAnchor),
            bottomAnchor.constraint(lessThanOrEqualTo: googleBanner.bottomAnchor),
            leftAnchor.constraint(greaterThanOrEqualTo: googleBanner.leftAnchor),
            rightAnchor.constraint(lessThanOrEqualTo: googleBanner.rightAnchor),
        ])
    }
    
    private func startRefresh() {
        guard refreshTask == nil || refreshTask?.isCancelled == true else { return }
        
        refreshTask = Task { @MainActor in
            while !Task.isCancelled {
                // If not on screen, sleep for 200 milliseconds and then check again
                guard !useOnScreenCheck || isOnScreen else {
                    await Task.sleep(seconds: 0.2)
                    continue
                }

                await Task.sleep(seconds: refreshInterval - Date().timeIntervalSince(lastRequestTime))

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
    
    func parentViewController() -> UIViewController {
        var responder: UIResponder? = self
        while !(responder is UIViewController) {
            responder = responder?.next
            if nil == responder {
                break
            }
        }
        return (responder as? UIViewController)!
    }
}
