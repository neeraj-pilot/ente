// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "PhotosPlatformCore",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "PhotosPlatformCore",
            targets: ["PhotosPlatformCore", "MediaLibraryCore"]
        )
    ],
    targets: [
        .target(
            name: "MediaLibraryCore",
            path: "Sources/MediaLibraryCore"
        ),
        .target(
            name: "PhotosPlatformCore",
            dependencies: ["MediaLibraryCore"],
            path: "Sources/PhotosPlatformCore"
        ),
    ],
    swiftLanguageVersions: [.v5]
)
