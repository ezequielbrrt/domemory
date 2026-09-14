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
| `core/deeplink` | `DeepLink`, `DeepLinkRouter` — `domemory://daily` and `domemory://join/CODE` parsing (Phase 5); `Join` parses but is not yet routed (Phase 6) |
| `feature/seasons`, `feature/daily` | `SeasonCard`/`SeasonLevelsScreen` and `DailyChallengeCard` — the menu entry points for Phases 4 and 5 |
| `services/notifications`, `feature/notifications` | `NotificationService`, reminder workers and the once-per-install permission primer — the three local reminders and their OS-permission sync (Phase 5) |
| `widget` | `DailyChallengeGlanceWidget`, receiver and calendar-aligned WorkManager refresh — the Daily Challenge home-screen widget (Phase 5) |
| `services/multiplayer` | Room wire models, code normalization, Firebase adapter and pure turn reducer — Phase 6 protocol foundation; UI, sharing and reconnect presentation remain follow-up work |
| `ui/theme` | `Palette` — every token is a light/dark pair resolved from the active appearance; there is no single-value color anywhere in the app |

Everything else in the plan's package layout (`ads/`, `purchases/`, `haptics/`,
`multiplayer/` feature UI) does not exist yet. Check
`ANDROID_PLAN.md` §4 before assuming a service is missing by accident rather
than by phase.

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

`app/src/test/java/` — 25 files, 207 tests, all pure Kotlin against an injected
clock, no Robolectric or instrumentation. Rule carried over from the spec: every
constant the spec pins in its §20 table, a test pins here too — that table is
the cross-check for the whole port. Run `./gradlew testDebugUnitTest` before
committing a change to anything under `core/` or `services/`.

## Status

Phases 0–5 (Levels, including its correctness hardening; Seasons; and Daily Challenge)
are complete in the current worktree. Phase 5 includes the deterministic board,
streak/milestone tracking, menu card, deep links, Glance widget and local reminders.
Phase 6 has its tested room-protocol foundation only; do not represent multiplayer as
shipped until its UI, invite flow and reconnect grace behavior land.
`ANDROID_PLAN.md` §7 contains the required handoff ledger: read it before starting
Android work, update it when a migration slice changes state, and do not infer a feature
is complete merely because its type or screen exists.
