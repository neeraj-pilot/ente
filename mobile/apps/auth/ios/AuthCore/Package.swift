// swift-tools-version: 6.0

import PackageDescription

let package = Package(
  name: "AuthCore",
  platforms: [
    .macOS(.v14),
    .iOS(.v17),
    .watchOS(.v10),
  ],
  products: [
    .library(name: "AuthCore", targets: ["AuthCore"])
  ],
  targets: [
    .target(
      name: "AuthCore",
      path: "Sources/AuthCore"
    ),
    .testTarget(
      name: "AuthCoreTests",
      dependencies: ["AuthCore"],
      path: "Tests/AuthCoreTests"
    ),
  ]
)
