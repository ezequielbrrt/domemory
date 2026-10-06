//
//  WhatsNewManagerTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

/// The version gate is decided once, in `init`, and then never observed again.
/// Getting it wrong is silent in both directions: a first-time player is greeted
/// with release notes for a version they have never not had, or an upgrading
/// player never learns what changed. Neither shows up in a screenshot of a
/// working app, so every branch of the gate is pinned here.
@MainActor
final class WhatsNewManagerTests: XCTestCase {
    private let suiteName = "WhatsNewManagerTests"
    private var defaults: UserDefaults!

    private var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
    }

    override func setUpWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
    }

    override func tearDownWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
    }

    // MARK: - First launch

    func testFreshInstallDoesNotShowTheSheet() {
        let manager = WhatsNewManager(defaults: defaults, isExistingUser: false)

        XCTAssertFalse(manager.shouldShow)
    }

    func testFreshInstallRecordsTheRunningVersionSoTheSheetStaysQuiet() {
        _ = WhatsNewManager(defaults: defaults, isExistingUser: false)

        XCTAssertEqual(
            defaults.string(forKey: UserDefaultsKeys.whatsNewLastSeenVersion),
            currentVersion
        )

        // Second launch of the same install, now onboarded: still nothing to announce.
        let next = WhatsNewManager(defaults: defaults, isExistingUser: true)
        XCTAssertFalse(next.shouldShow)
    }

    // MARK: - Existing installs

    /// Upgrading from a build that predates this manager leaves no stored version.
    /// That reads identically to a fresh install in `UserDefaults`, which is the
    /// whole reason `isExistingUser` exists — these players have earned the sheet.
    func testExistingUserWithNoStoredVersionSeesTheSheet() {
        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true)

        XCTAssertTrue(manager.shouldShow)
    }

    func testExistingUserOnAnOlderVersionSeesTheSheet() {
        defaults.set("0.0.1", forKey: UserDefaultsKeys.whatsNewLastSeenVersion)

        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true)

        XCTAssertTrue(manager.shouldShow)
    }

    func testExistingUserWhoAlreadySawThisVersionDoesNotSeeItAgain() {
        defaults.set(currentVersion, forKey: UserDefaultsKeys.whatsNewLastSeenVersion)

        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true)

        XCTAssertFalse(manager.shouldShow)
    }

    // MARK: - Releases with nothing to announce

    /// 4.5.0 ships fixes only and keeps the 4.4.1 notes. A player who dismissed the
    /// sheet on 4.4.1 must not be shown the same items again under a new number.
    func testAPlayerWhoSawTheseNotesIsNotShownThemAgainOnALaterVersion() {
        defaults.set("4.4.1", forKey: UserDefaultsKeys.whatsNewLastSeenVersion)

        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true, releaseNotesVersion: "4.4.1")

        XCTAssertFalse(manager.shouldShow)
    }

    /// Someone jumping from before the notes' release (say 4.3.0 straight to 4.5.0) has
    /// not seen those features yet, so the sheet still appears for them once.
    func testAPlayerFromBeforeTheNotesReleaseStillSeesThem() {
        defaults.set("4.3.0", forKey: UserDefaultsKeys.whatsNewLastSeenVersion)

        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true, releaseNotesVersion: "4.4.1")

        XCTAssertTrue(manager.shouldShow)
    }

    func testVersionsCompareNumericallyNotAsText() {
        XCTAssertTrue(WhatsNewManager.hasUnreadNotes(seen: "4.9.0", releaseNotesVersion: "4.10.0"))
        XCTAssertFalse(WhatsNewManager.hasUnreadNotes(seen: "4.10.0", releaseNotesVersion: "4.9.0"))
        XCTAssertTrue(WhatsNewManager.hasUnreadNotes(seen: nil, releaseNotesVersion: "4.4.1"))
    }

    func testTheShippedNotesAreNoNewerThanTheApp() {
        XCTAssertFalse(
            WhatsNewManager.hasUnreadNotes(seen: currentVersion, releaseNotesVersion: WhatsNewManager.releaseNotesVersion),
            "releaseNotesVersion is ahead of the app version, so dismissing the sheet would never stick"
        )
    }

    // MARK: - Dismissal

    func testMarkSeenPersistsTheVersionAndHidesTheSheet() {
        defaults.set("0.0.1", forKey: UserDefaultsKeys.whatsNewLastSeenVersion)
        let manager = WhatsNewManager(defaults: defaults, isExistingUser: true)
        XCTAssertTrue(manager.shouldShow)

        manager.markSeen()

        XCTAssertFalse(manager.shouldShow)
        XCTAssertEqual(
            defaults.string(forKey: UserDefaultsKeys.whatsNewLastSeenVersion),
            currentVersion
        )
    }
}
