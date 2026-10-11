//
//  LevelCurveTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class LevelCurveTests: XCTestCase {
    func testPairsHitsApprovedAnchorsExactly() {
        XCTAssertEqual(LevelCurve.pairs(for: 1), 3)
        XCTAssertEqual(LevelCurve.pairs(for: 5), 4)
        XCTAssertEqual(LevelCurve.pairs(for: 10), 6)
        XCTAssertEqual(LevelCurve.pairs(for: 25), 9)
        XCTAssertEqual(LevelCurve.pairs(for: 50), 12)
    }

    func testSecondsHitsApprovedAnchorsExactly() {
        XCTAssertEqual(LevelCurve.seconds(for: 1), 90)
        XCTAssertEqual(LevelCurve.seconds(for: 5), 85)
        XCTAssertEqual(LevelCurve.seconds(for: 10), 75)
        XCTAssertEqual(LevelCurve.seconds(for: 25), 60)
        XCTAssertEqual(LevelCurve.seconds(for: 50), 45)
        XCTAssertEqual(LevelCurve.seconds(for: 80), 35)
    }

    func testPairsIsNonDecreasing() {
        var previous = LevelCurve.pairs(for: 1)
        for level in 2...200 {
            let current = LevelCurve.pairs(for: level)
            XCTAssertGreaterThanOrEqual(current, previous, "pairs decreased at level \(level)")
            previous = current
        }
    }

    func testSecondsIsNonIncreasing() {
        var previous = LevelCurve.seconds(for: 1)
        for level in 2...200 {
            let current = LevelCurve.seconds(for: level)
            XCTAssertLessThanOrEqual(current, previous, "seconds increased at level \(level)")
            previous = current
        }
    }

    func testCapAndFloorHoldFarPastTheLastAnchor() {
        XCTAssertEqual(LevelCurve.pairs(for: 500), 12)
        XCTAssertEqual(LevelCurve.seconds(for: 500), 35)
    }

    func testLevelBelowOneClampsToLevelOne() {
        XCTAssertEqual(LevelCurve.pairs(for: 0), LevelCurve.pairs(for: 1))
        XCTAssertEqual(LevelCurve.seconds(for: -5), LevelCurve.seconds(for: 1))
        XCTAssertEqual(LevelCurve.maxFailures(for: 0), LevelCurve.maxFailures(for: 1))
    }

    // MARK: - Mistake budget

    func testMaxFailuresHitsApprovedAnchorsExactly() {
        XCTAssertEqual(LevelCurve.maxFailures(for: 1), 4)
        XCTAssertEqual(LevelCurve.maxFailures(for: 5), 6)
        XCTAssertEqual(LevelCurve.maxFailures(for: 10), 8)
        XCTAssertEqual(LevelCurve.maxFailures(for: 25), 11)
        XCTAssertEqual(LevelCurve.maxFailures(for: 50), 14)
    }

    func testMaxFailuresIsNonDecreasing() {
        var previous = LevelCurve.maxFailures(for: 1)
        for level in 2...200 {
            let current = LevelCurve.maxFailures(for: level)
            XCTAssertGreaterThanOrEqual(current, previous, "mistake budget decreased at level \(level)")
            previous = current
        }
    }

    func testMaxFailuresHoldsPastTheLastAnchor() {
        XCTAssertEqual(LevelCurve.maxFailures(for: 500), 14)
    }

    /// The winnability invariant. Even perfect recall costs roughly N/2–N
    /// mismatches on an N-pair board, so a budget at or below the pair count
    /// would make levels effectively impossible.
    func testMistakeBudgetAlwaysExceedsPairCount() {
        for level in 1...200 {
            XCTAssertGreaterThan(
                LevelCurve.maxFailures(for: level),
                LevelCurve.pairs(for: level),
                "level \(level) allows fewer mistakes than it has pairs, making it unwinnable"
            )
        }
    }
}

// MARK: - Frozen cards

extension LevelCurveTests {
    func testFrozenCardsStartAtZeroAndStepUp() {
        XCTAssertEqual(LevelCurve.frozenCards(for: 1), 0, "the plain game comes first")
        XCTAssertEqual(LevelCurve.frozenCards(for: 5), 0)
        XCTAssertEqual(LevelCurve.frozenCards(for: 6), 2, "first step")
        XCTAssertEqual(LevelCurve.frozenCards(for: 24), 2, "a step holds until the next one")
        XCTAssertEqual(LevelCurve.frozenCards(for: 25), 3)
        XCTAssertEqual(LevelCurve.frozenCards(for: 50), 4)
        XCTAssertEqual(LevelCurve.frozenCards(for: 500), 4, "held past the last step")
    }

    func testFrozenCardsIsNonDecreasing() {
        var previous = LevelCurve.frozenCards(for: 1)
        for level in 2...200 {
            let current = LevelCurve.frozenCards(for: level)
            XCTAssertGreaterThanOrEqual(current, previous, "frozen cards decreased at level \(level)")
            previous = current
        }
    }

    /// `MemoryGame.freezeCards` never ices both cards of a pair, so a board can
    /// hold at most one frozen card per pair.
    func testFrozenCardsNeverExceedPairs() {
        for level in 1...200 {
            XCTAssertLessThanOrEqual(
                LevelCurve.frozenCards(for: level),
                LevelCurve.pairs(for: level),
                "level \(level) asks for more frozen cards than it has pairs"
            )
        }
    }
}

final class StreakRewardTests: XCTestCase {
    func testMilestonesChargeThePowerLadderInOrder() {
        XCTAssertEqual(StreakReward.powerUp(forStreak: 3), .peek)
        XCTAssertEqual(StreakReward.powerUp(forStreak: 5), .freeze)
        XCTAssertEqual(StreakReward.powerUp(forStreak: 7), .revealPair)
    }

    func testStreaksBetweenMilestonesChargeNothing() {
        for streak in [0, 1, 2, 4, 6, 8, 12] {
            XCTAssertNil(StreakReward.powerUp(forStreak: streak), "streak \(streak) is not a milestone")
        }
    }

    func testMilestonesClimbThePriceList() {
        let costs = StreakReward.milestones.map(\.powerUp.cost)
        XCTAssertEqual(costs, costs.sorted(), "a longer run must earn the dearer power-up")
        let streaks = StreakReward.milestones.map(\.streak)
        XCTAssertEqual(streaks, streaks.sorted())
    }

    func testNextMilestoneLooksAhead() {
        XCTAssertEqual(StreakReward.nextMilestone(after: 0), 3)
        XCTAssertEqual(StreakReward.nextMilestone(after: 3), 5)
        XCTAssertEqual(StreakReward.nextMilestone(after: 6), 7)
        XCTAssertNil(StreakReward.nextMilestone(after: 7), "nothing is left to earn past the last milestone")
        XCTAssertEqual(StreakReward.finalMilestone, 7)
    }
}

// MARK: - Objectives and the other blockers

extension LevelCurveTests {
    func testMovesLevelsComeRoundEveryFourthLevel() {
        for level in [1, 2, 3, 5, 6, 7, 9, 101] {
            XCTAssertEqual(LevelCurve.objective(for: level), .timed, "level \(level)")
        }
        for level in [4, 8, 12, 40, 100] {
            XCTAssertEqual(LevelCurve.objective(for: level), .moves(LevelCurve.movesBudget(for: level)), "level \(level)")
        }
    }

    func testMovesBudgetIsPairsPlusTheMistakeBudget() {
        for level in [4, 8, 24, 48, 100] {
            XCTAssertEqual(
                LevelCurve.movesBudget(for: level),
                LevelCurve.pairs(for: level) + LevelCurve.maxFailures(for: level),
                "level \(level)"
            )
            XCTAssertGreaterThan(LevelCurve.movesBudget(for: level), LevelCurve.pairs(for: level), "a perfect clear must fit")
        }
    }

    func testBombAndChainStepsStartAfterIce() {
        XCTAssertEqual(LevelCurve.bombCards(for: 9), 0)
        XCTAssertEqual(LevelCurve.bombCards(for: 10), 1)
        XCTAssertEqual(LevelCurve.bombCards(for: 30), 2)
        XCTAssertEqual(LevelCurve.chainedCards(for: 14), 0)
        XCTAssertEqual(LevelCurve.chainedCards(for: 15), 1)
        XCTAssertEqual(LevelCurve.chainedCards(for: 35), 2)
        XCTAssertEqual(LevelCurve.chainLength, 2)
    }

    /// Every blocker takes one card of a distinct pair, so together they must
    /// fit the board or the later ones silently fail to place.
    func testAllBlockersTogetherFitEveryBoard() {
        for level in 1...200 {
            let blockers = LevelCurve.frozenCards(for: level) + LevelCurve.bombCards(for: level) + LevelCurve.chainedCards(for: level)
            XCTAssertLessThanOrEqual(blockers, LevelCurve.pairs(for: level), "level \(level) has more blockers than pairs")
        }
    }
}

extension StreakRewardTests {
    func testMovesLevelsNeverChargeTheClockPowerUps() {
        for streak in 0...12 {
            if let reward = StreakReward.powerUp(forStreak: streak, objective: .moves(10)) {
                XCTAssertTrue(LevelPowerUp.available(for: .moves(10)).contains(reward), "streak \(streak) charged \(reward)")
            }
        }
        XCTAssertEqual(StreakReward.powerUp(forStreak: 3, objective: .moves(10)), .peek)
        XCTAssertEqual(StreakReward.powerUp(forStreak: 5, objective: .moves(10)), .revealPair)
        XCTAssertEqual(StreakReward.powerUp(forStreak: 5, objective: .timed), .freeze)
    }

    func testTimeAndFreezeAreDroppedOnAMovesLevel() {
        XCTAssertEqual(LevelPowerUp.available(for: .timed), LevelPowerUp.allCases)
        XCTAssertEqual(LevelPowerUp.available(for: .moves(10)), [.peek, .revealPair])
    }
}
