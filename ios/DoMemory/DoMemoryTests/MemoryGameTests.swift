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

final class BoardLayoutTests: XCTestCase {
    private let spacing = BoardLayout.spacing
    private let padding = BoardLayout.padding

    /// Every card must fit the space it was laid out for.
    private func assertFits(_ layout: BoardLayout, count: Int, in size: CGSize, file: StaticString = #filePath, line: UInt = #line) {
        let rows = Int(ceil(Double(count) / Double(layout.columns)))
        let usedWidth = CGFloat(layout.columns) * layout.cardSize.width + spacing * CGFloat(layout.columns - 1)
        let usedHeight = CGFloat(rows) * layout.cardSize.height + spacing * CGFloat(rows - 1)
        XCTAssertLessThanOrEqual(usedWidth, size.width - padding * 2 + 0.01, "board overflows horizontally", file: file, line: line)
        XCTAssertLessThanOrEqual(usedHeight, size.height - padding * 2 + 0.01, "board overflows vertically", file: file, line: line)
    }

    func testPhoneKeepsItsColumnsAndStretchesCardsToFill() {
        let size = CGSize(width: 390, height: 650)
        let layout = BoardLayout.make(cardCount: 24, in: size, phoneColumns: 5, adaptsShape: false)

        XCTAssertEqual(layout.columns, 5)
        XCTAssertEqual(layout.cardSize.width, (390 - 32 - 40) / 5, accuracy: 0.001)
        XCTAssertEqual(layout.cardSize.height, (650 - 32 - 40) / 5, accuracy: 0.001)
    }

    func testLandscapeIPadUsesMoreColumnsAndKeepsTheCardShape() {
        let size = CGSize(width: 1376, height: 900)
        let layout = BoardLayout.make(cardCount: 24, in: size, phoneColumns: 5, adaptsShape: true)

        XCTAssertEqual(layout.columns, 8, "a wide window should lay 24 cards out as 8 × 3, not the phone's 5 × 5")
        XCTAssertEqual(layout.cardSize.width / layout.cardSize.height, BoardLayout.cardAspect, accuracy: 0.001)
        assertFits(layout, count: 24, in: size)
    }

    func testSmallBoardIsCappedAndFollowsTheWindowShape() {
        let landscape = BoardLayout.make(cardCount: 6, in: CGSize(width: 1376, height: 900), phoneColumns: 3, adaptsShape: true)
        XCTAssertEqual(landscape.cardSize.width, BoardLayout.maxCardWidth, accuracy: 0.001)
        XCTAssertEqual(landscape.columns, 3, "capped cards in a wide window should sit 3 × 2, not 2 × 3 or 6 × 1")

        let portrait = BoardLayout.make(cardCount: 6, in: CGSize(width: 700, height: 1200), phoneColumns: 3, adaptsShape: true)
        XCTAssertEqual(portrait.cardSize.width, BoardLayout.maxCardWidth, accuracy: 0.001)
        XCTAssertEqual(portrait.columns, 2, "capped cards in a tall window should sit 2 × 3")
    }

    func testEveryIPadWindowShapeFits() {
        let sizes = [
            CGSize(width: 1032, height: 1200),  // 13-inch portrait
            CGSize(width: 1133, height: 620),   // iPad mini landscape
            CGSize(width: 375, height: 950),    // one-third Split View
            CGSize(width: 688, height: 900),    // half Split View
        ]
        for size in sizes {
            for count in [6, 12, 16, 20, 24] {
                let layout = BoardLayout.make(cardCount: count, in: size, phoneColumns: 5, adaptsShape: true)
                XCTAssertGreaterThan(layout.cardSize.width, 0, "\(count) cards in \(size)")
                XCTAssertEqual(layout.cardSize.width / layout.cardSize.height, BoardLayout.cardAspect, accuracy: 0.001)
                assertFits(layout, count: count, in: size)
            }
        }
    }

    func testTooSmallASpaceNeverProducesANegativeCard() {
        for adapts in [false, true] {
            let layout = BoardLayout.make(cardCount: 24, in: CGSize(width: 10, height: 10), phoneColumns: 5, adaptsShape: adapts)
            XCTAssertGreaterThanOrEqual(layout.cardSize.width, 0)
            XCTAssertGreaterThanOrEqual(layout.cardSize.height, 0)
        }
    }
}
