//
//  SeasonCatalogServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class SeasonCatalogServiceTests: XCTestCase {
    private static let pool = ["👻", "🎃", "🕷️", "🦇", "🧛", "⚰️", "🕸️", "🔮", "🍬", "🧟", "🪦", "😱"]

    private let defaults = UserDefaults.standard

    /// UTC so window assertions cannot depend on the machine's timezone.
    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    override func setUpWithError() throws {
        defaults.removeObject(forKey: UserDefaultsKeys.seasonCatalog)
    }

    override func tearDownWithError() throws {
        defaults.removeObject(forKey: UserDefaultsKeys.seasonCatalog)
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private func body(
        enabled: Bool = true,
        startDate: String = "2026-10-01",
        endDate: String = "2026-11-02",
        priority: Int = 10,
        levelCount: Int = 20,
        emojiPool: [String]? = nil,
        cardImageURL: String? = nil,
        backgroundImageURL: String? = nil,
        backgroundImageURLDark: String? = nil
    ) -> [String: Any] {
        var body: [String: Any] = [
            "enabled": enabled,
            "startDate": startDate,
            "endDate": endDate,
            "priority": priority,
            "levelCount": levelCount,
            "icon": "🎃",
            "accentColor": "#FF6B1A",
            "emojiPool": emojiPool ?? Self.pool,
            "strings": ["en": ["title": "Spooky Season", "subtitle": "20 haunted levels"]]
        ]
        body["cardImageURL"] = cardImageURL
        body["backgroundImageURL"] = backgroundImageURL
        body["backgroundImageURLDark"] = backgroundImageURLDark
        return body
    }

    private let cardURL = URL(string: "https://example.com/seasons/spooky-2026/card.png")!
    private let lightURL = URL(string: "https://example.com/seasons/spooky-2026/bg.png")!
    private let darkURL = URL(string: "https://example.com/seasons/spooky-2026/bg-dark.png")!

    private func bodyWithArtwork(
        enabled: Bool = true,
        startDate: String = "2026-10-01",
        endDate: String = "2026-11-02",
        priority: Int = 10
    ) -> [String: Any] {
        body(
            enabled: enabled,
            startDate: startDate,
            endDate: endDate,
            priority: priority,
            cardImageURL: cardURL.absoluteString,
            backgroundImageURL: lightURL.absoluteString,
            backgroundImageURLDark: darkURL.absoluteString
        )
    }

    /// Thread-safe spy for the `prefetchImage` seam: `SeasonCatalogService`
    /// invokes it from a detached task, off the main actor, so recording must
    /// not race the test's own reads of `urls`.
    private final class PrefetchSpy: @unchecked Sendable {
        private let lock = NSLock()
        private var recordedURLs: [URL] = []
        let expectation: XCTestExpectation

        /// `expectedFulfillmentCount` must be at least 1 (`XCTestExpectation`'s
        /// own requirement). A test that expects *no* calls should simply
        /// never await `expectation` and instead assert `urls.isEmpty` after
        /// a short delay.
        init(expectedFulfillmentCount: Int = 1, description: String = "prefetch") {
            expectation = XCTestExpectation(description: description)
            expectation.expectedFulfillmentCount = expectedFulfillmentCount
        }

        var urls: [URL] {
            lock.lock()
            defer { lock.unlock() }
            return recordedURLs
        }

        func record(_ url: URL) {
            lock.lock()
            recordedURLs.append(url)
            lock.unlock()
            expectation.fulfill()
        }
    }

    // MARK: - Payload decoding

    func testTheDictionaryKeyBecomesTheSeasonID() {
        let seasons = SeasonCatalogService.decodeSeasons(from: ["spooky-2026": body()])
        XCTAssertEqual(seasons.map(\.id), ["spooky-2026"])
    }

    func testAMalformedSeasonIsSkippedWithoutLosingTheValidOnes() {
        let payload: [String: Any] = [
            "spooky-2026": body(),
            "broken": ["enabled": true],
            "too-small": body(emojiPool: Array(Self.pool.prefix(11))),
            "not-an-object": "nonsense"
        ]
        let seasons = SeasonCatalogService.decodeSeasons(from: payload)
        XCTAssertEqual(
            seasons.map(\.id),
            ["spooky-2026"],
            "one bad season must not cost the player a valid one"
        )
    }

    func testDecodedSeasonsComeBackInAStableOrder() {
        let payload: [String: Any] = [
            "zeta": body(),
            "alpha": body(),
            "middle": body()
        ]
        // Dictionary iteration order is unspecified; the service must impose one.
        XCTAssertEqual(SeasonCatalogService.decodeSeasons(from: payload).map(\.id), ["alpha", "middle", "zeta"])
    }

    func testAnEmptyPayloadDecodesToNothing() {
        XCTAssertTrue(SeasonCatalogService.decodeSeasons(from: [:]).isEmpty)
    }

    // MARK: - Active selection

    func testHighestPriorityActiveSeasonWins() {
        let seasons = SeasonCatalogService.decodeSeasons(from: [
            "low": body(priority: 1),
            "high": body(priority: 99)
        ])
        let active = SeasonCatalogService.selectActive(from: seasons, on: date(2026, 10, 15), calendar: calendar)
        XCTAssertEqual(active?.id, "high")
    }

    func testAHigherPriorityButInactiveSeasonLoses() {
        let seasons = SeasonCatalogService.decodeSeasons(from: [
            "active-low": body(priority: 1),
            "inactive-high": body(startDate: "2027-01-01", endDate: "2027-02-01", priority: 99)
        ])
        let active = SeasonCatalogService.selectActive(from: seasons, on: date(2026, 10, 15), calendar: calendar)
        XCTAssertEqual(active?.id, "active-low")
    }

    func testEqualPriorityTieBreaksOnIDDeterministically() {
        let payload: [String: Any] = [
            "zeta-season": body(priority: 5),
            "alpha-season": body(priority: 5)
        ]
        // Re-decoding from scratch each time exercises a fresh dictionary
        // ordering; the winner must not move between "launches".
        for _ in 0..<10 {
            let seasons = SeasonCatalogService.decodeSeasons(from: payload)
            let active = SeasonCatalogService.selectActive(from: seasons, on: date(2026, 10, 15), calendar: calendar)
            XCTAssertEqual(active?.id, "alpha-season")
        }
    }

    func testNoActiveSeasonOutsideEveryWindow() {
        let seasons = SeasonCatalogService.decodeSeasons(from: ["spooky-2026": body()])
        XCTAssertNil(SeasonCatalogService.selectActive(from: seasons, on: date(2026, 9, 30), calendar: calendar))
    }

    func testDisabledSeasonIsNeverSelected() {
        let seasons = SeasonCatalogService.decodeSeasons(from: ["spooky-2026": body(enabled: false)])
        XCTAssertNil(SeasonCatalogService.selectActive(from: seasons, on: date(2026, 10, 15), calendar: calendar))
    }

    // MARK: - Cache

    func testApplyingAPayloadCachesItForTheNextLaunch() {
        let service = SeasonCatalogService(defaults: defaults)
        service.apply(payload: ["spooky-2026": body()], on: date(2026, 10, 15))

        // A "cold launch": a brand new instance reading only the cache.
        let relaunched = SeasonCatalogService(defaults: defaults)
        XCTAssertEqual(relaunched.seasons.map(\.id), ["spooky-2026"])
        relaunched.refreshActiveSeason(on: date(2026, 10, 15), calendar: calendar)
        XCTAssertEqual(relaunched.activeSeason?.id, "spooky-2026")
    }

    func testFailsClosedWithNoCacheAndNoNetwork() {
        let service = SeasonCatalogService(defaults: defaults)
        XCTAssertTrue(service.seasons.isEmpty)
        XCTAssertNil(service.activeSeason)
    }

    func testACorruptCacheIsTreatedAsAbsent() {
        defaults.set(Data("not json".utf8), forKey: UserDefaultsKeys.seasonCatalog)
        let service = SeasonCatalogService(defaults: defaults)
        XCTAssertTrue(service.seasons.isEmpty)
        XCTAssertNil(service.activeSeason)
    }

    func testTheCachedSeasonStillFallsOutOfItsWindow() {
        let service = SeasonCatalogService(defaults: defaults)
        service.apply(payload: ["spooky-2026": body()], on: date(2026, 10, 15))

        let relaunched = SeasonCatalogService(defaults: defaults)
        relaunched.refreshActiveSeason(on: date(2026, 11, 3), calendar: calendar)
        XCTAssertNil(
            relaunched.activeSeason,
            "an expired season must not linger just because it is cached"
        )
    }

    // MARK: - Artwork prefetch

    /// When the active season changes to a new season carrying artwork, its
    /// card and both background variants must be handed to the prefetch seam
    /// — the cache-warm this phase exists to add.
    func testActiveSeasonChangeToASeasonWithArtworkPrefetchesCardAndBothBackgrounds() async {
        let spy = PrefetchSpy(expectedFulfillmentCount: 3)
        let service = SeasonCatalogService(defaults: defaults) { url in spy.record(url) }

        service.apply(payload: ["spooky-2026": bodyWithArtwork()], on: date(2026, 10, 15))

        await fulfillment(of: [spy.expectation], timeout: 2)
        XCTAssertEqual(Set(spy.urls), Set([cardURL, lightURL, darkURL]))
    }

    /// `refreshActiveSeason` runs repeatedly (e.g. on every app foreground).
    /// Re-evaluating to the *same* still-active season must not spawn another
    /// round of prefetch tasks.
    func testRefreshingTheSameActiveSeasonAgainDoesNotRetriggerPrefetch() async {
        let spy = PrefetchSpy(expectedFulfillmentCount: 3)
        let service = SeasonCatalogService(defaults: defaults) { url in spy.record(url) }

        service.apply(payload: ["spooky-2026": bodyWithArtwork()], on: date(2026, 10, 15))
        await fulfillment(of: [spy.expectation], timeout: 2)
        XCTAssertEqual(spy.urls.count, 3, "the initial change should prefetch exactly once per URL")

        // Re-evaluate for the same date/season, as a foreground refresh would.
        service.refreshActiveSeason(on: date(2026, 10, 16), calendar: calendar)
        XCTAssertEqual(service.activeSeason?.id, "spooky-2026")

        // Give any (incorrectly) spawned task a chance to run before asserting
        // its absence.
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertEqual(
            spy.urls.count,
            3,
            "re-evaluating to the same active season must not spawn another prefetch round"
        )
    }

    /// A season with no artwork URLs must not crash and must simply prefetch
    /// nothing for the absent URLs.
    func testASeasonWithNoArtworkPrefetchesNothing() async {
        let spy = PrefetchSpy()
        let service = SeasonCatalogService(defaults: defaults) { url in spy.record(url) }

        service.apply(payload: ["spooky-2026": body()], on: date(2026, 10, 15))
        XCTAssertEqual(service.activeSeason?.id, "spooky-2026")

        // Nothing to await: give a (hypothetical, incorrect) task a moment to
        // run before asserting no URLs were ever recorded.
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertTrue(spy.urls.isEmpty)
    }

    /// Prefetching must be fire-and-forget: `init` and `apply(payload:on:)`
    /// remain synchronous and their other observable behavior (the
    /// synchronously-updated `activeSeason`/`seasons`) is unchanged.
    func testInitAndApplyRemainSynchronousWithPrefetchWired() {
        let spy = PrefetchSpy(expectedFulfillmentCount: 3)
        // `init` itself triggers no prefetch (no seasons are known yet), but
        // must still return synchronously with its usual empty state.
        let service = SeasonCatalogService(defaults: defaults) { url in spy.record(url) }
        XCTAssertTrue(service.seasons.isEmpty)
        XCTAssertNil(service.activeSeason)

        // `apply(payload:on:)` must update `seasons`/`activeSeason`
        // synchronously, before any prefetch task has had a chance to run.
        service.apply(payload: ["spooky-2026": bodyWithArtwork()], on: date(2026, 10, 15))
        XCTAssertEqual(service.seasons.map(\.id), ["spooky-2026"])
        XCTAssertEqual(service.activeSeason?.id, "spooky-2026")
    }
}
