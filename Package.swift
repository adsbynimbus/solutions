// swift-tools-version: 6.3
import PackageDescription

let package = Package(
    name: "solutions",
    platforms: [.iOS(.v17)],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk", exact: "3.0.0-rc.4"),
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-admob", exact: "13.0.0-rc.4"),
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-aps", exact: "5.0.0-rc.4"),
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-swiftui", exact: "1.0.0-rc.4"),
        .package(url: "https://github.com/adsbynimbus/dynamic-price", branch: "3.0/main"),
        .package(url: "https://github.com/adsbynimbus/swift-package-aps", exact: "5.6.4"),
        .package(
            url: "https://github.com/googleads/swift-package-manager-google-interactive-media-ads-ios",
            exact: "3.32.0"
        ),
    ],
    targets: [
        .target(
            name: "DynamicPriceApp",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "DTBiOSSDK", package: "swift-package-aps"),
                .product(name: "DynamicPrice", package: "dynamic-price"),
            ],
            path: "dynamicprice/ios/Sources",
        ),
        .testTarget(
            name: "DynamicPriceTests",
            dependencies: ["DynamicPriceApp"],
            path: "dynamicprice/ios/Tests",
        ),
        .target(
            name: "OMSDK",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusSwiftUI", package: "nimbus-ios-swiftui"),
            ],
            path: "omsdk/ios/Sources",
        ),
        .target(
            name: "GAMDirect",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusAdMobKit", package: "nimbus-ios-admob"),
                .product(name: "NimbusAPSKit", package: "nimbus-ios-aps"),
                .product(
                    name: "GoogleInteractiveMediaAds",
                    package: "swift-package-manager-google-interactive-media-ads-ios",
                ),
            ],
            path: "gam-direct/ios/Sources",
        ),
    ]
)
