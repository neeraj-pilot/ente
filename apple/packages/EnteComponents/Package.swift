// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "EnteComponents",
    platforms: [.iOS(.v16), .macOS(.v14)],
    products: [.library(name: "EnteComponents", targets: ["EnteComponents"])],
    targets: [
        .target(
            name: "EnteComponents",
            resources: [.process("Resources")]
        )
    ]
)
