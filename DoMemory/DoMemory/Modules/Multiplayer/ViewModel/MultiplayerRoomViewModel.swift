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
        case create(Memorama)
        case join(String)
    }

    private let service: MultiplayerService
    private let entryMode: EntryMode
    let availableMemoramas: [Memorama]
    private var selectedMemorama: Memorama?
    private var roomObserverHandle: UInt?
    private var flipBackTask: Task<Void, Never>?
    private var hideMatchedTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?

    var room: MultiplayerRoom?
    var isLoading = false
    var errorMessage: String?
    var closeView = false
    var hasStarted = false

    init(entryMode: EntryMode, availableMemoramas: [Memorama] = [], service: MultiplayerService = .shared) {
        self.entryMode = entryMode
        self.availableMemoramas = availableMemoramas
        self.service = service
        if case .create(let memorama) = entryMode {
            selectedMemorama = memorama
        }
    }

    var roomCode: String {
        room?.code ?? ""
    }

    var canStartGame: Bool {
        guard let room else { return false }
        return room.hostId == MultiplayerService.currentUserID && room.status == .ready
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
            return Strings.multiplayerWaitingForPlayer
        case .ready:
            return room.hostId == MultiplayerService.currentUserID ? Strings.multiplayerReadyToStart : Strings.multiplayerWaitingForHost
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
                case .create(let memorama):
                    let createdRoom = try await service.createRoom(for: memorama)
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

    func startGame() {
        guard let room, let selectedMemorama else { return }
        Task {
            do {
                try await service.startGame(room: room, memorama: selectedMemorama)
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
                selectedMemorama = memorama
                try await service.startNewGame(room: room, memorama: memorama)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func choose(card: MemoryGame<String>.Card) {
        guard let room, isInteractionEnabled else { return }
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

    func handleRoomUpdate(_ room: MultiplayerRoom) {
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
