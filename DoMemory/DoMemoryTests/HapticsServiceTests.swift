//
//  HapticsServiceTests.swift
//  DoMemoryTests
//

import XCTest
@testable import DoMemory

@MainActor
final class HapticsServiceTests: XCTestCase {
    private let suiteName = "HapticsServiceTests"
    private var defaults: UserDefaults!
    private var fired: [HapticsService.Feedback] = []

    override func setUpWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
        defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        fired = []
    }

    override func tearDownWithError() throws {
        UserDefaults.standard.removePersistentDomain(forName: suiteName)
    }

    private func makeService() -> HapticsService {
        HapticsService(defaults: defaults) { [weak self] feedback in
            self?.fired.append(feedback)
        }
    }

    // MARK: - The default

    /// `UserDefaults.bool(forKey:)` returns false for a key that was never
    /// written. Reading the setting that way would ship haptics silently off
    /// for every existing install, which is the whole point of this test.
    func testHapticsAreOnByDefaultOnACleanInstall() {
        let service = makeService()
        XCTAssertTrue(service.isEnabled)

        service.fire(.tap)
        XCTAssertEqual(fired, [.impact(.light)])
    }

    func testExplicitlyEnablingKeepsHapticsOn() {
        defaults.set(true, forKey: UserDefaultsKeys.hapticsEnabled)
        let service = makeService()

        service.fire(.match)
        XCTAssertEqual(fired, [.impact(.medium)])
    }

    // MARK: - The gate

    func testNothingFiresWhenDisabled() {
        defaults.set(false, forKey: UserDefaultsKeys.hapticsEnabled)
        let service = makeService()

        XCTAssertFalse(service.isEnabled)
        for intent in HapticsService.Intent.allCases {
            service.fire(intent)
        }
        XCTAssertTrue(fired.isEmpty, "Disabling haptics must silence every intent, not just button taps")
    }

    func testTogglingBackOnResumesFiring() {
        defaults.set(false, forKey: UserDefaultsKeys.hapticsEnabled)
        let service = makeService()
        service.fire(.tap)
        XCTAssertTrue(fired.isEmpty)

        defaults.set(true, forKey: UserDefaultsKeys.hapticsEnabled)
        service.fire(.tap)
        XCTAssertEqual(fired, [.impact(.light)])
    }

    // MARK: - The mapping

    /// Locks the feel of the app in place: retuning is a deliberate edit here,
    /// not something that drifts as call sites are added.
    func testEveryIntentMapsToItsFeedback() {
        let expected: [HapticsService.Intent: HapticsService.Feedback] = [
            .tap: .impact(.light),
            .select: .selection,
            .cardFlip: .impact(.soft),
            .match: .impact(.medium),
            .mismatch: .impact(.rigid),
            .success: .notification(.success),
            .failure: .notification(.error),
            .warning: .notification(.warning),
            .reward: .impact(.heavy)
        ]

        for intent in HapticsService.Intent.allCases {
            XCTAssertEqual(
                HapticsService.feedback(for: intent),
                expected[intent],
                "Unmapped or changed feedback for .\(intent.rawValue)"
            )
        }
    }

    func testDistinctIntentsForWinAndLose() {
        // A win and a loss must not feel the same; this is the pairing players
        // actually notice.
        XCTAssertNotEqual(
            HapticsService.feedback(for: .success),
            HapticsService.feedback(for: .failure)
        )
        XCTAssertNotEqual(
            HapticsService.feedback(for: .match),
            HapticsService.feedback(for: .mismatch)
        )
    }

    // MARK: - Preparation

    func testPrepareDoesNotEmit() {
        let service = makeService()
        for intent in HapticsService.Intent.allCases {
            service.prepare(for: intent)
        }
        XCTAssertTrue(fired.isEmpty, "prepare() warms the engine; it must never produce feedback")
    }
}
