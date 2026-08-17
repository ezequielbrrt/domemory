//
//  LevelsIntroGateTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

final class LevelsIntroGateTests: XCTestCase {
    /// A scratch suite rather than `.standard`, so a failing test can never leave
    /// the real "intro already seen" flag behind for other tests or the simulator.
    private let suiteName = "LevelsIntroGateTests"
    private var defaults: UserDefaults!

    override func setUpWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
    }

    override func tearDownWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        defaults = nil
    }

    func testPresentsOnACleanInstall() {
        XCTAssertTrue(LevelsIntroGate(defaults: defaults).shouldPresent)
    }

    func testDoesNotPresentOnceSeen() {
        let gate = LevelsIntroGate(defaults: defaults)
        gate.markSeen()
        XCTAssertFalse(gate.shouldPresent)
    }

    /// Reopening the intro from the header info button calls `markSeen()` again;
    /// that must not flip the gate back open.
    func testMarkSeenIsIdempotent() {
        let gate = LevelsIntroGate(defaults: defaults)
        gate.markSeen()
        gate.markSeen()
        XCTAssertFalse(gate.shouldPresent)
    }

    /// The flag has to survive the struct, since the view builds a fresh gate on
    /// every body evaluation.
    func testSeenStatePersistsAcrossInstances() {
        LevelsIntroGate(defaults: defaults).markSeen()
        XCTAssertFalse(LevelsIntroGate(defaults: defaults).shouldPresent)
    }
}
