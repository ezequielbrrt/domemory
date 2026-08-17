//
//  LevelLivesServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

@MainActor
final class LevelLivesServiceTests: XCTestCase {
    private let service = LevelLivesService.shared
    private let defaults = UserDefaults.standard

    override func setUpWithError() throws {
        resetLives()
    }

    override func tearDownWithError() throws {
        resetLives()
    }

    private func resetLives() {
        defaults.removeObject(forKey: "levels.lives.remaining")
        defaults.removeObject(forKey: "levels.lives.lastResetDay")
    }

    func testDefaultStateStartsAtMaxLives() {
        XCTAssertEqual(service.livesRemaining(), LevelLivesService.maxLives)
        XCTAssertTrue(service.hasLivesRemaining())
    }

    func testConsumeLifeDecrements() {
        let updated = service.consumeLife()
        XCTAssertEqual(updated, LevelLivesService.maxLives - 1)
        XCTAssertEqual(service.livesRemaining(), LevelLivesService.maxLives - 1)
    }

    func testConsumeLifeFloorsAtZero() {
        for _ in 0..<(LevelLivesService.maxLives + 3) {
            service.consumeLife()
        }
        XCTAssertEqual(service.livesRemaining(), 0)
        XCTAssertFalse(service.hasLivesRemaining())
    }

    func testAddLifeFromAdIncrements() {
        service.consumeLife()
        service.consumeLife()
        let updated = service.addLife()
        XCTAssertEqual(updated, LevelLivesService.maxLives - 1)
    }

    func testAddLifeFromAdCapsAtMax() {
        // Already at max; watching an ad must not push past the cap.
        let updated = service.addLife()
        XCTAssertEqual(updated, LevelLivesService.maxLives)
        XCTAssertEqual(service.livesRemaining(), LevelLivesService.maxLives)
    }

    func testDailyResetRestoresMaxLives() {
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!

        service.consumeLife(for: yesterday)
        service.consumeLife(for: yesterday)
        XCTAssertEqual(service.livesRemaining(for: yesterday), LevelLivesService.maxLives - 2)

        // Reading with "today"'s date should lazily reset back to max.
        XCTAssertEqual(service.livesRemaining(for: Date()), LevelLivesService.maxLives)
    }
}
