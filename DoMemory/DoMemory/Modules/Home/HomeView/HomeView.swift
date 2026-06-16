//
//  HomeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

struct HomeView: View {
    let onDidComplete: () -> Void

    @State private var showingIntro = !UserDefaults.standard.bool(forKey: UserDefaultsKeys.onboardingIntroShown)

    var body: some View {
        if showingIntro {
            FeatureIntroView { withAnimation { showingIntro = false } }
        } else {
            difficultyPicker
        }
    }

    private var difficultyPicker: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                VStack(spacing: 4) {
                    Text(Strings.appName)
                        .font(.righteous(size: 44))
                        .foregroundStyle(Color.primaryColor)

                    Text(Strings.askDifficulty)
                        .font(.patrickHand(size: 17))
                        .foregroundStyle(Color.textMuted)
                }
                .padding(.top, 48)
                .padding(.bottom, 28)

                // 2×2 tile grid
                VStack(spacing: 14) {
                    HStack(spacing: 14) {
                        DifficultyTile(
                            title: Strings.easy,
                            subtitle: Strings.easySubtitle,
                            accentColor: Color.easyGreen,
                            bgColor: Color.easyGreen.opacity(0.14),
                            systemImage: "leaf.fill"
                        ) { selectDifficulty(.easy) }

                        DifficultyTile(
                            title: Strings.medium,
                            subtitle: Strings.mediumSubtitle,
                            accentColor: Color.primaryColor,
                            bgColor: Color.primaryColor.opacity(0.14),
                            systemImage: "circle.grid.2x2.fill"
                        ) { selectDifficulty(.medium) }
                    }

                    HStack(spacing: 14) {
                        DifficultyTile(
                            title: Strings.hard,
                            subtitle: Strings.hardSubtitle,
                            accentColor: Color.hardAmber,
                            bgColor: Color.hardAmber.opacity(0.14),
                            systemImage: "flame.fill"
                        ) { selectDifficulty(.hard) }

                        DifficultyTile(
                            title: Strings.veryHard,
                            subtitle: Strings.veryHardSubtitle,
                            accentColor: Color.secundaryColor,
                            bgColor: Color.secundaryColor.opacity(0.14),
                            systemImage: "bolt.trianglebadge.exclamationmark.fill"
                        ) { selectDifficulty(.veryHard) }
                    }
                }
                .padding(.horizontal, 20)

                Spacer()
            }
        }
        .onAppear {
            AnalyticsService.log(.screenView(name: "home", screenClass: "HomeView"))
        }
    }

    private func selectDifficulty(_ difficulty: Difficulty) {
        AnalyticsService.log(.difficultySelected(difficulty: difficulty.rawValue))
        UserManageObject().createUserSettings(withDifficulty: difficulty)
        onDidComplete()
    }
}

private struct DifficultyTile: View {
    let title: String
    let subtitle: String
    let accentColor: Color
    let bgColor: Color
    let systemImage: String
    let action: () -> Void

    @State private var isPressed = false

    var body: some View {
        Button(action: trigger) {
            VStack(alignment: .leading, spacing: 12) {
                ZStack {
                    Circle()
                        .fill(accentColor.opacity(0.15))
                        .frame(width: 48, height: 48)
                    Image(systemName: systemImage)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(accentColor)
                }

                Spacer()

                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.system(size: 20, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Color.textMuted)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(bgColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.surfaceBorder, lineWidth: 1)
            )
            .shadow(color: Color.shadowColor.opacity(0.9), radius: 12, x: 0, y: 6)
            .scaleEffect(isPressed ? 0.97 : 1.0)
            .animation(.spring(response: 0.28, dampingFraction: 0.8), value: isPressed)
        }
        .buttonStyle(.plain)
        .onLongPressGesture(minimumDuration: .infinity, pressing: { pressing in
            withAnimation { isPressed = pressing }
        }, perform: {})
    }

    private func trigger() {
        #if os(iOS)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        #endif
        action()
    }
}

#Preview {
    HomeView(onDidComplete: {})
}
