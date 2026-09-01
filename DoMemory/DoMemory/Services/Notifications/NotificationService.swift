//
//  NotificationService.swift
//  DoMemory
//

import Foundation
import UserNotifications

final class NotificationService {
    static let shared = NotificationService()

    private let center = UNUserNotificationCenter.current()

    // Inactivity reminders, scheduled in tiers so a user who ignores the first
    // nudge still gets a later one.
    private let inactivityDay2ID = "com.ezequielbrrt.domemory.inactivity_reminder"
    private let inactivityDay7ID = "com.ezequielbrrt.domemory.inactivity_reminder.day7"
    private let streakRiskID = "com.ezequielbrrt.domemory.streak_at_risk"
    private let streakRiskHour = 20

    private var inactivityTiers: [(id: String, days: Int)] {
        [(inactivityDay2ID, 2), (inactivityDay7ID, 7)]
    }

    private var allReminderIDs: [String] {
        [inactivityDay2ID, inactivityDay7ID, streakRiskID]
    }

    private var notificationsEnabled: Bool {
        UserDefaults.standard.bool(forKey: UserDefaultsKeys.notificationsEnabled)
    }

    private init() {}

    func requestPermission() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    /// Turns reminders on and arms every scheduled nudge.
    ///
    /// Granting system authorization is not enough on its own: every scheduling
    /// method early-returns on `notificationsEnabled`, so a grant that skips this
    /// leaves the user authorized but silently un-reminded. Call this from any
    /// path that obtains authorization.
    @MainActor
    func activateReminders() {
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.notificationsEnabled)
        scheduleInactivityReminder()
        refreshStreakAtRiskReminder()
    }

    func scheduleInactivityReminder() {
        center.removePendingNotificationRequests(withIdentifiers: [inactivityDay2ID, inactivityDay7ID])

        guard notificationsEnabled else { return }

        for tier in inactivityTiers {
            let content = UNMutableNotificationContent()
            content.title = NSLocalizedString("notification_reminder_title", comment: "Reminder notification title")
            content.body = NSLocalizedString("notification_reminder_body", comment: "Reminder notification body")
            content.sound = .default

            guard
                let fireDate = Calendar.current.date(byAdding: .day, value: tier.days, to: Date()),
                let fireAt7pm = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: fireDate)
            else { continue }

            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireAt7pm)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            center.add(UNNotificationRequest(identifier: tier.id, content: content, trigger: trigger))
        }
    }

    /// Schedules (or cancels) an end-of-day nudge when the user has an active
    /// daily-challenge streak they haven't kept alive today. Re-arm on launch /
    /// foreground and after every daily completion.
    func refreshStreakAtRiskReminder() {
        center.removePendingNotificationRequests(withIdentifiers: [streakRiskID])

        guard notificationsEnabled else { return }

        let streak = DailyChallengeService.shared.currentStreak
        guard streak > 0, !DailyChallengeService.shared.isCompletedToday() else { return }

        guard
            let fireDate = Calendar.current.date(bySettingHour: streakRiskHour, minute: 0, second: 0, of: Date()),
            fireDate > Date()
        else { return }

        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("notification_streak_risk_title", comment: "Streak-at-risk notification title")
        content.body = String(
            format: NSLocalizedString("notification_streak_risk_body", comment: "Streak-at-risk notification body, %d = streak days"),
            streak
        )
        content.sound = .default

        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        center.add(UNNotificationRequest(identifier: streakRiskID, content: content, trigger: trigger))
    }

    func cancelAll() {
        center.removePendingNotificationRequests(withIdentifiers: allReminderIDs)
    }

    func hasPendingReminder() async -> Bool {
        let pending = await center.pendingNotificationRequests()
        return pending.contains { $0.identifier == inactivityDay2ID }
    }

    @MainActor
    func syncAuthorizationStatus() async {
        let settings = await center.notificationSettings()
        let isEnabled = UserDefaults.standard.bool(forKey: UserDefaultsKeys.notificationsEnabled)
        if isEnabled && settings.authorizationStatus == .denied {
            UserDefaults.standard.set(false, forKey: UserDefaultsKeys.notificationsEnabled)
            cancelAll()
        }
    }
}
