//
//  ContentView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import AppTrackingTransparency
import GoogleMobileAds

struct ContentView: View {
    @State private var hasOnboarded: Bool = UserManageObject().getUserSettings() != nil

    var body: some View {
        Group {
            if hasOnboarded {
                MenuView()
            } else {
                HomeView(onDidComplete: { hasOnboarded = true })
            }
        }
        .task {
            // Wait until the app is fully active before requesting permissions.
            // ATTrackingManager requires the window to be ready to present the system dialog;
            // calling it immediately in .task (before the scene is active) silently drops the prompt.
            if UIApplication.shared.applicationState != .active {
                for await _ in NotificationCenter.default
                    .notifications(named: UIApplication.didBecomeActiveNotification)
                    .prefix(1) {}
            }
            try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            await ATTrackingManager.requestTrackingAuthorization()
            // Start AdMob after ATT is resolved so Google correctly marks requests
            // as personalized or non-personalized based on the user's choice.
            await MobileAds.shared.start()
        }
    }
}

#Preview {
    ContentView()
}
