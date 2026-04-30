// swift-tools-version:5.9
//
// Test-only Swift Package for `NumberFormattingAlgorithm`.
//
// The production iOS module is consumed by RN apps via CocoaPods + Expo
// autolinking (see `ios/ExpoInputMask.podspec`); this Package.swift exists
// solely so the algorithm can be exercised with `swift test` in CI without
// spinning up an iOS simulator. Only `ios/NumberFormattingAlgorithm.swift`
// is included — the algorithm depends only on Foundation.
import PackageDescription

let package = Package(
  name: "ExpoInputMaskAlgorithm",
  platforms: [.iOS(.v15), .macOS(.v12)],
  products: [
    .library(name: "ExpoInputMaskAlgorithm", targets: ["ExpoInputMaskAlgorithm"])
  ],
  targets: [
    .target(
      name: "ExpoInputMaskAlgorithm",
      path: "ios",
      sources: ["NumberFormattingAlgorithm.swift"]
    ),
    .testTarget(
      name: "ExpoInputMaskAlgorithmTests",
      dependencies: ["ExpoInputMaskAlgorithm"],
      path: "ios/Tests"
    )
  ]
)
