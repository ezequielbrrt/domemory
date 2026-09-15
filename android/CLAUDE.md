# CLAUDE.md

This file provides guidance to coding agents working on the Android app. See
[`../CLAUDE.md`](../CLAUDE.md) first for what is shared with iOS, especially the
parity table — several types here must stay byte-for-behaviour identical to
their Swift counterparts.

`DOMEMORY_ANDROID_SPEC.md` is the behavioural source of truth: it describes what
the shipping iOS app does, in enough detail to build Android from it without
reading the Swift. `ANDROID_PLAN.md` holds the Android-specific decisions, stack
and phase plan. When they disagree about *what the app does*, the spec wins;
when they disagree about *how Android does it*, the plan wins.

## Build & Run

```
./gradlew testDebugUnitTest   # unit tests — 70 passing as of Phase 2
./gradlew assembleDebug       # produces app/build/outputs/apk/debug/app-debug.apk
```

The wrapper is the only entry point anyone needs — no system Gradle, no manually
installed JDK. `settings.gradle.kts` applies the Foojay resolver, so Gradle
provisions its own JDK 21 toolchain on first run even though nothing on the
machine is pinned to it (Android Studio ships a JBR, but that is not the same
JDK the toolchain targets).

**`local.properties` is machine-local and gitignored** — it holds `sdk.dir`, an
absolute path to one developer's SDK install. Never commit it; a fresh checkout
regenerates it from the SDK location Android Studio or the wrapper detects.

**A `Pixel_10` (API 37) emulator system image is installed on this machine**,
and the app has had its first real emulator run — see `ANDROID_PLAN.md` §7's
verification note for what was exercised and the two bugs it found. Installing
that image elsewhere is a one-time ~1.5 GB step (`cmdline-tools`), not part of
the normal build loop.

**AGP 9 has built-in Kotlin support** — applying `org.jetbrains.kotlin.android`
alongside `com.android.application` is now an error. `app/build.gradle.kts`
applies only the Android and Compose plugins; `libs.versions.toml` still pins
the Kotlin version because the Compose compiler plugin needs it.

## Architecture

A Jetpack Compose port of the iOS SwiftUI memory-card (memorama) game, package-
per-feature in a single `:app` module (no Gradle module graph). Dependency
injection is manual — an `AppContainer` service locator built in
`DoMemoryApplication.onCreate()` and read off `(application as
DoMemoryApplication).container` — chosen to avoid an annotation-processor
version dependency; revisit only if the container exceeds ~25 services.

**Phase 1 is a placeholder shell, not the real navigation.** `MainActivity`'s
`Phase1Root` composable does difficulty → board → game → back with local
`remember` state, no `ViewModelStore`, no nav graph. It exists to exercise the
game loop end to end; Phase 2 replaces it with `NavGraph` + `DeepLinkRouter` and
the real menu, tabs, favourites, onboarding and Settings. Don't extend
`Phase1Root` — build the real screen in `feature/` and wire it into the nav
graph in the same change.

### Package layout (`app/src/main/java/com/ezequielbrrt/domemory/`)

| Package | Role |
|---------|------|
| `core/model` | `Card`, `Board`, `MemoryGame`, `Difficulty`, `GameMode` — pure, no coroutines, no clock |
| `core/rng` | `SeededGenerator` (SplitMix64 + FNV-1a), `EmojiPool` — the parity-critical pair, see the root CLAUDE.md |
| `core/time` | `DayKey` — the one place the app decides what day it is |
| `data/remote` | `FirebaseBoardCatalogSource`, `BoardDecoder` — reads `/data`, falls back to a bundled set on failure |
| `data/repository` | `BoardCatalogRepository` — holds the catalog for the session, filters by difficulty |
| `feature/game` | `GameScreen`/`GameViewModel` — serves every mode (free play, Levels, Seasons, Daily Challenge) |
| `feature/levels` | `LevelsScreen`/`LevelsViewModel` — the endless level map, lives header, out-of-lives modal, intro |
| `services/levels` | `LevelCurve`, `Stars`, `BoardGenerators`, `LevelProgressService`, `LevelLivesService`, `StarWalletService`, `LevelPowerUp`, `LevelsIntroGate` — Phase 3, feature-complete including hardening |
| `services/seasons` | `Season`, `SeasonDecoder`, `SeasonCatalogService`, `SeasonProgressService`, `SeasonLocaleResolver` — Phase 4, feature-complete |
| `services/daily` | `DailyChallengeService` — deterministic board, streak/milestone tracking (Phase 5) |
| `core/deeplink` | `DeepLink`, `DeepLinkRouter` — `domemory://daily` and `domemory://join/CODE` parsing and routing |
| `feature/seasons`, `feature/daily` | `SeasonCard`/`SeasonLevelsScreen` and `DailyChallengeCard` — the menu entry points for Phases 4 and 5 |
| `services/notifications`, `feature/notifications` | `NotificationService`, reminder workers and the once-per-install permission primer — the three local reminders and their OS-permission sync (Phase 5) |
| `widget` | `DailyChallengeGlanceWidget`, receiver and calendar-aligned WorkManager refresh — the Daily Challenge home-screen widget (Phase 5) |
| `services/multiplayer` | Room wire models, code normalization, Firebase adapter and pure turn reducer — Phase 6 in progress; create/join, invite sharing, QR rendering/scanning, gameplay, reconnect grace and rematch exist, while association deployment and live cross-platform verification remain follow-up work |
| `services/ads` | AdMob initialization, debug/release placement configuration, the frequency-cap policy plus its presentation-trigger gate, and banner/interstitial/rewarded/native presentation — Phase 7 in progress; banners, the completion interstitial, both Levels rewarded rescues and the multiplayer-finished native ad are wired and presenting; `game_rewarded_extra_time`/`game_rewarded_hint` and app-open remain unwired (see `ANDROID_PLAN.md` §7) |
| `services/whatsnew`, `feature/whatsnew` | Version-aware release-notes gate and dialog — Phase 8 complete; first installs stay silent and upgrades present once |
| `services/haptics` | `HapticIntent`, `HapticsService` — Phase 8 complete; single gated `fire(intent)` entry point wired into `feature/game/GameViewModel.kt`'s flip/match/mismatch/win/loss/power-up/rescue moments and a handful of `NavGraph.kt` view-only taps. Menu, Levels, Seasons, Multiplayer and Settings screens have no haptics wired yet |
| `services/review` | `AppReviews` — Play In-App Review wrapper (Phase 8 complete); fires on a genuine win via `GameViewModel.onGameWon`, deliberately thin (no local eligibility policy — see `ANDROID_PLAN.md` §7's Phase 8 note for why that diverges from iOS on purpose) |
| `services/stats` | `ProfileStats`, `Achievement`, `ProfileStatsService` (lifetime aggregates + the pure `achievements()` derivation), `ProfileStatsRecorder`/`UserPreferencesProfileStatsRecorder` (the write side, mirroring `GameStatsRecorder`'s seam shape) — Phase 8 complete; recording is wired into `GameViewModel.commit()`/`.commitLossIfNeeded()` (every mode) and `MultiplayerViewModel` (guarded by `services/multiplayer/MultiplayerWinGuard`), reading into `feature/settings/AchievementsScreen.kt` |
| `feature/share` | `ShareResultCard.kt` — the pure `resultGridString`/`resultShareCaption`, the `ShareResultCardView` composable, and `shareResultCard()` (renders it to a `Bitmap` via `GraphicsLayer.toImageBitmap()` and launches an `ACTION_SEND` chooser through a new `FileProvider`) — Phase 8 complete; reached from `GameScreen.kt`'s win overlay only (Levels/Seasons out-of-lives and lose-screen surfaces have no share affordance, matching iOS's own Win-Modal-only placement) |
| `services/share` | `PlayStoreLinks` — the one place the Play Store listing URL is built, shared by the Settings "Rate DoMemory" row and the share-card caption |
| `ui/theme` | `Palette` — every token is a light/dark pair resolved from the active appearance; there is no single-value color anywhere in the app |

Everything else in the plan's package layout (`services/haptics`/`HapticIntent.fire`
wiring beyond `feature/game/`'s own call sites, full `multiplayer/` feature gameplay UI)
does not exist yet. Check `ANDROID_PLAN.md` §4 before assuming a service is missing by
accident rather than by phase.

### Locked decisions worth knowing before changing behavior

- **Daily Challenge boards will not equal iOS's**, by decision (D1). Same seed
  string, same SplitMix64/FNV-1a RNG, same 48-emoji pool — but the shuffle is
  Kotlin's own Fisher–Yates, not a port of Swift's. Boards are deterministic per
  day and identical across all Android devices, just not equal to iOS's. This
  is a live product-copy risk: "the same board for everyone" stops being true
  across platforms. `SeededGenerator` isolates the RNG, so reversing this later
  is one class plus a fixture test — see `ANDROID_PLAN.md` §6.
- **No Room, DataStore Preferences only** (D5). CoreData holds one row on iOS;
  `hasOnboarded` becomes an explicit boolean here rather than "a record exists."
- **No progress migration from iOS** (D6). There is no account layer on either
  platform, so progress is device-local and starts fresh on Android.
- **The debug `applicationIdSuffix` is gone.** The Firebase project has exactly
  one Android client, registered as `com.ezequielbrrt.domemory`; the
  google-services plugin fails the build if `applicationId` matches none of its
  clients. Debug and release currently cannot be installed side by side.
  Registering a second `.debug` client restores that (`ANDROID_PLAN.md` O7);
  don't reintroduce the suffix without doing so first.
- **`app/google-services.json` is committed on purpose.** It ships inside every
  APK and is public config keyed to the package name, not a secret. The
  Firebase service-account key is a different file, lives outside the repo, and
  nothing here needs it.

### Firebase

`FirebaseBoardCatalogSource` reads `/data` — the same 134-board catalog iOS
reads, from the same project. `BoardDecoder` is pure and is pinned against
`app/src/test/resources/data.json`, a value-identical (not byte-identical) copy
of `firebase/scripts/data.json` — see the root CLAUDE.md before touching either.

`FirebaseSeasonCatalogSource` reads `/seasons` the same way, cache-first through
`SeasonCatalogService`. `SeasonDecoder` ports the spec's validation rules
exactly and is pinned against a fixture test — a payload valid on iOS but
rejected on Android (or the reverse) would silently hide a live season from one
platform's players with nothing reporting it. The `/seasons` read rule in
`firebase/firebase-database.rules.json` still needs a separate
`firebase deploy --only database` before a real season is live (root CLAUDE.md).

### Tests

`app/src/test/java/` — 43 files, 320 tests, all pure Kotlin against an injected
clock, no Robolectric or instrumentation. Rule carried over from the spec: every
constant the spec pins in its §20 table, a test pins here too — that table is
the cross-check for the whole port. Run `./gradlew testDebugUnitTest` before
committing a change to anything under `core/` or `services/`.

## Status

Phases 0–5 (Levels, including its correctness hardening; Seasons; and Daily Challenge)
are complete in the current worktree. Phase 5 includes the deterministic board,
streak/milestone tracking, menu card, deep links, Glance widget and local reminders.
Phase 6 has create/join, invite, QR rendering/scanning, scheme/App Link routing, gameplay,
reconnect grace and rematch over its tested room protocol foundation. Its exit criterion —
a live Android↔iOS match — is now verified (2026-09-15, via the in-app manual 6-character
code; see `ANDROID_PLAN.md` §7), including a full match to completion with a consistent
win/loss result on both platforms and the reconnect-grace forfeit rule. The only remaining
gap is App Links (O1): `domemory.app` is unregistered, so the deep-link join path
(tapping a shared link outside the app) is unverified, but the manual-code path this
session used is a fully supported, already-shipped alternative.
Phase 7 has AdMob init, all nine placement units, banners, the completion interstitial,
both Levels rewarded rescues (life, forgive-mistakes) and the multiplayer-finished native
ad all wired and presenting through `AdsService`; `game_rewarded_extra_time`/
`game_rewarded_hint` and app-open remain unwired — see `ANDROID_PLAN.md` §7's Phase 7
implementation note for exactly why, before assuming either is a missed requirement
rather than a deliberate seam.
Phase 8 has What's New (version-gated dialog), haptics (`HapticsService`/`HapticIntent`,
wired into `feature/game/GameViewModel.kt`'s flip/match/mismatch/win/loss/power-up/rescue
moments only — no other screen yet), Play In-App Review (`AppReviews`, fired on a genuine
win, deliberately thin with no local eligibility policy — Android's `ReviewManager` owns
that server-side, unlike iOS's `ReviewFlow`), three Settings rows ("Achievements", "Rate
DoMemory", "What's New"), achievements (`services/stats/ProfileStatsService`, recording
wired into every `GameViewModel` finish and into multiplayer via `MultiplayerWinGuard`,
reading into `feature/settings/AchievementsScreen.kt`), the spoiler-free share card
(`feature/share/ShareResultCard.kt`, reached from the win overlay only), the four animations
(tile pulse and press-to-0.92 spring via new `ui/anim/PressScale.kt`; numeric transitions via
new `ui/anim/NumericTransition.kt`, applied only to `PowerUpBar`'s star balance, iOS's one
verified `numericText` call site; the progress-bar spring already existed from Phase 4 in
`SeasonLevelsScreen.kt`) and the five spec-14.5 accessibility content descriptions (fails
chip, timer-while-frozen, star balance, power-up cost — silent when disabled — and the
already-wired season progress bar), all nine non-English resource tables, and
`LocalizationParityTest` are implemented and unit-test clean. Phase 8's parity exit criterion
is green across all ten locales — see `ANDROID_PLAN.md` §7's four Phase 8 implementation
notes for the full mapping and what was deliberately left as a seam, including that the
animation feel and real TalkBack behavior are compiled/tested only, never seen running.
`ANDROID_PLAN.md` §7 contains the required handoff ledger: read it before starting
Android work, update it when a migration slice changes state, and do not infer a feature
is complete merely because its type or screen exists.
