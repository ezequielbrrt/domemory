//
//  AdFreeDayOfferViewModel.swift
//  DoMemory
//
//  State behind the ad-free day sheet: which copy to show, whether an ad is
//  ready, and the two-ad chain that ends in a 24-hour grant. Progress lives in
//  `AdFreeDayService`; the grant in `PurchaseService`; the ad in `AdsService`.
//

import Foundation
import Observation

@MainActor
@Observable
final class AdFreeDayOfferViewModel {
    enum Phase: Equatable {
        /// A rewarded ad is being fetched; the primary button is disabled.
        case loading
        /// An ad is cached and can be shown on the next tap.
        case ready
        /// The ad is on screen.
        case presenting
        /// No ad could be loaded; the primary button offers a retry.
        case noFill
        /// The 24-hour grant is running.
        case active
    }

    /// Where the sheet was opened from, for analytics: `menu_levels`,
    /// `menu_mine`, `menu_all`, `season_levels`, `multiplayer_lobby`, `settings`.
    let source: String

    private(set) var phase: Phase
    private(set) var adsWatched: Int
    /// True for the whole first presentation, even after `markIntroSeen`, so
    /// the copy does not switch mid-sheet.
    let isIntro: Bool

    private let service: AdFreeDayService
    private let purchases: PurchaseService
    private let placement: AdPlacement = .adFreeDayRewarded

    var requiredAds: Int { AdFreeDayService.requiredAds }
    var nextAdNumber: Int { min(adsWatched + 1, requiredAds) }
    var hasPurchasedRemoveAds: Bool { purchases.hasPurchasedRemoveAds }
    var removeAdsPriceText: String { purchases.removeAdsPriceText }
    var expirationText: String { purchases.rewardedRemoveAdsExpirationText }

    init(source: String, service: AdFreeDayService = .shared, purchases: PurchaseService = .shared) {
        self.source = source
        self.service = service
        self.purchases = purchases
        adsWatched = service.adsWatched()
        isIntro = !service.hasSeenIntro
        phase = purchases.hasActiveRewardedRemoveAds ? .active : .loading
    }

    func onAppear() {
        AnalyticsService.log(.adFreeDayOfferShown(source: source, isIntro: isIntro, adsWatched: adsWatched))
        if isIntro {
            service.markIntroSeen()
        }
        prepareAd()
    }

    /// Fetches the next rewarded ad, or reports that none is available.
    func prepareAd() {
        guard phase != .active else { return }
        phase = .loading
        AdsService.shared.loadRewardedAd(for: placement) { [weak self] ready in
            guard let self, self.phase == .loading else { return }
            self.phase = ready ? .ready : .noFill
        }
    }

    /// The primary button. Each ad in the chain needs its own tap — the second
    /// is never auto-launched when the first closes.
    func primaryAction() {
        switch phase {
        case .ready:
            watchAd()
        case .noFill:
            prepareAd()
        case .loading, .presenting, .active:
            break
        }
    }

    func purchaseRemoveAds() async {
        await purchases.purchaseRemoveAds()
        if purchases.hasPurchasedRemoveAds {
            phase = .active
        }
        // `purchaseAlert` is only ever presented by Settings. When the sheet was
        // opened from anywhere else, the sheet closing on success (and the pill
        // and ads disappearing) is the confirmation; drop the alert the purchase
        // queued so it does not pop up the next time Settings opens.
        if source != "settings" {
            purchases.purchaseAlert = nil
        }
    }

    /// Logs a tap on an entry point to the sheet — the floating pill or the
    /// Settings row — with the state it showed at the time.
    static func logEntryTapped(source: String) {
        let adsWatched = AdFreeDayService.shared.adsWatched()
        let state: String
        if PurchaseService.shared.hasActiveRewardedRemoveAds {
            state = "active"
        } else if adsWatched > 0 {
            state = "in_progress"
        } else {
            state = "idle"
        }
        AnalyticsService.log(.adFreeDayEntryTapped(source: source, state: state, adsWatched: adsWatched))
    }

    private func watchAd() {
        phase = .presenting
        AnalyticsService.log(.adFreeDayWatchTapped(source: source, adNumber: nextAdNumber))
        AnalyticsService.log(.adLifecycle(placement: placement.rawValue, action: "requested"))
        AdsService.shared.presentRewardedAd(
            for: placement,
            rewardHandler: { [weak self] in
                guard let self else { return }
                AnalyticsService.log(.adLifecycle(placement: self.placement.rawValue, action: "reward_earned"))
                // Read before recording: completing the chain clears progress.
                let adNumber = self.service.adsWatched() + 1
                AnalyticsService.log(.adFreeDayAdWatched(source: self.source, adNumber: adNumber))
                let completed = self.service.recordAdWatched()
                self.adsWatched = self.service.adsWatched()
                guard completed else { return }
                self.purchases.grantRewardedRemoveAds(showsAlert: false)
                AnalyticsService.log(.adFreeDayGranted(source: self.source))
                HapticsService.shared.fire(.reward)
            },
            completion: { [weak self] didEarnReward in
                guard let self else { return }
                AnalyticsService.log(.adLifecycle(
                    placement: self.placement.rawValue,
                    action: didEarnReward ? "dismissed_rewarded" : "dismissed_unrewarded"
                ))
                if self.purchases.hasActiveRewardedRemoveAds {
                    self.phase = .active
                } else {
                    // Either the next ad in the chain or a retry after a miss;
                    // `AdsService` already started the preload when it presented.
                    self.prepareAd()
                }
            }
        )
    }
}
