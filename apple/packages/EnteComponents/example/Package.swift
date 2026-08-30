// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "EnteComponentsCatalog",
    platforms: [.iOS(.v16), .macOS(.v14)],
    dependencies: [.package(path: "..")],
    targets: [
        .executableTarget(
            name: "EnteComponentsCatalog",
            dependencies: ["EnteComponents"]
        )
    ]
)
