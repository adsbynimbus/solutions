// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "NimbusMediationAdapter",
    platforms: [
        .iOS(.iOS13)
    ],
    products: [
        .library(
            name: "NimbusMediationAdapter",
            targets: ["NimbusMediationAdapter"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/applovin/AppLovinSDK-iOS.git", from: "12.0.0"),
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk.git", from: "3.0.0-rc.5")
    ],
    targets: [
        .target(
            name: "NimbusMediationAdapter",
            dependencies: [
                "AppLovinSDK",
                "NimbusKit"
            ]
        ),
        .testTarget(
            name: "NimbusMediationAdapterTests",
            dependencies: ["NimbusMediationAdapter"]
        ),
    ]
)
