# Changelog

All notable changes to this project will be documented in this file.

[Unreleased]

### Added
- Added a notification permission primer (NotificationPermissionKit) that explains what reminders are for before the one-time iOS authorization alert, shown once per install on the menu and reused by the Settings toggle.
- Added a "Rate DoMemory" row in Settings linking to the App Store review page, so leaving a review no longer depends on catching the throttled system prompt.
- Added a "What's New" row in Settings that presents the current release highlights outside the automatic version gate.

### Fixed
- Fixed the Settings screen having no scroll view, which clipped every row past the viewport. "Restore purchases" was completely unreachable, leaving reinstalling customers with no way to recover their Remove Ads entitlement.
- Fixed the Settings header rendering underneath the status bar and Dynamic Island.
- Fixed scrolled content passing under the floating back button on Settings and Achievements, by giving both navigation bars an opaque background instead of leaving them transparent.
- Fixed Settings row subtitles truncating mid-word, by replacing the per-row action pills with chevrons and switches where the pill was not the point.
- Fixed notification permission granted from the menu never enabling reminders: the grant did not set `notificationsEnabled`, which every scheduling method checks, so users who accepted received no reminders until they separately toggled Settings on.
- Fixed the ATT, ads, and notification prompts firing back to back on first menu appearance with no context between them.

### Changed
- Regrouped Settings into four titled sections — Game, Preferences, Purchases and About — each a single card with hairline separators, replacing ten individually shadowed cards.
- Changed the Reminders and Haptics rows from pill buttons to switches, so their state is readable without parsing an "Enable" or "Disable" label.
- Replaced the hand-rolled `ReviewRequestService` with ReviewFlow's `ReviewManager`, carrying existing win counts, cooldown dates, and per-version prompt history across so returning players are not re-prompted.
- Changed the `whats-new-ios` dependency from its SSH URL to HTTPS so clones and CI no longer need a deploy key.
- Upgraded WhatsNewKit from 1.0.0 to 2.0.0 and adopted its redesigned sheet, which now shows a version badge and groups the highlights into a bordered card.
- Restyled the What's New sheet with the app's own palette instead of `Color.accentColor`, which was rendering the sheet in the asset-catalog blue rather than the DoMemory violet.
- Inverted the localized App Store name in all 10 locales to the required `<keywords>: <app name>` order, so `DoMemory: Memory Card Game` becomes `Memory Card Game: DoMemory`. Each locale's existing name was inverted in place rather than re-translated, so every storefront keeps the keyword phrase it is already indexed for.
- Normalized the App Store name separator to one ASCII colon and one space, replacing the spaced colon in `fr-FR` and the fullwidth colon in `ja` and `zh-Hans`.
- Bumped `MARKETING_VERSION` to 4.1.0 in `Project.swift` and regenerated `project.pbxproj`. `CURRENT_PROJECT_VERSION` is untouched because the fastlane release lane owns the build number.

[3.1.0] 23-06-2026

### Added
- Added 30 new emoji memoramas across all four difficulties (easy, medium, hard, very hard), expanding the catalog to 134 games — new themes include fruits, animals, sea life, plants, food, vehicles, sports, space, and hand gestures.
- Added six new localized iPad 12.9-inch App Store screenshots for all 10 supported locales.
- Added a repeatable Paper workflow that splits iPad artboards into lightweight locale pages and exports them through Paper's batch export API.

### Fixed
- Fixed a malformed card set (id 57) whose items contained an empty value and two glued-together emoji.
- Fixed a duplicated card in an emoji set (id 3) that showed the same emoji twice.

### Changed
- Added `upload_games.py`, a validating CSV-to-Firebase pipeline for building and publishing the game catalog.
- Expanded the localized App Store names with descriptive memory-game terms while preserving the DoMemory brand.
- Updated the iPad screenshot upload workflow to validate complete six-image locale sets before replacing App Store Connect assets.

[3.0.0] 24-05-2026

### Added
- Added real-time multiplayer mode powered by Firebase Realtime Database; players can create or join a room via a 6-character code or QR scan, take turns flipping cards, and see live scores.
- Added multiplayer lobby screens (create room, join room, waiting room) and end-of-game flow with rematch support.
- Added local notification reminders that fire at 7 PM two days after the last completed game.
- Added a Notifications toggle in Settings to enable or disable inactivity reminders.
- Added permission-denied alert in Settings that deep-links to iOS notification settings when access has been revoked.
- Added `NotificationService` singleton managing scheduling, cancellation, and authorization-status sync.
- Added matched-card hide animation: matched pairs fade off the board one second after being revealed.
- Added rewarded-ad hint feature in the pause modal that reveals one unmatched pair.
- Added App Store "05 Features" screenshot artboard for all 10 supported locales (en, es-419, de, fr, hi, it, ja, ko, pt-BR, zh-Hans).
- Added What's New modal sheet powered by WhatsNewKit that surfaces v3.0.0 highlights (multiplayer, reminders, themes, hints) to returning users on first launch after an update.
- Added full light and dark appearance support across the app while preserving the existing brand palette.
- Added a theme selector in Settings with `System`, `Light`, and `Dark` options.
- Added persisted theme preference storage and app-wide appearance application at launch.
- Added a conservative in-app review prompt flow that can request an App Store review after repeated successful wins.

### Changed
- Removed the forced dark-only app appearance so the app can follow the system theme by default.
- Updated core surfaces, cards, sheets, HUD elements, and gameplay modals to use semantic theme colors.
- Changed the `My memoramas` behavior so user-created memoramas are always shown regardless of the currently selected difficulty.

### Fixed
- Fixed the Add Memorama name placeholder visibility by applying explicit prompt styling.
- Fixed the Add Memorama emoji/item input so typed content remains visible with the active theme.
- Improved contrast for themed controls, borders, and overlays in Settings, Menu, Home, and gameplay screens.
