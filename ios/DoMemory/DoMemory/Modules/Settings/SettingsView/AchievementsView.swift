//
//  AchievementsView.swift
//  DoMemory
//

import SwiftUI

struct AchievementsView: View {
    private let stats = ProfileStatsService.shared.stats
    private let achievements = ProfileStatsService.shared.achievements()

    private var winRateText: String {
        "\(Int((stats.winRate * 100).rounded()))%"
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(Strings.achievementsStatsSection)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textMuted)

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 2), spacing: 12) {
                        StatCard(value: "\(stats.totalPlayed)", label: Strings.statTotalGames, color: Color.primaryColor)
                        StatCard(value: "\(stats.totalWon)", label: Strings.statTotalWins, color: Color.easyGreen)
                        StatCard(value: winRateText, label: Strings.statWinRate, color: Color.primaryColor)
                        StatCard(value: "\(stats.perfectGames)", label: Strings.statPerfectGames, color: Color.secundaryColor)
                        StatCard(value: "\(stats.longestStreak)", label: Strings.statLongestStreak, color: Color.hardAmber)
                        StatCard(value: "\(stats.multiplayerWins)", label: Strings.statMultiplayerWins, color: Color.primaryColor)
                    }

                    Text(Strings.achievementsBadgesSection)
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                        .padding(.top, 4)

                    VStack(spacing: 10) {
                        ForEach(achievements) { achievement in
                            AchievementRow(achievement: achievement)
                        }
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle(Strings.achievementsTitle)
        .navigationBarTitleDisplayMode(.inline)
        // Same reason as SettingsView: without an opaque bar the stat cards
        // scroll straight under the title and the floating back button.
        .toolbarBackground(Color.appBackground, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .onAppear {
            AnalyticsService.log(.screenView(name: "achievements", screenClass: "AchievementsView"))
        }
    }
}

private struct StatCard: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            Text(value)
                .font(.system(size: 28, weight: .heavy, design: .rounded))
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.textMuted)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 8, x: 0, y: 3)
        )
    }
}

private struct AchievementRow: View {
    let achievement: Achievement

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(achievement.isUnlocked ? Color.primaryColor.opacity(0.15) : Color.textMuted.opacity(0.12))
                    .frame(width: 46, height: 46)
                Image(systemName: achievement.isUnlocked ? achievement.symbol : "lock.fill")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(achievement.isUnlocked ? Color.primaryColor : Color.textMuted)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(achievement.title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(achievement.isUnlocked ? Color.textPrimary : Color.textMuted)
                Text(achievement.detail)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)

                if !achievement.isUnlocked, achievement.progress > 0 {
                    ProgressView(value: achievement.progress)
                        .tint(Color.primaryColor)
                        .padding(.top, 2)
                }
            }

            Spacer()

            if achievement.isUnlocked {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.easyGreen)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 2)
        )
        .opacity(achievement.isUnlocked ? 1 : 0.7)
    }
}

#Preview {
    NavigationStack {
        AchievementsView()
    }
}
