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
                    symbol: "person.2.fill",
                    title: Strings.whatsNewMultiplayerTitle,
                    description: Strings.whatsNewMultiplayerDescription
                ),
                .init(
                    symbol: "bell.badge",
                    title: Strings.whatsNewRemindersTitle,
                    description: Strings.whatsNewRemindersDescription
                ),
                .init(
                    symbol: "circle.lefthalf.filled",
                    title: Strings.whatsNewThemeTitle,
                    description: Strings.whatsNewThemeDescription
                ),
                .init(
                    symbol: "lightbulb",
                    title: Strings.whatsNewHintTitle,
                    description: Strings.whatsNewHintDescription
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
