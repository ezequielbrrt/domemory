//
//  MemoryGameTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class MemoryGameTests: XCTestCase {
    /// Builds a 3-pair board of distinct contents.
    private func makeGame() -> MemoryGame<String> {
        MemoryGame<String>(numbersOfPairsOfCards: 3) { index in "item-\(index)" }
    }

    /// Taps two cards that belong to different pairs, producing one failure.
    private func makeOneMistake(in game: inout MemoryGame<String>) {
        let first = game.cards.first!
        guard let second = game.cards.first(where: { $0.itemId != first.itemId }) else {
            return XCTFail("board should contain more than one pair")
        }
        game.choose(card: first)
        game.choose(card: second)
        game.flipBackUnmatchedCards()
    }

    func testMismatchIncrementsFailedTries() {
        var game = makeGame()
        XCTAssertEqual(game.failedTries, 0)
        makeOneMistake(in: &game)
        XCTAssertEqual(game.failedTries, 1)
    }

    func testForgiveFailuresSubtracts() {
        var game = makeGame()
        makeOneMistake(in: &game)
        makeOneMistake(in: &game)
        makeOneMistake(in: &game)
        XCTAssertEqual(game.failedTries, 3)

        game.forgiveFailures(2)
        XCTAssertEqual(game.failedTries, 1)
    }

    func testForgiveFailuresFloorsAtZero() {
        var game = makeGame()
        makeOneMistake(in: &game)
        game.forgiveFailures(10)
        XCTAssertEqual(game.failedTries, 0, "forgiving more than were made must not go negative")
    }

    func testForgiveFailuresIgnoresNonPositiveCounts() {
        var game = makeGame()
        makeOneMistake(in: &game)
        game.forgiveFailures(0)
        game.forgiveFailures(-3)
        XCTAssertEqual(game.failedTries, 1)
    }

    func testForgiveFailuresLeavesMatchedCardsAlone() {
        var game = makeGame()
        let first = game.cards.first!
        let itsPair = game.cards.first { $0.itemId == first.itemId && $0.id != first.id }!
        game.choose(card: first)
        game.choose(card: itsPair)
        let matchedBefore = game.cards.filter(\.isMatched).count
        XCTAssertEqual(matchedBefore, 2)

        game.forgiveFailures(3)
        XCTAssertEqual(game.cards.filter(\.isMatched).count, 2, "a rescue must not undo progress on the board")
    }
}
