// swift-tools-version: 6.2
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "APS-SwiftPM",
    products: [
        // Products define the executables and libraries a package produces, making them visible to other packages.
        .library(
            name: "APS-SwiftPM",
            targets: ["DTBiOSSDK-SwiftPM"]
        ),
    ],
    targets: [
        .binaryTarget(
             name: "DTBiOSSDK-SwiftPM",
             url: "https://d14jk8f50gmy3e.cloudfront.net/iOS_APS_SDK/APS_iOS_SDK-5.3.2.zip",
             checksum: "6fe3484fbfb92b5869e43811d402b49a837325b6458c7e56d2c17cd0c279b20e"),
    ]
)
