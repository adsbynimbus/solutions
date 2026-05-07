import SwiftUI
import GoogleMobileAds
import NimbusKit

@main
struct GAMDirectApp: App {
    init() {
        let apiKey = Bundle.main.infoDictionary?["Nimbus API Key"] as! String
        let publisherKey = Bundle.main.infoDictionary?["Nimbus Publisher Key"] as! String

        Nimbus.shared.initialize(publisher: publisherKey, apiKey: apiKey)

        Nimbus.shared.logLevel = .debug
        Nimbus.shared.testMode = true
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
        .init(directAdUnitId: "",
              adManagerAdSizes: [AdSizeBanner],
              apsSlotId: "",
              apsSize: .banner,
              admobBiddingAdUnitId: ""
        )
    }
    
    func updateUIView(_ uiView: GAMDirectView, context: Context) { }
}
