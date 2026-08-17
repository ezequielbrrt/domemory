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
}
