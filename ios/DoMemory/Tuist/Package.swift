// swift-tools-version: 5.9
import PackageDescription

#if TUIST
    import ProjectDescription

    // Xcode's iOS 27 Simulator SDK rejects any deployment target below 15.0,
    // and several generated third-party package projects still declare one
    // as low as 12.0 (their upstream Package.swift manifests say
    // "platforms: [.iOS(.v12)]" or similar). Worse, once those get bumped,
    // Swift's cross-module deployment-target check then rejects importing a
    // *higher*-minimum module (e.g. Promises at 18.6) from a target still
    // sitting at a lower one (Firebase's own targets default to 15.0) — so
    // the fix has to be uniform across every generated target, not just the
    // ones that were originally failing outright.
    //
    // `baseSettings` only reaches each generated project's *project-level*
    // default; Tuist also writes an explicit `IPHONEOS_DEPLOYMENT_TARGET`
    // directly onto most individual targets (which wins over the project
    // default), and cannot write one at all onto the resource-bundle targets
    // Tuist auto-synthesizes for a package's `resources:` declaration (their
    // deployment target isn't addressable through `PackageSettings` by any
    // name — verified directly, not assumed). `baseSettings` is kept here
    // only as a defensive fallback for any target Tuist adds without its own
    // explicit setting; it is not sufficient by itself. The actual, complete
    // fix is `Tuist/fix-deployment-targets.sh`, which must run after every
    // `tuist generate`/`tuist install` (see `../CLAUDE.md`'s Build & Run).
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
