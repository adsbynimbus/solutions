import NimbusKit
import NimbusRenderVASTKit
import SwiftUI

@main
struct OMSDKApp: App {
    let apiKey = Bundle.main.infoDictionary?["Nimbus API Key"] as? String ?? ""
    let publisherKey = Bundle.main.infoDictionary?["Nimbus Publisher Key"] as? String ?? ""
    init () {
        Nimbus.shared.initialize(publisher: publisherKey, apiKey: apiKey)

        Nimbus.shared.logLevel = .info
        Nimbus.shared.testMode = true

        Nimbus.shared.renderers[.forAuctionType(.video)] = NimbusVideoAdRenderer()
        Nimbus.shared.viewabilityProvider = .init(builder: NimbusAdViewabilityTrackerBuilder(
            verificationProviders: [UpdatedIABVerificationProvider()])
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    let adManager = NimbusAdManager()

    var body: some View {
        NavigationStack {
            List {
                Section("Ad Types") {
                    NavigationLink("Display Ad HTML - Inline") {
                        InlineView(
                            adManager: adManager,
                            request: NimbusRequest.forBannerAd(position: "Display Ad HTML", format: .letterbox))
                    }
                    NavigationLink("Video Ad Native - Inline") {
                        InlineView(
                            adManager: adManager,
                            request: NimbusRequest.forVideoAd(position: "Video Ad Native"))
                    }
                    Text("Display Ad HTML - Interstitial").onTapGesture {
                        guard let vc = UIApplication.shared.firstKeyWindow?.rootViewController else { return }
                        adManager.showBlockingAd(
                            request: NimbusRequest.forBannerAd(
                                position: "Interstitial Display HTML",
                                format: .interstitialPortrait,
                                adPosition: .fullScreen),
                            adPresentingViewController: vc)
                    }
                    Text("Video Ad Native - Interstitial").onTapGesture {
                        guard let vc = UIApplication.shared.firstKeyWindow?.rootViewController else { return }
                        adManager.showBlockingAd(
                            request: NimbusRequest.forVideoAd(position: "Interstitial Video Native"),
                            adPresentingViewController: vc)
                    }
                }
            }
            .navigationTitle("Nimbus OMSDK Validator")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct InlineView: UIViewControllerRepresentable {
    let adManager: NimbusAdManager
    let request: NimbusRequest

    func makeUIViewController(context: Context) -> some UIViewController {
        let vc = UIViewController()

        adManager.showAd(
            request: request,
            container: vc.view,
            adPresentingViewController: vc
        )

        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {}
}


extension UIApplication {
    var firstKeyWindow: UIWindow? {
        connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .first?.keyWindow

    }
}

#Preview {
    ContentView()
}
