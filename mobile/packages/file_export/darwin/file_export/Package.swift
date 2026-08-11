// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "FileExportCore",
    platforms: [.iOS(.v13), .macOS(.v10_15)],
    products: [.library(name: "FileExportCore", targets: ["FileExportCore"])],
    targets: [
        .target(
            name: "FileExportCore",
            path: "Sources/file_export",
            exclude: [
                "ChannelCodec.swift",
                "FileExportPlugin+iOS.swift",
                "FileExportPlugin+macOS.swift",
            ]
        ),
        .testTarget(
            name: "FileExportCoreTests",
            dependencies: ["FileExportCore"],
            path: "Tests"
        ),
    ],
    swiftLanguageVersions: [.v5]
)
