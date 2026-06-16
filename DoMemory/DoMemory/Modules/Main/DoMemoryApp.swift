//
//  DoMemoryApp.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

@main
struct DoMemoryApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @AppStorage(UserDefaultsKeys.themePreference) private var themePreference = AppTheme.system.rawValue

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(AppTheme(rawValue: themePreference)?.colorScheme)
                .task {
                    await NotificationService.shared.syncAuthorizationStatus()
                    guard UserDefaults.standard.bool(forKey: UserDefaultsKeys.notificationsEnabled) else { return }
                    let hasPending = await NotificationService.shared.hasPendingReminder()
                    if !hasPending {
                        NotificationService.shared.scheduleInactivityReminder()
                    }
                    NotificationService.shared.refreshStreakAtRiskReminder()
                }
                .onOpenURL { url in
                    DeepLinkRouter.shared.handle(url: url)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        DeepLinkRouter.shared.handle(url: url)
                    }
                }
        }
    }
}
