//
//  MultiplayerModelsTests.swift
//  DoMemoryTests
//
//  Covers the pure, Firebase-free logic behind the host-first multiplayer
//  flow: the `gameId == ""` "no game chosen yet" wire sentinel, and
//  `MultiplayerService.resolveMemorama(from:availableMemoramas:)`, which
//  reconstructs a `Memorama` from a pre-game room (one with no cards dealt
//  yet) for both a catalog game and a custom one.
//

import XCTest
@testable import DoMemory

final class MultiplayerModelsTests: XCTestCase {
    // MARK: - Fixtures

    private func makeRoom(
        gameId: String = "",
        gameName: String = "",
        difficulty: String = "",
        gameSource: MultiplayerGameSource = .firebase,
        customGamePayload: MultiplayerCustomGamePayload? = nil
    ) -> MultiplayerRoom {
        MultiplayerRoom(
            id: "room1",
            code: "ABC123",
            status: .waiting,
            createdAt: 0,
            updatedAt: 0,
            hostId: "host-uid",
            guestId: nil,
            players: [:],
            gameSource: gameSource,
            gameId: gameId,
            gameName: gameName,
            difficulty: difficulty,
            customGamePayload: customGamePayload,
            currentPlayerId: nil,
            cards: [],
            selectedCardIds: [],
            winnerId: nil,
            disconnectStartedAt: nil,
            disconnectPlayerId: nil
        )
    }

    // MARK: - hasSelectedGame sentinel

    func testHasSelectedGameIsFalseForEmptyGameId() {
        let room = makeRoom(gameId: "")
        XCTAssertFalse(room.hasSelectedGame)
    }

    func testHasSelectedGameIsTrueForNonEmptyGameId() {
        let room = makeRoom(gameId: "board_1", gameName: "Animals", difficulty: "easy")
        XCTAssertTrue(room.hasSelectedGame)
    }

    // MARK: - resolveMemorama

    func testResolveMemoramaReturnsNilWhenNoGameSelected() {
        let room = makeRoom(gameId: "")
        let resolved = MultiplayerService.shared.resolveMemorama(from: room, availableMemoramas: [])
        XCTAssertNil(resolved)
    }

    func testResolveMemoramaLooksUpCatalogGameByGameId() {
        let catalogMemorama = Memorama(
            id: "board_1",
            name: "Animals",
            category: "animals",
            difficulty: "easy",
            description: "",
            publishedDate: "",
            items: ["🐶", "🐱"],
            itemType: "string",
            isDoubleItem: true
        )
        let room = makeRoom(gameId: "board_1", gameName: "Animals", difficulty: "easy", gameSource: .firebase)

        let resolved = MultiplayerService.shared.resolveMemorama(
            from: room,
            availableMemoramas: [catalogMemorama]
        )

        XCTAssertEqual(resolved, catalogMemorama)
    }

    func testResolveMemoramaReturnsNilWhenCatalogGameIsNotInAvailableList() {
        let room = makeRoom(gameId: "board_unknown", gameName: "Animals", difficulty: "easy", gameSource: .firebase)
        let resolved = MultiplayerService.shared.resolveMemorama(from: room, availableMemoramas: [])
        XCTAssertNil(resolved)
    }

    func testResolveMemoramaReconstructsCustomGameFromEmbeddedPayload() {
        let payload = MultiplayerCustomGamePayload(
            title: "My Custom Game",
            category: "custom",
            items: ["😀", "😎", "🥳"],
            itemType: "string",
            isDoubleItem: false
        )
        let room = makeRoom(
            gameId: "custom_abc",
            gameName: "My Custom Game",
            difficulty: "hard",
            gameSource: .custom,
            customGamePayload: payload
        )

        let resolved = MultiplayerService.shared.resolveMemorama(from: room, availableMemoramas: [])

        XCTAssertEqual(resolved?.id, "custom_abc")
        XCTAssertEqual(resolved?.name, "My Custom Game")
        XCTAssertEqual(resolved?.category, "custom")
        XCTAssertEqual(resolved?.difficulty, "hard")
        XCTAssertEqual(resolved?.items, ["😀", "😎", "🥳"])
        XCTAssertEqual(resolved?.itemType, "string")
        XCTAssertEqual(resolved?.isDoubleItem, false)
    }
}
