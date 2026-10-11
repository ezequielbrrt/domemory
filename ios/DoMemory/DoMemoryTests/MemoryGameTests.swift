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

// MARK: - Match streak

extension MemoryGameTests {
    /// Matches the pair that `card` belongs to by tapping it and its partner.
    private func match(_ card: MemoryGame<String>.Card, in game: inout MemoryGame<String>) {
        let partner = game.cards.first { $0.itemId == card.itemId && $0.id != card.id }!
        game.choose(card: card)
        game.choose(card: partner)
        game.hideMatchedFaceUpCards()
    }

    private func makeLargeGame(pairs: Int = 6) -> MemoryGame<String> {
        MemoryGame<String>(numbersOfPairsOfCards: pairs) { index in "item-\(index)" }
    }

    func testStreakGrowsWithEveryMatch() {
        var game = makeLargeGame()
        XCTAssertEqual(game.matchStreak, 0)
        for (count, itemId) in [0, 1, 2].enumerated() {
            match(game.cards.first { $0.itemId == itemId }!, in: &game)
            XCTAssertEqual(game.matchStreak, count + 1)
        }
        XCTAssertEqual(game.bestStreak, 3)
    }

    func testAMismatchDropsTheStreakToZeroButKeepsTheBest() {
        var game = makeLargeGame()
        match(game.cards.first { $0.itemId == 0 }!, in: &game)
        match(game.cards.first { $0.itemId == 1 }!, in: &game)
        XCTAssertEqual(game.matchStreak, 2)

        // Two still-unmatched cards from different pairs; `makeOneMistake`
        // starts from `cards.first`, which may already be matched by now.
        let unmatched = game.cards.filter { !$0.isMatched }
        game.choose(card: unmatched[0])
        game.choose(card: unmatched.first { $0.itemId != unmatched[0].itemId }!)
        game.flipBackUnmatchedCards()
        XCTAssertEqual(game.failedTries, 1)
        XCTAssertEqual(game.matchStreak, 0, "the cliff is the point: one miss ends the run")
        XCTAssertEqual(game.bestStreak, 2)

        match(game.cards.first { $0.itemId == 2 && !$0.isMatched }!, in: &game)
        XCTAssertEqual(game.matchStreak, 1, "a new run starts from one")
    }

    func testTurningOverTheFirstCardOfAPairDoesNotMoveTheStreak() {
        var game = makeLargeGame()
        game.choose(card: game.cards[0])
        XCTAssertEqual(game.matchStreak, 0)
        XCTAssertEqual(game.failedTries, 0)
    }
}

// MARK: - Frozen cards

extension MemoryGameTests {
    func testFreezeCardsIcesTheRequestedCount() {
        var game = makeLargeGame()
        game.freezeCards(count: 3)
        XCTAssertEqual(game.cards.filter(\.isFrozen).count, 3)
    }

    func testFreezeCardsNeverIcesBothCardsOfAPair() {
        for _ in 0..<50 {
            var game = makeLargeGame()
            game.freezeCards(count: 6)
            let frozenItemIds = game.cards.filter(\.isFrozen).map(\.itemId)
            XCTAssertEqual(
                Set(frozenItemIds).count, frozenItemIds.count,
                "a pair with both cards iced is two dead taps in a row"
            )
        }
    }

    func testFreezeCardsClampsToOnePerPair() {
        var game = makeLargeGame(pairs: 3)
        game.freezeCards(count: 10)
        XCTAssertEqual(game.cards.filter(\.isFrozen).count, 3)
    }

    func testFreezeCardsIgnoresNonPositiveCounts() {
        var game = makeLargeGame()
        game.freezeCards(count: 0)
        game.freezeCards(count: -2)
        XCTAssertTrue(game.cards.allSatisfy { !$0.isFrozen })
    }

    func testChoosingAFrozenCardDoesNothing() {
        var game = makeLargeGame()
        game.freezeCards(count: 1)
        let frozen = game.cards.first(where: \.isFrozen)!

        game.choose(card: frozen)

        XCTAssertFalse(game.cards.first { $0.id == frozen.id }!.isFaceUp, "ice keeps the card face down")
        XCTAssertTrue(game.cards.first { $0.id == frozen.id }!.isFrozen, "choose does not crack; that is a separate intent")
        XCTAssertEqual(game.failedTries, 0)
    }

    func testCrackRemovesTheIceWithoutFlipping() {
        var game = makeLargeGame()
        game.freezeCards(count: 1)
        let frozen = game.cards.first(where: \.isFrozen)!

        XCTAssertTrue(game.crack(card: frozen))
        let cracked = game.cards.first { $0.id == frozen.id }!
        XCTAssertFalse(cracked.isFrozen)
        XCTAssertFalse(cracked.isFaceUp, "cracking is a tap of its own; the flip is the next one")

        XCTAssertFalse(game.crack(card: cracked), "a clear card has no ice to crack")
    }

    func testACrackedCardPlaysNormally() {
        var game = makeLargeGame()
        game.freezeCards(count: 1)
        let frozen = game.cards.first(where: \.isFrozen)!
        game.crack(card: frozen)

        match(frozen, in: &game)
        XCTAssertTrue(game.cards.first { $0.id == frozen.id }!.isMatched)
        XCTAssertEqual(game.matchStreak, 1)
    }

    func testPeekLeavesFrozenCardsFaceDown() {
        var game = makeLargeGame()
        game.freezeCards(count: 2)
        game.revealAllUnmatchedForPeek()

        for card in game.cards {
            XCTAssertEqual(card.isFaceUp, !card.isFrozen, "ice is opaque: Peek must not show what is under it")
        }
    }

    func testHintPrefersAPairWithNoIce() {
        var game = makeLargeGame()
        game.freezeCards(count: 2)

        XCTAssertTrue(game.revealUnmatchedPairForHint())
        let revealed = game.cards.filter(\.isFaceUp)
        XCTAssertEqual(revealed.count, 2)
        XCTAssertEqual(Set(revealed.map(\.itemId)).count, 1, "the hint shows one pair")
        let frozenItemIds = Set(game.cards.filter(\.isFrozen).map(\.itemId))
        XCTAssertFalse(frozenItemIds.contains(revealed[0].itemId), "a clear pair was available, so no iced pair should be shown")
    }

    func testHintCracksThePairWhenEveryPairHasIce() {
        var game = makeLargeGame(pairs: 2)
        game.freezeCards(count: 2)
        XCTAssertEqual(game.cards.filter(\.isFrozen).count, 2, "one iced card per pair")

        XCTAssertTrue(game.revealUnmatchedPairForHint())
        let revealed = game.cards.filter(\.isFaceUp)
        XCTAssertEqual(revealed.count, 2, "the hint still has to show something")
        XCTAssertTrue(revealed.allSatisfy { !$0.isFrozen }, "showing a face means its ice is gone")
    }
}

// MARK: - Bombs and chains

extension MemoryGameTests {
    private func mismatch(_ card: MemoryGame<String>.Card, in game: inout MemoryGame<String>) {
        let other = game.cards.first { $0.itemId != card.itemId && !$0.isMatched && !$0.isCovered && !$0.isFaceUp }!
        game.choose(card: card)
        game.choose(card: other)
        game.flipBackUnmatchedCards()
    }

    func testAMismatchOnABombChargesAnExtraAttempt() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
        game.placeBombs(count: 1)
        let bomb = game.cards.first(where: \.isBomb)!

        mismatch(bomb, in: &game)

        XCTAssertEqual(game.lastOutcome, .mismatch(involvedBomb: true))
        XCTAssertEqual(game.failedTries, 1)
        XCTAssertEqual(game.extraAttempts, 1)
        XCTAssertEqual(game.attempts, 2, "a bomb mismatch costs two attempts")
        XCTAssertTrue(game.cards.first { $0.id == bomb.id }!.isBomb, "the bomb stays armed until its pair is found")
    }

    func testMatchingABombsPairDefusesItForABonus() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
        game.placeBombs(count: 1)
        let bomb = game.cards.first(where: \.isBomb)!
        let partner = game.cards.first { $0.itemId == bomb.itemId && $0.id != bomb.id }!

        game.choose(card: partner)
        game.choose(card: bomb)

        XCTAssertEqual(game.lastOutcome, .match(involvedBomb: true))
        XCTAssertEqual(game.extraAttempts, -1, "a defused bomb hands a move back")
        XCTAssertEqual(game.attempts, 0)
        XCTAssertFalse(game.cards.contains(where: \.isBomb))
    }

    func testAPlainMismatchDoesNotInvolveABomb() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
        game.placeBombs(count: 1)
        let plain = game.cards.first { !$0.isBomb }!
        let other = game.cards.first { !$0.isBomb && $0.itemId != plain.itemId }!
        game.choose(card: plain)
        game.choose(card: other)
        XCTAssertEqual(game.lastOutcome, .mismatch(involvedBomb: false))
        XCTAssertEqual(game.extraAttempts, 0)
    }

    func testAChainedCardIgnoresTapsUntilEnoughPairsAreMatched() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
        game.lockCards(count: 1, forMatches: 2)
        let locked = game.cards.first(where: \.isLocked)!
        XCTAssertEqual(locked.lockedForMatches, 2)

        game.choose(card: locked)
        XCTAssertEqual(game.lastOutcome, .ignored)
        XCTAssertFalse(game.cards.first { $0.id == locked.id }!.isFaceUp)

        for _ in 0..<2 {
            let first = game.cards.first { !$0.isMatched && !$0.hasModifier && $0.itemId != locked.itemId }!
            let partner = game.cards.first { $0.itemId == first.itemId && $0.id != first.id }!
            game.choose(card: first)
            game.choose(card: partner)
            game.hideMatchedFaceUpCards()
        }

        let opened = game.cards.first { $0.id == locked.id }!
        XCTAssertEqual(opened.lockedForMatches, 0, "two matches open a chain of two")
        XCTAssertFalse(opened.isLocked)
        game.choose(card: opened)
        XCTAssertTrue(game.cards.first { $0.id == locked.id }!.isFaceUp, "an opened card plays normally")
    }

    func testPeekLeavesChainedCardsFaceDown() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
        game.lockCards(count: 2, forMatches: 2)
        game.revealAllUnmatchedForPeek()
        for card in game.cards {
            XCTAssertEqual(card.isFaceUp, !card.isLocked)
        }
    }

    func testBlockersNeverStackOnOneCardOrOnePair() {
        for _ in 0..<30 {
            var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { "item-\($0)" }
            game.freezeCards(count: 2)
            game.placeBombs(count: 2)
            game.lockCards(count: 2, forMatches: 2)
            let modified = game.cards.filter(\.hasModifier)
            XCTAssertEqual(modified.count, 6)
            XCTAssertEqual(Set(modified.map(\.itemId)).count, 6, "one blocker per pair")
            for card in modified {
                XCTAssertEqual([card.isFrozen, card.isBomb, card.isLocked].filter { $0 }.count, 1, "one blocker per card")
            }
        }
    }

    func testBlockersAreClampedToWhatTheBoardCanTake() {
        var game = MemoryGame<String>(numbersOfPairsOfCards: 3) { "item-\($0)" }
        game.freezeCards(count: 2)
        game.placeBombs(count: 5)
        XCTAssertEqual(game.cards.filter(\.isBomb).count, 1, "only one pair was left uncovered")
        game.lockCards(count: 1, forMatches: 2)
        XCTAssertFalse(game.cards.contains(where: \.isLocked), "no pair left to chain")
    }
}
