//
//  ContentView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WhatsNewKit

struct ContentView: View {
    @State private var hasOnboarded: Bool = ContentView.hasPlayedBefore
    @StateObject private var whatsNewManager = WhatsNewManager(isExistingUser: ContentView.hasPlayedBefore)
    @State private var showLaunch = true

    /// A stored `UserSettings` record is the app's own marker for "this install has
    /// been used before" — it is written when onboarding completes and never removed.
    private static var hasPlayedBefore: Bool {
        UserManageObject().getUserSettings() != nil
    }

    var body: some View {
        ZStack {
            Group {
                if hasOnboarded {
                    MenuView()
                        .sheet(isPresented: $whatsNewManager.shouldShow) {
                            WhatsNewView(whatsNew: .current, theme: .doMemory) {
                                whatsNewManager.markSeen()
                            }
                        }
                        // The app-open ad rides `didBecomeActive`, which fires
                        // again once the ATT prompt is dismissed — right when
                        // the release announcement is on screen.
                        .onAppear {
                            AdsService.shared.setFullScreenAdsSuppressed(whatsNewManager.shouldShow)
                        }
                        .onChange(of: whatsNewManager.shouldShow) { _, isShowing in
                            AdsService.shared.setFullScreenAdsSuppressed(isShowing)
                        }
                } else {
                    HomeView(onDidComplete: { hasOnboarded = true })
                }
            }

            if showLaunch {
                LaunchScreenView()
                    .transition(.opacity)
                    .task {
                        try? await Task.sleep(for: .seconds(1.2))
                        withAnimation(.easeOut(duration: 0.4)) { showLaunch = false }
                    }
            }
        }
    }
}

#Preview {
    ContentView()
}
