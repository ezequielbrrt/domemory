//
//  SeasonLevelsView.swift
//  DoMemory
//
//  A season's level map. The grid is the shared LevelMapView; only the header
//  differs from endless Levels — the season's title and countdown, its
//  progress through a finite `levelCount`, and the completion state once every
//  level has been cleared.
//

import SwiftUI

struct SeasonLevelsView: View {
    let season: Season

    @State private var viewModel: SeasonLevelsViewModel
    @State private var selectedLevel: Int?

    init(season: Season) {
        self.season = season
        _viewModel = State(initialValue: SeasonLevelsViewModel(season: season))
    }

    var body: some View {
        ZStack {
            LevelMapView(
                tiles: viewModel.tiles,
                onSelect: { tile in
                    guard viewModel.hasLivesRemaining else {
                        // Refusal, not a selection — the modal that follows is
                        // bad news.
                        HapticsService.shared.fire(.warning)
                        AnalyticsService.log(.levelOutOfLivesShown(source: "season_tile"))
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
                    viewModel: MemorizeViewModel(context: viewModel.context(for: level)),
                    gameStartSource: "season_levels"
                )
                .onDisappear {
                    viewModel.refresh()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            // The system back button floats over a transparent bar by default,
            // so tiles scrolled straight under it.
            .toolbarBackground(Color.appBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .onAppear {
                viewModel.refresh()
                viewModel.preloadLivesAd()
                AnalyticsService.log(.screenView(name: "season_levels", screenClass: "SeasonLevelsView"))
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

    // MARK: - Header

    private var header: some View {
        VStack(spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(season.displayIcon)
                            .font(.system(size: 24))
                        Text(season.displayTitle)
                            .font(.system(size: 20, weight: .heavy, design: .rounded))
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                    }

                    HStack(spacing: 8) {
                        Text(Strings.seasonProgressFormat(viewModel.clearedLevelCount, viewModel.levelCount))
                            .font(.system(size: 13, weight: .heavy, design: .rounded))
                            .foregroundStyle(season.accent)

                        if let days = viewModel.daysRemaining, !viewModel.isComplete {
                            Text(Strings.seasonDaysLeft(days))
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundStyle(Color.textMuted)
                        }
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 6) {
                    // One daily budget and one wallet across endless Levels and
                    // every season, so both counters belong here too — losing a
                    // life in a season must not look like it came from nowhere.
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
                        Text("\(viewModel.starBalance)")
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

            progressBar

            if viewModel.isComplete {
                completionBanner
            }
        }
        .padding(.horizontal, 16)
    }

    private var progressBar: some View {
        GeometryReader { proxy in
            let fraction = viewModel.levelCount > 0
                ? Double(viewModel.clearedLevelCount) / Double(viewModel.levelCount)
                : 0

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.surfaceSecondary)
                Capsule()
                    .fill(season.accent)
                    .frame(width: max(proxy.size.width * fraction, fraction > 0 ? 8 : 0))
            }
        }
        .frame(height: 8)
        .accessibilityElement()
        .accessibilityLabel(Strings.seasonProgressFormat(viewModel.clearedLevelCount, viewModel.levelCount))
    }

    /// A cleared season needs somewhere to land. Without this the map is just
    /// a wall of finished tiles with no acknowledgement that it is over.
    private var completionBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(season.accent)

            VStack(alignment: .leading, spacing: 3) {
                Text(Strings.seasonCompleteTitle)
                    .font(.system(size: 16, weight: .heavy, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                Text(Strings.seasonCompleteMessage)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(season.accent.opacity(0.12))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(season.accent.opacity(0.35), lineWidth: 1)
                )
        )
    }
}
