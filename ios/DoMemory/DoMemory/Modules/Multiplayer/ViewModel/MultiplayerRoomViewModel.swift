//
//  MultiplayerRoomViewModel.swift
//  DoMemory
//

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class MultiplayerRoomViewModel {
    enum EntryMode: Hashable {
        case host
        case join(String)
    }

    private let service: MultiplayerService
    private let entryMode: EntryMode
    let availableMemoramas: [Memorama]
    /// The player's current All-tab difficulty, used only to preselect the
    /// lobby game picker's own difficulty filter — the host can still change
    /// it there without affecting the All tab.
    let defaultDifficulty: Difficulty
    private var roomObserverHandle: UInt?
    // Remote updates arrive as whole-room snapshots, so each felt moment needs
    // its own edge detection to avoid re-firing on every snapshot.
    private var lastResolvedPair: String?
    private var wasCurrentUserTurn = false
    private var hasFiredFinishHaptic = false
    private var flipBackTask: Task<Void, Never>?
    private var hideMatchedTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?

    var room: MultiplayerRoom?
    var isLoading = false
    var errorMessage: String?
    var closeView = false
    var hasStarted = false
    private var hasRecordedMultiplayerWin = false
    // Guards the auto-start trigger below against firing more than once
    // while waiting for its own `startGame` write to round-trip back as a
    // new snapshot — an unrelated update (e.g. the 5s heartbeat) could
    // otherwise land in that window and re-evaluate the same "both ready"
    // condition before `status` has actually flipped to `.playing`.
    private var hasTriggeredAutoStart = false

    init(
        entryMode: EntryMode,
        availableMemoramas: [Memorama] = [],
        defaultDifficulty: Difficulty = .medium,
        service: MultiplayerService = .shared
    ) {
        self.entryMode = entryMode
        self.availableMemoramas = availableMemoramas
        self.defaultDifficulty = defaultDifficulty
        self.service = service
    }

    var roomCode: String {
        room?.code ?? ""
    }

    /// The room's currently chosen game, resolved from its `gameId`/
    /// `gameSource`/`customGamePayload` rather than tracked separately —
    /// the room itself is the single source of truth for what is picked.
    /// `nil` before any game has been chosen.
    var selectedMemorama: Memorama? {
        guard let room else { return nil }
        return service.resolveMemorama(from: room, availableMemoramas: availableMemoramas)
    }

    /// Whether *I* have confirmed readiness for the currently selected game.
    /// The match only starts once both players are — see
    /// `handleRoomUpdate`'s auto-start trigger.
    var isCurrentUserReady: Bool {
        room?.currentUserPlayer?.isReady ?? false
    }

    var isOpponentReady: Bool {
        room?.opponent?.isReady ?? false
    }

    /// Whether tapping "Start game" right now would do anything: a game
    /// must be picked, a guest must have joined, and I haven't already
    /// marked myself ready. True for either player, not just the host —
    /// both now confirm readiness before the match starts.
    var canMarkReady: Bool {
        guard let room else { return false }
        return room.status == .ready && room.hasSelectedGame && !isCurrentUserReady
    }

    /// The host may pick or change the room's game any time before the
    /// match starts. Once play begins the post-game "choose another game"
    /// rematch flow (`startNewGame`) takes over instead.
    var canSelectGame: Bool {
        guard let room else { return false }
        guard room.status == .waiting || room.status == .ready else { return false }
        return room.hostId == MultiplayerService.currentUserID
    }

    var canRestartGame: Bool {
        guard let room else { return false }
        return room.hostId == MultiplayerService.currentUserID && room.status == .finished
    }

    var cards: [MemoryGame<String>.Card] {
        room?.cards.map {
            MemoryGame<String>.Card(
                isFaceUp: $0.isFaceUp,
                isMatched: $0.isMatched,
                content: $0.content,
                id: $0.id,
                itemId: $0.itemId
            )
        } ?? []
    }

    var gridColumns: Int {
        max(1, Int(ceil(sqrt(Double(cards.count)))))
    }

    var statusText: String {
        guard let room else { return "" }
        switch room.status {
        case .waiting:
            guard room.hasSelectedGame else { return noGameChosenStatusText(room: room) }
            return Strings.multiplayerWaitingForPlayer
        case .ready:
            guard room.hasSelectedGame else { return noGameChosenStatusText(room: room) }
            return isCurrentUserReady ? Strings.multiplayerWaitingForOpponentReady : Strings.multiplayerTapStartWhenReady
        case .playing:
            return room.isCurrentUserTurn ? Strings.multiplayerYourTurn : Strings.multiplayerOpponentTurn
        case .reconnecting:
            return Strings.multiplayerReconnecting
        case .finished:
            if room.winnerId == MultiplayerService.currentUserID {
                return Strings.multiplayerYouWon
            }
            if room.winnerId == nil {
                return Strings.multiplayerDraw
            }
            return Strings.multiplayerYouLost
        case .abandoned:
            return Strings.multiplayerRoomClosed
        }
    }

    private func noGameChosenStatusText(room: MultiplayerRoom) -> String {
        room.hostId == MultiplayerService.currentUserID
            ? Strings.multiplayerHostChooseGamePrompt
            : Strings.multiplayerWaitingForGame
    }

    var currentUserScore: Int {
        room?.currentUserPlayer?.score ?? 0
    }

    var opponentScore: Int {
        room?.opponent?.score ?? 0
    }

    var isInteractionEnabled: Bool {
        room?.status == .playing && room?.isCurrentUserTurn == true && room?.selectedCardIds.count ?? 0 < 2
    }

    func prepare() {
        guard !hasStarted else { return }
        hasStarted = true
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let roomId: String
                switch entryMode {
                case .host:
                    let createdRoom = try await service.createRoom()
                    room = createdRoom
                    roomId = createdRoom.id
                case .join(let code):
                    roomId = try await service.joinRoom(code: code)
                }
                observeRoom(roomId: roomId)
                startHeartbeat(roomId: roomId)
            } catch {
                errorMessage = error.localizedDescription
                isLoading = false
            }
        }
    }

    /// Either player confirming they're ready to start the selected game.
    /// This does not itself start the match — `handleRoomUpdate` reacts once
    /// both players are ready and starts it from the host's client.
    func markReady() {
        guard let room else { return }
        Task {
            do {
                try await service.setReady(room: room, ready: true)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    /// The host picking or changing the room's game before the match
    /// starts. Unlike `startNewGame(with:)`, this leaves `status`, `cards`
    /// and `players` untouched — no cards are dealt until "Start game".
    func selectGame(_ memorama: Memorama) {
        guard let room else { return }
        Task {
            do {
                try await service.selectGame(room: room, memorama: memorama)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func restartGame() {
        guard let room else { return }
        Task {
            do {
                try await service.restartGame(room: room)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func startNewGame(with memorama: Memorama) {
        guard let room else { return }
        Task {
            do {
                try await service.startNewGame(room: room, memorama: memorama)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func choose(card: MemoryGame<String>.Card) {
        guard let room, isInteractionEnabled else { return }
        HapticsService.shared.fire(.cardFlip)
        Task {
            do {
                try await service.chooseCard(room: room, cardId: card.id)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func leaveRoom() {
        guard let room else {
            closeView = true
            return
        }
        Task {
            try? await service.leaveRoom(room: room)
            closeView = true
        }
    }

    func stop() {
        flipBackTask?.cancel()
        hideMatchedTask?.cancel()
        heartbeatTask?.cancel()
        reconnectTask?.cancel()
        if let room, let roomObserverHandle {
            service.removeRoomObserver(roomId: room.id, handle: roomObserverHandle)
        }
        if let room {
            Task { try? await service.markConnected(roomId: room.id, connected: false) }
        }
    }
}

private extension MultiplayerRoomViewModel {
    func observeRoom(roomId: String) {
        roomObserverHandle = service.observeRoom(roomId: roomId) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                switch result {
                case .success(let room):
                    self.room = room
                    self.isLoading = false
                    self.handleRoomUpdate(room)
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }

    /// Multiplayer is the one place the player may not be looking at the
    /// screen when something happens, so remote events are felt as well as seen.
    func fireRoomHaptics(_ room: MultiplayerRoom) {
        if room.status == .playing, room.selectedCardIds.count == 2 {
            let signature = room.selectedCardIds.sorted().map(String.init).joined(separator: "-")
            if signature != lastResolvedPair {
                lastResolvedPair = signature
                let selected = room.cards.filter { room.selectedCardIds.contains($0.id) }
                let matched = !selected.isEmpty && selected.allSatisfy(\.isMatched)
                HapticsService.shared.fire(matched ? .match : .mismatch)
            }
        } else if room.selectedCardIds.isEmpty {
            lastResolvedPair = nil
        }

        let isMyTurn = room.status == .playing && room.isCurrentUserTurn
        if isMyTurn, !wasCurrentUserTurn {
            HapticsService.shared.fire(.select)
        }
        wasCurrentUserTurn = isMyTurn

        if room.status == .finished {
            if !hasFiredFinishHaptic {
                hasFiredFinishHaptic = true
                let won = room.winnerId == MultiplayerService.currentUserID
                HapticsService.shared.fire(won ? .success : .failure)
            }
        } else {
            // restartGame puts the same room back to .playing, and this view
            // model outlives the rematch — without clearing the latch here,
            // every game after the first would finish silently.
            hasFiredFinishHaptic = false
        }
    }

    func handleRoomUpdate(_ room: MultiplayerRoom) {
        fireRoomHaptics(room)
        if room.status == .finished {
            if room.winnerId == MultiplayerService.currentUserID, !hasRecordedMultiplayerWin {
                hasRecordedMultiplayerWin = true
                ProfileStatsService.shared.recordMultiplayerWin()
            }
        } else {
            // Same reasoning as hasFiredFinishHaptic above: restartGame/startNewGame put
            // the same room back to .playing and this view model outlives the rematch, so
            // without clearing the latch here a later rematch win would never be recorded.
            hasRecordedMultiplayerWin = false
        }

        if room.status == .playing, room.selectedCardIds.count == 2 {
            scheduleFlipBack(room: room)
        } else {
            flipBackTask?.cancel()
            flipBackTask = nil
        }

        if room.status == .playing, room.cards.contains(where: { $0.isMatched && $0.isFaceUp }) {
            scheduleMatchedHide(roomID: room.id)
        } else {
            hideMatchedTask?.cancel()
            hideMatchedTask = nil
        }

        if room.status == .playing || room.status == .reconnecting {
            Task {
                try? await service.resumeAfterReconnect(room: room)
                try? await service.updateReconnectStateIfNeeded(room: room)
            }
        }

        if room.status != .ready {
            hasTriggeredAutoStart = false
        }
        triggerAutoStartIfBothReady(room)
    }

    /// Only the host's own client acts on this — both players' clients
    /// observe the same "both ready" snapshot, but `service.startGame`
    /// itself is host-only, so only one of them would ever succeed. Checking
    /// `hostId` here rather than relying solely on that server-side guard
    /// avoids the guest's client making a doomed network call every time.
    private func triggerAutoStartIfBothReady(_ room: MultiplayerRoom) {
        guard !hasTriggeredAutoStart,
              room.status == .ready,
              room.hostId == MultiplayerService.currentUserID,
              room.hasSelectedGame,
              room.players.count == 2,
              room.players.values.allSatisfy(\.isReady),
              let memorama = service.resolveMemorama(from: room, availableMemoramas: availableMemoramas) else {
            return
        }
        hasTriggeredAutoStart = true
        Task {
            do {
                try await service.startGame(room: room, memorama: memorama)
            } catch {
                hasTriggeredAutoStart = false
                errorMessage = error.localizedDescription
            }
        }
    }

    func scheduleFlipBack(room: MultiplayerRoom) {
        let selectedCardIds = room.selectedCardIds
        flipBackTask?.cancel()
        flipBackTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                guard let self else { return }
                Task { try? await self.service.clearUnmatchedSelection(roomId: room.id, selectedCardIds: selectedCardIds) }
            }
        }
    }

    func scheduleMatchedHide(roomID: String) {
        hideMatchedTask?.cancel()
        hideMatchedTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1))
            await MainActor.run {
                guard let self else { return }
                Task { try? await self.service.hideMatchedFaceUpCards(roomId: roomID) }
            }
        }
    }

    func startHeartbeat(roomId: String) {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await self?.service.markConnected(roomId: roomId, connected: true)
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }
}
