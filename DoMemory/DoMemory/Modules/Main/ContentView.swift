//
//  ContentView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WhatsNewKit

struct ContentView: View {
    @State private var hasOnboarded: Bool = UserManageObject().getUserSettings() != nil
    @StateObject private var whatsNewManager = WhatsNewManager()
    @State private var showLaunch = true

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
