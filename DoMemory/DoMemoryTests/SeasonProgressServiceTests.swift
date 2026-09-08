//
//  SeasonProgressServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class SeasonProgressServiceTests: XCTestCase {
    private static let spookyPool = ["👻", "🎃", "🕷️", "🦇", "🧛", "⚰️", "🕸️", "🔮", "🍬", "🧟", "🪦", "😱"]
    private static let winterPool = ["🎄", "⛄️", "🎁", "🔔", "🕯️", "🦌", "🧣", "🍪", "❄️", "🌟", "🧦", "🛷"]

    private let seasonID = "spooky-2026"
    private let otherSeasonID = "winter-2026"
    private lazy var service = SeasonProgressService(seasonID: seasonID)
    private let defaults = UserDefaults.standard
    /// Range of levels touched by these tests; reset before/after each test
    /// since the service is backed by the real UserDefaults.standard.
    private let testedLevels = 1...25

    override func setUpWithError() throws {
        resetProgress()
    }

    override func tearDownWithError() throws {
        resetProgress()
    }

    private func resetProgress() {
        for id in [seasonID, otherSeasonID] {
            defaults.removeObject(forKey: "season.\(id).highestUnlocked")
            for level in testedLevels {
                defaults.removeObject(forKey: "season.\(id).stars.\(level)")
            }
        }
        // The star wallet and the endless-Levels keys are shared state; clear
        // them too so the isolation assertions below start from nothing.
        defaults.removeObject(forKey: "levels.highestUnlocked")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")
        for level in testedLevels {
            defaults.removeObject(forKey: "levels.stars.\(level)")
        }
    }

    // MARK: - Board generation

    func testBoardIsDeterministicForTheSameSeasonAndLevel() {
        let first = service.board(for: 7, emojiPool: Self.spookyPool)
        let second = service.board(for: 7, emojiPool: Self.spookyPool)
        XCTAssertEqual(first.id, second.id)
        XCTAssertEqual(first.items, second.items)
    }

    func testBoardsDifferAcrossLevelsWithinASeason() {
        let level1 = service.board(for: 1, emojiPool: Self.spookyPool)
        let level2 = service.board(for: 2, emojiPool: Self.spookyPool)
        XCTAssertNotEqual(level1.id, level2.id)
        XCTAssertNotEqual(level1.items, level2.items)
    }

    func testBoardsDifferAcrossSeasonsAtTheSameLevel() {
        // Same level, same pool — only the season id differs, so the seed must
        // still produce a different deal.
        let spooky = SeasonProgressService(seasonID: seasonID).board(for: 5, emojiPool: Self.spookyPool)
        let winter = SeasonProgressService(seasonID: otherSeasonID).board(for: 5, emojiPool: Self.spookyPool)
        XCTAssertNotEqual(spooky.id, winter.id)
        XCTAssertNotEqual(spooky.items, winter.items)
    }

    func testBoardDrawsOnlyFromTheSeasonPool() {
        let board = service.board(for: 12, emojiPool: Self.winterPool)
        XCTAssertTrue(
            board.items.allSatisfy(Self.winterPool.contains),
            "season boards must be dealt from the season pool, not the shared EmojiPool"
        )
        // Same season, same level, different pool: the pool is what changes the deal.
        let spooky = service.board(for: 12, emojiPool: Self.spookyPool)
        XCTAssertNotEqual(board.items, spooky.items)
    }

    func testBoardItemCountMatchesTheSharedLevelCurve() {
        // No season offset and no separate curve: LevelCurve is reused as-is.
        for level in [1, 5, 10, 25, 50] {
            let board = service.board(for: level, emojiPool: Self.spookyPool)
            XCTAssertEqual(board.items.count, LevelCurve.pairs(for: level))
        }
    }

    func testTheSmallestLegalPoolFillsTheLargestBoard() {
        XCTAssertEqual(Self.spookyPool.count, Season.minimumEmojiPoolSize)
        let board = service.board(for: 500, emojiPool: Self.spookyPool)
        XCTAssertEqual(board.items.count, LevelCurve.pairs(for: 500))
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
    }

    func testProgressIsNamespacedPerSeason() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        let other = SeasonProgressService(seasonID: otherSeasonID)
        XCTAssertEqual(other.highestUnlockedLevel, 1, "one season's progress must not unlock another's")
        XCTAssertEqual(other.stars(for: 1), 0)
    }

    // MARK: - Star thresholds (identical to endless Levels)

    func testStarThresholdsMatchEndlessLevels() {
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 1),
            3
        )
        resetProgress()
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 2),
            2
        )
        resetProgress()
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 25, totalTime: 100, failedTries: 0),
            2
        )
        resetProgress()
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: true, timeRemaining: 24, totalTime: 100, failedTries: 0),
            1
        )
    }

    func testALossEarnsNoStars() {
        XCTAssertEqual(
            service.recordCompletion(level: 1, didWin: false, timeRemaining: 0, totalTime: 90, failedTries: 3),
            0
        )
        XCTAssertEqual(service.stars(for: 1), 0)
    }

    func testReplayingWorseNeverLowersStoredStars() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(service.stars(for: 1), 3)
        let replay = service.recordCompletion(level: 1, didWin: true, timeRemaining: 5, totalTime: 90, failedTries: 4)
        XCTAssertEqual(replay, 3)
        XCTAssertEqual(service.stars(for: 1), 3)
    }

    func testTotalStarsSumsTheSeason() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0) // 3
        _ = service.recordCompletion(level: 2, didWin: true, timeRemaining: 25, totalTime: 90, failedTries: 0) // 2
        XCTAssertEqual(service.totalStars(levelCount: 20), 5)
    }

    // MARK: - Shared star wallet

    func testStarsCreditTheSharedWallet() {
        XCTAssertEqual(StarWalletService.shared.balance, 0)
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertEqual(StarWalletService.shared.balance, 3, "season stars land in the one shared wallet")
    }

    func testOnlyTheImprovementIsPaidOutOnAReplay() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 25, totalTime: 100, failedTries: 0) // 2
        XCTAssertEqual(StarWalletService.shared.balance, 2)
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 0) // 3
        XCTAssertEqual(StarWalletService.shared.balance, 3, "beating your own rating pays the difference, not the full rating")
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 50, totalTime: 100, failedTries: 0)
        XCTAssertEqual(StarWalletService.shared.balance, 3, "a repeat of the same rating pays nothing")
    }

    // MARK: - Isolation from endless Levels

    func testSeasonProgressNeverTouchesTheEndlessLevelsKeys() {
        for level in 1...5 {
            _ = service.recordCompletion(level: level, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        }
        service.skipLevel(6)

        XCTAssertNil(
            defaults.object(forKey: "levels.highestUnlocked"),
            "season play must not write the endless-Levels unlock key"
        )
        for level in testedLevels {
            XCTAssertNil(
                defaults.object(forKey: "levels.stars.\(level)"),
                "season play must not write levels.stars.\(level)"
            )
        }
        XCTAssertEqual(LevelProgressService.shared.highestUnlockedLevel, 1)
        // `levels.lifetimeStars` is the endless-Levels mastery score. Reading
        // the shared wallet backfills it to zero on a fresh install, so the
        // assertion is that season stars never *inflate* it.
        XCTAssertEqual(LevelProgressService.shared.totalStars, 0)

        // The wallet is the one deliberate exception: the user chose a single
        // star balance across both modes, so it is expected to have moved.
        XCTAssertEqual(StarWalletService.shared.balance, 15)
    }

    func testEndlessLevelsProgressDoesNotUnlockASeason() {
        _ = LevelProgressService.shared.recordCompletion(
            level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0
        )
        XCTAssertEqual(LevelProgressService.shared.highestUnlockedLevel, 2)
        XCTAssertEqual(service.highestUnlockedLevel, 1, "a season starts at level 1 no matter how far endless Levels has gone")
    }

    // MARK: - Skipping

    func testSkipUnlocksTheNextLevelWithoutStars() {
        service.skipLevel(1)
        XCTAssertEqual(service.highestUnlockedLevel, 2)
        XCTAssertEqual(service.stars(for: 1), 0)
        XCTAssertEqual(service.totalStars(levelCount: 20), 0)
    }

    func testSkippingAnAlreadyClearedLevelDoesNotRegressProgress() {
        _ = service.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        _ = service.recordCompletion(level: 2, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        service.skipLevel(1)
        XCTAssertEqual(service.highestUnlockedLevel, 3)
    }

    // MARK: - Finite season

    func testSeasonIsNotCompleteUntilTheFinalLevelIsCleared() {
        for level in 1...2 {
            _ = service.recordCompletion(level: level, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        }
        XCTAssertFalse(service.isComplete(levelCount: 3))
        XCTAssertEqual(service.clearedLevelCount(levelCount: 3), 2)

        _ = service.recordCompletion(level: 3, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        XCTAssertTrue(service.isComplete(levelCount: 3))
        XCTAssertEqual(service.clearedLevelCount(levelCount: 3), 3, "cleared count is clamped to the season length")
    }

    func testNextLevelStopsAtTheSeasonLength() {
        XCTAssertEqual(service.nextLevel(after: 1, levelCount: 3), 2)
        XCTAssertEqual(service.nextLevel(after: 2, levelCount: 3), 3)
        XCTAssertNil(service.nextLevel(after: 3, levelCount: 3), "a finite season must not offer a level past levelCount")
    }
}
