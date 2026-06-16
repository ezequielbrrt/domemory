//
//  ReviewRequestService.swift
//  DoMemory
//
//  Created by OpenAI on 07/05/26.
//

import Foundation
import StoreKit
import UIKit

@MainActor
final class ReviewRequestService {
    static let shared = ReviewRequestService()

    private let minimumSuccessfulWins = 3
    private let cooldownDays = 120
    private let calendar = Calendar.current
    private let defaults = UserDefaults.standard

    private init() {}

    func registerSuccessfulGameWin() {
        let successfulWins = defaults.integer(forKey: UserDefaultsKeys.reviewSuccessfulWins) + 1
        defaults.set(successfulWins, forKey: UserDefaultsKeys.reviewSuccessfulWins)

        guard successfulWins >= minimumSuccessfulWins else { return }
        guard isEligibleForPrompt() else { return }
        guard let scene = activeWindowScene() else { return }

        defaults.set(currentMarketingVersion(), forKey: UserDefaultsKeys.reviewLastPromptedVersion)
        defaults.set(Date(), forKey: UserDefaultsKeys.reviewLastPromptedDate)
        requestReview(in: scene)
    }

    private func isEligibleForPrompt() -> Bool {
        let currentVersion = currentMarketingVersion()
        let lastPromptedVersion = defaults.string(forKey: UserDefaultsKeys.reviewLastPromptedVersion)

        if lastPromptedVersion == currentVersion {
            return false
        }

        guard let lastPromptedDate = defaults.object(forKey: UserDefaultsKeys.reviewLastPromptedDate) as? Date else {
            return true
        }

        guard let nextEligibleDate = calendar.date(byAdding: .day, value: cooldownDays, to: lastPromptedDate) else {
            return true
        }

        return Date() >= nextEligibleDate
    }

    private func currentMarketingVersion() -> String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
    }

    private func activeWindowScene() -> UIWindowScene? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
    }

    private func requestReview(in scene: UIWindowScene) {
        if #available(iOS 18.0, *) {
            AppStore.requestReview(in: scene)
        } else {
            SKStoreReviewController.requestReview(in: scene)
        }
    }
}
