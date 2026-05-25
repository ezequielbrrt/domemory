//
//  MultiplayerModels.swift
//  DoMemory
//

import Foundation

enum MultiplayerRoomStatus: String, Codable {
    case waiting
    case ready
    case playing
    case reconnecting
    case finished
    case abandoned
}

enum MultiplayerGameSource: String, Codable {
    case firebase
    case custom
}

struct MultiplayerPlayer: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var connected: Bool
    var lastSeenAt: TimeInterval
    var score: Int
}

struct MultiplayerCard: Codable, Identifiable, Hashable {
    var id: Int
    var itemId: Int
    var content: String
    var isFaceUp: Bool
    var isMatched: Bool
}

struct MultiplayerCustomGamePayload: Codable, Hashable {
    var title: String
    var category: String
    var items: [String]
    var itemType: String
    var isDoubleItem: Bool
}

struct MultiplayerRoom: Codable, Identifiable, Hashable {
    var id: String
    var code: String
    var status: MultiplayerRoomStatus
    var createdAt: TimeInterval
    var updatedAt: TimeInterval
    var hostId: String
    var guestId: String?
    var players: [String: MultiplayerPlayer]
    var gameSource: MultiplayerGameSource
    var gameId: String
    var gameName: String
    var difficulty: String
    var customGamePayload: MultiplayerCustomGamePayload?
    var currentPlayerId: String?
    var cards: [MultiplayerCard]
    var selectedCardIds: [Int]
    var winnerId: String?
    var disconnectStartedAt: TimeInterval?
    var disconnectPlayerId: String?

    enum CodingKeys: String, CodingKey {
        case id
        case code
        case status
        case createdAt
        case updatedAt
        case hostId
        case guestId
        case players
        case gameSource
        case gameId
        case gameName
        case difficulty
        case customGamePayload
        case currentPlayerId
        case cards
        case selectedCardIds
        case winnerId
        case disconnectStartedAt
        case disconnectPlayerId
    }

    init(
        id: String,
        code: String,
        status: MultiplayerRoomStatus,
        createdAt: TimeInterval,
        updatedAt: TimeInterval,
        hostId: String,
        guestId: String?,
        players: [String: MultiplayerPlayer],
        gameSource: MultiplayerGameSource,
        gameId: String,
        gameName: String,
        difficulty: String,
        customGamePayload: MultiplayerCustomGamePayload?,
        currentPlayerId: String?,
        cards: [MultiplayerCard],
        selectedCardIds: [Int],
        winnerId: String?,
        disconnectStartedAt: TimeInterval?,
        disconnectPlayerId: String?
    ) {
        self.id = id
        self.code = code
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.hostId = hostId
        self.guestId = guestId
        self.players = players
        self.gameSource = gameSource
        self.gameId = gameId
        self.gameName = gameName
        self.difficulty = difficulty
        self.customGamePayload = customGamePayload
        self.currentPlayerId = currentPlayerId
        self.cards = cards
        self.selectedCardIds = selectedCardIds
        self.winnerId = winnerId
        self.disconnectStartedAt = disconnectStartedAt
        self.disconnectPlayerId = disconnectPlayerId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        code = try container.decode(String.self, forKey: .code)
        status = try container.decode(MultiplayerRoomStatus.self, forKey: .status)
        createdAt = try container.decode(TimeInterval.self, forKey: .createdAt)
        updatedAt = try container.decode(TimeInterval.self, forKey: .updatedAt)
        hostId = try container.decode(String.self, forKey: .hostId)
        guestId = try container.decodeIfPresent(String.self, forKey: .guestId)
        players = try container.decode([String: MultiplayerPlayer].self, forKey: .players)
        gameSource = try container.decode(MultiplayerGameSource.self, forKey: .gameSource)
        gameId = try container.decode(String.self, forKey: .gameId)
        gameName = try container.decode(String.self, forKey: .gameName)
        difficulty = try container.decode(String.self, forKey: .difficulty)
        customGamePayload = try container.decodeIfPresent(MultiplayerCustomGamePayload.self, forKey: .customGamePayload)
        currentPlayerId = try container.decodeIfPresent(String.self, forKey: .currentPlayerId)
        cards = try container.decodeIfPresent([MultiplayerCard].self, forKey: .cards) ?? []
        selectedCardIds = try container.decodeIfPresent([Int].self, forKey: .selectedCardIds) ?? []
        winnerId = try container.decodeIfPresent(String.self, forKey: .winnerId)
        disconnectStartedAt = try container.decodeIfPresent(TimeInterval.self, forKey: .disconnectStartedAt)
        disconnectPlayerId = try container.decodeIfPresent(String.self, forKey: .disconnectPlayerId)
    }
}

extension MultiplayerRoom {
    var isHostCurrentUser: Bool {
        hostId == MultiplayerService.currentUserID
    }

    var opponent: MultiplayerPlayer? {
        players.values.first { $0.id != MultiplayerService.currentUserID }
    }

    var currentPlayer: MultiplayerPlayer? {
        guard let currentPlayerId else { return nil }
        return players[currentPlayerId]
    }

    var currentUserPlayer: MultiplayerPlayer? {
        guard let id = MultiplayerService.currentUserID else { return nil }
        return players[id]
    }

    var isCurrentUserTurn: Bool {
        currentPlayerId == MultiplayerService.currentUserID
    }

    var allPairsMatched: Bool {
        !cards.isEmpty && cards.allSatisfy(\.isMatched)
    }
}
