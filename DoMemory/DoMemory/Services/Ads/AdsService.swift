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
        case .gameFinishedInterstitial:
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
        case .homeBanner, .gameBanner:
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
    private let gameFinishedInterstitialCountKey = "ads.game_finished_interstitial_completion_count"

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
            } catch {
                self.interstitialAd = nil
                self.interstitialPlacement = nil
                self.log("Interstitial failed for \(placement.rawValue): \(error.localizedDescription)")
            }
        }
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
        log("Interstitial presented for \(placement.rawValue).")
        return true
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

extension AdsService: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        if ad === interstitialAd {
            let placement = interstitialPlacement
            interstitialAd = nil
            interstitialPlacement = nil
            if let placement {
                loadInterstitial(for: placement)
            }
        }
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        log("Ad failed to present: \(error.localizedDescription)")

        if ad === interstitialAd {
            interstitialAd = nil
            interstitialPlacement = nil
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
