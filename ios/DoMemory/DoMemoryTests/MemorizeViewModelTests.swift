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
