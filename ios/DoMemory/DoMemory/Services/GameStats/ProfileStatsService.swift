//
//  ProfileStatsService.swift
//  DoMemory
//

import Foundation

struct ProfileStats {
    let totalPlayed: Int
    let totalWon: Int
    let perfectGames: Int
    let multiplayerWins: Int
    let longestStreak: Int

    var winRate: Double {
        totalPlayed > 0 ? Double(totalWon) / Double(totalPlayed) : 0
    }
}

struct Achievement: Identifiable {
    let id: String
    let title: String
    let detail: String
    let symbol: String
    let isUnlocked: Bool
    /// 0...1 progress toward unlocking.
    let progress: Double
}

/// Lifetime aggregate stats and derived achievements, backed by `UserDefaults`.
/// Mirrors `GameStatsService` (per-game) but tracks global totals.
final class ProfileStatsService {
    static let shared = ProfileStatsService()

    private let defaults = UserDefaults.standard
    private init() {}

    private enum Key {
        static let played = "profile.totalPlayed"
        static let won = "profile.totalWon"
        static let perfect = "profile.perfectGames"
        static let multiplayerWins = "profile.multiplayerWins"
        static func bestRemaining(_ difficulty: Difficulty) -> String { "profile.bestRemaining.\(difficulty.rawValue)" }
    }

    // MARK: - Recording

    func recordGameFinished(didWin: Bool, isPerfect: Bool, difficulty: Difficulty, timeRemaining: Int) {
        defaults.set(defaults.integer(forKey: Key.played) + 1, forKey: Key.played)
        guard didWin else { return }
        defaults.set(defaults.integer(forKey: Key.won) + 1, forKey: Key.won)
        if isPerfect {
            defaults.set(defaults.integer(forKey: Key.perfect) + 1, forKey: Key.perfect)
        }
        let bestKey = Key.bestRemaining(difficulty)
        if timeRemaining > defaults.integer(forKey: bestKey) {
            defaults.set(timeRemaining, forKey: bestKey)
        }
    }

    func recordMultiplayerWin() {
        defaults.set(defaults.integer(forKey: Key.multiplayerWins) + 1, forKey: Key.multiplayerWins)
    }

    /// Highest remaining time (= fastest win) recorded for a difficulty, or nil if none.
    func bestRemaining(for difficulty: Difficulty) -> Int? {
        let value = defaults.integer(forKey: Key.bestRemaining(difficulty))
        return value > 0 ? value : nil
    }

    // MARK: - Reading

    var stats: ProfileStats {
        ProfileStats(
            totalPlayed: defaults.integer(forKey: Key.played),
            totalWon: defaults.integer(forKey: Key.won),
            perfectGames: defaults.integer(forKey: Key.perfect),
            multiplayerWins: defaults.integer(forKey: Key.multiplayerWins),
            longestStreak: DailyChallengeService.shared.longestStreak
        )
    }

    func achievements() -> [Achievement] {
        let stats = self.stats

        func winTier(_ threshold: Int) -> Achievement {
            Achievement(
                id: "wins_\(threshold)",
                title: String(format: NSLocalizedString("ach_wins_title_format", comment: "Wins achievement title, %d = count"), threshold),
                detail: String(format: NSLocalizedString("ach_wins_detail_format", comment: "Wins achievement detail, %d = count"), threshold),
                symbol: "rosette",
                isUnlocked: stats.totalWon >= threshold,
                progress: min(1, Double(stats.totalWon) / Double(threshold))
            )
        }

        func streakTier(_ threshold: Int) -> Achievement {
            Achievement(
                id: "streak_\(threshold)",
                title: String(format: NSLocalizedString("ach_streak_title_format", comment: "Streak achievement title, %d = days"), threshold),
                detail: String(format: NSLocalizedString("ach_streak_detail_format", comment: "Streak achievement detail, %d = days"), threshold),
                symbol: "flame.fill",
                isUnlocked: stats.longestStreak >= threshold,
                progress: min(1, Double(stats.longestStreak) / Double(threshold))
            )
        }

        let perfect = Achievement(
            id: "perfect",
            title: NSLocalizedString("ach_perfect_title", comment: "Perfect game achievement title"),
            detail: NSLocalizedString("ach_perfect_detail", comment: "Perfect game achievement detail"),
            symbol: "sparkles",
            isUnlocked: stats.perfectGames > 0,
            progress: stats.perfectGames > 0 ? 1 : 0
        )

        let multiplayer = Achievement(
            id: "multiplayer",
            title: NSLocalizedString("ach_multiplayer_title", comment: "Multiplayer achievement title"),
            detail: NSLocalizedString("ach_multiplayer_detail", comment: "Multiplayer achievement detail"),
            symbol: "person.2.fill",
            isUnlocked: stats.multiplayerWins > 0,
            progress: stats.multiplayerWins > 0 ? 1 : 0
        )

        return [winTier(10), winTier(50), winTier(100), streakTier(7), streakTier(30), perfect, multiplayer]
    }
}
