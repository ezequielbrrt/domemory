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

**No emulator system image is installed here.** The UI compiles and is unit
tested but has not been seen running. Installing an API 37 image is a one-time
~1.5 GB step (`cmdline-tools`), not part of the normal build loop.

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
| `feature/boardpicker`, `feature/game` | The only two real screens that exist today |
| `services/levels` | `LevelCurve`, `Stars`, `BoardGenerators` — ported, not yet wired into a screen |
| `ui/theme` | `Palette` — every token is a light/dark pair resolved from the active appearance; there is no single-value color anywhere in the app |

Everything else in the plan's package layout (`seasons/`, `daily/`, `ads/`,
`purchases/`, `notifications/`, `haptics/`, `multiplayer/`, `navigation/`) does
not exist yet. Check `ANDROID_PLAN.md` §4 before assuming a service is missing
by accident rather than by phase.

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

Seasons (`/seasons`) are not wired up yet. When they are, port the validation
rules in the spec exactly and add a fixture test against
`firebase/scripts/seasons.json` — a payload valid on iOS but rejected on
Android (or the reverse) would silently hide a live season from one platform's
players with nothing reporting it.

### Tests

`app/src/test/java/` — 10 files, 70 tests, all pure Kotlin against an injected
clock, no Robolectric or instrumentation. Rule carried over from the spec: every
constant the spec pins in its §20 table, a test pins here too — that table is
the cross-check for the whole port. Run `./gradlew testDebugUnitTest` before
committing a change to anything under `core/` or `services/`.

## Status

Phases 0–2 are merged. Phase 3 (Levels) and Phase 4 (Seasons) are in progress.
`ANDROID_PLAN.md` §7 contains the required handoff ledger: read it before starting
Android work, update it when a migration slice changes state, and do not infer a
feature is complete merely because its type or screen exists.
