//
//  Season+Presentation.swift
//  DoMemory
//
//  The display side of a Season: the icon and accent-colour fallbacks the
//  model deliberately left to the UI layer, plus the countdown the season
//  header and card show.
//

import SwiftUI

extension Season {
    /// Glyph for the menu card and the season header. Firebase may omit
    /// `icon`, and an author can leave it blank, so both fall back.
    var displayIcon: String {
        guard let icon, !icon.trimmingCharacters(in: .whitespaces).isEmpty else { return "✨" }
        return icon
    }

    /// Title for the player's locale, resolved through the model's
    /// `es-419` → `es` → `en` → fallback chain.
    var displayTitle: String { text().title }

    /// Accent colour for the season's chrome. An absent or malformed
    /// `accentColor` falls back to the app's own primary, so a typo in the
    /// console can never render a season invisible.
    var accent: Color { Season.color(fromHex: accentColor) ?? Color.primaryColor }

    /// Full-bleed artwork for the season's level map in `colorScheme`, or nil
    /// when the season carries none. The map paints plain `appBackground` in
    /// that case, exactly as it did before seasons had art.
    ///
    /// A season may ship one image or two. With two, dark mode takes
    /// `backgroundImageURLDark`; with one, both appearances take the same
    /// picture. A dark URL that is present but unusable falls through to the
    /// main one rather than leaving dark mode bare.
    func backgroundArtworkURL(for colorScheme: ColorScheme) -> URL? {
        if colorScheme == .dark, let dark = Season.artworkURL(from: backgroundImageURLDark) {
            return dark
        }
        return Season.artworkURL(from: backgroundImageURL)
    }

    /// Artwork for the season's menu card, or nil. The card falls back to the
    /// flat `accent` fill it has always used.
    var cardArtworkURL: URL? { Season.artworkURL(from: cardImageURL) }

    /// `https` URL, or nil for anything else.
    ///
    /// Validated rather than thrown on, matching `accentColor`: artwork is
    /// decoration, and a bad string in the console must degrade to the flat
    /// colour instead of taking the season's levels down with it.
    ///
    /// `https` specifically — App Transport Security blocks cleartext `http`,
    /// so an `http` URL would fail at load time with nothing on screen to say
    /// why. Rejecting it here at least makes the fallback deliberate.
    static func artworkURL(from raw: String?) -> URL? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              let url = URL(string: trimmed),
              url.scheme?.lowercased() == "https",
              let host = url.host(), !host.isEmpty
        else { return nil }
        return url
    }

    /// Whole days from `date` to the season's last day, inclusive — 0 means the
    /// season ends today. Nil when `endDate` is not a parseable `YYYY-MM-DD`.
    ///
    /// Measured in the player's own calendar, matching `isActive(on:calendar:)`
    /// and `DailyChallengeService`, so the countdown and the activation window
    /// roll over together at local midnight.
    func daysRemaining(on date: Date = Date(), calendar: Calendar = .current) -> Int? {
        guard let end = Season.date(fromISODay: endDate, calendar: calendar) else { return nil }
        let today = calendar.startOfDay(for: date)
        guard let days = calendar.dateComponents([.day], from: today, to: end).day else { return nil }
        return max(days, 0)
    }

    /// `"2026-10-01"` → the start of that day in `calendar`. Validated through
    /// the model's own `dayKey(fromISODay:)` so both agree on what a day is.
    static func date(fromISODay day: String, calendar: Calendar = .current) -> Date? {
        guard dayKey(fromISODay: day) != nil else { return nil }
        let parts = day.split(separator: "-")
        guard let year = Int(parts[0]), let month = Int(parts[1]), let dayOfMonth = Int(parts[2]) else {
            return nil
        }
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = dayOfMonth
        return calendar.date(from: components).map(calendar.startOfDay(for:))
    }

    /// `#RRGGBB` (with or without the `#`) to a `Color`. Nil for anything else,
    /// including the 8-digit form, which this payload does not define.
    static func color(fromHex hex: String?) -> Color? {
        guard var value = hex?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.count == 6, value.allSatisfy(\.isHexDigit) else { return nil }

        var number: UInt64 = 0
        guard Scanner(string: value).scanHexInt64(&number) else { return nil }
        return Color(
            red: Double((number & 0xFF0000) >> 16) / 255,
            green: Double((number & 0x00FF00) >> 8) / 255,
            blue: Double(number & 0x0000FF) / 255
        )
    }
}
