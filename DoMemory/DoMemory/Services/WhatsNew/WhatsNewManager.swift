//
//  WhatsNewManager.swift
//  DoMemory
//

import SwiftUI

/// Tracks whether the What's New sheet should be presented for the current app version.
///
/// On a brand-new install the current version is stored immediately so first-time users
/// never see the sheet. On updates (stored version ≠ running version) `shouldShow` starts
/// as `true`; calling `markSeen()` persists the new version and hides the sheet.
@MainActor
final class WhatsNewManager: ObservableObject {
    @Published var shouldShow: Bool = false

    private let defaults: UserDefaults

    /// - Parameter isExistingUser: whether this install has played before. A missing
    ///   stored version cannot tell us that on its own — someone upgrading from a build
    ///   that predates this manager has no stored version either, and they *should* see
    ///   the sheet. Onboarding state is what separates the two.
    init(defaults: UserDefaults = .standard, isExistingUser: Bool) {
        self.defaults = defaults

        // A first launch has nothing to announce: the "new" features are simply the
        // app. Record the running version so the sheet stays quiet until a real
        // upgrade happens, rather than greeting a brand-new player with release
        // notes for a version they have never not had.
        guard isExistingUser else {
            defaults.set(Self.currentVersion, forKey: UserDefaultsKeys.whatsNewLastSeenVersion)
            shouldShow = false
            return
        }

        let seen = defaults.string(forKey: UserDefaultsKeys.whatsNewLastSeenVersion)
        shouldShow = Self.currentVersion != seen
        if shouldShow, let version = Self.currentVersion {
            AnalyticsService.log(.whatsNewShown(version: version))
        }
    }

    /// Call this from the sheet's dismiss closure.
    func markSeen() {
        if let version = Self.currentVersion {
            AnalyticsService.log(.whatsNewDismissed(version: version))
        }
        defaults.set(Self.currentVersion, forKey: UserDefaultsKeys.whatsNewLastSeenVersion)
        shouldShow = false
    }

    private static var currentVersion: String? {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
    }

    #if DEBUG
    /// Resets the stored version so the sheet appears again on next launch.
    static func resetForTesting() {
        UserDefaults.standard.removeObject(forKey: UserDefaultsKeys.whatsNewLastSeenVersion)
    }
    #endif
}
