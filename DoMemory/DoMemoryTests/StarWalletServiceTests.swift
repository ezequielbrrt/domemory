//
//  StarWalletServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class StarWalletServiceTests: XCTestCase {
    private let wallet = StarWalletService.shared
    private let progress = LevelProgressService.shared
    private let defaults = UserDefaults.standard
    /// Range of levels touched by these tests; reset before/after each test
    /// since both services are backed by the real UserDefaults.standard.
    private let testedLevels = 1...10

    override func setUpWithError() throws {
        resetStarState()
    }

    override func tearDownWithError() throws {
        resetStarState()
    }

    private func resetStarState() {
        defaults.removeObject(forKey: "levels.highestUnlocked")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")
        for level in testedLevels {
            defaults.removeObject(forKey: "levels.stars.\(level)")
        }
    }

    // MARK: - Wallet arithmetic

    func testNewWalletStartsEmpty() {
        XCTAssertEqual(wallet.balance, 0)
        XCTAssertFalse(wallet.canAfford(1))
    }

    func testCreditThenSpend() {
        wallet.credit(10)
        XCTAssertEqual(wallet.balance, 10)
        XCTAssertTrue(wallet.spend(4))
        XCTAssertEqual(wallet.balance, 6)
    }

    func testSpendingMoreThanTheBalanceFailsAndChangesNothing() {
        wallet.credit(5)
        XCTAssertFalse(wallet.spend(6), "an unaffordable purchase must report failure")
        XCTAssertEqual(wallet.balance, 5, "a failed purchase must not move the balance")
    }

    func testSpendingTheExactBalanceIsAllowed() {
        wallet.credit(7)
        XCTAssertTrue(wallet.spend(7))
        XCTAssertEqual(wallet.balance, 0)
    }

    func testCreditIgnoresNonPositiveAmounts() {
        wallet.credit(3)
        wallet.credit(0)
        wallet.credit(-5)
        XCTAssertEqual(wallet.balance, 3)
    }

    // MARK: - Earning

    func testClearingALevelCreditsTheWallet() {
        let stars = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(stars, 3)
        XCTAssertEqual(wallet.balance, 3)
        XCTAssertEqual(progress.totalStars, 3)
    }

    func testALossCreditsNothing() {
        _ = progress.recordCompletion(level: 1, didWin: false, timeRemaining: 0, totalTime: 90, failedTries: 4)
        XCTAssertEqual(wallet.balance, 0)
        XCTAssertEqual(progress.totalStars, 0)
    }

    func testReplayingAtTheSameRatingCreditsNothing() {
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(wallet.balance, 3)

        // Replaying a maxed-out level must not be farmable for currency.
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(wallet.balance, 3)
        XCTAssertEqual(progress.totalStars, 3)
    }

    func testImprovingARatingCreditsOnlyTheDifference() {
        // 1 star first: under 25% of the clock left.
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 10, totalTime: 100, failedTries: 3)
        XCTAssertEqual(wallet.balance, 1)

        // Then 3 stars: the improvement pays out the 2-star difference.
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 90, totalTime: 100, failedTries: 0)
        XCTAssertEqual(wallet.balance, 3)
        XCTAssertEqual(progress.totalStars, 3)
    }

    func testAWorseReplayCreditsNothing() {
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 90, totalTime: 100, failedTries: 0)
        XCTAssertEqual(wallet.balance, 3)

        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 5, totalTime: 100, failedTries: 8)
        XCTAssertEqual(wallet.balance, 3)
        XCTAssertEqual(progress.totalStars, 3)
    }

    // MARK: - Lifetime total vs wallet

    func testSpendingNeverLowersTheLifetimeTotal() {
        _ = progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        _ = progress.recordCompletion(level: 2, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(progress.totalStars, 6)
        XCTAssertEqual(wallet.balance, 6)

        XCTAssertTrue(wallet.spend(5))
        XCTAssertEqual(wallet.balance, 1)
        XCTAssertEqual(progress.totalStars, 6, "spending is not supposed to touch the mastery score")
    }

    // MARK: - Migration

    func testMigrationSeedsLifetimeAndWalletFromPerLevelRatings() {
        // Simulate a pre-wallet install: per-level ratings exist, but neither
        // the lifetime total nor the wallet key has ever been written.
        defaults.set(4, forKey: "levels.highestUnlocked")
        defaults.set(3, forKey: "levels.stars.1")
        defaults.set(2, forKey: "levels.stars.2")
        defaults.set(1, forKey: "levels.stars.3")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")

        XCTAssertEqual(progress.totalStars, 6)
        XCTAssertEqual(wallet.balance, 6, "existing players should open the wallet holding what they already earned")
    }

    func testMigrationRunsOnlyOnce() {
        defaults.set(2, forKey: "levels.highestUnlocked")
        defaults.set(3, forKey: "levels.stars.1")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")

        XCTAssertEqual(wallet.balance, 3)
        XCTAssertTrue(wallet.spend(3))
        XCTAssertEqual(wallet.balance, 0)

        // Reading again must not re-seed the wallet from the per-level keys.
        XCTAssertEqual(wallet.balance, 0)
        XCTAssertEqual(progress.totalStars, 3)
    }
}
