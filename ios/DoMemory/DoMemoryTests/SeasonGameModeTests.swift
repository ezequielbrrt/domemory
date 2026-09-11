//
//  SeasonGameModeTests.swift
//  DoMemoryTests
//
//  The Phase 2 refactor's contract: a season LevelContext routes every read and
//  write to the season's own store, endless Levels is unchanged, and a finite
//  season never offers a level past its length.
//

import XCTest
@testable import DoMemory

@MainActor
final class SeasonGameModeTests: XCTestCase {
    private static let seasonID = "spooky-test"
    private static let pool = ["👻", "🎃", "🕷️", "🦇", "🧛", "⚰️", "🕸️", "🔮", "🍬", "🧟", "🪦", "😱"]
    private static let levelCount = 3

    private let defaults = UserDefaults.standard
    private let testedLevels = 1...25

    private func makeSeason(levelCount: Int = SeasonGameModeTests.levelCount) -> Season {
        Season(
            id: Self.seasonID,
            enabled: true,
            startDate: "2026-10-01",
            endDate: "2026-11-02",
            priority: 10,
            levelCount: levelCount,
            icon: "🎃",
            accentColor: "#FF6B1A",
            emojiPool: Self.pool,
            strings: [:]
        )
    }

    override func setUpWithError() throws {
        resetState()
    }

    override func tearDownWithError() throws {
        resetState()
    }

    private func resetState() {
        defaults.removeObject(forKey: "season.\(Self.seasonID).highestUnlocked")
        defaults.removeObject(forKey: "levels.highestUnlocked")
        defaults.removeObject(forKey: "levels.lifetimeStars")
        defaults.removeObject(forKey: "levels.wallet.balance")
        defaults.removeObject(forKey: "levels.lives.remaining")
        defaults.removeObject(forKey: "levels.lives.lastResetDay")
        for level in testedLevels {
            defaults.removeObject(forKey: "season.\(Self.seasonID).stars.\(level)")
            defaults.removeObject(forKey: "levels.stars.\(level)")
        }
    }

    // MARK: - Read routing

    /// The whole point of the refactor: the cards dealt in a season come from
    /// the season's Firebase pool, not from `EmojiPool.all`.
    func testSeasonBoardIsDealtFromTheSeasonPool() throws {
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: 1))
        defer { viewModel.stopTimer() }

        let items = try XCTUnwrap(viewModel.memorama?.items)
        XCTAssertEqual(items.count, LevelCurve.pairs(for: 1))
        XCTAssertTrue(
            items.allSatisfy(Self.pool.contains),
            "a season board must be dealt from the season's own emoji pool"
        )
        XCTAssertEqual(viewModel.memorama?.category, "season")
        XCTAssertEqual(viewModel.levelNumber, 1, "levelNumber must stay the plain level number consumers read")
    }

    /// Endless-Levels progress must be invisible to a season: unlocking level 9
    /// of the endless ladder cannot unlock season level 9.
    func testSeasonUnlockReadsIgnoreEndlessLevelsProgress() {
        defaults.set(9, forKey: "levels.highestUnlocked")
        let store = SeasonLevelProgressStore(season: makeSeason(levelCount: 20))

        XCTAssertEqual(store.highestUnlockedLevel, 1, "a fresh season starts at level 1 regardless of endless progress")
        XCTAssertFalse(store.isUnlocked(9))
        XCTAssertTrue(LevelProgressService.shared.isUnlocked(9), "the endless ladder itself is untouched")
    }

    // MARK: - Write routing

    /// Skipping through the view model must debit the season's namespace and
    /// leave `levels.highestUnlocked` alone.
    func testSkippingASeasonLevelWritesOnlyTheSeasonNamespace() {
        _ = LevelProgressService.shared.totalStars
        StarWalletService.shared.credit(LevelPowerUp.skipLevelCost)
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: 1))
        defer { viewModel.stopTimer() }
        XCTAssertTrue(viewModel.canSkipLevelWithStars)

        viewModel.tapOnConfirmSkipLevel()

        XCTAssertEqual(
            defaults.integer(forKey: "season.\(Self.seasonID).highestUnlocked"), 2,
            "the skip must unlock the next level of the season"
        )
        XCTAssertNil(
            defaults.object(forKey: "levels.highestUnlocked"),
            "a season skip must never advance the endless-Levels ladder"
        )
    }

    /// The same guarantee for the win path's `recordCompletion`, which the view
    /// model reaches through the context's store.
    func testRecordingASeasonWinWritesOnlyTheSeasonNamespace() {
        let context = LevelContext.season(makeSeason(), level: 1)

        let stars = context.store.recordCompletion(
            level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0
        )

        XCTAssertEqual(stars, 3)
        XCTAssertEqual(defaults.integer(forKey: "season.\(Self.seasonID).stars.1"), 3)
        XCTAssertEqual(defaults.integer(forKey: "season.\(Self.seasonID).highestUnlocked"), 2)
        XCTAssertNil(defaults.object(forKey: "levels.stars.1"), "season stars must not appear on the endless ladder")
        XCTAssertNil(defaults.object(forKey: "levels.highestUnlocked"))
        XCTAssertEqual(StarWalletService.shared.balance, 3, "the wallet is shared by design")
    }

    /// Endless Levels must still reach `LevelProgressService`.
    func testEndlessLevelContextStillWritesTheLevelsNamespace() {
        let context = LevelContext(number: 1)

        _ = context.store.recordCompletion(
            level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0
        )

        XCTAssertEqual(defaults.integer(forKey: "levels.stars.1"), 3)
        XCTAssertNil(defaults.object(forKey: "season.\(Self.seasonID).stars.1"))
    }

    // MARK: - Finite season terminal state

    func testSeasonOffersANextLevelBeforeItsLast() {
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: 2))
        defer { viewModel.stopTimer() }
        XCTAssertTrue(viewModel.hasNextLevel)
    }

    func testSeasonFinalLevelOffersNoNextLevel() {
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: Self.levelCount))
        defer { viewModel.stopTimer() }
        XCTAssertFalse(
            viewModel.hasNextLevel,
            "a \(Self.levelCount)-level season must not offer level \(Self.levelCount + 1)"
        )
    }

    /// The backstop behind the win modal: even if the action is somehow tapped
    /// on the final board, the game must not advance past `levelCount`.
    func testAdvancingPastTheSeasonEndIsANoOp() {
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: Self.levelCount))
        defer { viewModel.stopTimer() }
        let boardBefore = viewModel.memorama?.id
        viewModel.showWinView = true

        viewModel.tapOnNextLevel()

        XCTAssertEqual(viewModel.levelNumber, Self.levelCount, "the level must not increment past the season's length")
        XCTAssertEqual(viewModel.memorama?.id, boardBefore, "no new board may be dealt")
        XCTAssertTrue(viewModel.showWinView, "the win modal stays up rather than restarting play")
    }

    func testAdvancingWithinASeasonDealsTheNextSeasonBoard() {
        let viewModel = MemorizeViewModel(context: .season(makeSeason(), level: 1))
        defer { viewModel.stopTimer() }

        viewModel.tapOnNextLevel()

        XCTAssertEqual(viewModel.levelNumber, 2)
        XCTAssertEqual(viewModel.memorama?.id, "season-\(Self.seasonID)-2", "the next board must stay in the season")
        XCTAssertTrue(viewModel.memorama?.items.allSatisfy(Self.pool.contains) ?? false)
    }

    /// Endless Levels is unbounded, so the ceiling must not leak into it.
    func testEndlessLevelsAlwaysHasANextLevel() {
        let viewModel = MemorizeViewModel(level: 1)
        defer { viewModel.stopTimer() }
        XCTAssertTrue(viewModel.hasNextLevel)

        viewModel.tapOnNextLevel()

        XCTAssertEqual(viewModel.levelNumber, 2)
        XCTAssertEqual(viewModel.memorama?.id, "level-2", "endless play must still deal LevelProgressService boards")
        XCTAssertTrue(viewModel.hasNextLevel)
    }

    // MARK: - GameMode equality

    /// The synthesized conformance is gone because `LevelContext` carries a
    /// protocol existential; `isDailyChallenge` is written as
    /// `mode == .dailyChallenge` and depends on the hand-written one.
    func testGameModeEqualityDistinguishesLevelsSeasonsAndModes() {
        let season = makeSeason()
        let endlessOne = GameMode.level(LevelContext(number: 1))
        let endlessOneAgain = GameMode.level(LevelContext(number: 1))
        let endlessTwo = GameMode.level(LevelContext(number: 2))
        let seasonOne = GameMode.level(.season(season, level: 1))

        XCTAssertEqual(endlessOne, endlessOneAgain, "the store is identity, not value")
        XCTAssertNotEqual(endlessOne, endlessTwo)
        XCTAssertNotEqual(endlessOne, seasonOne, "the same number in a season is a different game")
        XCTAssertNotEqual(seasonOne, .dailyChallenge)
        XCTAssertNotEqual(GameMode.free, .dailyChallenge)
        XCTAssertEqual(GameMode.dailyChallenge, .dailyChallenge)
    }

    func testIsDailyChallengeStaysCorrectForEveryMode() {
        let level = MemorizeViewModel(context: .season(makeSeason(), level: 1))
        defer { level.stopTimer() }
        XCTAssertFalse(level.isDailyChallenge)
        XCTAssertNil(MemorizeViewModel(memorama: nil, mode: .free).levelNumber)
        XCTAssertTrue(MemorizeViewModel(memorama: nil, isDailyChallenge: true).isDailyChallenge)
        XCTAssertFalse(MemorizeViewModel(memorama: nil, isDailyChallenge: true).hasNextLevel)
    }
}
