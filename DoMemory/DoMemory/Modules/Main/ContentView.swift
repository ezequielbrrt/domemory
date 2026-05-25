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

    var body: some View {
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
    }
}

#Preview {
    ContentView()
}
