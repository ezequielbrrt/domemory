//
//  LocalizationParityTests.swift
//  DoMemoryTests
//
//  Guards the invariant every localization pass in this project has relied on
//  and nothing has enforced: the ten Localizable.strings files carry the same
//  set of keys. A key added to `en` alone does not fail the build, does not
//  fail any other test, and reaches the store as an English string sitting in
//  the middle of a translated screen — which is exactly what the season keys
//  did between Phase 3 and Phase 4.
//

import XCTest
@testable import DoMemory

final class LocalizationParityTests: XCTestCase {
    /// The locales the app ships, in the order `Project.swift` declares them.
    /// Hard-coded rather than read from `Bundle.main.localizations` so that
    /// *losing* a locale fails here instead of silently shrinking the
    /// comparison set.
    private static let supportedLocalizations = [
        "en", "es-419", "de", "fr", "hi", "it", "ja", "ko", "pt-BR", "zh-Hans"
    ]

    private static let developmentLocalization = "en"

    /// `Localizable.strings` for one localization, read out of the app bundle.
    ///
    /// The bundle copy is a compiled binary plist rather than the checked-in
    /// text, which `NSDictionary(contentsOf:)` reads either way — and going
    /// through the bundle means the test asserts on what actually shipped,
    /// including any key the build silently dropped.
    private func strings(for localization: String) throws -> [String: String] {
        // Resolved from a type in the app module rather than `Bundle.main`, so
        // the lookup is the app bundle no matter how the test target is hosted.
        let bundle = Bundle(for: SeasonProgressService.self)
        let url = try XCTUnwrap(
            bundle.url(
                forResource: "Localizable",
                withExtension: "strings",
                subdirectory: nil,
                localization: localization
            ),
            "No Localizable.strings for localization \"\(localization)\" in the app bundle."
        )
        return try XCTUnwrap(
            NSDictionary(contentsOf: url) as? [String: String],
            "Localizable.strings for \"\(localization)\" could not be read as a string table."
        )
    }

    func testEveryLocalizationHasTheSameKeyCount() throws {
        let reference = try strings(for: Self.developmentLocalization)
        XCTAssertGreaterThan(reference.count, 0, "The \"en\" string table is empty; the test is not reading the app bundle.")

        for localization in Self.supportedLocalizations {
            let table = try strings(for: localization)
            XCTAssertEqual(
                table.count,
                reference.count,
                """
                Locale "\(localization)" has \(table.count) keys, \
                "\(Self.developmentLocalization)" has \(reference.count). \
                Every Localizable.strings file must carry the same keys.
                """
            )
        }
    }

    func testEveryEnglishKeyExistsInEveryLocalization() throws {
        let referenceKeys = Set(try strings(for: Self.developmentLocalization).keys)

        for localization in Self.supportedLocalizations where localization != Self.developmentLocalization {
            let keys = Set(try strings(for: localization).keys)

            for key in referenceKeys.subtracting(keys).sorted() {
                XCTFail("Locale \"\(localization)\" is missing key \"\(key)\", which exists in \"\(Self.developmentLocalization)\".")
            }
            for key in keys.subtracting(referenceKeys).sorted() {
                XCTFail("Locale \"\(localization)\" has key \"\(key)\", which does not exist in \"\(Self.developmentLocalization)\".")
            }
        }
    }

    /// A translated value that drops or adds a format specifier crashes
    /// `String(format:)` at runtime in a locale the author most likely cannot
    /// read, so the specifiers are compared rather than trusted.
    ///
    /// Only the conversion characters are compared, as a multiset: a language
    /// is free to reorder `%1$d` and `%2$d`, and several already do.
    func testFormatSpecifiersMatchEnglishInEveryLocalization() throws {
        let reference = try strings(for: Self.developmentLocalization)

        for localization in Self.supportedLocalizations where localization != Self.developmentLocalization {
            let table = try strings(for: localization)

            for (key, englishValue) in reference {
                guard let translated = table[key] else { continue }
                let expected = Self.conversionCharacters(in: englishValue)
                let actual = Self.conversionCharacters(in: translated)
                XCTAssertEqual(
                    actual,
                    expected,
                    """
                    Locale "\(localization)" key "\(key)" has format specifiers \(actual) \
                    but "\(Self.developmentLocalization)" has \(expected). \
                    Value: "\(translated)"
                    """
                )
            }
        }
    }

    /// The conversion characters of every `%…` specifier in `value`, sorted so
    /// reordering positional arguments does not read as a mismatch.
    /// `"%2$d of %1$d"` yields `["d", "d"]`.
    private static func conversionCharacters(in value: String) -> [String] {
        let pattern = try! NSRegularExpression(pattern: "%(?:\\d+\\$)?([a-zA-Z@])")
        let range = NSRange(value.startIndex..., in: value)
        return pattern.matches(in: value, range: range).compactMap { match in
            guard let characterRange = Range(match.range(at: 1), in: value) else { return nil }
            return String(value[characterRange])
        }.sorted()
    }
}
