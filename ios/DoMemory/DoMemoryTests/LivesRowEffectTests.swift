//
//  LivesRowEffectTests.swift
//  DoMemoryTests
//
//  Pins which heart the Levels/Seasons headers animate when the lives count
//  changes. Mirrored by Android's `HeaderEffectsTest`.
//

import XCTest
@testable import DoMemory

final class LivesRowEffectTests: XCTestCase {
    func testDropBreaksTheFirstNowEmptyHeart() {
        XCTAssertEqual(LivesRowEffect.forTransition(from: 3, to: 2), .lost(slot: 2))
        XCTAssertEqual(LivesRowEffect.forTransition(from: 1, to: 0), .lost(slot: 0))
    }

    func testRiseBurstsTheLastNowFilledHeart() {
        XCTAssertEqual(LivesRowEffect.forTransition(from: 2, to: 3), .gained(slot: 2))
        XCTAssertEqual(LivesRowEffect.forTransition(from: 0, to: 1), .gained(slot: 0))
    }

    func testDayResetAnimatesOneHeartNotFour() {
        XCTAssertEqual(LivesRowEffect.forTransition(from: 0, to: 4), .gained(slot: 3))
    }

    func testNoChangeIsNoEffect() {
        XCTAssertNil(LivesRowEffect.forTransition(from: 2, to: 2))
    }
}
