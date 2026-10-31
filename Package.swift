// swift-tools-version: 6.0
import PackageDescription

// At the repository root because that is the only place Swift Package Manager
// looks for a package; the sources live in ios/, next to android/.
let package = Package(
    name: "FounderPing",
    // Lower than the iOS 17 the widget runs on, so an app that still supports
    // older versions can add it: there it stays off. See FounderPing.start().
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "FounderPing", targets: ["FounderPing"]),
    ],
    targets: [
        .target(
            name: "FounderPing",
            path: "ios/Sources/FounderPing",
            resources: [.copy("PrivacyInfo.xcprivacy")]
        ),
        .testTarget(
            name: "FounderPingTests",
            dependencies: ["FounderPing"],
            path: "ios/Tests/FounderPingTests"
        ),
    ]
)
