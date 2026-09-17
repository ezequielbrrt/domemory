//
//  WhatsNewContent.swift
//  DoMemory
//

import SwiftUI
import WhatsNewKit

// MARK: - App theme

extension WhatsNewTheme {
    /// Built from the app's own palette rather than `Color.accentColor`, which
    /// resolves to the asset-catalog blue and left the sheet looking like a
    /// different app. Surfaces reuse the Settings row tokens so the card reads
    /// as part of DoMemory in both light and dark mode.
    static let doMemory = WhatsNewTheme(
        accentColor: .primaryColor,
        backgroundColor: .appBackground,
        titleColor: .textPrimary,
        itemTitleColor: .textPrimary,
        itemDescriptionColor: .textSecondary,
        symbolColor: .primaryColor,
        versionColor: .primaryColor,
        versionBackgroundColor: .primaryColor.opacity(0.14),
        symbolBackgroundColor: .primaryColor.opacity(0.14),
        cardBackgroundColor: .surfacePrimary,
        cardBorderColor: .surfaceBorder,
        cardShadowColor: .shadowColor,
        separatorColor: .surfaceBorder
    )
}

// MARK: - Current release content

extension WhatsNew {
    /// The What's New sheet content for the running app version.
    /// All strings are localised via `Localizable.strings`.
    static var current: WhatsNew {
        WhatsNew(
            title: Strings.whatsNewTitle,
            version: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
            items: [
                .init(
                    symbol: "person.2.fill",
                    title: Strings.whatsNewMultiplayerHostTitle,
                    description: Strings.whatsNewMultiplayerHostDescription
                ),
                .init(
                    symbol: "checkmark.circle.fill",
                    title: Strings.whatsNewMultiplayerReadyTitle,
                    description: Strings.whatsNewMultiplayerReadyDescription
                ),
                .init(
                    symbol: "gift.fill",
                    title: Strings.whatsNewSeasonsTitle,
                    description: Strings.whatsNewSeasonsDescription
                ),
            ],
            primaryButtonTitle: Strings.whatsNewButton
        )
    }
}

// MARK: - Preview

#Preview("What's New Sheet") {
    Color.clear.sheet(isPresented: .constant(true)) {
        WhatsNewView(whatsNew: .current, theme: .doMemory, onDismiss: {})
    }
}
