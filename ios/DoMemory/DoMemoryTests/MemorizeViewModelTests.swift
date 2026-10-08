//
//  MemorizeViewModelTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

@MainActor
final class MemorizeViewModelTests: XCTestCase {
    private let defaults = UserDefaults.standard
    private let testedLevels = 1...10

    override func setUpWithError() throws {
        resetLevelState()
    }

    override func tearDownWithError() throws {
        resetLevelState()
    }

    private func resetLevelState() {
        defaults.removeObject(forKey: "levels.highestUnlocked")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")
        defaults.removeObject(forKey: "levels.lives.remaining")
        defaults.removeObject(forKey: "levels.lives.lastResetDay")
        for level in testedLevels {
            defaults.removeObject(forKey: "levels.stars.\(level)")
        }
    }

    /// A Levels-mode view model holding exactly enough stars for one rescue.
    private func makeFundedLevelViewModel(level: Int = 1) -> MemorizeViewModel {
        // Reading the total runs the one-time migration before we credit, so
        // the seeded opening balance can't overwrite the credit below.
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.forgiveCost)
        return MemorizeViewModel(level: level)
    }

    // MARK: - Forgive rescue

    func testForgiveRescueRestoresAPlayableClock() {
        let viewModel = makeFundedLevelViewModel()
        defer { viewModel.stopTimer() }
        // Busting the mistake budget as the clock expires leaves 0 seconds.
        viewModel.timeRemaining = 0

        viewModel.tapOnForgiveWithStars()

        XCTAssertGreaterThanOrEqual(
            viewModel.timeRemaining,
            LevelPowerUp.forgiveMinimumSeconds,
            "resuming at 0 would leave the countdown frozen, since startTimer()'s loop exits immediately"
        )
        XCTAssertNil(viewModel.loseReason, "the rescue must clear the loss so the modal dismisses")
    }

    func testForgiveRescueDoesNotShortenAHealthyClock() {
        let viewModel = makeFundedLevelViewModel()
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 60

        viewModel.tapOnForgiveWithStars()

        XCTAssertEqual(viewModel.timeRemaining, 60, "the floor must never cap a clock that is already healthy")
    }

    func testForgiveRescueDebitsTheWallet() {
        let viewModel = makeFundedLevelViewModel()
        defer { viewModel.stopTimer() }
        XCTAssertTrue(viewModel.canForgiveWithStars)

        viewModel.tapOnForgiveWithStars()

        XCTAssertEqual(viewModel.starBalance, 0)
        XCTAssertEqual(StarWalletService.shared.balance, 0)
    }

    func testUnaffordableForgiveRescueChangesNothing() {
        _ = LevelProgressService.shared.totalStars
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 0
        XCTAssertFalse(viewModel.canForgiveWithStars, "an empty wallet cannot afford a rescue")

        viewModel.tapOnForgiveWithStars()

        XCTAssertEqual(viewModel.timeRemaining, 0, "an unaffordable rescue must not touch the board")
        XCTAssertEqual(StarWalletService.shared.balance, 0)
    }

    // MARK: - Mistake budget wiring

    func testLevelModeExposesTheMistakeBudget() {
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }
        XCTAssertEqual(viewModel.maxFailures, LevelCurve.maxFailures(for: 1))
        XCTAssertFalse(viewModel.isNearFailureLimit, "a fresh board is not near the limit")
    }

    func testFreePlayHasNoMistakeBudget() {
        let board = Memorama(
            id: "test",
            name: "test",
            category: "test",
            difficulty: Difficulty.medium.rawValue,
            description: "",
            publishedDate: "",
            items: ["a", "b", "c"],
            itemType: "string",
            isDoubleItem: true
        )
        let viewModel = MemorizeViewModel(memorama: board, mode: .free)
        defer { viewModel.stopTimer() }
        XCTAssertNil(viewModel.maxFailures, "the budget is a Levels-mode feature only")
        XCTAssertFalse(viewModel.isNearFailureLimit)
        XCTAssertFalse(viewModel.canUsePowerUps)
    }

    // MARK: - Freeze

    /// The HUD's only cue that Freeze did anything. Nothing else changes while
    /// the clock is held — `timeRemaining` stops ticking — so if this flag does
    /// not flip, the player pays 5 stars for no visible feedback at all.
    func testFreezeRaisesTheFrozenFlag() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.freeze.cost)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        XCTAssertFalse(viewModel.isFrozen, "a fresh board is not frozen")
        viewModel.use(.freeze)
        XCTAssertTrue(viewModel.isFrozen, "using Freeze must be visible in the HUD")
    }

    func testFreezeIsNotAppliedWhenItCannotBeAfforded() {
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.freeze)
        XCTAssertFalse(viewModel.isFrozen, "an unaffordable power-up must not fire")
    }

    /// Restarting has to clear the flag, or a frozen-looking clock survives into
    /// a board where the countdown is actually running.
    func testFrozenFlagClearsOnRestart() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.freeze.cost)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.freeze)
        XCTAssertTrue(viewModel.isFrozen)
        viewModel.tapOnTryAgain()
        XCTAssertFalse(viewModel.isFrozen, "a fresh board must not look frozen")
    }

    /// A second Freeze restarts the hold rather than extending it, so buying it
    /// again while it runs would charge twice for what plays as one.
    func testASecondFreezeIsRefusedWhileFrozen() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.freeze.cost * 2)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.freeze)
        viewModel.use(.freeze)
        XCTAssertEqual(StarWalletService.shared.balance, LevelPowerUp.freeze.cost)
    }

    /// A freeze is still running — and still not for sale — once play resumes.
    /// That paused seconds don't count against it is pinned on Android, whose
    /// view model takes an injected clock; this one reads `Date()` directly.
    func testFreezeSurvivesAPause() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.freeze.cost * 2)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.freeze)
        viewModel.tapOnPause()
        viewModel.reconnectTime()
        XCTAssertTrue(viewModel.isFrozen, "resuming must not drop a paid-for freeze")
        viewModel.use(.freeze)
        XCTAssertEqual(StarWalletService.shared.balance, LevelPowerUp.freeze.cost)
    }

    func testASecondPeekIsRefusedWhileTheFirstIsShowing() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.peek.cost * 2)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.peek)
        XCTAssertTrue(viewModel.isPeeking)
        viewModel.use(.peek)
        XCTAssertEqual(StarWalletService.shared.balance, LevelPowerUp.peek.cost)
    }

    /// Pausing ends a Peek (it would otherwise leave the board revealed), so
    /// the button has to come back with it.
    func testPausingEndsAPeekAndReopensIt() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.peek.cost)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        viewModel.use(.peek)
        viewModel.tapOnPause()
        XCTAssertFalse(viewModel.isPeeking)
        XCTAssertFalse(viewModel.isActive(.peek))
    }

    // MARK: - Lose modal: the life at stake

    /// Runs the clock out on a real board, the way the tick loop ends a game.
    private func loseOnTime(_ viewModel: MemorizeViewModel) async {
        viewModel.timeRemaining = 1
        viewModel.startTimer()
        for _ in 0..<40 where !viewModel.hasLost {
            try? await Task.sleep(for: .milliseconds(100))
        }
        XCTAssertTrue(viewModel.hasLost, "the clock running out must end the game")
    }

    private func spendLives(_ count: Int) {
        for _ in 0..<count {
            LevelLivesService.shared.consumeLife()
        }
    }

    /// The lose modal shows before the loss is booked: the heart it would cost
    /// is still there, which is what a rescue keeps.
    func testALossKeepsItsLifeAtStakeUntilThePlayerLeavesIt() async {
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }
        XCTAssertFalse(viewModel.isLifeAtStake, "nothing is at stake mid-game")

        await loseOnTime(viewModel)

        XCTAssertTrue(viewModel.isLifeAtStake)
        XCTAssertEqual(viewModel.levelLivesRemaining, LevelLivesService.maxLives, "the life is not spent yet")

        viewModel.tapOnGoToMenuAfterLose()

        XCTAssertFalse(viewModel.isLifeAtStake, "leaving the loss books it")
        XCTAssertEqual(viewModel.levelLivesRemaining, LevelLivesService.maxLives - 1)
    }

    func testFreePlayNeverHasALifeAtStake() async {
        let board = Memorama(
            id: "test",
            name: "test",
            category: "test",
            difficulty: Difficulty.medium.rawValue,
            description: "",
            publishedDate: "",
            items: ["a", "b", "c"],
            itemType: "string",
            isDoubleItem: true
        )
        let viewModel = MemorizeViewModel(memorama: board, mode: .free)
        defer { viewModel.stopTimer() }

        await loseOnTime(viewModel)

        XCTAssertFalse(viewModel.isLifeAtStake)
    }

    /// Skipping books the loss. With one life left that spends it, and the map
    /// would then refuse to open the level the player just paid to unlock.
    func testSkipIsNotOfferedWhenItWouldSpendTheLastLife() async {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.skipLevelCost)
        spendLives(LevelLivesService.maxLives - 1)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        await loseOnTime(viewModel)

        XCTAssertFalse(viewModel.canSkipLevelWithStars)
    }

    func testSkipIsOfferedWhenALifeIsLeftAfterIt() async {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.skipLevelCost)
        spendLives(LevelLivesService.maxLives - 2)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        await loseOnTime(viewModel)

        XCTAssertTrue(viewModel.canSkipLevelWithStars)
    }

    func testSkipIsNotOfferedWhenOutOfLives() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.skipLevelCost)
        spendLives(LevelLivesService.maxLives)
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }

        XCTAssertFalse(viewModel.canSkipLevelWithStars)
    }
}

// MARK: - Streak charges and frozen cards

extension MemorizeViewModelTests {
    /// Matches `count` pairs in a row on the view model's board, the way a
    /// player on a hot run would.
    private func matchPairs(_ count: Int, on viewModel: MemorizeViewModel) {
        for _ in 0..<count {
            // Face-down and uncovered: a card already turned, say by a
            // mismatch whose flip-back is still pending, is not a fresh pick.
            guard let first = viewModel.cards.first(where: { !$0.isMatched && !$0.isCovered && !$0.isFaceUp }),
                  let partner = viewModel.cards.first(where: { $0.itemId == first.itemId && $0.id != first.id })
            else { return XCTFail("ran out of pairs to match") }
            viewModel.choose(card: first)
            viewModel.choose(card: partner)
        }
    }

    /// A 4-pair free-play board: enough room for a 3-streak.
    private func makeFreeBoard() -> Memorama {
        Memorama(
            id: "test", name: "Test", category: "test", difficulty: "medium", description: "",
            publishedDate: "", items: ["a", "b", "c", "d"], itemType: "emoji", isDoubleItem: true
        )
    }

    /// Level 5 has 4 pairs and no frozen cards: room for a 3-streak without
    /// clearing the board, and nothing between the taps and the pairs.
    func testAThreeStreakChargesPeekForFree() {
        let viewModel = MemorizeViewModel(level: 5)
        defer { viewModel.stopTimer() }
        XCTAssertEqual(StarWalletService.shared.balance, 0)
        XCTAssertFalse(viewModel.canAfford(.peek), "no stars, no charge")

        matchPairs(3, on: viewModel)

        XCTAssertEqual(viewModel.matchStreak, 3)
        XCTAssertTrue(viewModel.isCharged(.peek))
        XCTAssertTrue(viewModel.canAfford(.peek), "the charge pays for it")
        XCTAssertEqual(viewModel.streakBanner, .charged(.peek))

        viewModel.use(.peek)
        XCTAssertTrue(viewModel.isPeeking, "the charged power-up fires")
        XCTAssertEqual(StarWalletService.shared.balance, 0, "a charge is spent before stars")
        XCTAssertFalse(viewModel.isCharged(.peek), "one milestone, one use")
    }

    func testAChargeIsSpentBeforeStars() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.peek.cost)
        let viewModel = MemorizeViewModel(level: 5)
        defer { viewModel.stopTimer() }

        matchPairs(3, on: viewModel)
        viewModel.use(.peek)

        XCTAssertEqual(StarWalletService.shared.balance, LevelPowerUp.peek.cost, "stars stay in the wallet while a charge is waiting")
    }

    func testStreakMilestonesOutsideLevelsChargeNothing() {
        let board = makeFreeBoard()
        let viewModel = MemorizeViewModel(memorama: board, mode: .free)
        defer { viewModel.stopTimer() }

        matchPairs(3, on: viewModel)

        XCTAssertEqual(viewModel.matchStreak, 3)
        XCTAssertFalse(viewModel.isCharged(.peek), "there is no power-up bar in free play to spend it on")
        XCTAssertEqual(viewModel.streakBanner, .streak(3), "the run is still celebrated")
    }

    func testARetryClearsTheCharges() {
        let viewModel = MemorizeViewModel(level: 5)
        defer { viewModel.stopTimer() }
        matchPairs(3, on: viewModel)
        XCTAssertTrue(viewModel.isCharged(.peek))

        viewModel.tapOnReloadGame()

        XCTAssertFalse(viewModel.isCharged(.peek), "the charge belonged to the run that was abandoned")
        XCTAssertEqual(viewModel.matchStreak, 0)
    }

    func testALevelBoardCarriesItsFrozenCards() {
        let level = 6
        let viewModel = MemorizeViewModel(level: level)
        defer { viewModel.stopTimer() }
        viewModel.resetGame()

        XCTAssertEqual(
            viewModel.cards.filter(\.isFrozen).count,
            LevelCurve.frozenCards(for: level),
            "the board has to match the curve after the onAppear reset too"
        )
        XCTAssertTrue(viewModel.hasFrozenCards)
    }

    func testFreePlayNeverFreezesCards() {
        let board = makeFreeBoard()
        let viewModel = MemorizeViewModel(memorama: board, mode: .free)
        defer { viewModel.stopTimer() }
        viewModel.resetGame()
        XCTAssertFalse(viewModel.hasFrozenCards)
    }

    func testTappingAFrozenCardCracksItWithoutFlippingOrCountingAMistake() {
        let viewModel = MemorizeViewModel(level: 6)
        defer { viewModel.stopTimer() }
        guard let frozen = viewModel.cards.first(where: \.isFrozen) else { return XCTFail("level 6 should ice two cards") }

        viewModel.choose(card: frozen)

        let cracked = viewModel.cards.first { $0.id == frozen.id }!
        XCTAssertFalse(cracked.isFrozen)
        XCTAssertFalse(cracked.isFaceUp)
        XCTAssertEqual(viewModel.failedTries, 0)
        XCTAssertEqual(viewModel.matchStreak, 0)
    }
}

// MARK: - Moves mode, bombs, cascade and pins

extension MemorizeViewModelTests {
    private func mismatchOnce(on viewModel: MemorizeViewModel, using first: MemoryGame<String>.Card? = nil) {
        let card = first ?? viewModel.cards.first { !$0.isMatched && !$0.isCovered && !$0.isFaceUp }!
        let other = viewModel.cards.first { $0.itemId != card.itemId && !$0.isMatched && !$0.isCovered && !$0.isFaceUp && !$0.isBomb }!
        viewModel.choose(card: card)
        viewModel.choose(card: other)
    }

    /// Level 4 is the first moves level: 4 pairs, no blockers yet.
    func testLevelFourIsAMovesLevelWithNoClockAndNoMistakeBudget() {
        let viewModel = MemorizeViewModel(level: 4)
        defer { viewModel.stopTimer() }

        XCTAssertTrue(viewModel.isMovesMode)
        XCTAssertNil(viewModel.maxFailures, "a moves level has no mistake budget; the moves are the budget")
        XCTAssertEqual(viewModel.movesBudget, LevelCurve.movesBudget(for: 4))
        XCTAssertEqual(viewModel.movesRemaining, viewModel.movesBudget)
        XCTAssertEqual(viewModel.getRemainingTime(), viewModel.movesBudget, "the view seeds the clock from this; it must not tick")
        XCTAssertEqual(viewModel.availablePowerUps, [.peek, .revealPair])
        XCTAssertFalse(viewModel.shouldShowPie)
    }

    func testEveryAttemptSpendsAMove() {
        let viewModel = MemorizeViewModel(level: 4)
        defer { viewModel.stopTimer() }
        let budget = viewModel.movesBudget!

        mismatchOnce(on: viewModel)
        XCTAssertEqual(viewModel.movesRemaining, budget - 1)
        matchPairs(1, on: viewModel)
        XCTAssertEqual(viewModel.movesRemaining, budget - 2, "a match is an attempt too")
    }

    func testAClockPowerUpIsRefusedOnAMovesLevel() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.freeze.cost)
        let viewModel = MemorizeViewModel(level: 4)
        defer { viewModel.stopTimer() }

        viewModel.use(.freeze)

        XCTAssertFalse(viewModel.isFrozen)
        XCTAssertEqual(StarWalletService.shared.balance, LevelPowerUp.freeze.cost, "nothing was sold")
    }

    func testABombMismatchCostsTheClockOnATimedLevel() {
        let viewModel = MemorizeViewModel(level: 10)
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 60
        guard let bomb = viewModel.cards.first(where: \.isBomb) else { return XCTFail("level 10 should arm one bomb") }

        mismatchOnce(on: viewModel, using: bomb)

        XCTAssertEqual(viewModel.timeRemaining, 60 - MemorizeViewModel.bombPenaltySeconds)
        XCTAssertEqual(viewModel.streakBanner, .bombSeconds(MemorizeViewModel.bombPenaltySeconds))
        XCTAssertNil(viewModel.loseReason)
    }

    func testABombMismatchThatEmptiesTheClockLosesAtOnce() {
        let viewModel = MemorizeViewModel(level: 10)
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 4
        guard let bomb = viewModel.cards.first(where: \.isBomb) else { return XCTFail("level 10 should arm one bomb") }

        mismatchOnce(on: viewModel, using: bomb)

        XCTAssertEqual(viewModel.timeRemaining, 0, "never below zero")
        XCTAssertEqual(viewModel.loseReason, .outOfTime)
    }

    func testDefusingABombPaysTheClockBack() {
        let viewModel = MemorizeViewModel(level: 10)
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 60
        guard let bomb = viewModel.cards.first(where: \.isBomb) else { return XCTFail("level 10 should arm one bomb") }
        let partner = viewModel.cards.first { $0.itemId == bomb.itemId && $0.id != bomb.id }!
        if partner.isFrozen { viewModel.choose(card: partner) }

        viewModel.choose(card: partner)
        viewModel.choose(card: bomb)

        XCTAssertEqual(viewModel.timeRemaining, 60 + MemorizeViewModel.bombDefuseBonusSeconds)
        XCTAssertEqual(viewModel.streakBanner, .defusedSeconds(MemorizeViewModel.bombDefuseBonusSeconds))
    }

    func testNeighbourIndicesFollowTheDrawnGrid() {
        let viewModel = MemorizeViewModel(level: 10) // 12 cards
        defer { viewModel.stopTimer() }
        viewModel.boardColumns = 4

        XCTAssertEqual(Set(viewModel.neighbourIndices(of: 0)), [1, 4])
        XCTAssertEqual(Set(viewModel.neighbourIndices(of: 5)), [4, 6, 1, 9])
        XCTAssertEqual(Set(viewModel.neighbourIndices(of: 3)), [2, 7], "no wrap from the end of a row")
        XCTAssertEqual(Set(viewModel.neighbourIndices(of: 11)), [10, 7])
    }

    func testTheCascadeOnlyFlashesPlainFaceDownCardsOnARun() {
        let viewModel = MemorizeViewModel(memorama: makeFreeBoard(), mode: .free)
        defer { viewModel.stopTimer() }
        viewModel.boardColumns = 3

        matchPairs(1, on: viewModel)
        XCTAssertTrue(viewModel.flashedCardIDs.isEmpty, "the first match of a run flashes nothing")

        matchPairs(1, on: viewModel)
        for id in viewModel.flashedCardIDs {
            let card = viewModel.cards.first { $0.id == id }!
            XCTAssertFalse(card.isMatched)
            XCTAssertFalse(card.isFaceUp, "the model never turns a flashed card")
            XCTAssertFalse(card.isCovered)
        }
    }

    func testPinsAreCappedAndClearedByAMatch() {
        let viewModel = MemorizeViewModel(memorama: makeFreeBoard(), mode: .free)
        defer { viewModel.stopTimer() }
        let cards = viewModel.cards

        for card in cards.prefix(3) { viewModel.togglePin(on: card) }
        XCTAssertEqual(viewModel.pinnedCards.count, 3)
        XCTAssertEqual(Set(viewModel.pinnedCards.values), [0, 1, 2], "each pin gets its own colour slot")

        viewModel.togglePin(on: cards[3])
        XCTAssertEqual(viewModel.pinnedCards.count, 3, "a fourth pin is refused")

        viewModel.togglePin(on: cards[1])
        XCTAssertNil(viewModel.pinSlot(cards[1]), "holding a pinned card clears it")
        viewModel.togglePin(on: cards[3])
        XCTAssertEqual(viewModel.pinSlot(cards[3]), 1, "the freed slot is reused")

        let pinned = cards[0]
        let partner = cards.first { $0.itemId == pinned.itemId && $0.id != pinned.id }!
        viewModel.choose(card: pinned)
        viewModel.choose(card: partner)
        XCTAssertFalse(viewModel.isPinned(pinned), "a matched card has nothing left to mark")
    }

    func testAFaceUpCardCannotBePinned() {
        let viewModel = MemorizeViewModel(memorama: makeFreeBoard(), mode: .free)
        defer { viewModel.stopTimer() }
        let card = viewModel.cards[0]
        viewModel.choose(card: card)
        viewModel.togglePin(on: viewModel.cards[0])
        XCTAssertTrue(viewModel.pinnedCards.isEmpty)
    }
}

// MARK: - Juice

extension MemorizeViewModelTests {
    func testAMatchOnARunFloatsTheMultiplierOffTheTappedCard() {
        let viewModel = MemorizeViewModel(memorama: makeFreeBoard(), mode: .free)
        defer { viewModel.stopTimer() }

        matchPairs(1, on: viewModel)
        let firstMatch = viewModel.cards.filter(\.isMatched)
        XCTAssertEqual(firstMatch.count, 2)
        XCTAssertTrue(firstMatch.allSatisfy { viewModel.effect(on: $0) != nil }, "both cards of the pair burst")
        XCTAssertTrue(firstMatch.allSatisfy { viewModel.effect(on: $0)?.text == nil }, "a single match carries no multiplier")
        XCTAssertEqual(viewModel.effect(on: firstMatch[0])?.tier, 1)

        let second = viewModel.cards.first { !$0.isMatched && !$0.isFaceUp }!
        let partner = viewModel.cards.first { $0.itemId == second.itemId && $0.id != second.id }!
        viewModel.choose(card: second)
        viewModel.choose(card: partner)

        XCTAssertEqual(viewModel.effect(on: partner)?.text, "×2", "the card the player tapped carries the number")
        XCTAssertNil(viewModel.effect(on: second)?.text)
        XCTAssertEqual(viewModel.effect(on: partner)?.tier, 2)
        XCTAssertFalse(viewModel.effect(on: partner)!.isPenalty)
    }

    func testABombGoingOffShakesTheBoardAndFloatsThePenalty() {
        let viewModel = MemorizeViewModel(level: 10)
        defer { viewModel.stopTimer() }
        viewModel.timeRemaining = 60
        guard let bomb = viewModel.cards.first(where: \.isBomb) else { return XCTFail("level 10 should arm one bomb") }
        XCTAssertEqual(viewModel.shakeToken, 0)

        mismatchOnce(on: viewModel, using: bomb)

        XCTAssertEqual(viewModel.shakeToken, 1)
        let effect = viewModel.effect(on: bomb)
        XCTAssertEqual(effect?.text, "−\(MemorizeViewModel.bombPenaltySeconds) s")
        XCTAssertEqual(effect?.isPenalty, true)
        XCTAssertEqual(effect?.tier, 0, "a penalty gets the number, not a celebration burst")
    }

    func testEffectsExpireOnTheirOwn() async {
        let viewModel = MemorizeViewModel(memorama: makeFreeBoard(), mode: .free)
        defer { viewModel.stopTimer() }
        matchPairs(1, on: viewModel)
        XCTAssertFalse(viewModel.matchEffects.isEmpty)

        try? await Task.sleep(for: .seconds(MatchEffect.lifetime + 0.3))
        XCTAssertTrue(viewModel.matchEffects.isEmpty, "a burst must not stay on a card")
    }

    func testBurstTiersGrowWithTheRun() {
        XCTAssertEqual(MatchEffect.tier(forStreak: 1), 1)
        XCTAssertEqual(MatchEffect.tier(forStreak: 2), 2)
        XCTAssertEqual(MatchEffect.tier(forStreak: 3), 2)
        XCTAssertEqual(MatchEffect.tier(forStreak: 4), 3)
        XCTAssertEqual(MatchEffect.tier(forStreak: 9), 3)
    }
}
