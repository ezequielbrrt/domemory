//
//  LottieAssetsTests.swift
//  DoMemoryTests
//
//  The bundled animations are hand-authored JSON (see
//  `assets/lottie/generate_animations.py`), not exported from After Effects —
//  nothing checks that Lottie's own parser accepts them until a player
//  actually reaches the moment that plays one. This exercises Lottie's real
//  decoder against the shipped files, without needing a UI at all.
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

    /// Every clip `LottieView(tint:)` is handed must carry a shape named
    /// `tint`, or the palette colour silently never lands and the clip plays
    /// in its authored light-mode colour on a dark surface.
    func testTintedClipsExposeTheTintKeypath() throws {
        for name in ["clock-crack", "x-shake", "heart-break", "heart-refill", "freeze-thaw", "star-sparkle"] {
            let url = try XCTUnwrap(appBundle.url(forResource: name, withExtension: "json"), "\(name).json is not bundled.")
            let json = try String(contentsOf: url, encoding: .utf8)
            XCTAssertTrue(json.contains("\"nm\": \"tint\""), "\(name).json has no shape named tint.")
        }
    }

    func testLoseHeroesDecodeAndEndOnAStillFrameWithinASecond() throws {
        for name in ["clock-crack", "x-shake"] {
            let animation = try loadAnimation(named: name)
            XCTAssertLessThanOrEqual(animation.duration, 1.0, name)
        }
    }

    func testOverlayBurstsDecodeAndStayUnderASecond() throws {
        // Transient effects over a heart, a chip or a star: they must not
        // outstay the state change they decorate.
        for name in ["heart-break", "heart-refill", "freeze-thaw", "star-sparkle"] {
            let animation = try loadAnimation(named: name)
            XCTAssertLessThan(animation.duration, 1.0, name)
        }
    }

    func testStarPopDecodesAndIsShortAndSnappy() throws {
        let animation = try loadAnimation(named: "star-pop")

        // "Well under 1s", per the win-modal spec this animation was authored for.
        XCTAssertLessThan(animation.duration, 1.0)
    }
}
