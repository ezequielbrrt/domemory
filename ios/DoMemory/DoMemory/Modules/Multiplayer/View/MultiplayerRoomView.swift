//
//  MultiplayerRoomView.swift
//  DoMemory
//

import SwiftUI

struct MultiplayerRoomView: View {
    @State private var viewModel: MultiplayerRoomViewModel
    @State private var showGamePicker = false
    @Environment(\.dismiss) private var dismiss

    init(entryMode: MultiplayerRoomViewModel.EntryMode, availableMemoramas: [Memorama] = []) {
        _viewModel = State(initialValue: MultiplayerRoomViewModel(entryMode: entryMode, availableMemoramas: availableMemoramas))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            if viewModel.isLoading {
                LoadingView().padding()
            } else if let room = viewModel.room, room.status == .playing || room.status == .reconnecting || room.status == .finished {
                multiplayerBoard
            } else {
                lobby
            }
        }
        .navigationBarHidden(true)
        .task {
            viewModel.prepare()
            AnalyticsService.log(.screenView(name: "multiplayer_room", screenClass: "MultiplayerRoomView"))
        }
        .onDisappear {
            viewModel.stop()
        }
        .onChange(of: viewModel.closeView) {
            if viewModel.closeView { dismiss() }
        }
        .sheet(isPresented: $showGamePicker) {
            MultiplayerGamePickerView(
                memoramas: viewModel.availableMemoramas,
                onSelect: { memorama in
                    showGamePicker = false
                    viewModel.startNewGame(with: memorama)
                }
            )
        }
    }

    private var lobby: some View {
        VStack(spacing: 20) {
            header

            Spacer(minLength: 8)

            VStack(spacing: 16) {
                Text(Strings.multiplayerTitle)
                    .font(.righteous(size: 30))
                    .foregroundStyle(Color.primaryColor)

                Text(viewModel.statusText)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
                    .multilineTextAlignment(.center)

                if !viewModel.roomCode.isEmpty {
                    QRCodeView(code: InviteLink.schemeURL(code: viewModel.roomCode).absoluteString)
                        .frame(width: 190, height: 190)
                        .padding(18)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(Color.surfacePrimary)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                )
                        )

                    Text(viewModel.roomCode)
                        .font(.system(size: 34, weight: .heavy, design: .monospaced))
                        .foregroundStyle(Color.textPrimary)
                        .tracking(4)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(
                            Capsule()
                                .fill(Color.primaryColor.opacity(0.12))
                        )
                        .textSelection(.enabled)

                    ShareLink(item: InviteLink.shareMessage(code: viewModel.roomCode)) {
                        Label(Strings.multiplayerInviteFriend, systemImage: "square.and.arrow.up")
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.primaryColor)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(
                                Capsule()
                                    .strokeBorder(Color.primaryColor.opacity(0.4), lineWidth: 1.5)
                            )
                    }
                    .simultaneousGesture(TapGesture().onEnded {
                        AnalyticsService.log(.multiplayerInviteSent(source: "lobby"))
                    })
                }

                playerScoreRow

                if let errorMessage = viewModel.errorMessage {
                    Text(Strings.multiplayerGenericError)
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.secundaryColor)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .accessibilityHint(errorMessage)
                }

                Button {
                    HapticsService.shared.fire(.tap)
                    viewModel.startGame()
                } label: {
                    Label(Strings.multiplayerStartGame, systemImage: "play.fill")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(viewModel.canStartGame ? Color.primaryColor : Color.textMuted.opacity(0.45))
                        )
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.canStartGame)
                .padding(.horizontal, 16)
            }

            Spacer()
        }
        .padding(.bottom, 18)
    }

    private var multiplayerBoard: some View {
        VStack(spacing: 0) {
            header

            HStack(spacing: 12) {
                ScoreChip(title: Strings.multiplayerYou, score: viewModel.currentUserScore, color: Color.primaryColor)

                VStack(spacing: 2) {
                    Text(viewModel.statusText)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textPrimary)
                        .multilineTextAlignment(.center)
                    if let room = viewModel.room, room.status == .finished {
                        Text(Strings.multiplayerFinalScore)
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                    }
                }
                .frame(maxWidth: .infinity)

                ScoreChip(title: Strings.multiplayerOpponent, score: viewModel.opponentScore, color: Color.secundaryColor)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            if let room = viewModel.room, room.status == .finished {
                Spacer(minLength: 12)

                if AdsService.shared.isNativeConfigured(for: .multiplayerFinishedNative) {
                    AdMobNativeAdView(placement: .multiplayerFinishedNative)
                        .frame(maxWidth: .infinity)
                        .frame(height: 270)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 12)
            } else {
                GeometryReader { geo in
                    let cols = viewModel.gridColumns
                    let rows = max(1, Int(ceil(Double(viewModel.cards.count) / Double(cols))))
                    let spacing: CGFloat = 10
                    let padding: CGFloat = 16
                    let cardWidth = (geo.size.width - padding * 2 - spacing * CGFloat(cols - 1)) / CGFloat(cols)
                    let cardHeight = (geo.size.height - padding * 2 - spacing * CGFloat(rows - 1)) / CGFloat(rows)

                    LazyVGrid(
                        columns: Array(repeating: GridItem(.fixed(cardWidth)), count: cols),
                        spacing: spacing
                    ) {
                        ForEach(viewModel.cards) { card in
                            Button {
                                // choose() emits .cardFlip; a .tap here as well
                                // would double-buzz every card.
                                withAnimation(.linear(duration: 0.35)) {
                                    viewModel.choose(card: card)
                                }
                            } label: {
                                CardView(card: card, shouldShowPie: false)
                                    .frame(width: cardWidth, height: cardHeight)
                            }
                            .buttonStyle(.plain)
                            .disabled(!viewModel.isInteractionEnabled || card.isFaceUp || card.isMatched)
                        }
                    }
                    .padding(padding)
                }
            }

            if let room = viewModel.room, room.status == .finished {
                VStack(spacing: 10) {
                    Button {
                        HapticsService.shared.fire(.tap)
                        viewModel.restartGame()
                    } label: {
                        Label(
                            viewModel.canRestartGame ? Strings.multiplayerPlayAgain : Strings.multiplayerWaitingForRematch,
                            systemImage: "arrow.clockwise"
                        )
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(viewModel.canRestartGame ? Color.primaryColor : Color.textMuted.opacity(0.45))
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(!viewModel.canRestartGame)

                    Button {
                        HapticsService.shared.fire(.tap)
                        showGamePicker = true
                    } label: {
                        Label(
                            viewModel.canRestartGame ? Strings.multiplayerChooseAnotherGame : Strings.multiplayerWaitingForRematch,
                            systemImage: "square.grid.2x2"
                        )
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(viewModel.canRestartGame ? Color.primaryColor : Color.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color.surfacePrimary)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(!viewModel.canRestartGame)

                    Button {
                        HapticsService.shared.fire(.tap)
                        viewModel.leaveRoom()
                    } label: {
                        Text(Strings.goToMenu)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
        }
    }

    private var header: some View {
        HStack {
            Button {
                HapticsService.shared.fire(.tap)
                viewModel.leaveRoom()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.textPrimary.opacity(0.65))
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

            Text(Strings.multiplayerTitle)
                .font(.system(size: 17, weight: .heavy, design: .rounded))
                .foregroundStyle(Color.primaryColor)

            Spacer()

            Color.clear.frame(width: 40, height: 40)
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 10)
    }

    private var playerScoreRow: some View {
        HStack(spacing: 12) {
            ScoreChip(title: Strings.multiplayerYou, score: viewModel.currentUserScore, color: Color.primaryColor)
            ScoreChip(title: Strings.multiplayerOpponent, score: viewModel.opponentScore, color: Color.secundaryColor)
        }
        .padding(.horizontal, 16)
    }
}

private struct MultiplayerGamePickerView: View {
    let memoramas: [Memorama]
    let onSelect: (Memorama) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(memoramas) { memorama in
                            Button {
                                HapticsService.shared.fire(.tap)
                                onSelect(memorama)
                            } label: {
                                HStack(spacing: 12) {
                                    Text(memorama.items.first ?? "•")
                                        .font(.system(size: 34))
                                        .frame(width: 48, height: 48)

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(memorama.name)
                                            .font(.system(size: 16, weight: .bold, design: .rounded))
                                            .foregroundStyle(Color.textPrimary)
                                            .lineLimit(1)

                                        Text(memorama.difficulty.capitalized)
                                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                                            .foregroundStyle(Color.textMuted)
                                    }

                                    Spacer()

                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundStyle(Color.textMuted)
                                }
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(Color.surfacePrimary)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                .stroke(Color.surfaceBorder, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle(Strings.multiplayerChooseAnotherGame)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Strings.cancel) { dismiss() }
                        .foregroundStyle(Color.textMuted)
                }
            }
        }
    }
}

private struct ScoreChip: View {
    let title: String
    let score: Int
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(title)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(Color.textMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Text("\(score)")
                .font(.system(size: 22, weight: .heavy, design: .rounded))
                .foregroundStyle(color)
        }
        .frame(width: 96)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
        )
    }
}
