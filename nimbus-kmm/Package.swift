// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "NimbusKMM",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "NimbusKMM", type: .dynamic, targets: ["NimbusKMM"]),
    ],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk.git", from: "2.21.1"),
    ],
    targets: [
        .target(
            name: "NimbusKMM",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusRenderVASTKit", package: "nimbus-ios-sdk"),
            ])
    ]
)
