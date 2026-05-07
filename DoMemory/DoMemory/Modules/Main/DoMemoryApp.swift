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
        }
    }
}
