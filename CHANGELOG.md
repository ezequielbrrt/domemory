# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

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
