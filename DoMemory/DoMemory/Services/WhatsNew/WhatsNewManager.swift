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

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
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
