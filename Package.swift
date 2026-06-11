// swift-tools-version: 6.3
import PackageDescription

let package = Package(
    name: "solutions",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk", exact: "2.34.0"),
        .package(url: "https://github.com/adsbynimbus/dynamic-price", exact: "0.0.1"),
    ],
    targets: [
        .target(
            name: "DynamicPriceApp",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusRequestAPSKit", package: "nimbus-ios-sdk"),
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
