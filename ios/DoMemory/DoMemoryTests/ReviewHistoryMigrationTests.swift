//
//  ReviewHistoryMigrationTests.swift
//  DoMemoryTests
//

import ReviewFlow
import XCTest
@testable import DoMemory

/// The migration runs exactly once per install and is unobservable when it goes
/// wrong: a bad carry-over either re-prompts a player who was already asked, or
/// silently suppresses the prompt forever. Neither shows up in a screenshot, so
/// the mapping is pinned here instead.
@MainActor
final class ReviewHistoryMigrationTests: XCTestCase {
    private let suiteName = "ReviewHistoryMigrationTests"
    private var defaults: UserDefaults!
    private var store: SpyStore!

    override func setUpWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        store = SpyStore()
    }

    override func tearDownWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
    }

    // MARK: - Carrying legacy state across

    func testCarriesWinsCooldownAndPromptedVersionAcross() throws {
        let lastPrompted = Date(timeIntervalSince1970: 1_700_000_000)
        defaults.set(4, forKey: UserDefaultsKeys.reviewSuccessfulWins)
        defaults.set(lastPrompted, forKey: UserDefaultsKeys.reviewLastPromptedDate)
        defaults.set("3.1.0", forKey: UserDefaultsKeys.reviewLastPromptedVersion)

        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)

        let saved = try XCTUnwrap(store.saved)
        XCTAssertEqual(saved.completedActionCount, 4)
        XCTAssertEqual(saved.lastRequestDate, lastPrompted)
        XCTAssertEqual(saved.requestCountByVersion, ["3.1.0": 1])
        // Anchoring first use to the last prompt keeps the cooldown meaningful
        // without letting the "days since first use" gate re-block the player.
        XCTAssertEqual(saved.firstUseDate, lastPrompted)
    }

    /// Wins but no prompt yet: there is no date to anchor to, so first use has
    /// to be backdated far enough that the new-user window is already clear.
    func testBackdatesFirstUseWhenThePlayerWasNeverPrompted() throws {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        defaults.set(2, forKey: UserDefaultsKeys.reviewSuccessfulWins)

        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store, now: now)

        let saved = try XCTUnwrap(store.saved)
        XCTAssertEqual(saved.completedActionCount, 2)
        XCTAssertNil(saved.lastRequestDate)
        XCTAssertTrue(saved.requestCountByVersion.isEmpty)
        XCTAssertEqual(
            saved.firstUseDate,
            try XCTUnwrap(Calendar.current.date(byAdding: .day, value: -30, to: now))
        )
    }

    // MARK: - When it must not write

    func testFreshInstallIsLeftForReviewFlowToStartClean() {
        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)

        XCTAssertNil(store.saved)
    }

    func testNeverOverwritesHistoryReviewFlowAlreadyOwns() throws {
        let live = ReviewHistory(firstUseDate: Date(timeIntervalSince1970: 1), completedActionCount: 9)
        store.saved = live
        defaults.set(4, forKey: UserDefaultsKeys.reviewSuccessfulWins)

        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)

        XCTAssertEqual(store.saved, live)
        XCTAssertEqual(store.saveCount, 0)
    }

    // MARK: - Running exactly once

    func testClearsLegacyKeysAndMarksItselfDoneEvenWhenNothingWasCarried() {
        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)

        XCTAssertTrue(defaults.bool(forKey: UserDefaultsKeys.reviewHistoryMigrated))
        XCTAssertNil(defaults.object(forKey: UserDefaultsKeys.reviewSuccessfulWins))
        XCTAssertNil(defaults.object(forKey: UserDefaultsKeys.reviewLastPromptedVersion))
        XCTAssertNil(defaults.object(forKey: UserDefaultsKeys.reviewLastPromptedDate))
    }

    /// A second run must not resurrect anything. Legacy keys are gone by then,
    /// so a re-run would otherwise save an empty history over real progress.
    func testASecondRunDoesNothing() {
        defaults.set(4, forKey: UserDefaultsKeys.reviewSuccessfulWins)
        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)
        XCTAssertEqual(store.saveCount, 1)

        store.saved = nil
        ReviewHistoryMigration.runIfNeeded(defaults: defaults, store: store)

        XCTAssertEqual(store.saveCount, 1)
        XCTAssertNil(store.saved)
    }
}

@MainActor
private final class SpyStore: ReviewHistoryStore {
    var saved: ReviewHistory?
    private(set) var saveCount = 0

    func load() -> ReviewHistory? { saved }

    func save(_ history: ReviewHistory) {
        saved = history
        saveCount += 1
    }
}
