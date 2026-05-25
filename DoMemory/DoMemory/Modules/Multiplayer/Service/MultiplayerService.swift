//
//  MultiplayerService.swift
//  DoMemory
//

import Foundation
import FirebaseAuth
import FirebaseCore
import FirebaseDatabase

enum MultiplayerServiceError: LocalizedError {
    case firebaseUnavailable
    case authenticationFailed
    case roomNotFound
    case roomFull
    case roomUnavailable
    case invalidMove
    case permissionDenied
    case encodingFailed
    case decodingFailed

    var errorDescription: String? {
        switch self {
        case .firebaseUnavailable: return Strings.multiplayerErrorFirebaseUnavailable
        case .authenticationFailed: return Strings.multiplayerErrorAuthentication
        case .roomNotFound: return Strings.multiplayerErrorRoomNotFound
        case .roomFull: return Strings.multiplayerErrorRoomFull
        case .roomUnavailable: return Strings.multiplayerErrorRoomUnavailable
        case .invalidMove: return Strings.multiplayerErrorInvalidMove
        case .permissionDenied: return Strings.multiplayerErrorPermissionDenied
        case .encodingFailed: return Strings.multiplayerErrorEncoding
        case .decodingFailed: return Strings.multiplayerErrorDecoding
        }
    }
}

final class MultiplayerService {
    static let shared = MultiplayerService()
    static var currentUserID: String? { Auth.auth().currentUser?.uid }

    private let roomsPath = "multiplayerRooms"
    private let codesPath = "multiplayerRoomCodes"
    private let reconnectGraceSeconds: TimeInterval = 15

    private var rootRef: DatabaseReference? {
        guard FirebaseApp.app() != nil else { return nil }
        return Database.database().reference()
    }

    private init() {}

    func createRoom(for memorama: Memorama) async throws -> MultiplayerRoom {
        let uid = try await ensureAuthenticated()
        guard let rootRef else { throw MultiplayerServiceError.firebaseUnavailable }

        let roomId = rootRef.child(roomsPath).childByAutoId().key ?? UUID().uuidString
        let code = try await uniqueRoomCode(rootRef: rootRef)
        let now = Date().timeIntervalSince1970
        let player = MultiplayerPlayer(
            id: uid,
            name: Strings.multiplayerHostName,
            connected: true,
            lastSeenAt: now,
            score: 0
        )
        let isCustom = memorama.id.hasPrefix("custom_")
        let room = MultiplayerRoom(
            id: roomId,
            code: code,
            status: .waiting,
            createdAt: now,
            updatedAt: now,
            hostId: uid,
            guestId: nil,
            players: [uid: player],
            gameSource: isCustom ? .custom : .firebase,
            gameId: memorama.id,
            gameName: memorama.name,
            difficulty: memorama.difficulty,
            customGamePayload: isCustom ? MultiplayerCustomGamePayload(
                title: memorama.name,
                category: memorama.category,
                items: memorama.items,
                itemType: memorama.itemType,
                isDoubleItem: memorama.isDoubleItem
            ) : nil,
            currentPlayerId: nil,
            cards: [],
            selectedCardIds: [],
            winnerId: nil,
            disconnectStartedAt: nil,
            disconnectPlayerId: nil
        )

        try await setCodable(room, at: rootRef.child(roomsPath).child(roomId))
        try await rootRef.child(codesPath).child(code).setValueAsync(roomId)
        configurePresence(roomId: roomId, playerId: uid)
        AnalyticsService.log(.multiplayerRoomCreated(gameID: memorama.id, isCustom: isCustom))
        return room
    }

    func joinRoom(code rawCode: String) async throws -> String {
        let uid = try await ensureAuthenticated()
        guard let rootRef else { throw MultiplayerServiceError.firebaseUnavailable }
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let roomIdSnapshot = try await rootRef.child(codesPath).child(code).getDataAsync()
        guard let roomId = roomIdSnapshot.value as? String else {
            throw MultiplayerServiceError.roomNotFound
        }

        let roomRef = rootRef.child(roomsPath).child(roomId)
        let room = try await roomRef.getCodable(MultiplayerRoom.self)
        guard room.status == .waiting || room.status == .ready else {
            throw MultiplayerServiceError.roomUnavailable
        }
        if let guestId = room.guestId, guestId != uid {
            throw MultiplayerServiceError.roomFull
        }
        if room.hostId == uid {
            configurePresence(roomId: roomId, playerId: uid)
            return roomId
        }

        let now = Date().timeIntervalSince1970
        let guest = MultiplayerPlayer(
            id: uid,
            name: Strings.multiplayerGuestName,
            connected: true,
            lastSeenAt: now,
            score: room.players[uid]?.score ?? 0
        )
        try await roomRef.updateChildValuesAsync([
            "guestId": uid,
            "status": MultiplayerRoomStatus.ready.rawValue,
            "updatedAt": now,
            "players/\(uid)": try dictionary(from: guest)
        ])
        configurePresence(roomId: roomId, playerId: uid)
        AnalyticsService.log(.multiplayerRoomJoined)
        return roomId
    }

    func observeRoom(roomId: String, onChange: @escaping (Result<MultiplayerRoom, Error>) -> Void) -> UInt {
        guard let rootRef else {
            onChange(.failure(MultiplayerServiceError.firebaseUnavailable))
            return 0
        }
        return rootRef.child(roomsPath).child(roomId).observe(.value) { snapshot in
            guard snapshot.exists() else {
                onChange(.failure(MultiplayerServiceError.roomNotFound))
                return
            }
            do {
                onChange(.success(try snapshot.decode(MultiplayerRoom.self)))
            } catch {
                onChange(.failure(error))
            }
        }
    }

    func removeRoomObserver(roomId: String, handle: UInt) {
        guard handle != 0 else { return }
        rootRef?.child(roomsPath).child(roomId).removeObserver(withHandle: handle)
    }

    func markConnected(roomId: String, connected: Bool) async throws {
        guard let uid = Self.currentUserID, let rootRef else { return }
        let now = Date().timeIntervalSince1970
        try await rootRef.child(roomsPath).child(roomId).updateChildValuesAsync([
            "players/\(uid)/connected": connected,
            "players/\(uid)/lastSeenAt": now,
            "updatedAt": now
        ])
    }

    func startGame(room: MultiplayerRoom, memorama: Memorama) async throws {
        guard room.hostId == Self.currentUserID else { throw MultiplayerServiceError.invalidMove }
        guard room.guestId != nil else { throw MultiplayerServiceError.roomUnavailable }
        guard let rootRef else { throw MultiplayerServiceError.firebaseUnavailable }

        let now = Date().timeIntervalSince1970
        let cards = makeCards(from: memorama)
        try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
            "status": MultiplayerRoomStatus.playing.rawValue,
            "currentPlayerId": room.hostId,
            "cards": try array(from: cards),
            "selectedCardIds": [],
            "winnerId": NSNull(),
            "disconnectStartedAt": NSNull(),
            "disconnectPlayerId": NSNull(),
            "updatedAt": now
        ])
        AnalyticsService.log(.multiplayerGameStarted(gameID: room.gameId))
    }

    func restartGame(room: MultiplayerRoom) async throws {
        guard room.hostId == Self.currentUserID else { throw MultiplayerServiceError.invalidMove }
        guard let rootRef else { throw MultiplayerServiceError.firebaseUnavailable }

        let now = Date().timeIntervalSince1970
        let cards = makeCards(from: room)
        var players = room.players
        for id in players.keys {
            players[id]?.score = 0
        }

        try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
            "status": MultiplayerRoomStatus.playing.rawValue,
            "currentPlayerId": room.hostId,
            "cards": try array(from: cards),
            "selectedCardIds": [],
            "players": try dictionary(from: players),
            "winnerId": NSNull(),
            "disconnectStartedAt": NSNull(),
            "disconnectPlayerId": NSNull(),
            "updatedAt": now
        ])
        AnalyticsService.log(.multiplayerGameStarted(gameID: room.gameId))
    }

    func startNewGame(room: MultiplayerRoom, memorama: Memorama) async throws {
        guard room.hostId == Self.currentUserID else { throw MultiplayerServiceError.invalidMove }
        guard let rootRef else { throw MultiplayerServiceError.firebaseUnavailable }

        let now = Date().timeIntervalSince1970
        let cards = makeCards(from: memorama)
        var players = room.players
        for id in players.keys {
            players[id]?.score = 0
        }
        let isCustom = memorama.id.hasPrefix("custom_")

        try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
            "status": MultiplayerRoomStatus.playing.rawValue,
            "gameSource": (isCustom ? MultiplayerGameSource.custom : MultiplayerGameSource.firebase).rawValue,
            "gameId": memorama.id,
            "gameName": memorama.name,
            "difficulty": memorama.difficulty,
            "customGamePayload": isCustom ? try dictionary(from: MultiplayerCustomGamePayload(
                title: memorama.name,
                category: memorama.category,
                items: memorama.items,
                itemType: memorama.itemType,
                isDoubleItem: memorama.isDoubleItem
            )) : NSNull(),
            "currentPlayerId": room.hostId,
            "cards": try array(from: cards),
            "selectedCardIds": [],
            "players": try dictionary(from: players),
            "winnerId": NSNull(),
            "disconnectStartedAt": NSNull(),
            "disconnectPlayerId": NSNull(),
            "updatedAt": now
        ])
        AnalyticsService.log(.multiplayerGameStarted(gameID: memorama.id))
    }

    func chooseCard(room: MultiplayerRoom, cardId: Int) async throws {
        guard let uid = Self.currentUserID,
              let rootRef else {
            throw MultiplayerServiceError.invalidMove
        }
        let roomRef = rootRef.child(roomsPath).child(room.id)
        let currentRoom = try await roomRef.getCodable(MultiplayerRoom.self)

        guard currentRoom.status == .playing,
              currentRoom.currentPlayerId == uid else {
            throw MultiplayerServiceError.invalidMove
        }
        guard let selectedCard = currentRoom.cards.first(where: { $0.id == cardId }),
              !selectedCard.isMatched,
              !selectedCard.isFaceUp,
              currentRoom.selectedCardIds.count < 2 else {
            throw MultiplayerServiceError.invalidMove
        }

        var cards = currentRoom.cards
        guard let chosenIndex = cards.firstIndex(where: { $0.id == cardId }) else {
            throw MultiplayerServiceError.invalidMove
        }
        cards[chosenIndex].isFaceUp = true

        var selectedIds = currentRoom.selectedCardIds + [cardId]
        var updates: [String: Any] = [
            "cards": try array(from: cards),
            "selectedCardIds": selectedIds,
            "updatedAt": Date().timeIntervalSince1970
        ]

        if selectedIds.count == 2 {
            let selectedCards = cards.filter { selectedIds.contains($0.id) }
            if selectedCards.count == 2, selectedCards[0].itemId == selectedCards[1].itemId {
                for index in cards.indices where selectedIds.contains(cards[index].id) {
                    cards[index].isMatched = true
                    cards[index].isFaceUp = true
                }
                var players = currentRoom.players
                if var player = players[uid] {
                    player.score += 1
                    players[uid] = player
                }
                selectedIds = []
                updates["cards"] = try array(from: cards)
                updates["selectedCardIds"] = []
                updates["players"] = try dictionary(from: players)

                if cards.allSatisfy(\.isMatched) {
                    updates["status"] = MultiplayerRoomStatus.finished.rawValue
                    updates["winnerId"] = winnerId(players: players) ?? NSNull()
                    AnalyticsService.log(.multiplayerGameFinished(result: "completed"))
                }
            } else {
                updates["currentPlayerId"] = nextPlayerId(room: currentRoom)
            }
        }

        try await roomRef.updateChildValuesAsync(updates)
    }

    func clearUnmatchedSelection(roomId: String, selectedCardIds: [Int]) async throws {
        guard selectedCardIds.count == 2, let rootRef else { return }
        let roomRef = rootRef.child(roomsPath).child(roomId)
        let currentRoom = try await roomRef.getCodable(MultiplayerRoom.self)
        guard currentRoom.status == .playing,
              currentRoom.selectedCardIds.sorted() == selectedCardIds.sorted() else {
            return
        }

        var cards = currentRoom.cards
        for index in cards.indices where selectedCardIds.contains(cards[index].id) && !cards[index].isMatched {
            cards[index].isFaceUp = false
        }
        try await roomRef.updateChildValuesAsync([
            "cards": try array(from: cards),
            "selectedCardIds": [],
            "updatedAt": Date().timeIntervalSince1970
        ])
    }

    func hideMatchedFaceUpCards(roomId: String) async throws {
        guard let rootRef else { return }
        let roomRef = rootRef.child(roomsPath).child(roomId)
        let currentRoom = try await roomRef.getCodable(MultiplayerRoom.self)
        guard currentRoom.status == .playing else { return }

        var cards = currentRoom.cards
        var didChange = false
        for index in cards.indices where cards[index].isMatched && cards[index].isFaceUp {
            cards[index].isFaceUp = false
            didChange = true
        }
        guard didChange else { return }

        try await roomRef.updateChildValuesAsync([
            "cards": try array(from: cards),
            "updatedAt": Date().timeIntervalSince1970
        ])
    }

    func updateReconnectStateIfNeeded(room: MultiplayerRoom) async throws {
        guard room.status == .playing || room.status == .reconnecting,
              let disconnectedPlayer = room.players.values.first(where: { !$0.connected }),
              let rootRef else { return }

        let now = Date().timeIntervalSince1970
        if room.disconnectStartedAt == nil {
            try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
                "status": MultiplayerRoomStatus.reconnecting.rawValue,
                "disconnectStartedAt": now,
                "disconnectPlayerId": disconnectedPlayer.id,
                "updatedAt": now
            ])
            return
        }

        guard let startedAt = room.disconnectStartedAt else { return }
        if now - startedAt >= reconnectGraceSeconds {
            let remainingPlayerId = room.players.keys.first { $0 != disconnectedPlayer.id }
            try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
                "status": MultiplayerRoomStatus.finished.rawValue,
                "winnerId": remainingPlayerId ?? NSNull(),
                "updatedAt": now
            ])
            AnalyticsService.log(.multiplayerGameFinished(result: "disconnect"))
        }
    }

    func resumeAfterReconnect(room: MultiplayerRoom) async throws {
        guard room.status == .reconnecting,
              room.players.values.allSatisfy(\.connected),
              let rootRef else { return }
        try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
            "status": MultiplayerRoomStatus.playing.rawValue,
            "disconnectStartedAt": NSNull(),
            "disconnectPlayerId": NSNull(),
            "updatedAt": Date().timeIntervalSince1970
        ])
    }

    func leaveRoom(room: MultiplayerRoom) async throws {
        guard let uid = Self.currentUserID, let rootRef else { return }
        let now = Date().timeIntervalSince1970
        if room.status == .waiting || room.status == .ready {
            try await rootRef.child(roomsPath).child(room.id).updateChildValuesAsync([
                "status": MultiplayerRoomStatus.abandoned.rawValue,
                "players/\(uid)/connected": false,
                "players/\(uid)/lastSeenAt": now,
                "updatedAt": now
            ])
        } else {
            try await markConnected(roomId: room.id, connected: false)
        }
    }
}

private extension MultiplayerService {
    func ensureAuthenticated() async throws -> String {
        if let uid = Auth.auth().currentUser?.uid { return uid }
        guard FirebaseApp.app() != nil else { throw MultiplayerServiceError.firebaseUnavailable }
        return try await withCheckedThrowingContinuation { continuation in
            Auth.auth().signInAnonymously { result, error in
                if let error {
                    continuation.resume(throwing: error.multiplayerMappedError)
                } else if let uid = result?.user.uid {
                    continuation.resume(returning: uid)
                } else {
                    continuation.resume(throwing: MultiplayerServiceError.authenticationFailed)
                }
            }
        }
    }

    func uniqueRoomCode(rootRef: DatabaseReference) async throws -> String {
        for _ in 0..<12 {
            let code = Self.makeRoomCode()
            let snapshot = try await rootRef.child(codesPath).child(code).getDataAsync()
            if !snapshot.exists() { return code }
        }
        return Self.makeRoomCode()
    }

    static func makeRoomCode() -> String {
        let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        return String((0..<6).compactMap { _ in alphabet.randomElement() })
    }

    func makeCards(from memorama: Memorama) -> [MultiplayerCard] {
        var cards: [MultiplayerCard] = []
        if memorama.isDoubleItem {
            for pairIndex in memorama.items.indices {
                cards.append(MultiplayerCard(id: pairIndex * 2, itemId: pairIndex, content: memorama.items[pairIndex], isFaceUp: false, isMatched: false))
                cards.append(MultiplayerCard(id: pairIndex * 2 + 1, itemId: pairIndex, content: memorama.items[pairIndex], isFaceUp: false, isMatched: false))
            }
        } else {
            for pairIndex in memorama.items.indices {
                let itemId = pairIndex % 2 == 0 ? pairIndex : pairIndex - 1
                cards.append(MultiplayerCard(id: pairIndex, itemId: itemId, content: memorama.items[pairIndex], isFaceUp: false, isMatched: false))
            }
        }
        cards.shuffle()
        return cards
    }

    func makeCards(from room: MultiplayerRoom) -> [MultiplayerCard] {
        if let payload = room.customGamePayload {
            let memorama = Memorama(
                id: room.gameId,
                name: payload.title,
                category: payload.category,
                difficulty: room.difficulty,
                description: "",
                publishedDate: "",
                items: payload.items,
                itemType: payload.itemType,
                isDoubleItem: payload.isDoubleItem
            )
            return makeCards(from: memorama)
        }

        var groupedCards: [Int: String] = [:]
        for card in room.cards {
            groupedCards[card.itemId] = card.content
        }

        var cards: [MultiplayerCard] = []
        for (index, pair) in groupedCards.sorted(by: { $0.key < $1.key }).enumerated() {
            cards.append(MultiplayerCard(id: index * 2, itemId: pair.key, content: pair.value, isFaceUp: false, isMatched: false))
            cards.append(MultiplayerCard(id: index * 2 + 1, itemId: pair.key, content: pair.value, isFaceUp: false, isMatched: false))
        }
        cards.shuffle()
        return cards
    }

    func nextPlayerId(room: MultiplayerRoom) -> String? {
        room.players.keys.first { $0 != room.currentPlayerId }
    }

    func winnerId(players: [String: MultiplayerPlayer]) -> String? {
        let sortedPlayers = players.values.sorted { lhs, rhs in
            if lhs.score == rhs.score { return lhs.id < rhs.id }
            return lhs.score > rhs.score
        }
        guard let winner = sortedPlayers.first else { return nil }
        guard sortedPlayers.dropFirst().allSatisfy({ $0.score < winner.score }) else {
            return nil
        }
        return winner.id
    }

    func configurePresence(roomId: String, playerId: String) {
        guard let rootRef else { return }
        let playerRef = rootRef.child(roomsPath).child(roomId).child("players").child(playerId)
        playerRef.child("connected").onDisconnectSetValue(false)
        playerRef.child("lastSeenAt").onDisconnectSetValue(Date().timeIntervalSince1970)
    }

    func setCodable<T: Encodable>(_ value: T, at ref: DatabaseReference) async throws {
        try await ref.setValueAsync(dictionary(from: value))
    }

    func dictionary<T: Encodable>(from value: T) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MultiplayerServiceError.encodingFailed
        }
        return object
    }

    func array<T: Encodable>(from value: T) throws -> [Any] {
        let data = try JSONEncoder().encode(value)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [Any] else {
            throw MultiplayerServiceError.encodingFailed
        }
        return object
    }
}

private extension DatabaseReference {
    func setValueAsync(_ value: Any?) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            setValue(value) { error, _ in
                if let error {
                    continuation.resume(throwing: error.multiplayerMappedError)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    func updateChildValuesAsync(_ values: [AnyHashable: Any]) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            updateChildValues(values) { error, _ in
                if let error {
                    continuation.resume(throwing: error.multiplayerMappedError)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    func getDataAsync() async throws -> DataSnapshot {
        try await withCheckedThrowingContinuation { continuation in
            getData { error, snapshot in
                if let error {
                    continuation.resume(throwing: error)
                } else if let snapshot {
                    continuation.resume(returning: snapshot)
                } else {
                    continuation.resume(throwing: MultiplayerServiceError.roomNotFound)
                }
            }
        }
    }

    func getCodable<T: Decodable>(_ type: T.Type) async throws -> T {
        try await getDataAsync().decode(type)
    }
}

private extension Error {
    var multiplayerMappedError: Error {
        let nsError = self as NSError
        let searchableText = "\(nsError.localizedDescription) \(nsError.domain) \(nsError.userInfo)"
            .lowercased()
        return searchableText.contains("permission_denied") || searchableText.contains("permission denied")
            ? MultiplayerServiceError.permissionDenied
            : self
    }
}

private extension DataSnapshot {
    func decode<T: Decodable>(_ type: T.Type) throws -> T {
        guard let value else { throw MultiplayerServiceError.decodingFailed }
        let data = try JSONSerialization.data(withJSONObject: value, options: [])
        return try JSONDecoder().decode(type, from: data)
    }
}
