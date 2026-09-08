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
        emojiPool: [String]? = nil
    ) -> [String: Any] {
        [
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
}
