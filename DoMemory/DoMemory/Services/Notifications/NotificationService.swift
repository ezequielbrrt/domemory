//
//  NotificationService.swift
//  DoMemory
//

import Foundation
import UserNotifications

final class NotificationService {
    static let shared = NotificationService()

    private let center = UNUserNotificationCenter.current()
    private let requestID = "com.ezequielbrrt.domemory.inactivity_reminder"
    private let inactivityDays = 2

    private init() {}

    func requestPermission() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    func scheduleInactivityReminder() {
        center.removePendingNotificationRequests(withIdentifiers: [requestID])

        guard UserDefaults.standard.bool(forKey: UserDefaultsKeys.notificationsEnabled) else { return }

        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("notification_reminder_title", comment: "Reminder notification title")
        content.body = NSLocalizedString("notification_reminder_body", comment: "Reminder notification body")
        content.sound = .default

        guard
            let fireDate = Calendar.current.date(byAdding: .day, value: inactivityDays, to: Date()),
            let fireAt7pm = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: fireDate)
        else { return }

        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireAt7pm)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        let request = UNNotificationRequest(identifier: requestID, content: content, trigger: trigger)
        center.add(request)
    }

    func cancelAll() {
        center.removePendingNotificationRequests(withIdentifiers: [requestID])
    }

    func hasPendingReminder() async -> Bool {
        let pending = await center.pendingNotificationRequests()
        return pending.contains { $0.identifier == requestID }
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
