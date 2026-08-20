import SwiftUI
import GoogleMobileAds
import NimbusKit

#if !SWIFT_PACKAGE
@main
#endif
struct GAMDirectApp: App {
    static let adManagerId = Bundle.main.infoDictionary?["Ad Manager Ad Unit Id"] as! String
    init() {
        let apiKey = Bundle.main.infoDictionary?["Nimbus API Key"] as! String
        let publisherKey = Bundle.main.infoDictionary?["Nimbus Publisher Key"] as! String

        Nimbus.initialize(publisherKey: publisherKey, apiKey: apiKey)

        Nimbus.configuration.testMode = true
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
                    NavigationLink("Banner") {
                        GAMDirectUI()
                    }
                    NavigationLink("Instream") {
                        GAMDirectInstream()
                    }
                }
            }
            .navigationTitle("GAM Direct")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct GAMDirectUI: UIViewRepresentable {
    typealias UIViewType = GAMDirectView
    
    func makeUIView(context: Context) -> GAMDirectView {
        .init(directAdUnitId: GAMDirectApp.adManagerId,
              adManagerAdSizes: [AdSizeBanner],
              apsSlotId: "",
              apsSize: .banner,
              admobBiddingAdUnitId: ""
        )
    }
    
    func updateUIView(_ uiView: GAMDirectView, context: Context) { }

    static func dismantleUIView(_ uiView: GAMDirectView, coordinator: ()) {
        uiView.destroy()
    }
}
