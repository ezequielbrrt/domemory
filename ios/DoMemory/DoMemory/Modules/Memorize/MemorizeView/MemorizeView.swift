//
//  MemorizeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct MemorizeView: View {
    @State var viewModel: MemorizeViewModel
    let gameStartSource: String
    @State private var hasPreparedGame = false
    @State private var purchaseService = PurchaseService.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The ice-shatter burst over the timer chip when a Freeze runs out. The
    /// clock restarting is otherwise easy to miss — the number just starts
    /// moving again — which is why a haptic already marks the moment.
    @State private var showThaw = false

    init(viewModel: MemorizeViewModel, gameStartSource: String = "initial") {
        self._viewModel = State(initialValue: viewModel)
        self.gameStartSource = gameStartSource
    }

    private var gridColumns: Int {
        max(1, Int(ceil(sqrt(Double(viewModel.cards.count)))))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // HUD bar
                HStack(spacing: 12) {
                    // Quit button
                    Button {
                        viewModel.tapOnQuitPrompt()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.textPrimary.opacity(0.6))
                            .frame(width: 40, height: 40)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Color.surfacePrimary)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(Color.surfaceBorder, lineWidth: 1)
                                    )
                                    .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                            )
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    // Fails chip — shows the budget in Levels mode, plain count elsewhere
                    HStack(spacing: 5) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(Color.secundaryColor)
                            .font(.system(size: 14))
                        if let maxFailures = viewModel.maxFailures {
                            Text("\(viewModel.failedTries)/\(maxFailures)")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(viewModel.isNearFailureLimit ? Color.secundaryColor : Color.textPrimary)
                                .accessibilityLabel(Strings.mistakesRemainingFormat(viewModel.failedTries, maxFailures))
                        } else {
                            Text("\(viewModel.failedTries)")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(Color.textPrimary)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        Capsule()
                            .fill(viewModel.isNearFailureLimit ? Color.secundaryColor.opacity(0.12) : Color.surfacePrimary)
                            .overlay(
                                Capsule()
                                    .stroke(viewModel.isNearFailureLimit ? Color.secundaryColor.opacity(0.5) : Color.surfaceBorder, lineWidth: 1)
                            )
                            .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                    )

                    // Timer chip — turns frosty while the Freeze power-up holds
                    // the countdown, which otherwise looks like a stalled clock.
                    HStack(spacing: 5) {
                        Image(systemName: viewModel.isFrozen ? "snowflake" : "timer")
                            .foregroundStyle(viewModel.isFrozen ? Color.freezeBlue : Color.primaryColor)
                            .font(.system(size: 14))
                        // Labelled only while frozen, matching the fails chip:
                        // an explicit label here would otherwise replace the
                        // icon-plus-number VoiceOver reads by default with a
                        // bare, contextless number.
                        if viewModel.isFrozen {
                            Text("\(viewModel.timeRemaining)")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(Color.freezeBlue)
                                .accessibilityLabel(Strings.timerFrozenFormat(viewModel.timeRemaining))
                        } else {
                            Text("\(viewModel.timeRemaining)")
                                .font(.system(size: 16, weight: .bold, design: .rounded))
                                .foregroundStyle(Color.textPrimary)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        Capsule()
                            .fill(viewModel.isFrozen ? Color.freezeBlue.opacity(0.12) : Color.surfacePrimary)
                            .overlay(
                                Capsule()
                                    .stroke(viewModel.isFrozen ? Color.freezeBlue.opacity(0.5) : Color.surfaceBorder, lineWidth: 1)
                            )
                            .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                    )
                    .accessibilityLabel(viewModel.isFrozen
                        ? Strings.timerFrozenFormat(viewModel.timeRemaining)
                        : "\(viewModel.timeRemaining)")
                    .animation(.easeInOut(duration: 0.2), value: viewModel.isFrozen)
                    .overlay {
                        if showThaw {
                            LottieView(name: "freeze-thaw", tint: Color.freezeBlue) { showThaw = false }
                                .frame(width: 96, height: 96)
                                .allowsHitTesting(false)
                                .accessibilityHidden(true)
                        }
                    }
                    .onChange(of: viewModel.isFrozen) { wasFrozen, isFrozen in
                        if wasFrozen && !isFrozen && !reduceMotion { showThaw = true }
                    }

                    Spacer()

                    // Pause button
                    Button {
                        viewModel.tapOnPause()
                    } label: {
                        Image(systemName: "pause.fill")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.primaryColor)
                            .frame(width: 40, height: 40)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Color.surfacePrimary)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(Color.surfaceBorder, lineWidth: 1)
                                    )
                                    .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                            )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)

                if viewModel.canUsePowerUps {
                    PowerUpBar(balance: viewModel.starBalance) { powerUp in
                        viewModel.use(powerUp)
                    }
                }

                GeometryReader { geo in
                    let layout = BoardLayout.make(
                        cardCount: viewModel.cards.count,
                        in: geo.size,
                        phoneColumns: gridColumns,
                        adaptsShape: BoardLayout.adaptsToWindowShape
                    )
                    LazyVGrid(
                        columns: Array(repeating: GridItem(.fixed(layout.cardSize.width)), count: layout.columns),
                        spacing: BoardLayout.spacing
                    ) {
                        ForEach(viewModel.cards) { card in
                            CardView(card: card, shouldShowPie: viewModel.shouldShowPie)
                                .frame(width: layout.cardSize.width, height: layout.cardSize.height)
                                .onTapGesture {
                                    withAnimation(.linear(duration: 1)) {
                                        viewModel.choose(card: card)
                                        viewModel.getIfAllAreMatched()
                                    }
                                }
                        }
                    }
                    .padding(BoardLayout.padding)
                    .frame(width: geo.size.width, height: geo.size.height)
                }

                if !purchaseService.hasRemovedAds,
                   AdsService.shared.isBannerConfigured(for: .gameBanner) {
                    AdMobBannerView(placement: .gameBanner)
                        .frame(height: 50)
                        .padding(.bottom, 8)
                }
            }

            // MODALS
            if viewModel.showPauseView {
                PauseModal(listener: viewModel)
            }

            if viewModel.hasLost {
                LoseModal(listener: viewModel)
            }

            if viewModel.showQuitView {
                QuitModal(listener: viewModel)
                    .onDisappear {
                        if viewModel.closeView {
                            dismiss()
                        } else {
                            viewModel.startTimer()
                        }
                    }
            }

            if viewModel.showWinView {
                WinModal(
                    listener: viewModel,
                    pairsCount: viewModel.cards.count / 2,
                    timeRemaining: viewModel.timeRemaining,
                    failedTries: viewModel.failedTries,
                    difficultyTitle: viewModel.difficultyDisplayTitle,
                    isDailyChallenge: viewModel.isDailyChallenge,
                    streak: viewModel.dailyChallengeStreak,
                    levelNumber: viewModel.levelNumber,
                    starsEarned: viewModel.lastEarnedStars,
                    hasNextLevel: viewModel.hasNextLevel
                )
                    .onAppear {
                        viewModel.stopTimer()
                    }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            AnalyticsService.log(.screenView(name: "gameplay", screenClass: "MemorizeView"))
            guard !hasPreparedGame else { return }
            hasPreparedGame = true
            viewModel.resetGame()
            viewModel.timeRemaining = viewModel.getRemainingTime()
            viewModel.startTimer()
            viewModel.trackGameStarted(source: gameStartSource)
        }
        .onDisappear {
            viewModel.stopTimer()
        }
        .onChange(of: viewModel.closeView) {
            if viewModel.closeView {
                dismiss()
            }
        }
    }
}

/// How a board's cards are arranged in the space the screen gives them.
///
/// iPhone keeps the rule the board has always used — `phoneColumns` columns,
/// each card stretched to fill its cell — so phone boards, and their Android
/// twins, are unchanged. An iPad window can be any shape, from a third-width
/// Split View column to a landscape 13-inch screen, and that rule turns cards
/// into slivers or wide tiles there. So on iPad the column count is the one
/// that fits the largest card of a fixed playing-card shape, capped so a
/// small board doesn't grow to poster size; the caller centres the grid in
/// whatever space the cap leaves.
struct BoardLayout: Equatable {
    let columns: Int
    let cardSize: CGSize

    static let spacing: CGFloat = 10
    static let padding: CGFloat = 16
    /// Width over height of an iPad card: a playing card's proportions.
    static let cardAspect: CGFloat = 0.72
    static let maxCardWidth: CGFloat = 200

    static var adaptsToWindowShape: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    static func make(cardCount: Int, in size: CGSize, phoneColumns: Int, adaptsShape: Bool) -> BoardLayout {
        let count = max(1, cardCount)
        // `size` can briefly be smaller than the padding/spacing budget while
        // the screen transitions in or a Split View divider moves, which would
        // otherwise drive the card size negative for a frame.
        let width = max(0, size.width - padding * 2)
        let height = max(0, size.height - padding * 2)

        guard adaptsShape else {
            let columns = max(1, phoneColumns)
            return BoardLayout(columns: columns, cardSize: cell(columns: columns, count: count, width: width, height: height))
        }

        let windowShape = max(width, 1) / max(height, 1)
        var best = BoardLayout(columns: 1, cardSize: .zero)
        var bestGaps = Int.max
        var bestMismatch = CGFloat.infinity
        for columns in 1...count {
            let cell = cell(columns: columns, count: count, width: width, height: height)
            let cardWidth = min(cell.width, cell.height * cardAspect, maxCardWidth)
            let card = CGSize(width: cardWidth, height: cardWidth / cardAspect)
            let rows = Int(ceil(Double(count) / Double(columns)))
            let gridWidth = CGFloat(columns) * card.width + spacing * CGFloat(columns - 1)
            let gridHeight = CGFloat(rows) * card.height + spacing * CGFloat(rows - 1)
            // Two tie-breakers, used only once several column counts give the
            // same card (typically because they all reach the size cap): first
            // the fewest empty slots in the last row, then the grid whose shape
            // is closest to the window's — so 6 capped cards sit 3 × 2 in a
            // landscape window and 2 × 3 in a portrait one, never 4 + 2.
            let gaps = columns * rows - count
            let mismatch = abs(log(max(gridWidth, 1) / max(gridHeight, 1) / windowShape))
            let isLarger = cardWidth > best.cardSize.width + 0.5
            let isAsLarge = abs(cardWidth - best.cardSize.width) <= 0.5
            let isTidier = gaps < bestGaps || (gaps == bestGaps && mismatch < bestMismatch)
            if isLarger || (isAsLarge && isTidier) {
                best = BoardLayout(columns: columns, cardSize: card)
                bestGaps = gaps
                bestMismatch = mismatch
            }
        }
        return best
    }

    private static func cell(columns: Int, count: Int, width: CGFloat, height: CGFloat) -> CGSize {
        let rows = Int(ceil(Double(count) / Double(columns)))
        let cellWidth = (width - spacing * CGFloat(columns - 1)) / CGFloat(columns)
        let cellHeight = (height - spacing * CGFloat(rows - 1)) / CGFloat(rows)
        return CGSize(
            width: cellWidth.isFinite ? max(0, cellWidth) : 0,
            height: cellHeight.isFinite ? max(0, cellHeight) : 0
        )
    }
}

#Preview {
    MemorizeView(viewModel: MemorizeViewModel(memorama: nil))
}
