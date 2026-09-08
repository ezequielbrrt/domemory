//
//  SeasonProgressService.swift
//  DoMemory
//
//  Per-season unlock/star progress and board generation. Mirrors
//  LevelProgressService, but namespaced under `season.<id>.*` so a season
//  ending cannot disturb endless-Levels progress and a returning season
//  resumes exactly where it left off.
//

import Foundation

final class SeasonProgressService {
    /// Realtime Database key of the season this instance tracks. It is part of
    /// every UserDefaults key it writes, so two seasons never collide.
    let seasonID: String

    private let defaults: UserDefaults

    init(seasonID: String, defaults: UserDefaults = .standard) {
        self.seasonID = seasonID
        self.defaults = defaults
    }

    convenience init(season: Season, defaults: UserDefaults = .standard) {
        self.init(seasonID: season.id, defaults: defaults)
    }

    private var namespace: String { "season.\(seasonID)" }
    private var highestUnlockedKey: String { "\(namespace).highestUnlocked" }
    private func starsKey(_ level: Int) -> String { "\(namespace).stars.\(level)" }

    // Deliberately no `season.<id>.lifetimeStars` mirror and no
    // `migrateStarTotalsIfNeeded` equivalent. LevelProgressService stores a
    // lifetime total because endless levels are unbounded and summing them in a
    // view body would grow without limit; it backfills that total for installs
    // that predate the star economy. A season is finite and brand new — there
    // is no historical data to migrate, and `totalStars(levelCount:)` can just
    // add up at most `levelCount` values on demand.

    // MARK: - Progress

    /// Highest level the player may currently play in this season. Levels
    /// `1..<highestUnlockedLevel` have been cleared. It is allowed to reach
    /// `levelCount + 1`, which is what `isComplete(levelCount:)` reads.
    var highestUnlockedLevel: Int {
        let stored = defaults.integer(forKey: highestUnlockedKey)
        return stored > 0 ? stored : 1
    }

    /// 0 if the level has not been cleared yet.
    func stars(for level: Int) -> Int {
        defaults.integer(forKey: starsKey(level))
    }

    func isUnlocked(_ level: Int) -> Bool {
        level <= highestUnlockedLevel
    }

    /// Stars earned across the whole season. Bounded by `levelCount`, so it is
    /// summed on demand rather than stored.
    func totalStars(levelCount: Int) -> Int {
        guard levelCount >= 1 else { return 0 }
        return (1...levelCount).reduce(0) { $0 + stars(for: $1) }
    }

    /// How many of the season's levels have been cleared, clamped to its length.
    func clearedLevelCount(levelCount: Int) -> Int {
        min(max(highestUnlockedLevel - 1, 0), max(levelCount, 0))
    }

    // MARK: - Finite season

    /// True once the season's final level has been cleared.
    func isComplete(levelCount: Int) -> Bool {
        highestUnlockedLevel > levelCount
    }

    /// The level to offer after clearing `level`, or nil when that was the
    /// season's last one. A season is finite, so callers must not simply
    /// increment.
    // Phase 2 wires this into `advanceToNextLevel` / `WinModal.primaryAction`.
    func nextLevel(after level: Int, levelCount: Int) -> Int? {
        level + 1 <= levelCount ? level + 1 : nil
    }

    // MARK: - Board generation

    /// Deterministic board for a season level: the same items every time for a
    /// given `(season, level)`, on every device and across reinstalls.
    ///
    /// The pair count comes from the shared `LevelCurve` with no offset — a
    /// season's level 1 is a 3-pair board even for a veteran — and only the
    /// item source differs from endless Levels: `season.emojiPool` instead of
    /// `EmojiPool.all`.
    func board(for level: Int, emojiPool: [String]) -> Memorama {
        let seed = "season-\(seasonID)-\(level)"
        var generator = SeededGenerator(seed: seed)
        let pairCount = LevelCurve.pairs(for: level)
        let items = Array(emojiPool.shuffled(using: &generator).prefix(pairCount))

        return Memorama(
            id: seed,
            name: Strings.levelTitle(level),
            category: "season",
            difficulty: Difficulty.medium.rawValue,
            description: "",
            publishedDate: seed,
            items: items,
            itemType: "string",
            isDoubleItem: true
        )
    }

    // MARK: - Completion

    /// Records the result of a season level attempt. A win may raise (never
    /// lower) the level's stored star rating and unlocks the next level.
    /// Returns the level's stored star count after recording (0 on a loss).
    ///
    /// Star thresholds are the same as endless Levels', so a 3-star board means
    /// the same thing in both modes.
    @discardableResult
    func recordCompletion(level: Int, didWin: Bool, timeRemaining: Int, totalTime: Int, failedTries: Int) -> Int {
        guard didWin else { return stars(for: level) }

        let earned = starRating(timeRemaining: timeRemaining, totalTime: totalTime, failedTries: failedTries)
        let existing = stars(for: level)
        if earned > existing {
            defaults.set(earned, forKey: starsKey(level))
            // Only the improvement is paid out, so replaying a cleared level
            // can't be farmed for currency while beating your own rating still
            // earns the difference.
            creditNewStars(earned - existing)
        }

        if level + 1 > highestUnlockedLevel {
            defaults.set(level + 1, forKey: highestUnlockedKey)
        }

        return max(earned, existing)
    }

    /// Unlocks the next level without clearing this one. No stars are stored,
    /// so the skipped level renders as cleared-with-0-stars and stays
    /// replayable for credit later.
    func skipLevel(_ level: Int) {
        guard level + 1 > highestUnlockedLevel else { return }
        defaults.set(level + 1, forKey: highestUnlockedKey)
    }

    /// Season stars land in the same wallet endless Levels pays into — one
    /// balance across both modes, per the approved plan. Note this deliberately
    /// does *not* touch `levels.lifetimeStars`: that value is the endless-Levels
    /// mastery score and season play must not inflate it.
    private func creditNewStars(_ amount: Int) {
        guard amount > 0 else { return }
        StarWalletService.shared.credit(amount)
        // Phase 4: log `levelStarsCredited` here once the analytics events
        // carry an optional `seasonID`. Logging it now would fold season play
        // into the endless-Levels funnel with no way to separate them again.
    }

    private func starRating(timeRemaining: Int, totalTime: Int, failedTries: Int) -> Int {
        guard totalTime > 0 else { return 1 }
        let fractionRemaining = Double(timeRemaining) / Double(totalTime)
        if fractionRemaining >= 0.5 && failedTries <= 1 { return 3 }
        if fractionRemaining >= 0.25 { return 2 }
        return 1
    }
}
