//
//  LevelProgressServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class LevelProgressServiceTests: XCTestCase {
    private let service = LevelProgressService.shared
    private let defaults = UserDefaults.standard
    /// Range of levels touched by these tests; reset before/after each test
    /// since LevelProgressService is backed by the real UserDefaults.standard.
    private let testedLevels = 1...10

    override func setUpWithError() throws {
        resetProgress()
    }

    override func tearDownWithError() throws {
        resetProgress()
    }

    private func resetProgress() {
        defaults.removeObject(forKey: "levels.highestUnlocked")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")
        for level in testedLevels {
            defaults.removeObject(forKey: "levels.stars.\(level)")
        }
    }

    // MARK: - Board generation

    func testBoardIsDeterministicForTheSameLevel() {
        let first = service.board(for: 7)
        let second = service.board(for: 7)
        XCTAssertEqual(first.id, second.id)
        XCTAssertEqual(first.items, second.items)
    }

    func testBoardsDifferAcrossLevels() {
        let level1 = service.board(for: 1)
        let level2 = service.board(for: 2)
        XCTAssertNotEqual(level1.items, level2.items)
    }

    func testBoardItemCountMatchesTheCurve() {
        for level in [1, 5, 10, 25, 50] {
            let board = service.board(for: level)
            XCTAssertEqual(board.items.count, LevelCurve.pairs(for: level))
        }
    }

    // MARK: - Default / unlock state

    func testDefaultStateStartsAtLevelOneUnlocked() {
        XCTAssertEqual(service.highestUnlockedLevel, 1)
        XCTAssertTrue(service.isUnlocked(1))
        XCTAssertFalse(service.isUnlocked(2))
    }

    func testWinUnlocksExactlyTheNextLevel() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(service.highestUnlockedLevel, 2)
        XCTAssertTrue(service.isUnlocked(2))
        XCTAssertFalse(service.isUnlocked(3))
    }

    func testLossDoesNotUnlockTheNextLevel() {
        _ = service.recordCompletion(level: 1, didWin: false, timeRemaining: 0, totalTime: 90, failedTries: 5)
        XCTAssertEqual(service.highestUnlockedLevel, 1)
        XCTAssertFalse(service.isUnlocked(2))
    }

    func testReplayingAnAlreadyClearedLevelDoesNotRegressUnlock() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        _ = service.recordCompletion(level: 2, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(service.highestUnlockedLevel, 3)

        // Replaying level 1 (already cleared) must not move progress backwards.
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 10, totalTime: 90, failedTries: 5)
        XCTAssertEqual(service.highestUnlockedLevel, 3)
    }

    // MARK: - Star thresholds

    func testStarThresholds() {
        // 3 stars: >=50% time remaining and <=1 mistake.
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 1),
            3
        )
        resetProgress()

        // Same time remaining, but too many mistakes drops it to 2 stars.
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 2),
            2
        )
        resetProgress()

        // 2 stars: >=25% time remaining.
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 25, totalTime: 100, failedTries: 0),
            2
        )
        resetProgress()

        // Just under the 2-star threshold falls to 1 star.
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 24, totalTime: 100, failedTries: 0),
            1
        )
        resetProgress()

        // Any win with < 25% time remaining still earns 1 star.
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 1, totalTime: 100, failedTries: 9),
            1
        )
    }

    func testALossEarnsNoStars() {
        let stars = service.recordCompletion(level: 1, didWin: false, timeRemaining: 0, totalTime: 90, failedTries: 3)
        XCTAssertEqual(stars, 0)
        XCTAssertEqual(service.stars(for: 1), 0)
    }

    func testReplayingWorseNeverLowersStoredStars() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(service.stars(for: 1), 3)

        let secondAttempt = service.recordCompletion(level: 1, didWin: true, timeRemaining: 5, totalTime: 90, failedTries: 4)
        XCTAssertEqual(secondAttempt, 3, "recordCompletion should report the best stored rating, not the latest attempt")
        XCTAssertEqual(service.stars(for: 1), 3, "a worse replay must not lower the stored rating")
    }

    func testTotalStarsSumsClearedLevels() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0) // 3 stars
        _ = service.recordCompletion(level: 2, didWin: true, timeRemaining: 25, totalTime: 90, failedTries: 0) // 2 stars
        XCTAssertEqual(service.totalStars, 5)
    }

    // MARK: - Skipping

    func testSkipUnlocksTheNextLevelWithoutStars() {
        service.skipLevel(1)
        XCTAssertEqual(service.highestUnlockedLevel, 2)
        XCTAssertTrue(service.isUnlocked(2))
        XCTAssertEqual(service.stars(for: 1), 0, "a skipped level stays at zero stars so it's worth replaying")
        XCTAssertEqual(service.totalStars, 0)
    }

    func testSkippingAnAlreadyClearedLevelDoesNotRegressProgress() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        _ = service.recordCompletion(level: 2, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(service.highestUnlockedLevel, 3)

        service.skipLevel(1)
        XCTAssertEqual(service.highestUnlockedLevel, 3)
    }

    func testASkippedLevelCanStillBeClearedForStarsLater() {
        service.skipLevel(1)
        XCTAssertEqual(service.stars(for: 1), 0)

        let earned = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(earned, 3)
        XCTAssertEqual(service.stars(for: 1), 3)
    }
}
