//
//  NotificationPrimerContent.swift
//  DoMemory
//

import NotificationPermissionKit
import SwiftUI

// MARK: - App theme

extension NotificationPermissionTheme {
    /// The violet gradient reads the same in light and dark mode, so the screen
    /// keeps one identity while the rest of the app follows the system scheme.
    static let doMemory = NotificationPermissionTheme(
        backgroundTop: .make(75, 63, 200),
        backgroundBottom: .make(142, 129, 255),
        primaryText: .white,
        secondaryText: .white.opacity(0.86),
        accent: .white,
        primaryButtonBackground: .white,
        primaryButtonForeground: .make(56, 47, 150),
        previewTint: .make(56, 47, 150),
        previewSurface: .white.opacity(0.22)
    )
}

// MARK: - Primer copy

extension NotificationPermissionConfiguration {
    /// Context shown before the one-time system authorization alert.
    ///
    /// Every benefit maps to a reminder `NotificationService` actually schedules —
    /// the streak-at-risk nudge and the two inactivity tiers. Nothing here promises
    /// a notification the app does not send.
    static var doMemory: NotificationPermissionConfiguration {
        NotificationPermissionConfiguration(
            title: Strings.notificationPrimerTitle,
            message: Strings.notificationPrimerMessage,
            benefits: [
                .init(symbol: "flame.fill", text: Strings.notificationPrimerBenefitStreak),
                .init(symbol: "brain.head.profile", text: Strings.notificationPrimerBenefitInactive),
                .init(symbol: "hand.raised.fill", text: Strings.notificationPrimerBenefitNoSpam),
            ],
            preview: .init(
                appName: Strings.appName,
                title: Strings.notificationPrimerPreviewTitle,
                message: Strings.notificationPrimerPreviewMessage,
                symbol: "flame.fill"
            ),
            primaryButtonTitle: Strings.notificationPrimerEnable,
            secondaryButtonTitle: Strings.notificationPrimerLater,
            deniedTitle: Strings.settingsNotificationsDeniedTitle,
            deniedMessage: Strings.settingsNotificationsDeniedMessage,
            settingsButtonTitle: Strings.settingsNotificationsOpenSettings
        )
    }
}

// MARK: - Presentation

/// The primer screen, wired to the app's reminder scheduling.
///
/// `onFinished` fires for every outcome so the caller can dismiss; the grant
/// itself is applied here so no call site can forget to arm scheduling.
struct NotificationPrimerView: View {
    let source: String
    let onFinished: () -> Void

    var body: some View {
        NotificationPermissionView(
            configuration: .doMemory,
            theme: .doMemory
        ) { result in
            if case .authorized = result {
                NotificationService.shared.activateReminders()
            }
            AnalyticsService.log(.notificationPrimerCompleted(source: source, outcome: result.analyticsOutcome))
            onFinished()
        }
    }
}

private extension NotificationPermissionResult {
    var analyticsOutcome: String {
        switch self {
        case .authorized: return "authorized"
        case .denied: return "denied"
        case .deferred: return "deferred"
        case .failed: return "failed"
        }
    }
}

#Preview("Notification Primer") {
    NotificationPrimerView(source: "preview", onFinished: {})
}
