//
//  LevelLivesService.swift
//  DoMemory
//
//  Daily lives budget for Levels mode: 4 attempts/day, spent only on a loss.
//  Mirrors DailyChallengeService's day-boundary pattern, reusing its shared
//  day-seed helper so both features agree on what "today" is.
//

import Foundation

@MainActor
final class LevelLivesService {
    static let shared = LevelLivesService()
    static let maxLives = 4

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private enum Key {
        static let livesRemaining = "levels.lives.remaining"
        static let lastResetDay = "levels.lives.lastResetDay"
    }

    /// Lives left for today, lazily resetting to `maxLives` the first time
    /// it's read on a new day.
    func livesRemaining(for date: Date = Date()) -> Int {
        resetIfNeeded(for: date)
        guard let stored = defaults.object(forKey: Key.livesRemaining) as? Int else {
            return Self.maxLives
        }
        return stored
    }

    /// The daily budget applies to everyone, including Remove-Ads purchasers:
    /// losing has to cost something or the levels have no stakes. Both refills
    /// stay open to them — Remove Ads suppresses involuntary advertising only,
    /// so the rewarded ad is still offered (see `suppressesInvoluntaryAds`),
    /// and `canBuyLifeWithStars` is deliberately not entitlement-gated either.
    func hasLivesRemaining(for date: Date = Date()) -> Bool {
        livesRemaining(for: date) > 0
    }

    /// Spends one life on a loss. Never goes below 0.
    @discardableResult
    func consumeLife(for date: Date = Date()) -> Int {
        resetIfNeeded(for: date)
        let current = livesRemaining(for: date)
        guard current > 0 else { return 0 }
        let updated = current - 1
        defaults.set(updated, forKey: Key.livesRemaining)
        return updated
    }

    /// Grants one life, capped at `maxLives`. Source-agnostic — callers log
    /// whether it came from a rewarded ad or a star purchase.
    @discardableResult
    func addLife(for date: Date = Date()) -> Int {
        resetIfNeeded(for: date)
        let updated = min(Self.maxLives, livesRemaining(for: date) + 1)
        defaults.set(updated, forKey: Key.livesRemaining)
        return updated
    }

    private func resetIfNeeded(for date: Date) {
        let today = dailyChallengeSeed(for: date)
        guard defaults.string(forKey: Key.lastResetDay) != today else { return }
        defaults.set(Self.maxLives, forKey: Key.livesRemaining)
        defaults.set(today, forKey: Key.lastResetDay)
    }
}
