//
//  SeasonLevelsViewModelTests.swift
//  DoMemoryTests
//
//  The bounded season map, and the display fallbacks the UI layer owns.
//
//  Endless Levels pages its map forever; a season's is built once at its full
//  length. These cover the rules that differ, plus the presentation helpers
//  that a malformed Firebase payload reaches first.
//

import XCTest
import SwiftUI
@testable import DoMemory

@MainActor
final class SeasonLevelsViewModelTests: XCTestCase {
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
        levelCount: Int = 20,
        icon: Any = "🎃",
        accentColor: Any = "#FF6B1A",
        endDate: String = "2026-11-02"
    ) -> [String: Any] {
        [
            "id": "spooky-2026",
            "enabled": true,
            "startDate": "2026-10-01",
            "endDate": endDate,
            "priority": 10,
            "levelCount": levelCount,
            "icon": icon,
            "accentColor": accentColor,
            "emojiPool": Self.minimalPool,
            "strings": ["en": ["title": "Spooky Season", "subtitle": "20 haunted levels"]]
        ]
    }

    /// Every level cleared with the same rating, so a tile's stars are only
    /// evidence that the closure was consulted for that level.
    private func stars(_ value: Int) -> (Int) -> Int { { _ in value } }

    // MARK: - The map is bounded by levelCount

    func testMapStopsAtTheSeasonsLastLevel() {
        let tiles = SeasonLevelsViewModel.tiles(highestUnlocked: 1, levelCount: 20, stars: stars(0))

        XCTAssertEqual(tiles.count, 20)
        XCTAssertEqual(tiles.last?.level, 20, "a finite season must not render a tile past its length")
    }

    func testAFreshSeasonOffersLevelOneAndLocksTheRest() {
        let tiles = SeasonLevelsViewModel.tiles(highestUnlocked: 1, levelCount: 5, stars: stars(0))

        XCTAssertTrue(tiles[0].isCurrent)
        XCTAssertEqual(tiles.filter(\.isLocked).map(\.level), [2, 3, 4, 5])
    }

    func testClearedCurrentAndLockedTilesSplitAroundHighestUnlocked() {
        let tiles = SeasonLevelsViewModel.tiles(highestUnlocked: 3, levelCount: 5, stars: stars(2))

        XCTAssertEqual(tiles[0].state, .cleared(stars: 2))
        XCTAssertEqual(tiles[1].state, .cleared(stars: 2))
        XCTAssertEqual(tiles[2].state, .current)
        XCTAssertEqual(tiles[3].state, .locked)
        XCTAssertEqual(tiles[4].state, .locked)
    }

    /// `highestUnlockedLevel` is allowed to reach `levelCount + 1` — that is
    /// exactly what `isComplete(levelCount:)` reads. The map must render that
    /// as every level cleared, with no current tile left pointing nowhere.
    func testACompletedSeasonRendersEveryLevelClearedAndNoCurrentTile() {
        let tiles = SeasonLevelsViewModel.tiles(highestUnlocked: 6, levelCount: 5, stars: stars(3))

        XCTAssertEqual(tiles.count, 5)
        XCTAssertTrue(tiles.allSatisfy { $0.state == .cleared(stars: 3) })
        XCTAssertNil(tiles.first(where: \.isCurrent), "a cleared season must not still highlight a current level")
        XCTAssertNil(tiles.first(where: \.isLocked))
    }

    func testStarsComeFromTheProgressStorePerLevel() {
        let tiles = SeasonLevelsViewModel.tiles(highestUnlocked: 4, levelCount: 4, stars: { $0 })

        XCTAssertEqual(tiles[0].state, .cleared(stars: 1))
        XCTAssertEqual(tiles[1].state, .cleared(stars: 2))
        XCTAssertEqual(tiles[2].state, .cleared(stars: 3))
        XCTAssertTrue(tiles[3].isCurrent, "the current level has no rating yet")
    }

    /// `levelCount` is rejected at decode time, so this is defence against a
    /// cached season from a future payload shape rather than a reachable state.
    func testANonPositiveLevelCountRendersAnEmptyMapRatherThanCrashing() {
        XCTAssertTrue(SeasonLevelsViewModel.tiles(highestUnlocked: 1, levelCount: 0, stars: stars(0)).isEmpty)
        XCTAssertTrue(SeasonLevelsViewModel.tiles(highestUnlocked: 1, levelCount: -3, stars: stars(0)).isEmpty)
    }

    // MARK: - View model over a real progress store

    func testViewModelReportsProgressAgainstTheSeasonsLength() throws {
        let season = try decodeSeason(payload(levelCount: 4))
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "SeasonLevelsViewModelTests"))
        defaults.removePersistentDomain(forName: "SeasonLevelsViewModelTests")
        defer { defaults.removePersistentDomain(forName: "SeasonLevelsViewModelTests") }

        let progress = SeasonProgressService(season: season, defaults: defaults)
        progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)

        let viewModel = SeasonLevelsViewModel(season: season, progress: progress)

        XCTAssertEqual(viewModel.levelCount, 4)
        XCTAssertEqual(viewModel.clearedLevelCount, 1)
        XCTAssertFalse(viewModel.isComplete)
        XCTAssertEqual(viewModel.tiles.count, 4)
        XCTAssertTrue(viewModel.tiles[1].isCurrent, "clearing level 1 must move the current tile to level 2")
    }

    func testViewModelReportsCompletionOnceTheLastLevelIsCleared() throws {
        let season = try decodeSeason(payload(levelCount: 2))
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "SeasonLevelsViewModelTests"))
        defaults.removePersistentDomain(forName: "SeasonLevelsViewModelTests")
        defer { defaults.removePersistentDomain(forName: "SeasonLevelsViewModelTests") }

        let progress = SeasonProgressService(season: season, defaults: defaults)
        progress.recordCompletion(level: 1, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)
        progress.recordCompletion(level: 2, didWin: true, timeRemaining: 80, totalTime: 90, failedTries: 0)

        let viewModel = SeasonLevelsViewModel(season: season, progress: progress)

        XCTAssertTrue(viewModel.isComplete)
        XCTAssertEqual(viewModel.clearedLevelCount, 2, "cleared count must clamp to the season's length, not report 3")
        XCTAssertNil(viewModel.tiles.first(where: \.isCurrent))
    }

    // MARK: - Countdown

    func testDaysRemainingCountsWholeDaysToTheLastDayInclusive() throws {
        let season = try decodeSeason(payload(endDate: "2026-11-02"))

        XCTAssertEqual(season.daysRemaining(on: date(2026, 10, 31), calendar: calendar), 2)
        XCTAssertEqual(season.daysRemaining(on: date(2026, 11, 1), calendar: calendar), 1)
    }

    func testDaysRemainingIsZeroOnTheSeasonsFinalDay() throws {
        let season = try decodeSeason(payload(endDate: "2026-11-02"))

        XCTAssertEqual(season.daysRemaining(on: date(2026, 11, 2), calendar: calendar), 0)
    }

    /// A season the catalog has not dropped yet — an app left open across local
    /// midnight — must not render a negative countdown.
    func testDaysRemainingClampsToZeroAfterTheSeasonEnds() throws {
        let season = try decodeSeason(payload(endDate: "2026-11-02"))

        XCTAssertEqual(season.daysRemaining(on: date(2026, 11, 5), calendar: calendar), 0)
    }

    func testDaysRemainingIsNilWhenTheEndDateDoesNotParse() throws {
        let season = try decodeSeason(payload(endDate: "not-a-date"))

        XCTAssertNil(season.daysRemaining(on: date(2026, 11, 1), calendar: calendar), "the header omits an unparseable countdown rather than showing 0")
    }

    // MARK: - Display fallbacks

    func testDisplayIconFallsBackWhenFirebaseOmitsOrBlanksIt() throws {
        XCTAssertEqual(try decodeSeason(payload()).displayIcon, "🎃")
        XCTAssertEqual(try decodeSeason(payload(icon: "   ")).displayIcon, "✨")

        var body = payload()
        body.removeValue(forKey: "icon")
        XCTAssertEqual(try decodeSeason(body).displayIcon, "✨")
    }

    func testAccentColorParsesSixDigitHexWithOrWithoutTheHash() {
        XCTAssertNotNil(Season.color(fromHex: "#FF6B1A"))
        XCTAssertNotNil(Season.color(fromHex: "FF6B1A"))
        XCTAssertNotNil(Season.color(fromHex: " #ff6b1a "))
    }

    /// A typo in the Firebase console must not render a season's chrome
    /// invisible, so anything unparseable falls back to the app's own primary.
    func testMalformedAccentColorIsRejectedSoTheSeasonFallsBackToThePrimary() throws {
        XCTAssertNil(Season.color(fromHex: nil))
        XCTAssertNil(Season.color(fromHex: ""))
        XCTAssertNil(Season.color(fromHex: "#FFF"), "the 3-digit shorthand is not part of this payload")
        XCTAssertNil(Season.color(fromHex: "#FF6B1AFF"), "the 8-digit form is not part of this payload")
        XCTAssertNil(Season.color(fromHex: "#GGGGGG"))
        XCTAssertNil(Season.color(fromHex: "orange"))

        let season = try decodeSeason(payload(accentColor: "nonsense"))
        // `Color.primaryColor` is a dynamic asset-catalog colour, so two Color
        // values wrapping the same provider are never `==`. Resolve both in a
        // fixed appearance and compare the components instead.
        XCTAssertEqual(rgba(season.accent), rgba(Color.primaryColor))
    }

    /// The colour's components in light appearance, rounded so a round-trip
    /// through the colour space cannot fail the comparison on the last bit.
    private func rgba(_ color: Color) -> [Int] {
        let resolved = UIColor(color).resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        resolved.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return [red, green, blue, alpha].map { Int(($0 * 255).rounded()) }
    }
}
