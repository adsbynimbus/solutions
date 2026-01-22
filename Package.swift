// swift-tools-version: 6.1
// Code QL Runner has Swift 6.1 installed, check the runner reference before updating
import PackageDescription

let package = Package(
    name: "solutions",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/adsbynimbus/nimbus-ios-sdk", exact: "2.32.1"),
    ],
    targets: [
        // This target builds the simple AsyncUtils.swift file and includes the same products
        // to pre-compile the SPM modules so only code in this project is scanned
        .target(
            name: "CodeQLPackages",
            dependencies: [
                .product(name: "NimbusKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusRequestAPSKit", package: "nimbus-ios-sdk"),
                .product(name: "NimbusGAMKit", package: "nimbus-ios-sdk"),
            ],
            path: ".github/codeql",
        ),
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
