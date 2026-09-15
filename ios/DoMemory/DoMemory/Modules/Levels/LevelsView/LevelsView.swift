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
    /// Whether the launch sequence has cleared the screen for the one-shot
    /// intro. The Menu holds this false while the ATT prompt, the What's New
    /// sheet or the notification primer still owns the screen — Levels is the
    /// landing tab, so without it the intro's cover races them. A `LevelsView`
    /// opened outside that sequence never has to wait.
    var canPresentIntro: Bool = true

    @State private var viewModel = LevelsViewModel()
    @State private var selectedLevel: Int?
    @State private var showIntro = false
    @State private var introSource = "auto"
    /// A refill playing over the header's hearts. Only a *gain* animates here:
    /// a loss already broke its heart on the lose screen.
    @State private var livesEffect: LivesRowEffect?
    /// Sparkle over the star chip while a credit that just landed counts up.
    @State private var starsCredited = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                presentIntroIfNeeded()
            }
            // The launch sequence normally settles *after* this view has
            // appeared, so the intro waits on the flag rather than on another
            // appearance that would never come.
            .onChange(of: canPresentIntro) { _, _ in
                presentIntroIfNeeded()
            }
            // The intro is a first-run surface the player is meant to read, so
            // it holds off the app-open ad for as long as it is up — the same
            // guard the What's New sheet and the notification primer use.
            .onChange(of: showIntro) { _, isShowing in
                AdsService.shared.setFullScreenAdsSuppressed(isShowing)
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

    /// Presents the one-shot intro once the launch sequence has cleared the
    /// screen. Also runs on every return from a level; the gate closes when the
    /// intro is dismissed, so this is a no-op from then on.
    private func presentIntroIfNeeded() {
        guard canPresentIntro, introGate.shouldPresent, !showIntro else { return }
        introSource = "auto"
        showIntro = true
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
                LivesRow(remaining: viewModel.livesRemaining, iconSize: 13, effect: livesEffect) {
                    livesEffect = nil
                }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color.surfacePrimary)
                            .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
                    )
                    .onChange(of: viewModel.livesRemaining) { previous, current in
                        if current > previous {
                            livesEffect = LivesRowEffect.forTransition(from: previous, to: current)
                        }
                    }

                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(Color.hardAmber)
                        .overlay {
                            if starsCredited && !reduceMotion {
                                LottieView(name: "star-sparkle", tint: Color.hardAmber) { starsCredited = false }
                                    .frame(width: 56, height: 56)
                                    .allowsHitTesting(false)
                            }
                        }
                        .onChange(of: viewModel.totalStars) { previous, current in
                            if current > previous { starsCredited = true }
                        }
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
