//
//  LevelsView.swift
//  DoMemory
//
//  Endless procedural level map: a scrollable grid of numbered tiles —
//  cleared (with stars, replayable), current (highlighted), and locked.
//

import SwiftUI

struct LevelsView: View {
    @State private var viewModel = LevelsViewModel()
    @State private var selectedLevel: Int?
    @State private var purchaseService = PurchaseService.shared
    @State private var showIntro = false
    @State private var introSource = "auto"

    private let introGate = LevelsIntroGate()

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 18), count: 4)

    var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 16) {
                    header

                    LazyVGrid(columns: columns, spacing: 22) {
                        ForEach(viewModel.tiles) { tile in
                            LevelTileView(tile: tile)
                                .frame(maxWidth: .infinity)
                                .onTapGesture {
                                    guard !tile.isLocked else { return }
                                    guard viewModel.hasLivesRemaining else {
                                        AnalyticsService.log(.levelOutOfLivesShown(source: "level_tile"))
                                        viewModel.showOutOfLivesPrompt = true
                                        return
                                    }
                                    selectedLevel = tile.level
                                }
                                .onAppear {
                                    viewModel.extendIfNeeded(nearing: tile)
                                }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 16)
                }
                .padding(.top, 8)
            }
            .background(Color.appBackground)
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
                if !purchaseService.hasRemovedAds {
                    LivesRow(remaining: viewModel.livesRemaining, iconSize: 13)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(Color.surfacePrimary)
                                .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
                        )
                }

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

private struct LevelTileView: View {
    let tile: LevelsViewModel.LevelTile

    private let nodeSize: CGFloat = 64

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(fillColor)
                    .overlay(
                        Circle().stroke(borderColor, lineWidth: tile.isCurrent ? 3 : 1.5)
                    )
                    .shadow(color: Color.shadowColor, radius: tile.isLocked ? 0 : 8, x: 0, y: 4)

                content
            }
            .frame(width: nodeSize, height: nodeSize)

            starsRow
        }
        .opacity(tile.isLocked ? 0.55 : 1)
    }

    @ViewBuilder
    private var content: some View {
        switch tile.state {
        case .locked:
            Image(systemName: "lock.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.textMuted)

        case .current:
            Text("\(tile.level)")
                .font(.system(size: 26, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.6)
                .lineLimit(1)

        case .cleared:
            Text("\(tile.level)")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Color.textPrimary)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
    }

    /// Reserves a consistent row height across every tile so cleared tiles'
    /// star ratings don't shift the grid's row spacing.
    @ViewBuilder
    private var starsRow: some View {
        if case .cleared(let stars) = tile.state {
            HStack(spacing: 2) {
                ForEach(0..<3, id: \.self) { index in
                    Image(systemName: index < stars ? "star.fill" : "star")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(index < stars ? Color.hardAmber : Color.textMuted.opacity(0.3))
                }
            }
            .frame(height: 14)
        } else {
            Color.clear.frame(height: 14)
        }
    }

    private var fillColor: Color {
        switch tile.state {
        case .current: return Color.primaryColor
        case .cleared: return Color.surfacePrimary
        case .locked: return Color.surfaceSecondary
        }
    }

    private var borderColor: Color {
        switch tile.state {
        case .current: return Color.primaryColor
        case .cleared, .locked: return Color.surfaceBorder
        }
    }
}

#Preview {
    LevelsView()
}
