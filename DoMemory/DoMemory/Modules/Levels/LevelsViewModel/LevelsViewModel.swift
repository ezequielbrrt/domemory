//
//  LevelsViewModel.swift
//  DoMemory
//
//  Thin @Observable wrapper over LevelProgressService, exposing a windowed
//  list of level tiles for LevelsView's map.
//

import Foundation
import Observation

@Observable
@MainActor
final class LevelsViewModel {
    struct LevelTile: Identifiable, Hashable {
        enum State: Hashable {
            case cleared(stars: Int)
            case current
            case locked
        }

        let level: Int
        let state: State
        var id: Int { level }
    }

    private(set) var tiles: [LevelTile] = []
    var showOutOfLivesPrompt = false
    var isWatchingLivesAd = false

    private let progressService = LevelProgressService.shared
    /// How many extra (locked) tiles to render past the current level, and
    /// how many more to add each time the user scrolls near the end.
    private let pageSize = 20

    var totalStars: Int { progressService.totalStars }
    var currentLevel: Int { progressService.highestUnlockedLevel }
    var livesRemaining: Int { LevelLivesService.shared.livesRemaining() }
    var hasLivesRemaining: Bool { LevelLivesService.shared.hasLivesRemaining() }
    var canWatchAdForLife: Bool { AdsService.shared.isRewardedConfigured(for: .levelsRewardedLife) }
    var starBalance: Int { StarWalletService.shared.balance }
    /// Not gated on the Remove-Ads entitlement — see MemorizeViewModel's twin.
    var canBuyLifeWithStars: Bool {
        StarWalletService.shared.canAfford(LevelPowerUp.lifeCost)
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

    init() {
        refresh()
    }

    func preloadLivesAd() {
        AdsService.shared.loadRewardedAd(for: .levelsRewardedLife)
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

    /// Rebuilds the tile list from persisted progress. Call after returning
    /// from a game so newly-earned stars / unlocks show up.
    func refresh() {
        let highest = progressService.highestUnlockedLevel
        let visibleCount = highest + pageSize
        tiles = (1...visibleCount).map(tile(for:))
    }

    /// Appends another page of locked tiles once the user scrolls within
    /// range of the current end, so the map keeps growing lazily.
    func extendIfNeeded(nearing tile: LevelTile) {
        guard let lastLevel = tiles.last?.level, tile.level >= lastLevel - 4 else { return }
        let newLevels = (lastLevel + 1)...(lastLevel + pageSize)
        tiles.append(contentsOf: newLevels.map(tile(for:)))
    }

    private func tile(for level: Int) -> LevelTile {
        let highest = progressService.highestUnlockedLevel
        if level < highest {
            return LevelTile(level: level, state: .cleared(stars: progressService.stars(for: level)))
        } else if level == highest {
            return LevelTile(level: level, state: .current)
        } else {
            return LevelTile(level: level, state: .locked)
        }
    }
}

extension LevelsViewModel.LevelTile {
    var isLocked: Bool {
        if case .locked = state { return true }
        return false
    }

    var isCurrent: Bool {
        if case .current = state { return true }
        return false
    }
}
