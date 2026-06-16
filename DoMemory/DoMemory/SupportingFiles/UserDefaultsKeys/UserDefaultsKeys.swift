//
//  UserDefaultsKeys.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import Foundation

class UserDefaultsKeys: NSObject {
    static var dificulty: String = "dificulty"
    static var favoriteIDs: String = "favoriteIDs"
    static var customMemoramas: String = "customMemoramas"
    static var themePreference: String = "themePreference"
    static var reviewSuccessfulWins: String = "reviewSuccessfulWins"
    static var reviewLastPromptedVersion: String = "reviewLastPromptedVersion"
    static var reviewLastPromptedDate: String = "reviewLastPromptedDate"
    static var notificationsEnabled: String = "notificationsEnabled"
    static var whatsNewLastSeenVersion: String = "whatsNewLastSeenVersion"
    static var onboardingIntroShown: String = "onboardingIntroShown"
    // Daily-challenge keys live in DailyChallengeKeys (shared with the widget target).
}
