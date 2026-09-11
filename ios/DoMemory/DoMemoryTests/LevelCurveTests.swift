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
