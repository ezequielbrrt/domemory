// swift-tools-version: 5.9
import PackageDescription

#if TUIST
    import ProjectDescription

    let packageSettings = PackageSettings()
#endif

let package = Package(
    name: "DoMemoryDependencies",
    dependencies: [
        .package(url: "https://github.com/firebase/firebase-ios-sdk", from: "12.7.0"),
        .package(url: "https://github.com/paololeonardi/WaterfallGrid", from: "1.1.0"),
        .package(url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git", from: "12.0.0"),
        .package(url: "git@github.com:ezequielbrrt/whats-new-ios.git", from: "1.0.0"),
    ]
)
