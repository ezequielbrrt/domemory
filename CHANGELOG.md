# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Added
- Added full light and dark appearance support across the app while preserving the existing brand palette.
- Added a theme selector in Settings with `System`, `Light`, and `Dark` options.
- Added persisted theme preference storage and app-wide appearance application at launch.

### Changed
- Removed the forced dark-only app appearance so the app can follow the system theme by default.
- Updated core surfaces, cards, sheets, HUD elements, and gameplay modals to use semantic theme colors.
- Changed the `My memoramas` behavior so user-created memoramas are always shown regardless of the currently selected difficulty.

### Fixed
- Fixed the Add Memorama name placeholder visibility by applying explicit prompt styling.
- Fixed the Add Memorama emoji/item input so typed content remains visible with the active theme.
- Improved contrast for themed controls, borders, and overlays in Settings, Menu, Home, and gameplay screens.
