//
//  LevelsIntroGate.swift
//  DoMemory
//
//  Tracks whether the Levels feature intro has been shown yet. Kept out of the
//  view so the one-shot rule is testable, mirroring `WhatsNewManager`'s shape.
//

import Foundation

struct LevelsIntroGate {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// True until the player has dismissed the intro at least once.
    var shouldPresent: Bool {
        !defaults.bool(forKey: UserDefaultsKeys.levelsIntroShown)
    }

    /// Call this when the intro is dismissed, not when it is presented — a kill
    /// mid-intro should leave the player eligible to see it again.
    func markSeen() {
        defaults.set(true, forKey: UserDefaultsKeys.levelsIntroShown)
    }
}
