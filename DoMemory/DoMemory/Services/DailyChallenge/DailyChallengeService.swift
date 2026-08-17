//
//  DailyChallengeService.swift
//  DoMemory
//

import Foundation
import WidgetKit

/// Tells the home-screen widget to refresh after the daily-challenge state changes.
enum WidgetReloader {
    static let dailyChallengeKind = "DoMemoryDailyWidget"

    static func reloadDailyChallenge() {
        WidgetCenter.shared.reloadTimelines(ofKind: dailyChallengeKind)
    }
}

/// Deterministic daily challenge + streak tracking.
///
/// One board per calendar day, identical *content* for every user (the emoji
/// pairs are chosen with a date-seeded RNG). The card layout is still shuffled
/// randomly per play by `MemoryGame.init` — this is intentional so a shared
/// screenshot can't be used to memorise positions.
final class DailyChallengeService {
    static let shared = DailyChallengeService()

    /// Medium difficulty → 6 pairs, matching the `medium` boards seeded from `data.json`.
    private let pairsPerChallenge = 6
    private let challengeDifficulty: Difficulty = .medium

    private let defaults = DailyChallengeSharedStore.defaults
    private var calendar: Calendar { Calendar.current }

    private init() {}

    // MARK: - Today's board

    /// Stable per-day seed string, e.g. `20260616`.
    func todaysSeed(for date: Date = Date()) -> String {
        dailyChallengeSeed(for: date, calendar: calendar)
    }

    /// Deterministic board for the given day. Same pairs for all users; layout
    /// is shuffled per play inside `MemoryGame.init`.
    func boardForToday(for date: Date = Date()) -> Memorama {
        let seed = todaysSeed(for: date)
        var generator = SeededGenerator(seed: seed)
        let items = Array(EmojiPool.all.shuffled(using: &generator).prefix(pairsPerChallenge))

        return Memorama(
            id: "daily-\(seed)",
            name: Strings.dailyChallengeTitle,
            category: "daily",
            difficulty: challengeDifficulty.rawValue,
            description: Strings.dailyChallengeTitle,
            publishedDate: seed,
            items: items,
            itemType: "string",
            isDoubleItem: true
        )
    }

    // MARK: - Completion / streak state

    var currentStreak: Int { defaults.integer(forKey: DailyChallengeKeys.streakCurrent) }
    var longestStreak: Int { defaults.integer(forKey: DailyChallengeKeys.streakLongest) }
    /// Day of the last winning completion — used for streak continuity.
    var lastWinDay: String? { defaults.string(forKey: DailyChallengeKeys.lastCompletedDay) }
    /// Day of the last *attempt* (win or loss) — used to lock the challenge for the day.
    var lastAttemptDay: String? { defaults.string(forKey: DailyChallengeKeys.lastAttemptDay) }

    /// True once today's challenge has been finished (win or loss); the board is
    /// then locked for the rest of the day — one attempt per day.
    func isCompletedToday(for date: Date = Date()) -> Bool {
        lastAttemptDay == todaysSeed(for: date)
    }

    /// Record the result of today's challenge. Any finish consumes the day; a
    /// win advances the streak, a loss breaks it. Idempotent within a day.
    @discardableResult
    func recordCompletion(didWin: Bool, for date: Date = Date()) -> Int {
        guard !isCompletedToday(for: date) else { return currentStreak }

        let today = todaysSeed(for: date)
        defaults.set(today, forKey: DailyChallengeKeys.lastAttemptDay)

        guard didWin else {
            defaults.set(0, forKey: DailyChallengeKeys.streakCurrent)
            WidgetReloader.reloadDailyChallenge()
            return 0
        }

        let continued = lastWinDay.map { isConsecutiveDay(previous: $0, current: today) } ?? false
        let newStreak = continued ? currentStreak + 1 : 1
        defaults.set(newStreak, forKey: DailyChallengeKeys.streakCurrent)
        defaults.set(today, forKey: DailyChallengeKeys.lastCompletedDay)
        if newStreak > longestStreak {
            defaults.set(newStreak, forKey: DailyChallengeKeys.streakLongest)
        }
        WidgetReloader.reloadDailyChallenge()
        return newStreak
    }

    // MARK: - Helpers

    private func isConsecutiveDay(previous: String, current: String) -> Bool {
        guard
            let previousDate = date(fromSeed: previous),
            let currentDate = date(fromSeed: current),
            let diff = calendar.dateComponents([.day], from: previousDate, to: currentDate).day
        else { return false }
        return diff == 1
    }

    private func date(fromSeed seed: String) -> Date? {
        guard seed.count == 8,
              let year = Int(seed.prefix(4)),
              let month = Int(seed.dropFirst(4).prefix(2)),
              let day = Int(seed.suffix(2)) else { return nil }
        return calendar.date(from: DateComponents(year: year, month: month, day: day))
    }
}
