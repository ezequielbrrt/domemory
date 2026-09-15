// swift-tools-version: 5.9
import PackageDescription

#if TUIST
    import ProjectDescription

    // Xcode's iOS 27 Simulator SDK rejects the iOS 12/13 deployment targets
    // declared by several generated third-party package projects. Keep their
    // generated projects aligned with DoMemory's real iOS 18.6 minimum.
    let packageSettings = PackageSettings(
        baseSettings: .settings(
            base: [
                "IPHONEOS_DEPLOYMENT_TARGET": "18.6",
            ]
        )
    )
#endif

let package = Package(
    name: "DoMemoryDependencies",
    dependencies: [
        .package(url: "https://github.com/firebase/firebase-ios-sdk", from: "12.7.0"),
        .package(url: "https://github.com/paololeonardi/WaterfallGrid", from: "1.1.0"),
        .package(url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git", from: "12.0.0"),
        .package(url: "https://github.com/ezequielbrrt/whats-new-ios.git", from: "2.0.0"),
        .package(url: "https://github.com/ezequielbrrt/NotificationPermissionKit.git", from: "1.0.0"),
        .package(url: "https://github.com/ezequielbrrt/ReviewFlow.git", from: "1.0.0"),
        .package(url: "https://github.com/airbnb/lottie-ios", from: "4.6.1"),
    ]
)
