//
//  AdFreeDayServiceTests.swift
//  DoMemoryTests
//
//  Pins the two-ad chain behind the ad-free day: one ad grants nothing, the
//  second completes and resets, progress survives a relaunch but not a new
//  local day, and the apology copy is shown exactly once.
//

import XCTest
@testable import DoMemory

@MainActor
final class AdFreeDayServiceTests: XCTestCase {
    private static let suiteName = "AdFreeDayServiceTests"

    private var defaults: UserDefaults!
    private var service: AdFreeDayService!

    // Noon on two days 48 hours apart, so the day seed differs in every time zone.
    private let dayOne = Date(timeIntervalSince1970: 1_790_337_600)
    private let dayThree = Date(timeIntervalSince1970: 1_790_337_600 + 48 * 60 * 60)

    override func setUpWithError() throws {
        defaults = try XCTUnwrap(UserDefaults(suiteName: Self.suiteName))
        defaults.removePersistentDomain(forName: Self.suiteName)
        service = AdFreeDayService(defaults: defaults)
    }

    override func tearDownWithError() throws {
        defaults.removePersistentDomain(forName: Self.suiteName)
    }

    func testChainRequiresTwoAds() {
        XCTAssertEqual(AdFreeDayService.requiredAds, 2, "The offer copy, the stepper and the spec all promise two ads")
    }

    func testStartsWithNothingWatched() {
        XCTAssertEqual(service.adsWatched(for: dayOne), 0)
        XCTAssertEqual(service.adsRemaining(for: dayOne), AdFreeDayService.requiredAds)
    }

    func testFirstAdDoesNotCompleteTheChain() {
        XCTAssertFalse(service.recordAdWatched(for: dayOne), "one ad must never grant the day")
        XCTAssertEqual(service.adsWatched(for: dayOne), 1)
        XCTAssertEqual(service.adsRemaining(for: dayOne), 1)
    }

    func testSecondAdCompletesAndResetsProgress() {
        service.recordAdWatched(for: dayOne)
        XCTAssertTrue(service.recordAdWatched(for: dayOne))
        XCTAssertEqual(service.adsWatched(for: dayOne), 0, "a completed chain starts the next one from zero")
    }

    func testProgressPersistsAcrossInstancesWithinTheDay() {
        service.recordAdWatched(for: dayOne)
        let relaunched = AdFreeDayService(defaults: defaults)
        XCTAssertEqual(relaunched.adsWatched(for: dayOne), 1)
    }

    func testProgressResetsOnANewDay() {
        service.recordAdWatched(for: dayOne)
        XCTAssertEqual(service.adsWatched(for: dayThree), 0)
        XCTAssertFalse(service.recordAdWatched(for: dayThree), "yesterday's ad must not count toward today's chain")
    }

    func testIntroIsShownOnce() {
        XCTAssertFalse(service.hasSeenIntro)
        service.markIntroSeen()
        XCTAssertTrue(service.hasSeenIntro)
        XCTAssertTrue(AdFreeDayService(defaults: defaults).hasSeenIntro)
    }

    func testResetClearsProgressAndIntro() {
        service.recordAdWatched(for: dayOne)
        service.markIntroSeen()
        service.reset()
        XCTAssertEqual(service.adsWatched(for: dayOne), 0)
        XCTAssertFalse(service.hasSeenIntro)
    }
}
