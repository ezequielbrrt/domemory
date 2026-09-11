//
//  AppReviews.swift
//  DoMemory
//

import Foundation
import ReviewFlow

/// App-wide holder for the single `ReviewManager`.
///
/// `reviewRequest(using:)` and `recordSuccessfulAction(appVersion:)` must run
/// against the same instance, and the recording site sits deep inside
/// `MemorizeViewModel`. A shared holder keeps that reachable without threading
/// the manager through every view, matching how the app's other services work.
@MainActor
enum AppReviews {
    static let manager: ReviewManager = {
        ReviewHistoryMigration.runIfNeeded()
        return ReviewManager(configuration: .recommended)
    }()

    /// The running app version, as ReviewFlow's per-version policy expects it.
    static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
    }

    /// Records a genuine success. StoreKit decides whether a prompt appears.
    static func recordSuccessfulGameWin() {
        manager.recordSuccessfulAction(appVersion: appVersion)
    }
}

/// Carries the retired `ReviewRequestService` state into ReviewFlow's store.
///
/// Without this, an existing player who was already prompted on the current
/// version starts from an empty history and can be asked again as soon as they
/// hit three wins. The cooldown and per-version caps are the guards worth
/// preserving, so both are mapped across exactly.
@MainActor
enum ReviewHistoryMigration {
    /// `store` is optional rather than defaulted because the default value of a
    /// parameter is evaluated outside the actor, and the store is main-isolated.
    /// `defaults` is injectable so the migration can be exercised against a
    /// throwaway suite rather than the real user's state.
    static func runIfNeeded(
        defaults: UserDefaults = .standard,
        store: (any ReviewHistoryStore)? = nil,
        now: Date = .now
    ) {
        guard !defaults.bool(forKey: UserDefaultsKeys.reviewHistoryMigrated) else { return }
        defer { clearLegacyKeys(in: defaults) }

        let store = store ?? UserDefaultsReviewHistoryStore(defaults: defaults)

        // A store that already holds history wins; never overwrite live state.
        guard store.load() == nil else { return }

        let wins = defaults.integer(forKey: UserDefaultsKeys.reviewSuccessfulWins)
        let lastPromptedDate = defaults.object(forKey: UserDefaultsKeys.reviewLastPromptedDate) as? Date
        let lastPromptedVersion = defaults.string(forKey: UserDefaultsKeys.reviewLastPromptedVersion)

        // Nothing to carry over: a fresh install, so let ReviewFlow start clean.
        guard wins > 0 || lastPromptedDate != nil || lastPromptedVersion != nil else { return }

        // These players are by definition not new, so the "days since first use"
        // gate should not hold them back. Anchor it to the last prompt when we
        // have one, otherwise just far enough back to clear the default window.
        let firstUseDate = lastPromptedDate
            ?? Calendar.current.date(byAdding: .day, value: -30, to: now)
            ?? now

        var requestCountByVersion: [String: Int] = [:]
        if let lastPromptedVersion {
            requestCountByVersion[lastPromptedVersion] = 1
        }

        store.save(
            ReviewHistory(
                firstUseDate: firstUseDate,
                completedActionCount: wins,
                lastRequestDate: lastPromptedDate,
                requestCountByVersion: requestCountByVersion
            )
        )
    }

    private static func clearLegacyKeys(in defaults: UserDefaults) {
        defaults.set(true, forKey: UserDefaultsKeys.reviewHistoryMigrated)
        defaults.removeObject(forKey: UserDefaultsKeys.reviewSuccessfulWins)
        defaults.removeObject(forKey: UserDefaultsKeys.reviewLastPromptedVersion)
        defaults.removeObject(forKey: UserDefaultsKeys.reviewLastPromptedDate)
    }
}
