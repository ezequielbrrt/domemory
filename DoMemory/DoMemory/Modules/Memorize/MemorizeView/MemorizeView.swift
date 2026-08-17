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

                    // Timer chip
                    HStack(spacing: 5) {
                        Image(systemName: "timer")
                            .foregroundStyle(Color.primaryColor)
                            .font(.system(size: 14))
                        Text("\(viewModel.timeRemaining)")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.textPrimary)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        Capsule()
                            .fill(Color.surfacePrimary)
                            .overlay(
                                Capsule()
                                    .stroke(Color.surfaceBorder, lineWidth: 1)
                            )
                            .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                    )

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
                    let cols = gridColumns
                    let rows = max(1, Int(ceil(Double(viewModel.cards.count) / Double(cols))))
                    let spacing: CGFloat = 10
                    let padding: CGFloat = 16
                    let cardWidth  = (geo.size.width  - padding * 2 - spacing * CGFloat(cols - 1)) / CGFloat(cols)
                    let cardHeight = (geo.size.height - padding * 2 - spacing * CGFloat(rows - 1)) / CGFloat(rows)
                    LazyVGrid(
                        columns: Array(repeating: GridItem(.fixed(cardWidth)), count: cols),
                        spacing: spacing
                    ) {
                        ForEach(viewModel.cards) { card in
                            CardView(card: card, shouldShowPie: viewModel.shouldShowPie)
                                .frame(width: cardWidth, height: cardHeight)
                                .onTapGesture {
                                    withAnimation(.linear(duration: 1)) {
                                        viewModel.choose(card: card)
                                        viewModel.getIfAllAreMatched()
                                    }
                                }
                        }
                    }
                    .padding(padding)
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
                    starsEarned: viewModel.lastEarnedStars
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

#Preview {
    MemorizeView(viewModel: MemorizeViewModel(memorama: nil))
}
