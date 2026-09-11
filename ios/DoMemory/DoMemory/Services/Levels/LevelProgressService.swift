//
//  LevelProgressService.swift
//  DoMemory
//
//  Endless procedural levels: board generation + unlock/star progress,
//  backed by UserDefaults. Mirrors GameStatsService / DailyChallengeService.
//

import Foundation

final class LevelProgressService {
    static let shared = LevelProgressService()

    private let defaults = UserDefaults.standard
    private init() {}

    private enum Key {
        static let highestUnlocked = "levels.highestUnlocked"
        static let lifetimeStars = "levels.lifetimeStars"
        static func stars(_ level: Int) -> String { "levels.stars.\(level)" }
    }

    // MARK: - Progress

    /// Highest level the player may currently play. Levels `1..<highestUnlockedLevel`
    /// have been cleared; `highestUnlockedLevel` itself is the next one to attempt.
    var highestUnlockedLevel: Int {
        let stored = defaults.integer(forKey: Key.highestUnlocked)
        return stored > 0 ? stored : 1
    }

    /// 0 if the level has not been cleared yet.
    func stars(for level: Int) -> Int {
        defaults.integer(forKey: Key.stars(level))
    }

    func isUnlocked(_ level: Int) -> Bool {
        level <= highestUnlockedLevel
    }

    /// Lifetime stars earned. Monotonic — spending from `StarWalletService`
    /// never lowers this. Stored rather than summed so reading it from a view
    /// body stays O(1) as the player climbs.
    var totalStars: Int {
        migrateStarTotalsIfNeeded()
        return defaults.integer(forKey: Key.lifetimeStars)
    }

    /// Backfills the stored lifetime total (and the opening wallet balance)
    /// for installs that predate the star economy, by summing the per-level
    /// ratings once. A no-op on every subsequent call.
    func migrateStarTotalsIfNeeded() {
        guard defaults.object(forKey: Key.lifetimeStars) == nil else { return }
        let earned = (1...highestUnlockedLevel).reduce(0) { $0 + stars(for: $1) }
        defaults.set(earned, forKey: Key.lifetimeStars)
        // Existing players haven't spent anything yet, so they open the wallet
        // holding everything they've already earned.
        StarWalletService.shared.seedBalance(earned)
    }

    // MARK: - Board generation

    /// Deterministic board for a level: same items every time for a given level number.
    func board(for level: Int) -> Memorama {
        let seed = "level-\(level)"
        var generator = SeededGenerator(seed: seed)
        let pairCount = LevelCurve.pairs(for: level)
        let items = Array(EmojiPool.all.shuffled(using: &generator).prefix(pairCount))

        return Memorama(
            id: seed,
            name: Strings.levelTitle(level),
            category: "level",
            difficulty: Difficulty.medium.rawValue,
            description: "",
            publishedDate: seed,
            items: items,
            itemType: "string",
            isDoubleItem: true
        )
    }

    // MARK: - Completion

    /// Records the result of a level attempt. A win may raise (never lower) the
    /// level's stored star rating and unlocks the next level. Returns the
    /// level's stored star count after recording (0 on a loss).
    @discardableResult
    func recordCompletion(level: Int, didWin: Bool, timeRemaining: Int, totalTime: Int, failedTries: Int) -> Int {
        guard didWin else { return stars(for: level) }
        migrateStarTotalsIfNeeded()

        let earned = starRating(timeRemaining: timeRemaining, totalTime: totalTime, failedTries: failedTries)
        let existing = stars(for: level)
        if earned > existing {
            defaults.set(earned, forKey: Key.stars(level))
            // Only the improvement is paid out, so replaying a cleared level
            // can't be farmed for currency while beating your own rating still
            // earns the difference.
            creditNewStars(earned - existing, level: level)
        }

        if level + 1 > highestUnlockedLevel {
            defaults.set(level + 1, forKey: Key.highestUnlocked)
        }

        return max(earned, existing)
    }

    /// Unlocks the next level without clearing this one. No stars are stored,
    /// so the skipped level renders as cleared-with-0-stars and stays
    /// replayable for credit later.
    func skipLevel(_ level: Int) {
        guard level + 1 > highestUnlockedLevel else { return }
        defaults.set(level + 1, forKey: Key.highestUnlocked)
    }

    private func creditNewStars(_ amount: Int, level: Int) {
        guard amount > 0 else { return }
        defaults.set(defaults.integer(forKey: Key.lifetimeStars) + amount, forKey: Key.lifetimeStars)
        let balance = StarWalletService.shared.credit(amount)
        AnalyticsService.log(.levelStarsCredited(level: level, amount: amount, balanceAfter: balance))
    }

    private func starRating(timeRemaining: Int, totalTime: Int, failedTries: Int) -> Int {
        guard totalTime > 0 else { return 1 }
        let fractionRemaining = Double(timeRemaining) / Double(totalTime)
        if fractionRemaining >= 0.5 && failedTries <= 1 { return 3 }
        if fractionRemaining >= 0.25 { return 2 }
        return 1
    }
}
