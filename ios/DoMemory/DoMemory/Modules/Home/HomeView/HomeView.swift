//
//  HomeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

struct HomeView: View {
    let onDidComplete: () -> Void

    var body: some View {
        FeatureIntroView(onFinish: completeOnboarding)
    }

    private func completeOnboarding() {
        UserManageObject().createUserSettings(withDifficulty: .medium)
        onDidComplete()
    }
}

#Preview {
    HomeView(onDidComplete: {})
}
