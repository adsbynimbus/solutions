// swift-tools-version: 6.3
import PackageDescription

let package = Package(
    name: "solutions",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk", exact: "3.0.0-rc.4"),
        .package(url: "https://github.com/adsbynimbus/dynamic-price", branch: "3.0/main"),
        .package(url: "https://github.com/adsbynimbus/swift-package-aps", exact: "5.6.4"),
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
            dependencies: [.product(name: "NimbusKit", package: "nimbus-ios-sdk")],
            path: "omsdk/ios/Sources",
        ),
    ]
)
