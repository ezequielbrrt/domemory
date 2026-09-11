//
//  DailyChallengeShared.swift
//  DoMemory
//
//  Shared between the app and the widget extension. Add this file to BOTH
//  targets' membership so the widget can read the daily-challenge state.
//

import Foundation

/// App Group used to share daily-challenge state with the widget extension.
/// Enable this App Group capability on both the app and widget targets in Xcode.
let dailyChallengeAppGroupID = "group.com.ezequielbrrt.domemory"

enum DailyChallengeSharedStore {
    /// Shared defaults backed by the App Group; falls back to `.standard` if the
    /// App Group capability has not been enabled yet (so the app still works).
    static let defaults: UserDefaults = UserDefaults(suiteName: dailyChallengeAppGroupID) ?? .standard
}

enum DailyChallengeKeys {
    static let streakCurrent = "dailyStreakCurrent"
    static let streakLongest = "dailyStreakLongest"
    static let lastCompletedDay = "dailyLastCompletedDay"
    static let lastAttemptDay = "dailyLastAttemptDay"
}

/// Stable per-day seed string, e.g. `20260616`. Shared so the app and widget
/// agree on what "today" is.
func dailyChallengeSeed(for date: Date = Date(), calendar: Calendar = .current) -> String {
    let components = calendar.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d%02d%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
}

/// Read-only snapshot of the daily-challenge state for display (used by the widget).
struct DailyChallengeSnapshot {
    let currentStreak: Int
    let isCompletedToday: Bool

    static func current(for date: Date = Date()) -> DailyChallengeSnapshot {
        let defaults = DailyChallengeSharedStore.defaults
        let completed = defaults.string(forKey: DailyChallengeKeys.lastAttemptDay) == dailyChallengeSeed(for: date)
        return DailyChallengeSnapshot(
            currentStreak: defaults.integer(forKey: DailyChallengeKeys.streakCurrent),
            isCompletedToday: completed
        )
    }
}
