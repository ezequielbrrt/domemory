//
//  LottieAssetsTests.swift
//  DoMemoryTests
//
//  The two bundled win-moment animations are hand-authored JSON, not exported
//  from After Effects — nothing checks that Lottie's own parser accepts them
//  until a player actually reaches the win screen. This exercises Lottie's
//  real decoder against the shipped files, without needing a UI at all.
//

import XCTest
import Lottie
@testable import DoMemory

final class LottieAssetsTests: XCTestCase {
    /// Resolved from a type in the app module rather than `Bundle.main`, so
    /// the lookup is the app bundle no matter how the test target is hosted
    /// (see `LocalizationParityTests`).
    private var appBundle: Bundle { Bundle(for: SeasonProgressService.self) }

    private func loadAnimation(named name: String) throws -> LottieAnimation {
        try XCTUnwrap(
            LottieAnimation.named(name, bundle: appBundle),
            "\"\(name).json\" did not decode as a Lottie animation."
        )
    }

    func testConfettiBurstDecodesAndPlaysOnce() throws {
        let animation = try loadAnimation(named: "confetti-burst")

        // 1-1.5s, per the win-modal spec this animation was authored for.
        XCTAssertGreaterThanOrEqual(animation.duration, 1.0)
        XCTAssertLessThanOrEqual(animation.duration, 1.5)
    }

    func testStarPopDecodesAndIsShortAndSnappy() throws {
        let animation = try loadAnimation(named: "star-pop")

        // "Well under 1s", per the win-modal spec this animation was authored for.
        XCTAssertLessThan(animation.duration, 1.0)
    }
}
