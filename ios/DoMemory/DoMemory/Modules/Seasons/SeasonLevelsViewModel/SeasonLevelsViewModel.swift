//
//  SeasonLevelsViewModel.swift
//  DoMemory
//
//  The season twin of LevelsViewModel: the same tiles, lives and star wallet,
//  but bounded by the season's `levelCount` instead of paging forever.
//

import Foundation
import Observation

@Observable
@MainActor
final class SeasonLevelsViewModel {
    let season: Season

    private(set) var tiles: [LevelTile] = []
    var showOutOfLivesPrompt = false
    var isWatchingLivesAd = false

    /// Namespaced under `season.<id>.*`, so nothing here can disturb
    /// endless-Levels progress.
    private let progressService: SeasonProgressService

    /// - Parameter progress: the progress service to use. Defaults to one built
    ///   from the season, and exists so tests can inject a scratch
    ///   `UserDefaults` suite.
    init(season: Season, progress: SeasonProgressService? = nil) {
        self.season = season
        self.progressService = progress ?? SeasonProgressService(season: season)
        refresh()
    }

    // MARK: - Season state

    var levelCount: Int { season.levelCount }
    var clearedLevelCount: Int { progressService.clearedLevelCount(levelCount: levelCount) }
    var isComplete: Bool { progressService.isComplete(levelCount: levelCount) }
    var seasonStars: Int { progressService.totalStars(levelCount: levelCount) }
    /// The season's last day countdown, or nil when the payload's `endDate`
    /// does not parse. The header simply omits it in that case.
    var daysRemaining: Int? { season.daysRemaining() }

    // MARK: - Shared budgets
    //
    // Lives and the star wallet are global singletons by design — one daily
    // budget and one balance across endless Levels and every season — so these
    // deliberately mirror `LevelsViewModel`'s twins rather than namespacing
    // anything per season.

    var livesRemaining: Int { LevelLivesService.shared.livesRemaining() }
    var hasLivesRemaining: Bool { LevelLivesService.shared.hasLivesRemaining() }
    var canWatchAdForLife: Bool { AdsService.shared.isRewardedConfigured(for: .levelsRewardedLife) }
    var starBalance: Int { StarWalletService.shared.balance }
    /// Not gated on the Remove-Ads entitlement — see `LevelsViewModel`'s twin.
    var canBuyLifeWithStars: Bool {
        StarWalletService.shared.canAfford(LevelPowerUp.lifeCost)
    }

    // MARK: - Playing a level

    /// The context to hand `MemorizeViewModel`. `LevelContext.season` is the
    /// single place the season's id, pool, length and store are read together,
    /// so the ceiling the win modal enforces cannot drift from the
    /// `levelCount` this screen displays.
    func context(for level: Int) -> LevelContext {
        .season(season, level: level, progress: progressService)
    }

    // MARK: - Tiles

    /// Rebuilds the tile list from persisted progress. Call after returning
    /// from a game so newly-earned stars / unlocks show up.
    ///
    /// A season is finite, so the map is built once at its full length — there
    /// is no paging, and no locked tile past the season's last level.
    func refresh() {
        tiles = Self.tiles(
            highestUnlocked: progressService.highestUnlockedLevel,
            levelCount: levelCount,
            stars: progressService.stars(for:)
        )
    }

    /// Pure so the bounded-map rules stay testable without a view.
    static func tiles(
        highestUnlocked: Int,
        levelCount: Int,
        stars: (Int) -> Int
    ) -> [LevelTile] {
        guard levelCount >= 1 else { return [] }
        return (1...levelCount).map { level in
            if level < highestUnlocked {
                return LevelTile(level: level, state: .cleared(stars: stars(level)))
            } else if level == highestUnlocked {
                return LevelTile(level: level, state: .current)
            } else {
                return LevelTile(level: level, state: .locked)
            }
        }
    }

    // MARK: - Out of lives
    //
    // Mirrors `LevelsViewModel`'s rescue flow: the same rewarded-ad placement
    // and the same star price, because the budget being refilled is the same
    // one. Kept as a twin rather than hoisted into a shared controller so the
    // endless-Levels path this phase must not regress stays untouched.

    func preloadLivesAd() {
        AdsService.shared.loadRewardedAd(for: .levelsRewardedLife)
    }

    func buyLifeWithStars() {
        guard canBuyLifeWithStars else { return }
        guard StarWalletService.shared.spend(LevelPowerUp.lifeCost) else { return }
        HapticsService.shared.fire(.reward)
        LevelLivesService.shared.addLife()
        AnalyticsService.log(
            .levelLifePurchasedWithStars(
                cost: LevelPowerUp.lifeCost,
                balanceAfter: StarWalletService.shared.balance
            )
        )
        showOutOfLivesPrompt = false
        refresh()
    }

    func watchAdForLife() {
        guard !isWatchingLivesAd else { return }
        isWatchingLivesAd = true
        AnalyticsService.log(.adLifecycle(placement: AdPlacement.levelsRewardedLife.rawValue, action: "requested"))
        AdsService.shared.presentRewardedAd(
            for: .levelsRewardedLife,
            rewardHandler: {
                HapticsService.shared.fire(.reward)
                let updated = LevelLivesService.shared.addLife()
                AnalyticsService.log(.levelLifeGrantedFromAd(livesRemaining: updated))
                AnalyticsService.log(.adLifecycle(placement: AdPlacement.levelsRewardedLife.rawValue, action: "reward_earned"))
            },
            completion: { [weak self] didEarnReward in
                guard let self else { return }
                self.isWatchingLivesAd = false
                if didEarnReward {
                    self.showOutOfLivesPrompt = false
                }
                AnalyticsService.log(.adLifecycle(placement: AdPlacement.levelsRewardedLife.rawValue, action: didEarnReward ? "dismissed_rewarded" : "dismissed_unrewarded"))
            }
        )
    }
}
