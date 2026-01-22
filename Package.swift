// swift-tools-version: 6.2

import PackageDescription

let package = Package(
    name: "solutions",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk", exact: "2.32.1"),
    ],
    targets: [
        .target(
            name: "DynamicPrice",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusRequestAPSKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusGAMKit", package: "nimbus-ios-sdk"),
            ],
            path: "dynamicprice/ios/Sources",
        ),
        .testTarget(
            name: "DynamicPriceTests",
            dependencies: ["DynamicPrice"],
            path: "dynamicprice/ios/Tests",
        ),
        .target(
            name: "OMSDK",
            dependencies: [.product(name: "NimbusKit", package: "nimbus-ios-sdk")],
            path: "omsdk/ios/Sources",
        )
    ]
)
