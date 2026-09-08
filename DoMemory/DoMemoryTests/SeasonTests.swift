//
//  SeasonTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class SeasonTests: XCTestCase {
    /// Twelve distinct emoji — the smallest pool a season may legally carry.
    private static let minimalPool = ["👻", "🎃", "🕷️", "🦇", "🧛", "⚰️", "🕸️", "🔮", "🍬", "🧟", "🪦", "😱"]

    /// UTC so a boundary assertion cannot depend on the machine's timezone.
    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private func decodeSeason(_ body: [String: Any]) throws -> Season {
        let data = try JSONSerialization.data(withJSONObject: body, options: [])
        return try JSONDecoder().decode(Season.self, from: data)
    }

    private func payload(
        id: String = "spooky-2026",
        enabled: Bool = true,
        startDate: String = "2026-10-01",
        endDate: String = "2026-11-02",
        priority: Int = 10,
        levelCount: Int = 20,
        emojiPool: [String]? = nil,
        strings: [String: Any] = ["en": ["title": "Spooky Season", "subtitle": "20 haunted levels"]]
    ) -> [String: Any] {
        [
            "id": id,
            "enabled": enabled,
            "startDate": startDate,
            "endDate": endDate,
            "priority": priority,
            "levelCount": levelCount,
            "icon": "🎃",
            "accentColor": "#FF6B1A",
            "emojiPool": emojiPool ?? Self.minimalPool,
            "strings": strings
        ]
    }

    // MARK: - Emoji pool validation

    func testMinimumEmojiPoolSizeTracksTheLevelCurveCap() {
        // LevelCurve tops out at 12 pairs; a season pool must be able to fill
        // that board. If the curve is retuned this assertion is the tripwire.
        XCTAssertEqual(Season.minimumEmojiPoolSize, 12)
        XCTAssertEqual(Season.minimumEmojiPoolSize, LevelCurve.pairs(for: 50))
    }

    func testDecodingAcceptsAPoolOfExactlyTwelve() throws {
        let season = try decodeSeason(payload())
        XCTAssertEqual(season.emojiPool.count, 12)
    }

    func testDecodingRejectsAPoolBelowTwelve() {
        let short = Array(Self.minimalPool.prefix(11))
        XCTAssertThrowsError(try decodeSeason(payload(emojiPool: short))) { error in
            XCTAssertTrue(error is DecodingError, "a short pool must fail decoding, not decode to a broken season")
        }
    }

    func testDuplicateEmojiDoNotCountTowardTheMinimum() {
        // 11 distinct + 1 repeat = 12 entries but only 11 usable pairs.
        let padded = Array(Self.minimalPool.prefix(11)) + ["👻"]
        XCTAssertEqual(padded.count, 12)
        XCTAssertThrowsError(try decodeSeason(payload(emojiPool: padded)))
    }

    func testDecodingDeduplicatesThePoolPreservingOrder() throws {
        let withRepeat = Self.minimalPool + ["👻", "🎃"]
        let season = try decodeSeason(payload(emojiPool: withRepeat))
        XCTAssertEqual(season.emojiPool, Self.minimalPool)
    }

    func testDecodingRejectsANonPositiveLevelCount() {
        XCTAssertThrowsError(try decodeSeason(payload(levelCount: 0)))
    }

    func testAMissingEnabledFlagFailsClosed() throws {
        var body = payload()
        body.removeValue(forKey: "enabled")
        let season = try decodeSeason(body)
        XCTAssertFalse(season.enabled)
        XCTAssertFalse(season.isActive(on: date(2026, 10, 15), calendar: calendar))
    }

    // MARK: - Locale resolution

    func testExactLocaleMatchWins() throws {
        let season = try decodeSeason(payload(strings: [
            "en": ["title": "Spooky Season", "subtitle": "20 haunted levels"],
            "es-419": ["title": "Temporada de Sustos", "subtitle": "20 niveles embrujados"]
        ]))
        let text = season.text(for: Locale(identifier: "es_419"))
        XCTAssertEqual(text.title, "Temporada de Sustos")
        XCTAssertEqual(text.subtitle, "20 niveles embrujados")
    }

    func testLocaleFallsBackToLanguageOnly() throws {
        let season = try decodeSeason(payload(strings: [
            "en": ["title": "Spooky Season", "subtitle": "en subtitle"],
            "es": ["title": "Temporada", "subtitle": "es subtitle"]
        ]))
        // No `es-419` entry, so the language-only `es` entry answers.
        XCTAssertEqual(season.text(for: Locale(identifier: "es_419")).title, "Temporada")
        XCTAssertEqual(season.text(for: Locale(identifier: "es_MX")).title, "Temporada")
    }

    func testLocaleFallsBackToEnglish() throws {
        let season = try decodeSeason(payload(strings: [
            "en": ["title": "Spooky Season", "subtitle": "en subtitle"]
        ]))
        XCTAssertEqual(season.text(for: Locale(identifier: "ja_JP")).title, "Spooky Season")
        XCTAssertEqual(season.text(for: Locale(identifier: "es_419")).title, "Spooky Season")
    }

    func testLocaleFallsBackToTheAppSideConstantWhenNothingMatches() throws {
        let season = try decodeSeason(payload(strings: [
            "de": ["title": "Grusel-Saison", "subtitle": "de subtitle"]
        ]))
        let text = season.text(for: Locale(identifier: "ja_JP"))
        XCTAssertEqual(text.title, Season.fallbackTitle)
        XCTAssertEqual(text.subtitle, "")
    }

    func testEmptyStringsDictionaryFallsBack() throws {
        let season = try decodeSeason(payload(strings: [:]))
        XCTAssertEqual(season.text(for: Locale(identifier: "en_US")).title, Season.fallbackTitle)
    }

    func testLocaleCandidateChainOrder() {
        XCTAssertEqual(Season.localeCandidates(for: "es_419"), ["es-419", "es", "en"])
        XCTAssertEqual(Season.localeCandidates(for: "pt-BR"), ["pt-br", "pt", "en"])
        XCTAssertEqual(Season.localeCandidates(for: "en_US"), ["en-us", "en"])
        // A script-bearing identifier must still be able to reach `zh-Hans`.
        XCTAssertEqual(Season.localeCandidates(for: "zh_Hans_CN"), ["zh-hans-cn", "zh-hans", "zh", "en"])
    }

    func testLocaleKeysMatchCaseInsensitively() throws {
        let season = try decodeSeason(payload(strings: [
            "PT-br": ["title": "Temporada Assustadora", "subtitle": ""]
        ]))
        XCTAssertEqual(season.text(for: Locale(identifier: "pt_BR")).title, "Temporada Assustadora")
    }

    func testMissingSubtitleDecodesAsEmpty() throws {
        let season = try decodeSeason(payload(strings: ["en": ["title": "Spooky Season"]]))
        XCTAssertEqual(season.text(for: Locale(identifier: "en_US")).subtitle, "")
    }

    // MARK: - Activation window

    func testActiveOnTheFirstDayOfTheWindow() throws {
        let season = try decodeSeason(payload())
        XCTAssertTrue(season.isActive(on: date(2026, 10, 1), calendar: calendar))
    }

    func testActiveOnTheLastDayOfTheWindow() throws {
        let season = try decodeSeason(payload())
        XCTAssertTrue(season.isActive(on: date(2026, 11, 2), calendar: calendar))
    }

    func testInactiveTheDayBeforeAndTheDayAfter() throws {
        let season = try decodeSeason(payload())
        XCTAssertFalse(season.isActive(on: date(2026, 9, 30), calendar: calendar))
        XCTAssertFalse(season.isActive(on: date(2026, 11, 3), calendar: calendar))
    }

    func testSingleDaySeasonIsActiveOnlyThatDay() throws {
        let season = try decodeSeason(payload(startDate: "2026-12-25", endDate: "2026-12-25"))
        XCTAssertTrue(season.isActive(on: date(2026, 12, 25), calendar: calendar))
        XCTAssertFalse(season.isActive(on: date(2026, 12, 24), calendar: calendar))
        XCTAssertFalse(season.isActive(on: date(2026, 12, 26), calendar: calendar))
    }

    func testEnabledFalseIsAKillSwitchInsideTheWindow() throws {
        let season = try decodeSeason(payload(enabled: false))
        XCTAssertFalse(
            season.isActive(on: date(2026, 10, 15), calendar: calendar),
            "enabled:false must switch a season off even mid-window"
        )
    }

    func testAMalformedDateWindowFailsClosed() throws {
        let season = try decodeSeason(payload(startDate: "2026-10", endDate: "not-a-date"))
        XCTAssertFalse(season.isActive(on: date(2026, 10, 15), calendar: calendar))
    }

    func testDayKeyParsing() {
        XCTAssertEqual(Season.dayKey(fromISODay: "2026-10-01"), "20261001")
        XCTAssertNil(Season.dayKey(fromISODay: "2026-1-1"))
        XCTAssertNil(Season.dayKey(fromISODay: "20261001"))
        XCTAssertNil(Season.dayKey(fromISODay: "20xx-10-01"))
    }

    // MARK: - Cache round trip

    func testSeasonRoundTripsThroughJSONCoding() throws {
        let season = try decodeSeason(payload())
        let data = try JSONEncoder().encode([season])
        let restored = try JSONDecoder().decode([Season].self, from: data)
        XCTAssertEqual(restored, [season])
    }
}
