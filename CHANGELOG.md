# Changelog

All notable changes to this project will be documented in this file.

[4.1.0] 01-09-2026

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
- Fixed the What's New sheet appearing on a brand-new install, greeting a first-time player with release notes for a version they had never not had. Nothing wrote `whatsNewLastSeenVersion` on install, so the version gate read the empty slot as an upgrade. A first launch now records the running version and stays quiet. Onboarding state, not the empty slot, decides this — someone upgrading from a build that predates the What's New manager also has nothing stored, and they still see the sheet.

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

[4.0.0] 18-08-2026

### Added
- Added Levels, an endless mode of procedurally generated boards where clearing a level unlocks the next. Each board is generated from its level number, so a given level draws the same set of emoji on every device.
- Added a 1-3 star rating on every level, earned on how much time is left and how few wrong matches were made, with any level replayable to improve its rating.
- Added a spendable star economy: stars earned by clearing levels buy four in-game power-ups (+15 seconds, a peek at the board, a 10-second clock freeze, and revealing a matching pair), an extra life for 10 stars, or a skip past a level you are stuck on for 15.
- Added a budget of four Levels lives per day, spent only on a loss and refilled by a rewarded ad or 10 stars.
- Added a mistake budget to every level, so a level can no longer be brute-forced by tapping until the clock runs out. Ordinary memoramas are unaffected and still end only on the clock. Busting it offers a rescue — a rewarded ad or 8 stars — that forgives three mistakes and resumes the same board without costing a life.
- Added a four-slide Levels intro carousel covering progression, stars, lives and the mistake budget, shown the first time the Levels tab is opened and reopenable from an info button in the level map header.
- Added app-wide haptic feedback across cards, matches, mismatches, wins, losses, star rewards and multiplayer turn changes, with a Haptics toggle in Settings that defaults to on.
- Added a "Levels" App Store screenshot to both the iPhone and iPad galleries in all 10 supported locales.
- Added 4.0.0 release notes and promotional text for all 10 App Store locales under `DoMemory/fastlane/metadata/`, which the release lane now uploads.
- Added analytics coverage for Levels: level starts and results, unlocks, lives spent and granted, star credits and spends, power-up use, skips, mistake losses and rescues, and the intro carousel.

### Changed
- Replaced the custom "All / My memoramas" segmented picker with a native iOS tab bar pinned to the bottom of the menu, which now also carries the Levels tab.
- Rewrote the What's New sheet around the 4.0.0 highlights — Levels, star ratings, power-ups, and lives and mistakes — replacing the 3.1.0 list.
- Bumped `MARKETING_VERSION` to 4.0.0, without which the What's New sheet would never have presented and every existing player would have upgraded into Levels with no announcement at all.
- Applied the daily lives budget to Remove Ads purchasers, who were previously exempt from it and so had nothing at stake in Levels.
- Narrowed Remove Ads to suppress only involuntary advertising — banners, natives, interstitials and app-open. Rewarded ads are opt-in and now stay available to purchasers, which is what keeps the lives and mistake refills reachable for them.
- Pinned Tuist to 4.195.6 in `.mise.toml`, matching the version the release lane hardcodes, so `tuist generate` no longer fails with no version to resolve.

### Fixed
- Fixed the app-open ad landing on top of the What's New sheet. The ad rides `didBecomeActive`, which fires again the moment the ATT prompt is dismissed, so the release announcement was buried before it could be read.
- Fixed `export_screenshots.py` reporting success after exporting an incomplete set. A spec whose artboard was missing was skipped silently, so a partial export could overwrite the previous release's screenshots.
- Fixed `export_ipad_screenshots.py` and `split_ipad_paper_pages.py` silently doing nothing after Paper renamed `open_page` to `open_file`. They checked only for a JSON-RPC `error` key, while Paper reports an unknown tool as `isError` inside `result`, so each script carried on against whichever page happened to be open.

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
