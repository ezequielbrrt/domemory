// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DoMemoryDependencies",
    dependencies: [
        .package(url: "https://github.com/firebase/firebase-ios-sdk", from: "12.7.0"),
        .package(url: "https://github.com/paololeonardi/WaterfallGrid", from: "1.1.0"),
        .package(url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git", from: "12.0.0"),
        .package(url: "https://github.com/ezequielbrrt/whats-new-ios.git", from: "2.0.0"),
        .package(url: "https://github.com/ezequielbrrt/NotificationPermissionKit.git", from: "1.0.0"),
        .package(url: "https://github.com/ezequielbrrt/ReviewFlow.git", from: "2.0.0"),
        .package(url: "https://github.com/airbnb/lottie-ios", from: "4.6.1"),
    ]
)
