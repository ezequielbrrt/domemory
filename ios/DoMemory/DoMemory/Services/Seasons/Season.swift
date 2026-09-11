//
//  Season.swift
//  DoMemory
//
//  Firebase-supplied seasonal level progression ("Spooky Season",
//  "Christmas Season"). Firebase supplies the emoji pool, the length and the
//  theming — never the individual boards, which are generated locally and
//  deterministically by SeasonProgressService.
//

import Foundation

/// One seasonal level progression, decoded from a child of the Realtime
/// Database `/seasons` node.
///
/// `/seasons` is a *dictionary* keyed by season id (unlike `/data`, which is an
/// array), so `id` does not appear in the value body. `SeasonCatalogService`
/// injects the dictionary key as `id` before decoding, which also lets the
/// decoded value round-trip cleanly through the UserDefaults cache.
struct Season: Codable, Identifiable, Hashable {
    /// The title/subtitle pair for a single locale.
    struct LocalizedText: Codable, Hashable {
        let title: String
        let subtitle: String

        init(title: String, subtitle: String) {
            self.title = title
            self.subtitle = subtitle
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            title = try container.decode(String.self, forKey: .title)
            subtitle = try container.decodeIfPresent(String.self, forKey: .subtitle) ?? ""
        }
    }

    /// The Realtime Database key of the season, e.g. `spooky-2026`.
    let id: String
    /// Kill switch. Absent means `false`: a season only appears when Firebase
    /// says so explicitly.
    let enabled: Bool
    /// Inclusive first day, `YYYY-MM-DD`.
    let startDate: String
    /// Inclusive last day, `YYYY-MM-DD`.
    let endDate: String
    /// Highest priority wins when several seasons are active on the same day.
    let priority: Int
    /// How many levels the season holds. A season is finite.
    let levelCount: Int
    /// Optional display glyph for the menu card. Phase 3 owns the fallback.
    let icon: String?
    /// Optional accent colour as a `#RRGGBB` hex string. Phase 3 owns parsing
    /// and the fallback.
    let accentColor: String?
    /// Optional full-bleed artwork for the season's level map, as an absolute
    /// `https` URL.
    ///
    /// Remote rather than bundled because a season is published from Firebase
    /// without an app update: art shipped in the binary would only ever cover
    /// the seasons that existed at build time, and every player who had not
    /// updated would see the *previous* season's picture behind the new
    /// season's levels. `Season+Presentation` owns validation and the fallback.
    let backgroundImageURL: String?
    /// Optional dark-appearance replacement for `backgroundImageURL`.
    ///
    /// One image cannot serve both appearances: the level tiles are near-white
    /// in light mode and near-black in dark, so artwork that separates from the
    /// tiles in one collapses into them in the other. Absent means the season
    /// has a single image and both appearances use it.
    let backgroundImageURLDark: String?
    /// Optional artwork for the season's menu card, under the same contract as
    /// `backgroundImageURL`. The card is the season's accent colour with white
    /// text in both appearances, so it takes one image, not a pair.
    let cardImageURL: String?
    /// Emoji the season's boards are dealt from. Deduplicated at decode time;
    /// see `minimumEmojiPoolSize`.
    let emojiPool: [String]
    /// Locale code (`en`, `es-419`, …) to display strings.
    let strings: [String: LocalizedText]

    // MARK: - Pool validation

    /// The largest board a season will ever be asked to deal.
    ///
    /// `LevelCurve.pairAnchors` tops out at 12 pairs
    /// (`Services/Levels/LevelCurve.swift:15`) and holds the last anchor's
    /// value for every level beyond it, so asking the curve for an arbitrarily
    /// high level yields that cap without duplicating the number here — if the
    /// curve is ever retuned, this follows it.
    ///
    /// A season whose pool is smaller than this cannot fill its own late
    /// boards, so decoding rejects it outright rather than dealing a short
    /// board at level 25.
    ///
    /// **Cross-reference — keep in sync.** `firebase/scripts/upload_seasons.py` restates
    /// this rule as `MINIMUM_EMOJI_POOL_SIZE` so a season is rejected before it
    /// is published rather than skipped after. Python cannot import Swift, so
    /// that script hardcodes 12 and will *not* follow a retuned
    /// `LevelCurve.pairAnchors` the way this constant does. Change both
    /// together; the script carries the matching pointer back here.
    static let minimumEmojiPoolSize = LevelCurve.pairs(for: .max)

    /// Title shown when a season carries no usable strings for the current
    /// locale and no `en` entry either.
    ///
    /// `Strings` is a plain `Foundation`-only enum of `NSLocalizedString`
    /// lookups, which the services layer already reaches directly
    /// (`PurchaseService`, `SeasonProgressService`), so a model reading it
    /// pulls in no UI dependency.
    static let fallbackTitle = Strings.seasonFallbackTitle

    // MARK: - Decoding

    init(
        id: String,
        enabled: Bool,
        startDate: String,
        endDate: String,
        priority: Int,
        levelCount: Int,
        icon: String?,
        accentColor: String?,
        backgroundImageURL: String? = nil,
        backgroundImageURLDark: String? = nil,
        cardImageURL: String? = nil,
        emojiPool: [String],
        strings: [String: LocalizedText]
    ) {
        self.id = id
        self.enabled = enabled
        self.startDate = startDate
        self.endDate = endDate
        self.priority = priority
        self.levelCount = levelCount
        self.icon = icon
        self.accentColor = accentColor
        self.backgroundImageURL = backgroundImageURL
        self.backgroundImageURLDark = backgroundImageURLDark
        self.cardImageURL = cardImageURL
        self.emojiPool = emojiPool
        self.strings = strings
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = try container.decode(String.self, forKey: .id)
        // Fail closed on every optional field: a season missing its kill switch
        // is treated as switched off rather than silently shipped.
        enabled = try container.decodeIfPresent(Bool.self, forKey: .enabled) ?? false
        startDate = try container.decode(String.self, forKey: .startDate)
        endDate = try container.decode(String.self, forKey: .endDate)
        priority = try container.decodeIfPresent(Int.self, forKey: .priority) ?? 0
        levelCount = try container.decode(Int.self, forKey: .levelCount)
        icon = try container.decodeIfPresent(String.self, forKey: .icon)
        accentColor = try container.decodeIfPresent(String.self, forKey: .accentColor)
        // Artwork is decoration. A blank or malformed URL resolves to nil in
        // `Season+Presentation` and the surface falls back to its flat colour,
        // so a typo in the console never costs the player the season itself.
        backgroundImageURL = try container.decodeIfPresent(String.self, forKey: .backgroundImageURL)
        backgroundImageURLDark = try container.decodeIfPresent(String.self, forKey: .backgroundImageURLDark)
        cardImageURL = try container.decodeIfPresent(String.self, forKey: .cardImageURL)
        strings = try container.decodeIfPresent([String: LocalizedText].self, forKey: .strings) ?? [:]

        guard levelCount >= 1 else {
            throw DecodingError.dataCorruptedError(
                forKey: .levelCount,
                in: container,
                debugDescription: "Season \"\(id)\" has levelCount \(levelCount); a season needs at least one level."
            )
        }

        // A duplicated emoji would deal two identical pairs — four matching
        // cards — which the matching rules cannot resolve, so the pool is
        // deduplicated (order preserved, so the seeded shuffle stays stable)
        // before it is measured.
        let rawPool = try container.decode([String].self, forKey: .emojiPool)
        var seen = Set<String>()
        let pool = rawPool.filter { emoji in
            guard !emoji.isEmpty else { return false }
            return seen.insert(emoji).inserted
        }
        guard pool.count >= Season.minimumEmojiPoolSize else {
            throw DecodingError.dataCorruptedError(
                forKey: .emojiPool,
                in: container,
                debugDescription: """
                    Season "\(id)" has \(pool.count) distinct emoji; \
                    \(Season.minimumEmojiPoolSize) are required to fill its late boards.
                    """
            )
        }
        emojiPool = pool
    }

    // MARK: - Locale resolution

    /// Display strings for `locale`, resolved through
    /// `es-419` → `es` → `en` → an app-side fallback.
    ///
    /// The chain is built from progressively shorter prefixes of the locale
    /// identifier rather than just "exact, then language", so a script-bearing
    /// identifier such as `zh_Hans_CN` still reaches a `zh-Hans` entry instead
    /// of falling straight through to English.
    func text(for locale: Locale = .current) -> LocalizedText {
        Season.resolveText(from: strings, identifier: locale.identifier)
    }

    static func resolveText(from strings: [String: LocalizedText], identifier: String) -> LocalizedText {
        // Author-written keys are matched case-insensitively so `pt-BR` and
        // `pt-br` behave the same in the console.
        var lookup: [String: LocalizedText] = [:]
        for (key, value) in strings {
            lookup[normalizedLocaleKey(key)] = value
        }
        for candidate in localeCandidates(for: identifier) where lookup[candidate] != nil {
            return lookup[candidate]!
        }
        return LocalizedText(title: fallbackTitle, subtitle: "")
    }

    /// The ordered lookup keys tried for a locale identifier, always ending in
    /// `en`. `es_419` yields `["es-419", "es", "en"]`.
    static func localeCandidates(for identifier: String) -> [String] {
        let components = normalizedLocaleKey(identifier)
            .split(separator: "-")
            .map(String.init)
            .filter { !$0.isEmpty }

        var candidates: [String] = []
        var index = components.count
        while index > 0 {
            candidates.append(components.prefix(index).joined(separator: "-"))
            index -= 1
        }
        if !candidates.contains("en") {
            candidates.append("en")
        }
        return candidates
    }

    /// Apple identifiers use `_` separators (`es_419`), Firebase keys use `-`
    /// (`es-419`). Normalise both to lowercase hyphenated form.
    private static func normalizedLocaleKey(_ identifier: String) -> String {
        identifier.replacingOccurrences(of: "_", with: "-").lowercased()
    }

    // MARK: - Activation window

    /// `enabled`, and `date` falls inside `[startDate, endDate]` inclusive.
    ///
    /// The window is evaluated as *wall-clock days in the player's own
    /// calendar*, matching `DailyChallengeService`, which derives its day from
    /// `Calendar.current` through `dailyChallengeSeed(for:calendar:)`. Both
    /// therefore roll over together at the player's local midnight, so a
    /// player never sees the daily challenge advance to a new day while the
    /// season card still believes it is yesterday.
    ///
    /// The comparison is done on the same zero-padded `YYYYMMDD` day key rather
    /// than on parsed `Date` values: string ordering on that format is
    /// chronological, the bounds are inclusive by construction, and no
    /// timezone offset can shift a boundary day by one.
    func isActive(on date: Date = Date(), calendar: Calendar = .current) -> Bool {
        guard enabled else { return false }
        guard let start = Season.dayKey(fromISODay: startDate),
              let end = Season.dayKey(fromISODay: endDate) else {
            // An unparseable window fails closed.
            return false
        }
        let today = dailyChallengeSeed(for: date, calendar: calendar)
        return today >= start && today <= end
    }

    /// `"2026-10-01"` → `"20261001"`. Returns nil for anything that is not a
    /// strict `YYYY-MM-DD`.
    static func dayKey(fromISODay day: String) -> String? {
        let parts = day.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCII) && $0.allSatisfy(\.isNumber) }) else {
            return nil
        }
        return parts.joined()
    }
}
