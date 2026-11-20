import NimbusKit
import NimbusSwiftUI
import SwiftUI

@main
struct OMSDKApp: App {
    let apiKey = Bundle.main.infoDictionary?["Nimbus API Key"] as? String ?? ""
    let publisherKey = Bundle.main.infoDictionary?["Nimbus Publisher Key"] as? String ?? ""
    init() {
        Nimbus.initialize(publisherKey: publisherKey, apiKey: apiKey)

        Nimbus.configuration.testMode = true

        Nimbus.configuration.verificationProviders = [UpdatedIABVerificationProvider()]
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

enum AdTypes : String, CaseIterable {
    case DisplayInline = "Display Ad HTML - Inline"
    case VideoInline = "Video Ad Native - Inline"
    case DisplayInterstitial = "Display Ad HTML - Interstitial"
    case VideoInterstitial = "Video Ad Native - Interstitial"
}

struct ContentView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Ad Types") {
                    ForEach(AdTypes.allCases, id: \.self) { adType in
                        NavigationLink(adType.rawValue, value: adType)
                    }
                }
            }
            .navigationTitle("Nimbus OMSDK Validator")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: AdTypes.self) { destination in
                switch destination {
                case .DisplayInline:
                    InlineAdView(ad: Nimbus.bannerAd(position: "Display Ad HTML", size: .mrec))
                        .onEvent { debugPrint("Banner Event: \($0)") }
                        .onError { debugPrint("Banner Error: \($0)") }
                        .frame(width: 300, height: 250)
                case .VideoInline:
                    InlineAdView(ad: Nimbus.inlineAd(position: "Video Ad Native") {
                        video()
                    })
                        .onEvent { debugPrint("Video Event: \($0)") }
                        .onError { debugPrint("Video Error: \($0)") }
                        .frame(width: 300, height: 250)
                case .DisplayInterstitial:
                    FullscreenAdView(ad: Nimbus.fullscreenAd(position: "Interstitial Display HTML") {
                        banner(size: AdSize.interstitialPortrait)
                    })
                        .onEvent { debugPrint("Interstitial Banner Event: \($0)") }
                        .onError { debugPrint("Interstitial Banner Error: \($0)") }
                case .VideoInterstitial:
                    FullscreenAdView(ad: Nimbus.fullscreenAd(position: "Interstitial Display HTML") {
                        video()
                    })
                        .onEvent { debugPrint("Interstitial Video Event: \($0)") }
                        .onError { debugPrint("Interstitail Video Error: \($0)") }
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
