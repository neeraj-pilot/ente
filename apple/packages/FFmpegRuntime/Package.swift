// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "FFmpegRuntime",
    platforms: [.iOS("15.1")],
    products: [
        .library(name: "FFmpegRuntime", targets: ["FFmpegRuntime"])
    ],
    targets: [
        .binaryTarget(
            name: "FFmpegRuntime",
            url:
                "https://github.com/ente/ffmpeg-packaging/releases/download/9.0.2/ios-FFmpegRuntime.xcframework.zip",
            checksum: "2c3923ac301d3deea5df47ab86d5479c5a95cb09b90eae797910498afd554844"
        )
    ]
)
