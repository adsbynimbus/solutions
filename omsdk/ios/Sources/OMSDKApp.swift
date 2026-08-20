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

struct ContentView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Ad Types") {
                    NavigationLink("Display Ad HTML - Inline") {
                        InlineAdView(ad: Nimbus.bannerAd(position: "Display Ad HTML", size: .mrec))
                            .onEvent { debugPrint("Banner Event: \($0)") }
                            .onError { debugPrint("Banner Error: \($0)") }
                            .frame(width: 300, height: 250)
                    }
                    NavigationLink("Video Ad Native - Inline") {
                        InlineAdView(ad: Nimbus.inlineAd(position: "Video Ad Native") {
                            video()
                        })
                            .onEvent { debugPrint("Video Event: \($0)") }
                            .onError { debugPrint("Video Error: \($0)") }
                            .frame(width: 300, height: 250)
                    }
                    NavigationLink("Display Ad HTML - Interstitial") {
                        FullscreenAdView(ad: Nimbus.fullscreenAd(position: "Interstitial Display HTML") {
                            banner(size: AdSize.interstitialPortrait)
                        })
                            .onEvent { debugPrint("Interstitial Banner Event: \($0)") }
                            .onError { debugPrint("Interstitial Banner Error: \($0)") }
                    }
                    NavigationLink("Video Ad Native - Interstitial") {
                        FullscreenAdView(ad: Nimbus.fullscreenAd(position: "Interstitial Display HTML") {
                            video()
                        })
                            .onEvent { debugPrint("Interstitial Video Event: \($0)") }
                            .onError { debugPrint("Interstitail Video Error: \($0)") }
                    }
                }
            }
            .navigationTitle("Nimbus OMSDK Validator")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview {
    ContentView()
}
