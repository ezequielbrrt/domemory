//
//  AdsService.swift
//  DoMemory
//
//  Created by OpenAI on 06/04/26.
//

import Foundation
import GoogleMobileAds
import SwiftUI
import UIKit

enum AdPlacement: String {
    case homeBanner = "home_banner"
    case gameBanner = "game_banner"
    case gameFinishedInterstitial = "game_finished_interstitial"
    case gameRewardedExtraTime = "game_rewarded_extra_time"
    case gameRewardedHint = "game_rewarded_hint"
    case settingsRewardedRemoveAds = "settings_rewarded_remove_ads"
    case appOpen = "app_open"
    case multiplayerFinishedNative = "multiplayer_finished_native"
    case levelsRewardedLife = "levels_rewarded_life"
    case levelsRewardedForgive = "levels_rewarded_forgive"
}

enum AdUnitConfiguration {
    static func bannerUnitID(for placement: AdPlacement) -> String? {
        switch placement {
        case .homeBanner:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/2435281174",
                releaseID: "ca-app-pub-4297174845441653/2000067481"
            )
        case .gameBanner:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/2435281174",
                releaseID: "ca-app-pub-4297174845441653/5761017394"
            )
        case .gameFinishedInterstitial, .gameRewardedExtraTime, .gameRewardedHint, .settingsRewardedRemoveAds, .appOpen, .multiplayerFinishedNative, .levelsRewardedLife, .levelsRewardedForgive:
            return nil
        }
    }

    static func interstitialUnitID(for placement: AdPlacement) -> String? {
        switch placement {
        case .gameFinishedInterstitial:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/4411468910",
                releaseID: "ca-app-pub-4297174845441653/8078681089"
            )
        case .homeBanner, .gameBanner, .gameRewardedExtraTime, .gameRewardedHint, .settingsRewardedRemoveAds, .appOpen, .multiplayerFinishedNative, .levelsRewardedLife, .levelsRewardedForgive:
            return nil
        }
    }

    static func rewardedUnitID(for placement: AdPlacement) -> String? {
        switch placement {
        case .gameRewardedExtraTime:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/1712485313",
                releaseID: "ca-app-pub-4297174845441653/8861220613"
            )
        case .gameRewardedHint:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/1712485313",
                releaseID: "ca-app-pub-4297174845441653/8937242230"
            )
        case .settingsRewardedRemoveAds:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/1712485313",
                releaseID: ""
            )
        case .levelsRewardedLife:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/1712485313",
                releaseID: "ca-app-pub-4297174845441653/6370619149"
            )
        case .levelsRewardedForgive:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/1712485313",
                releaseID: "ca-app-pub-4297174845441653/9605593478"
            )
        case .homeBanner, .gameBanner, .gameFinishedInterstitial, .appOpen, .multiplayerFinishedNative:
            return nil
        }
    }

    static func appOpenUnitID(for placement: AdPlacement) -> String? {
        switch placement {
        case .appOpen:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/5575463023",
                releaseID: "ca-app-pub-4297174845441653/6311078899"
            )
        case .homeBanner, .gameBanner, .gameFinishedInterstitial, .gameRewardedExtraTime, .gameRewardedHint, .settingsRewardedRemoveAds, .multiplayerFinishedNative, .levelsRewardedLife, .levelsRewardedForgive:
            return nil
        }
    }

    static func nativeUnitID(for placement: AdPlacement) -> String? {
        switch placement {
        case .multiplayerFinishedNative:
            return configuredUnitID(
                debugID: "ca-app-pub-3940256099942544/3986624511",
                releaseID: "ca-app-pub-4297174845441653/7396929856"
            )
        case .homeBanner, .gameBanner, .gameFinishedInterstitial, .gameRewardedExtraTime, .gameRewardedHint, .settingsRewardedRemoveAds, .appOpen, .levelsRewardedLife, .levelsRewardedForgive:
            return nil
        }
    }

    private static func configuredUnitID(debugID: String, releaseID: String) -> String? {
        #if DEBUG
        return debugID
        #else
        return releaseID.isEmpty ? nil : releaseID
        #endif
    }
}

@MainActor
final class AdsService: NSObject {
    static let shared = AdsService()

    private var interstitialAd: InterstitialAd?
    private var interstitialPlacement: AdPlacement?
    private var pendingInterstitialPlacement: AdPlacement?
    private var rewardedAds: [AdPlacement: RewardedAd] = [:]
    private var presentingRewardedPlacement: AdPlacement?
    private var presentingRewardedCompletion: ((Bool) -> Void)?
    private var appOpenAd: AppOpenAd?
    private var appOpenLoadTime: Date?
    private var isLoadingAppOpenAd = false
    private var isPresentingFullScreenAd = false
    private var menuIsReadyForAppOpenAds = false
    private var fullScreenAdsSuppressed = false
    private var lastFullScreenAdPresentationDate: Date?
    private let gameFinishedInterstitialCountKey = "ads.game_finished_interstitial_completion_count"
    private let appOpenAdExpirationInterval: TimeInterval = 4 * 60 * 60
    private let minimumSecondsBetweenFullScreenAds: TimeInterval = 90

    private override init() {}

    func isBannerConfigured(for placement: AdPlacement) -> Bool {
        !PurchaseService.shared.hasRemovedAds && AdUnitConfiguration.bannerUnitID(for: placement) != nil
    }

    func makeBannerView(for placement: AdPlacement, rootViewController: UIViewController?) -> BannerView? {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Banner skipped. Remove Ads is active.")
            return nil
        }

        guard let adUnitID = AdUnitConfiguration.bannerUnitID(for: placement) else {
            log("Banner skipped. Missing AdMob unit ID for \(placement.rawValue).")
            return nil
        }

        let bannerView = BannerView(adSize: AdSizeBanner)
        bannerView.adUnitID = adUnitID
        bannerView.rootViewController = rootViewController
        bannerView.load(Request())
        log("Banner requested for \(placement.rawValue).")
        return bannerView
    }

    func isNativeConfigured(for placement: AdPlacement) -> Bool {
        !PurchaseService.shared.hasRemovedAds && AdUnitConfiguration.nativeUnitID(for: placement) != nil
    }

    func loadInterstitial(for placement: AdPlacement) {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Interstitial skipped. Remove Ads is active.")
            return
        }

        guard let adUnitID = AdUnitConfiguration.interstitialUnitID(for: placement) else {
            log("Interstitial skipped. Missing AdMob unit ID for \(placement.rawValue).")
            return
        }

        Task { [weak self] in
            guard let self else { return }

            do {
                let ad = try await InterstitialAd.load(with: adUnitID, request: Request())
                self.interstitialAd = ad
                self.interstitialPlacement = placement
                ad.fullScreenContentDelegate = self
                self.log("Interstitial loaded for \(placement.rawValue).")
                if self.pendingInterstitialPlacement == placement {
                    self.log("Attempting pending interstitial for \(placement.rawValue).")
                    if self.presentInterstitial(for: placement) {
                        UserDefaults.standard.set(0, forKey: self.gameFinishedInterstitialCountKey)
                        self.pendingInterstitialPlacement = nil
                    }
                }
            } catch {
                self.interstitialAd = nil
                self.interstitialPlacement = nil
                self.log("Interstitial failed for \(placement.rawValue): \(error.localizedDescription)")
            }
        }
    }

    func isRewardedConfigured(for placement: AdPlacement) -> Bool {
        !PurchaseService.shared.hasRemovedAds && AdUnitConfiguration.rewardedUnitID(for: placement) != nil
    }

    func loadRewardedAd(for placement: AdPlacement) {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Rewarded skipped. Remove Ads is active.")
            return
        }

        guard rewardedAds[placement] == nil else { return }

        guard let adUnitID = AdUnitConfiguration.rewardedUnitID(for: placement) else {
            log("Rewarded skipped. Missing AdMob unit ID for \(placement.rawValue).")
            return
        }

        Task { [weak self] in
            guard let self else { return }

            do {
                let ad = try await RewardedAd.load(with: adUnitID, request: Request())
                ad.fullScreenContentDelegate = self
                self.rewardedAds[placement] = ad
                self.log("Rewarded loaded for \(placement.rawValue).")
            } catch {
                self.rewardedAds[placement] = nil
                self.log("Rewarded failed for \(placement.rawValue): \(error.localizedDescription)")
            }
        }
    }

    func presentRewardedAd(
        for placement: AdPlacement,
        from viewController: UIViewController? = nil,
        rewardHandler: @escaping () -> Void,
        completion: @escaping (Bool) -> Void
    ) {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Rewarded skipped. Remove Ads is active.")
            completion(false)
            return
        }

        guard let rewardedAd = rewardedAds[placement] else {
            log("Rewarded not ready for \(placement.rawValue).")
            loadRewardedAd(for: placement)
            completion(false)
            return
        }

        guard let presenter = viewController ?? UIApplication.shared.topMostViewController() else {
            log("Rewarded skipped. Missing presenter for \(placement.rawValue).")
            completion(false)
            return
        }

        isPresentingFullScreenAd = true
        lastFullScreenAdPresentationDate = Date()
        presentingRewardedPlacement = placement
        presentingRewardedCompletion = completion
        rewardedAd.present(from: presenter) {
            rewardHandler()
            self.presentingRewardedCompletion?(true)
            self.presentingRewardedCompletion = nil
            self.presentingRewardedPlacement = nil
        }
        rewardedAds[placement] = nil
        loadRewardedAd(for: placement)
        log("Rewarded presented for \(placement.rawValue).")
    }

    /// Set while a first-run surface that the player is meant to read owns the
    /// screen — currently the What's New sheet. An app-open ad arrives on
    /// `didBecomeActive`, which fires again after the ATT prompt is dismissed,
    /// so without this it lands directly on top of the release announcement.
    func setFullScreenAdsSuppressed(_ suppressed: Bool) {
        guard fullScreenAdsSuppressed != suppressed else { return }
        fullScreenAdsSuppressed = suppressed
        log("Full-screen ads \(suppressed ? "suppressed" : "unsuppressed").")
    }

    func registerMenuReadyForAppOpenAds() {
        guard !PurchaseService.shared.hasRemovedAds else { return }
        menuIsReadyForAppOpenAds = true
        loadAppOpenAdIfNeeded()
    }

    func presentAppOpenAdIfAvailable(from viewController: UIViewController? = nil) {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("App open skipped. Remove Ads is active.")
            return
        }

        guard menuIsReadyForAppOpenAds else {
            log("App open skipped. Menu is not ready.")
            loadAppOpenAdIfNeeded()
            return
        }

        guard !fullScreenAdsSuppressed else {
            log("App open skipped. A first-run surface is on screen.")
            loadAppOpenAdIfNeeded()
            return
        }

        guard !isPresentingFullScreenAd else {
            log("App open skipped. Another full-screen ad is presenting.")
            return
        }

        if let lastFullScreenAdPresentationDate,
           Date().timeIntervalSince(lastFullScreenAdPresentationDate) < minimumSecondsBetweenFullScreenAds {
            log("App open skipped. Full-screen cap is active.")
            return
        }

        guard let appOpenAd, isAppOpenAdFresh else {
            log("App open not ready.")
            loadAppOpenAdIfNeeded()
            return
        }

        guard let presenter = viewController ?? UIApplication.shared.topMostViewController() else {
            log("App open skipped. Missing presenter.")
            return
        }

        isPresentingFullScreenAd = true
        lastFullScreenAdPresentationDate = Date()
        appOpenAd.present(from: presenter)
        log("App open presented.")
    }

    func presentInterstitialEvery(
        _ completedGamesFrequency: Int,
        for placement: AdPlacement,
        from viewController: UIViewController? = nil
    ) {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Interstitial skipped. Remove Ads is active.")
            return
        }

        guard completedGamesFrequency > 0 else {
            log("Interstitial skipped. Invalid frequency for \(placement.rawValue).")
            return
        }

        let completedGames = UserDefaults.standard.integer(forKey: gameFinishedInterstitialCountKey) + 1
        UserDefaults.standard.set(completedGames, forKey: gameFinishedInterstitialCountKey)

        guard completedGames >= completedGamesFrequency else {
            log("Interstitial deferred for \(placement.rawValue). Completed games: \(completedGames)/\(completedGamesFrequency).")
            loadInterstitial(for: placement)
            return
        }

        if presentInterstitial(for: placement, from: viewController) {
            UserDefaults.standard.set(0, forKey: gameFinishedInterstitialCountKey)
            pendingInterstitialPlacement = nil
        } else {
            pendingInterstitialPlacement = placement
            log("Interstitial queued for \(placement.rawValue) once loading completes.")
        }
    }

    @discardableResult
    private func presentInterstitial(for placement: AdPlacement, from viewController: UIViewController? = nil) -> Bool {
        guard !PurchaseService.shared.hasRemovedAds else {
            log("Interstitial skipped. Remove Ads is active.")
            return false
        }

        guard interstitialPlacement == placement, let interstitialAd else {
            log("Interstitial not ready for \(placement.rawValue).")
            loadInterstitial(for: placement)
            return false
        }

        guard let presenter = viewController ?? UIApplication.shared.topMostViewController() else {
            log("Interstitial skipped. Missing presenter for \(placement.rawValue).")
            return false
        }

        interstitialAd.present(from: presenter)
        isPresentingFullScreenAd = true
        lastFullScreenAdPresentationDate = Date()
        log("Interstitial presented for \(placement.rawValue).")
        return true
    }

    private var isAppOpenAdFresh: Bool {
        guard let appOpenLoadTime else { return false }
        return Date().timeIntervalSince(appOpenLoadTime) < appOpenAdExpirationInterval
    }

    private func loadAppOpenAdIfNeeded() {
        guard !PurchaseService.shared.hasRemovedAds else { return }
        guard !isLoadingAppOpenAd else { return }
        guard appOpenAd == nil || !isAppOpenAdFresh else { return }

        guard let adUnitID = AdUnitConfiguration.appOpenUnitID(for: .appOpen) else {
            log("App open skipped. Missing AdMob unit ID.")
            return
        }

        isLoadingAppOpenAd = true
        Task { [weak self] in
            guard let self else { return }

            do {
                let ad = try await AppOpenAd.load(with: adUnitID, request: Request())
                ad.fullScreenContentDelegate = self
                self.appOpenAd = ad
                self.appOpenLoadTime = Date()
                self.log("App open loaded.")
            } catch {
                self.appOpenAd = nil
                self.appOpenLoadTime = nil
                self.log("App open failed: \(error.localizedDescription)")
            }
            self.isLoadingAppOpenAd = false
        }
    }

    private func log(_ message: String) {
        #if DEBUG
        print("[AdsService] \(message)")
        #endif
    }
}

@MainActor
struct AdMobBannerView: UIViewRepresentable {
    let placement: AdPlacement

    func makeUIView(context: Context) -> UIView {
        AdsService.shared.makeBannerView(for: placement, rootViewController: UIApplication.shared.topMostViewController()) ?? UIView()
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

@MainActor
struct AdMobNativeAdView: UIViewRepresentable {
    let placement: AdPlacement

    func makeCoordinator() -> Coordinator {
        Coordinator(placement: placement)
    }

    func makeUIView(context: Context) -> UIView {
        guard AdsService.shared.isNativeConfigured(for: placement) else {
            return UIView()
        }

        let nativeAdView = NativeAdView()
        nativeAdView.backgroundColor = UIColor(Color.surfacePrimary)
        nativeAdView.layer.cornerRadius = 18
        nativeAdView.layer.borderWidth = 1
        nativeAdView.layer.borderColor = UIColor(Color.surfaceBorder).cgColor
        nativeAdView.clipsToBounds = true

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        nativeAdView.addSubview(stack)

        let mediaView = MediaView()
        mediaView.translatesAutoresizingMaskIntoConstraints = false
        mediaView.heightAnchor.constraint(equalToConstant: 120).isActive = true
        nativeAdView.mediaView = mediaView
        stack.addArrangedSubview(mediaView)

        let contentStack = UIStackView()
        contentStack.axis = .vertical
        contentStack.spacing = 6
        contentStack.layoutMargins = UIEdgeInsets(top: 0, left: 14, bottom: 14, right: 14)
        contentStack.isLayoutMarginsRelativeArrangement = true
        stack.addArrangedSubview(contentStack)

        let adBadge = UILabel()
        adBadge.text = Strings.adLabel
        adBadge.font = .systemFont(ofSize: 11, weight: .bold)
        adBadge.textColor = UIColor(Color.primaryColor)
        nativeAdView.advertiserView = adBadge
        contentStack.addArrangedSubview(adBadge)

        let headlineLabel = UILabel()
        headlineLabel.font = .systemFont(ofSize: 17, weight: .bold)
        headlineLabel.textColor = UIColor(Color.textPrimary)
        headlineLabel.numberOfLines = 2
        nativeAdView.headlineView = headlineLabel
        contentStack.addArrangedSubview(headlineLabel)

        let bodyLabel = UILabel()
        bodyLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        bodyLabel.textColor = UIColor(Color.textSecondary)
        bodyLabel.numberOfLines = 2
        nativeAdView.bodyView = bodyLabel
        contentStack.addArrangedSubview(bodyLabel)

        let callToActionButton = UIButton(type: .system)
        var buttonConfiguration = UIButton.Configuration.filled()
        buttonConfiguration.baseBackgroundColor = UIColor(Color.primaryColor)
        buttonConfiguration.baseForegroundColor = .white
        buttonConfiguration.cornerStyle = .medium
        buttonConfiguration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 14, bottom: 10, trailing: 14)
        callToActionButton.configuration = buttonConfiguration
        callToActionButton.isUserInteractionEnabled = false
        nativeAdView.callToActionView = callToActionButton
        contentStack.addArrangedSubview(callToActionButton)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: nativeAdView.topAnchor),
            stack.leadingAnchor.constraint(equalTo: nativeAdView.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: nativeAdView.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: nativeAdView.bottomAnchor)
        ])

        context.coordinator.loadAd(into: nativeAdView)
        return nativeAdView
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    final class Coordinator: NSObject, NativeAdLoaderDelegate, AdLoaderDelegate, NativeAdDelegate {
        private let placement: AdPlacement
        private var adLoader: AdLoader?
        private weak var nativeAdView: NativeAdView?

        init(placement: AdPlacement) {
            self.placement = placement
        }

        @MainActor
        func loadAd(into nativeAdView: NativeAdView) {
            guard let adUnitID = AdUnitConfiguration.nativeUnitID(for: placement),
                  let rootViewController = UIApplication.shared.topMostViewController() else {
                return
            }

            self.nativeAdView = nativeAdView
            let adLoader = AdLoader(
                adUnitID: adUnitID,
                rootViewController: rootViewController,
                adTypes: [.native],
                options: nil
            )
            adLoader.delegate = self
            self.adLoader = adLoader
            adLoader.load(Request())
            AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: "native_requested"))
        }

        func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
            Task { @MainActor [weak self] in
                guard let self else { return }
                nativeAd.delegate = self
                guard let nativeAdView = self.nativeAdView else { return }

                (nativeAdView.headlineView as? UILabel)?.text = nativeAd.headline
                nativeAdView.mediaView?.mediaContent = nativeAd.mediaContent
                (nativeAdView.bodyView as? UILabel)?.text = nativeAd.body
                nativeAdView.bodyView?.isHidden = nativeAd.body == nil
                if let callToActionButton = nativeAdView.callToActionView as? UIButton {
                    callToActionButton.configuration?.title = nativeAd.callToAction
                }
                nativeAdView.callToActionView?.isHidden = nativeAd.callToAction == nil
                nativeAdView.nativeAd = nativeAd
                AnalyticsService.log(.adLifecycle(placement: self.placement.rawValue, action: "native_loaded"))
            }
        }

        func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {
            Task { @MainActor [placement] in
                AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: "native_failed"))
            }
        }
    }
}

extension AdsService: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        isPresentingFullScreenAd = false

        if ad === interstitialAd {
            let placement = interstitialPlacement
            interstitialAd = nil
            interstitialPlacement = nil
            if let placement {
                loadInterstitial(for: placement)
            }
        }

        if ad === appOpenAd {
            appOpenAd = nil
            appOpenLoadTime = nil
            loadAppOpenAdIfNeeded()
        }

        if let presentingRewardedPlacement {
            self.presentingRewardedPlacement = nil
            presentingRewardedCompletion?(false)
            presentingRewardedCompletion = nil
            loadRewardedAd(for: presentingRewardedPlacement)
        }
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        log("Ad failed to present: \(error.localizedDescription)")
        isPresentingFullScreenAd = false

        if ad === interstitialAd {
            interstitialAd = nil
            interstitialPlacement = nil
            pendingInterstitialPlacement = nil
        }

        if ad === appOpenAd {
            appOpenAd = nil
            appOpenLoadTime = nil
        }

        if let presentingRewardedPlacement {
            self.presentingRewardedPlacement = nil
            presentingRewardedCompletion?(false)
            presentingRewardedCompletion = nil
            loadRewardedAd(for: presentingRewardedPlacement)
        }
    }
}

private extension UIApplication {
    func topMostViewController(
        base: UIViewController? = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController
    ) -> UIViewController? {
        if let navigationController = base as? UINavigationController {
            return topMostViewController(base: navigationController.visibleViewController)
        }

        if let tabBarController = base as? UITabBarController,
           let selectedViewController = tabBarController.selectedViewController {
            return topMostViewController(base: selectedViewController)
        }

        if let presentedViewController = base?.presentedViewController {
            return topMostViewController(base: presentedViewController)
        }

        return base
    }
}
