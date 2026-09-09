//
//  LevelsView.swift
//  DoMemory
//
//  Endless procedural level map. The grid and its tiles live in the shared
//  LevelMapView; this screen supplies the stars/lives header, the lazy paging
//  and the out-of-lives refusal.
//

import SwiftUI

struct LevelsView: View {
    @State private var viewModel = LevelsViewModel()
    @State private var selectedLevel: Int?
    @State private var showIntro = false
    @State private var introSource = "auto"

    private let introGate = LevelsIntroGate()

    var body: some View {
        ZStack {
            LevelMapView(
                tiles: viewModel.tiles,
                onTileAppear: { viewModel.extendIfNeeded(nearing: $0) },
                onSelect: { tile in
                    guard viewModel.hasLivesRemaining else {
                        // Refusal, not a selection — the modal
                        // that follows is bad news.
                        HapticsService.shared.fire(.warning)
                        AnalyticsService.log(.levelOutOfLivesShown(source: "level_tile"))
                        viewModel.showOutOfLivesPrompt = true
                        return
                    }
                    HapticsService.shared.fire(.select)
                    selectedLevel = tile.level
                },
                header: { header }
            )
            .navigationDestination(item: $selectedLevel) { level in
                MemorizeView(
                    viewModel: MemorizeViewModel(level: level),
                    gameStartSource: "levels_tab"
                )
                .onDisappear {
                    viewModel.refresh()
                }
            }
            .fullScreenCover(isPresented: $showIntro) {
                LevelsIntroView(source: introSource) { showIntro = false }
            }
            .onAppear {
                viewModel.refresh()
                viewModel.preloadLivesAd()
                AnalyticsService.log(.screenView(name: "levels", screenClass: "LevelsView"))
                // Also fires on every return from a level; the gate closes once
                // the intro is dismissed, so this is a no-op from then on.
                if introGate.shouldPresent {
                    introSource = "auto"
                    showIntro = true
                }
            }

            if viewModel.showOutOfLivesPrompt {
                OutOfLivesModal(
                    canWatchAd: viewModel.canWatchAdForLife,
                    isAdInProgress: viewModel.isWatchingLivesAd,
                    canBuyWithStars: viewModel.canBuyLifeWithStars,
                    onWatchAd: { viewModel.watchAdForLife() },
                    onBuyWithStars: { viewModel.buyLifeWithStars() },
                    onDismiss: { viewModel.showOutOfLivesPrompt = false }
                )
            }
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(Strings.levelsScreenTitle)
                        .font(.system(size: 20, weight: .heavy, design: .rounded))
                        .foregroundStyle(Color.textPrimary)

                    Button {
                        HapticsService.shared.fire(.tap)
                        introSource = "info_button"
                        showIntro = true
                    } label: {
                        Image(systemName: "questionmark.circle")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color.textMuted)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Strings.levelsIntroInfoAccessibility)
                }
                Text(Strings.levelsCurrentLevelFormat(viewModel.currentLevel))
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 6) {
                // Shown to everyone, Remove-Ads purchasers included: the
                // daily budget applies to them too, so hiding the counter
                // would mean losing a life with no visible cause.
                LivesRow(remaining: viewModel.livesRemaining, iconSize: 13)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color.surfacePrimary)
                            .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
                    )

                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(Color.hardAmber)
                    Text("\(viewModel.totalStars)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textPrimary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(Color.surfacePrimary)
                        .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
                )
            }
        }
        .padding(.horizontal, 16)
    }
}

#Preview {
    LevelsView()
}
