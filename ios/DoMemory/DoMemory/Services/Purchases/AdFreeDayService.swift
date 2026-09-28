//
//  AdFreeDayService.swift
//  DoMemory
//
//  Progress through the two-ad chain that earns a 24-hour ad-free day. The
//  grant itself lives in `PurchaseService.grantRewardedRemoveAds`; this type
//  only counts ads, so it stays testable without StoreKit or AdMob.
//
//  Progress is scoped to the local day, using the same day seed as lives and
//  the Daily Challenge, so a chain left half-finished resets at midnight
//  rather than lingering for weeks. One ad on its own grants nothing.
//

import Foundation

@MainActor
final class AdFreeDayService {
    static let shared = AdFreeDayService()

    /// Ads the player must watch, each on its own tap, before the day is
    /// granted. A constant for now so it can move to Remote Config later.
    static let requiredAds = 2

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private enum Key {
        static let adsWatched = "adFreeDay.adsWatched"
        static let progressDay = "adFreeDay.progressDay"
        static let introShown = "adFreeDay.introShown"
    }

    /// Ads watched so far today, in `0..<requiredAds`. Lazily resets on the
    /// first read of a new local day.
    func adsWatched(for date: Date = Date()) -> Int {
        resetIfNeeded(for: date)
        return min(defaults.integer(forKey: Key.adsWatched), Self.requiredAds - 1)
    }

    /// Ads still needed today.
    func adsRemaining(for date: Date = Date()) -> Int {
        Self.requiredAds - adsWatched(for: date)
    }

    /// Records one rewarded ad. Returns `true` when this ad completed the
    /// chain — the caller then grants the day. Completing also clears the
    /// progress, so the next chain starts from zero.
    @discardableResult
    func recordAdWatched(for date: Date = Date()) -> Bool {
        resetIfNeeded(for: date)
        let watched = defaults.integer(forKey: Key.adsWatched) + 1
        guard watched >= Self.requiredAds else {
            defaults.set(watched, forKey: Key.adsWatched)
            return false
        }
        clearProgress()
        return true
    }

    /// Whether the apologetic first-open copy has already been shown. The
    /// sheet switches to its shorter neutral copy after that.
    var hasSeenIntro: Bool {
        defaults.bool(forKey: Key.introShown)
    }

    func markIntroSeen() {
        defaults.set(true, forKey: Key.introShown)
    }

    /// Debug/QA only: forgets both today's progress and the intro flag.
    func reset() {
        clearProgress()
        defaults.removeObject(forKey: Key.introShown)
    }

    private func clearProgress() {
        defaults.removeObject(forKey: Key.adsWatched)
        defaults.removeObject(forKey: Key.progressDay)
    }

    private func resetIfNeeded(for date: Date) {
        let today = dailyChallengeSeed(for: date)
        guard defaults.string(forKey: Key.progressDay) != today else { return }
        defaults.set(0, forKey: Key.adsWatched)
        defaults.set(today, forKey: Key.progressDay)
    }
}
