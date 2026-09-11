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

    /// Chooses between a season's light and dark artwork. `RemoteImage` is
    /// keyed on the URL, so flipping appearance swaps the picture, and both are
    /// cached after their first load.
    @Environment(\.colorScheme) private var colorScheme

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
                background: { background },
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
            // Without artwork the bar stays opaque: the system back button
            // floats over a transparent bar by default, and tiles scrolled
            // straight under it.
            //
            // With artwork it is hidden, so the image reaches the very top of
            // the screen instead of stopping at a band of flat colour. The cost
            // is the original problem coming back — tiles pass under the bar as
            // they scroll — which is the trade a full-bleed background makes.
            .toolbarBackground(Color.appBackground, for: .navigationBar)
            .toolbarBackground(hasBackgroundArtwork ? .hidden : .visible, for: .navigationBar)
            .onAppear {
                viewModel.refresh()
                viewModel.preloadLivesAd()
                AnalyticsService.log(.screenView(name: "season_levels", screenClass: "SeasonLevelsView"))
                AnalyticsService.log(.seasonLevelsEntered(seasonID: season.id))
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

    // MARK: - Background

    /// Whether this season has artwork for the current appearance. Drives the
    /// navigation bar too, not just the map, so a season without art keeps the
    /// opaque bar it has always had.
    private var hasBackgroundArtwork: Bool {
        season.backgroundArtworkURL(for: colorScheme) != nil
    }

    /// The season's Firebase-supplied artwork behind the map, or the flat app
    /// background when the season carries none — which is also what a bad URL,
    /// a transparent PNG and a dead network all degrade to.
    @ViewBuilder
    private var background: some View {
        if let url = season.backgroundArtworkURL(for: colorScheme) {
            ZStack {
                // Under the artwork, not merely before it: it also backs a PNG
                // with transparency and covers the moment before it loads.
                Color.appBackground

                // Filled to the screen without letting the oversized image
                // grow the stack around it: `Color.clear` fixes the size, the
                // overlay cannot enlarge it, and the clip takes the overflow.
                Color.clear
                    .overlay {
                        RemoteImage(url: url) { Color.clear }
                            .scaledToFill()
                    }
                    .clipped()
            }
        } else {
            Color.appBackground
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
                    .animation(.spring(response: 0.5, dampingFraction: 0.8), value: viewModel.clearedLevelCount)
            }
        }
        .frame(height: 8)
        .accessibilityElement()
        // Not the visual "7 / 20": VoiceOver reads that as two bare numbers with
        // no idea what they count.
        .accessibilityLabel(Strings.seasonProgressAccessibility(viewModel.clearedLevelCount, viewModel.levelCount))
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
