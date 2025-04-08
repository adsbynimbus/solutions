import SwiftUI

@main
struct DynamicPriceApp: App {

    init () {
        initNimbus()
        initAmazon()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {

    let interstitialListener = GoogleInterstitialListener()

    var body: some View {
        NavigationStack {
            List {
                Section("Ad Types") {
                    NavigationLink("Banner") {
                        InlineView(dynamicPriceView: .footerBannerAd(
                            googleAdUnitId: DynamicPriceApp.googleAdUnitId,
                            amazonSlotId: DynamicPriceApp.amazonBannerSlot,
                            nimbusPosition: "Banner")
                        )
                    }
                    NavigationLink("Inline") {
                        InlineView(dynamicPriceView: .inlineMrecAd(
                            googleAdUnitId: DynamicPriceApp.googleAdUnitId,
                            amazonSlotId: DynamicPriceApp.amazonBannerSlot,
                            nimbusPosition: "Inline")
                        )
                    }
                    Text("Interstitial").onTapGesture {
                        guard let vc = UIApplication.rootViewController else { return }
                        Task {
                            guard let interstitialAd = await loadInterstitialWithBidders(
                                googleAdUnitId: DynamicPriceApp.googleAdUnitId,
                                amazonSlotId: DynamicPriceApp.amazonInterstitialSlot,
                                nimbusPosition: "Interstitial",
                                appEventDelegate: interstitialListener,
                                fullScreenDelegate: interstitialListener) else {
                                print("No ad to show")
                                return
                            }
                            interstitialAd.presentDynamicPrice(fromRootViewController: vc)
                        }
                    }
                }
            }
            .navigationTitle("Dynamic Price")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct InlineView: UIViewControllerRepresentable {
    let dynamicPriceView: DynamicPriceView
    let listener = GoogleAdListener()

    func makeUIViewController(context: Context) -> some UIViewController {
        let vc = UIViewController()
        dynamicPriceView.delegate = listener

        vc.view.addSubview(dynamicPriceView)

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

    static var rootViewController: UIViewController? {
        shared.firstKeyWindow?.rootViewController
    }
}
