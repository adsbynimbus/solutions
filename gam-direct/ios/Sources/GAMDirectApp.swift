import SwiftUI
import GoogleMobileAds

@main
struct GAMDirectApp: App {
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
