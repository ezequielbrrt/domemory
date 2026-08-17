//
//  WhatsNewContent.swift
//  DoMemory
//

import SwiftUI
import WhatsNewKit

// MARK: - App theme

extension WhatsNewTheme {
    /// Matches the app's accent colour with sensible defaults for everything else.
    static let doMemory = WhatsNewTheme(
        accentColor: .accentColor,
        symbolColor: .accentColor
    )
}

// MARK: - Current release content

extension WhatsNew {
    /// The What's New sheet content for the running app version.
    /// All strings are localised via `Localizable.strings`.
    static var current: WhatsNew {
        WhatsNew(
            title: Strings.whatsNewTitle,
            items: [
                .init(
                    symbol: "trophy.fill",
                    title: Strings.whatsNewLevelsTitle,
                    description: Strings.whatsNewLevelsDescription
                ),
                .init(
                    symbol: "star.fill",
                    title: Strings.whatsNewStarsTitle,
                    description: Strings.whatsNewStarsDescription
                ),
                .init(
                    symbol: "wand.and.stars",
                    title: Strings.whatsNewPowerUpsTitle,
                    description: Strings.whatsNewPowerUpsDescription
                ),
                .init(
                    symbol: "heart.fill",
                    title: Strings.whatsNewLivesTitle,
                    description: Strings.whatsNewLivesDescription
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
