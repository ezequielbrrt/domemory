//
//  MultiplayerRoomView.swift
//  DoMemory
//

import SwiftUI

struct MultiplayerRoomView: View {
    @State private var viewModel: MultiplayerRoomViewModel
    /// Rematch picker, shown from the finished-game screen; calls
    /// `startNewGame(with:)`, which deals a fresh hand immediately.
    @State private var showGamePicker = false
    /// Pre-start lobby picker, shown before "Start game" is tappable; calls
    /// `selectGame(_:)`, which only sets the room's game and deals nothing.
    /// Kept separate from `showGamePicker` since the two trigger different
    /// view-model actions.
    @State private var showLobbyGamePicker = false
    @Environment(\.dismiss) private var dismiss

    init(
        entryMode: MultiplayerRoomViewModel.EntryMode,
        availableMemoramas: [Memorama] = [],
        defaultDifficulty: Difficulty = .medium
    ) {
        _viewModel = State(initialValue: MultiplayerRoomViewModel(
            entryMode: entryMode,
            availableMemoramas: availableMemoramas,
            defaultDifficulty: defaultDifficulty
        ))
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
                title: Strings.multiplayerChooseAnotherGame,
                memoramas: viewModel.availableMemoramas,
                initialDifficulty: viewModel.defaultDifficulty,
                onSelect: { memorama in
                    showGamePicker = false
                    viewModel.startNewGame(with: memorama)
                }
            )
        }
        .sheet(isPresented: $showLobbyGamePicker) {
            MultiplayerGamePickerView(
                // The very first pick has nothing to be "another" game than,
                // so it gets its own title; changing an already-picked game
                // reuses the rematch flow's "Choose another game" copy.
                title: (viewModel.room?.hasSelectedGame ?? false)
                    ? Strings.multiplayerChooseAnotherGame
                    : Strings.multiplayerChooseGame,
                memoramas: viewModel.availableMemoramas,
                initialDifficulty: viewModel.defaultDifficulty,
                onSelect: { memorama in
                    showLobbyGamePicker = false
                    viewModel.selectGame(memorama)
                }
            )
        }
    }

    /// The lobby centres itself vertically while it fits, and scrolls once
    /// it doesn't — a landscape iPad mini, or Split View on a small iPad, is
    /// shorter than the QR code, room code, scores and actions stacked up.
    private var lobby: some View {
        GeometryReader { geo in
            ScrollView {
                lobbyContent
                    .frame(minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    private var lobbyContent: some View {
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

                gameSelectionSection

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
                    viewModel.markReady()
                } label: {
                    // Tapping only marks *me* ready — the match starts once
                    // the other player has too, so once I've tapped this
                    // reflects that back rather than staying labeled "Start
                    // game" while doing nothing on a second tap.
                    Label(
                        viewModel.isCurrentUserReady ? Strings.multiplayerWaitingForOpponentReady : Strings.multiplayerStartGame,
                        systemImage: viewModel.isCurrentUserReady ? "hourglass" : "play.fill"
                    )
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(viewModel.canMarkReady ? Color.primaryColor : Color.textMuted.opacity(0.45))
                    )
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.canMarkReady)
                .padding(.horizontal, 16)
            }
            .readableWidth(ContentWidth.modal)

            Spacer()
        }
        .padding(.bottom, 18)
    }

    /// Sits between the player scores and "Start game". Before any game is
    /// chosen the host sees a prominent CTA and the guest sees nothing here
    /// (the `statusText` above already tells them the host hasn't picked);
    /// once a game is chosen, everyone sees what it is, with a "change"
    /// affordance for the host only.
    @ViewBuilder
    private var gameSelectionSection: some View {
        if let room = viewModel.room {
            if room.hasSelectedGame {
                selectedGameRow(room: room)
            } else if viewModel.canSelectGame {
                Button {
                    HapticsService.shared.fire(.tap)
                    showLobbyGamePicker = true
                } label: {
                    Label(Strings.multiplayerChooseGame, systemImage: "square.grid.2x2")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color.primaryColor)
                        )
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
            }
        }
    }

    @ViewBuilder
    private func selectedGameRow(room: MultiplayerRoom) -> some View {
        HStack(spacing: 12) {
            if let emoji = viewModel.selectedMemorama?.items.first {
                Text(emoji)
                    .font(.system(size: 30))
                    .frame(width: 44, height: 44)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.selectedMemorama?.name ?? room.gameName)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)

                if viewModel.canSelectGame {
                    Button {
                        HapticsService.shared.fire(.tap)
                        showLobbyGamePicker = true
                    } label: {
                        Text(Strings.multiplayerChooseAnotherGame)
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.primaryColor)
                    }
                    .buttonStyle(.plain)
                }
            }

            Spacer()
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
        )
        .padding(.horizontal, 16)
    }

    private var multiplayerBoard: some View {
        VStack(spacing: 0) {
            header

            HStack(spacing: 12) {
                ScoreChip(title: Strings.multiplayerYou, score: viewModel.currentUserScore, color: Color.primaryColor)

                VStack(spacing: 2) {
                    if viewModel.resultKind == nil {
                        Text(viewModel.statusText)
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.textPrimary)
                            .multilineTextAlignment(.center)
                    }
                }
                .frame(maxWidth: .infinity)

                ScoreChip(title: Strings.multiplayerOpponent, score: viewModel.opponentScore, color: Color.secundaryColor)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            if let room = viewModel.room, room.status == .finished {
                Spacer(minLength: 12)

                if let resultKind = viewModel.resultKind {
                    resultBanner(kind: resultKind)
                }

                if AdsService.shared.isNativeConfigured(for: .multiplayerFinishedNative) {
                    AdMobNativeAdView(placement: .multiplayerFinishedNative)
                        .frame(maxWidth: .infinity)
                        .frame(height: 270)
                        .padding(.horizontal, 16)
                        .readableWidth(ContentWidth.modal)
                }

                Spacer(minLength: 12)
            } else {
                GeometryReader { geo in
                    let layout = BoardLayout.make(
                        cardCount: viewModel.cards.count,
                        in: geo.size,
                        phoneColumns: viewModel.gridColumns,
                        adaptsShape: BoardLayout.adaptsToWindowShape
                    )

                    LazyVGrid(
                        columns: Array(repeating: GridItem(.fixed(layout.cardSize.width)), count: layout.columns),
                        spacing: BoardLayout.spacing
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
                                    .frame(width: layout.cardSize.width, height: layout.cardSize.height)
                            }
                            .buttonStyle(.plain)
                            .disabled(!viewModel.isInteractionEnabled || card.isFaceUp || card.isMatched)
                        }
                    }
                    .padding(BoardLayout.padding)
                    .frame(width: geo.size.width, height: geo.size.height)
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
                .readableWidth(ContentWidth.modal)
                .padding(.bottom, 12)
            }
        }
    }

    /// Headline shown once a match ends, replacing the small mid-game status
    /// label with the same big-emoji-plus-heavy-headline language the
    /// single-player Win/Lose modals use, so a result reads as clearly here
    /// as it does everywhere else in the app.
    @ViewBuilder
    private func resultBanner(kind: MultiplayerRoomViewModel.ResultKind) -> some View {
        VStack(spacing: 6) {
            Text(resultEmoji(for: kind))
                .font(.system(size: 44))

            Text(viewModel.statusText)
                .font(.system(size: 30, weight: .heavy, design: .rounded))
                .foregroundStyle(resultColor(for: kind))
                .multilineTextAlignment(.center)

            Text(Strings.multiplayerFinalScore)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.textMuted)
        }
        .padding(.horizontal, 24)
    }

    private func resultEmoji(for kind: MultiplayerRoomViewModel.ResultKind) -> String {
        switch kind {
        case .won: return "😎"
        case .lost: return "😳"
        case .draw: return "🤝"
        }
    }

    private func resultColor(for kind: MultiplayerRoomViewModel.ResultKind) -> Color {
        switch kind {
        case .won: return Color.primaryColor
        case .lost: return Color.secundaryColor
        case .draw: return Color.textPrimary
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
    var title: String = Strings.multiplayerChooseAnotherGame
    let memoramas: [Memorama]
    let onSelect: (Memorama) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedDifficulty: Difficulty

    init(
        title: String = Strings.multiplayerChooseAnotherGame,
        memoramas: [Memorama],
        initialDifficulty: Difficulty = .medium,
        onSelect: @escaping (Memorama) -> Void
    ) {
        self.title = title
        self.memoramas = memoramas
        self.onSelect = onSelect
        _selectedDifficulty = State(initialValue: initialDifficulty)
    }

    /// Catalog boards are filtered to the picked difficulty, the same rule
    /// the All tab applies to `memoramaArray`; custom memoramas always show
    /// regardless of difficulty, matching how the All tab's filter already
    /// leaves "My memoramas" untouched.
    private var filteredMemoramas: [Memorama] {
        memoramas.filter { memorama in
            memorama.id.hasPrefix("custom_") || Difficulty(rawValue: memorama.difficulty) == selectedDifficulty
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Strings.difficulty)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)

                        MenuDifficultyPicker(selectedDifficulty: selectedDifficulty) { difficulty in
                            HapticsService.shared.fire(.select)
                            selectedDifficulty = difficulty
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                    if filteredMemoramas.isEmpty {
                        Spacer()
                        Text(Strings.multiplayerNoGamesForDifficulty)
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 10) {
                                ForEach(filteredMemoramas) { memorama in
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
                }
            }
            .navigationTitle(title)
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
