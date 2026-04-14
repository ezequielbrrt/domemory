//
//  ContentView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

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
    }
}

#Preview {
    ContentView()
}
