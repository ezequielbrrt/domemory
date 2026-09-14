# DoMemory Android — Development Plan

Companion to `DOMEMORY_ANDROID_SPEC.md`, which is the behavioural source of truth.
This document holds the **decisions**, the **stack**, the **layout** and the
**phase plan**. When the two disagree about *what the app does*, the spec wins.
When they disagree about *how Android builds it*, this file wins.

Target: feature parity with iOS 4.2.0.

---

## 1. Decisions (locked)

| # | Decision | Value | Consequence |
|---|---|---|---|
| D1 | Daily Challenge board parity with iOS | **Not required** | Android uses the same seed string (`YYYYMMDD`), the same SplitMix64/FNV-1a RNG and the same 48-emoji pool, but the shuffle is Kotlin's own Fisher–Yates. Boards will be *deterministic per day and identical across all Android devices*, but not necessarily equal to iOS. See §6 Risks — this changes a marketing claim. |
| D2 | Backend + monetization credentials | **Firebase wired; ads/billing still stubbed** | `app/google-services.json` is in place for `domemory-c9211`, so the real `/data` catalog is read. Ads and Play Billing remain behind interfaces with working fakes. |
| D3 | Project structure | **Single `:app` module, package-per-feature** | Mirrors the iOS `Modules/` + `Services/` split. No Gradle module graph to maintain. Boundaries enforced by package discipline and constructor injection, not by the build system. |
| D4 | Dependency injection | **Manual — an `AppContainer` service locator** | No Hilt/KSP. The iOS app wires services by hand too; this keeps the build fast and removes an annotation-processor version dependency. Revisit if the container exceeds ~25 services. |
| D5 | Persistence | **DataStore Preferences only. No Room.** | Spec §13.2: CoreData holds one row. `hasOnboarded` becomes an explicit boolean flag rather than "a record exists". |
| D6 | Progress migration from iOS | **Out of scope** | No account layer exists on either platform. Progress stays device-local. |
| D7 | UI | **Jetpack Compose + Material 3**, custom palette | The spec's colour tokens (§14.1) are the design system; Material 3 supplies layout primitives and the shape/typography scaffolding only. |
| D8 | State | **`ViewModel` + `StateFlow`** | Direct analogue of iOS `@Observable` view models. Game loop state is one immutable `data class` emitted as `StateFlow`. |

### Decisions still open (owner: you)

| # | Question | Needed by |
|---|---|---|
| O7 | Register a second Firebase Android client for `com.ezequielbrrt.domemory.debug`? Without one, debug and release cannot be installed side by side (see §2). | any time |
| O1 | Deploy `assetlinks.json` at `domemory.app` for App Links? (iOS's `apple-app-site-association` is also undeployed — cheaper to do both at once.) | Phase 6 |
| O3 | AdMob: separate Android app id + 10 unit ids. | Phase 7 |
| O4 | Play Console app + `removeads` one-time product. | Phase 7 |
| O5 | Consent/UMP dialog on Android in place of ATT? (Affects the launch sequence, §11.4.) | Phase 7 |
| O6 | Ship the bundled `Righteous`/`PatrickHand` TTFs, or use rounded system faces as iOS effectively does? | Phase 8 |

---

## 2. Stack and toolchain

Pinned against what is installed on this machine and what is current as of 2026-09-10.

| Item | Version | Note |
|---|---|---|
| Gradle | 9.7.1 | wrapper committed; bootstrapped locally (no system Gradle) |
| Android Gradle Plugin | 9.4.0 | latest stable |
| Kotlin | 2.4.20 | K2, Compose compiler plugin bundled with Kotlin |
| JDK | 21 toolchain | Android Studio ships JBR 25; the toolchain pins 21 so CI and Studio agree |
| compileSdk / targetSdk | 37 | the only platform installed locally (`android-37.0`) |
| minSdk | 26 | gives `java.time` without desugaring — the day-key logic (§8) leans on it heavily |
| Compose BOM | 2026.09.00 | |
| Build tools | 36.0.0 | installed |

Three things the toolchain forced, worth knowing before you open the project:

- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android`
  alongside it is now an error, so the app module applies only the Android and Compose
  plugins. `libs.versions.toml` still pins the Kotlin version for the Compose plugin.
- **The JDK 21 toolchain is provisioned, not assumed.** No JDK 21 is installed on this
  machine (Android Studio ships JBR 25), so `settings.gradle.kts` applies the Foojay
  resolver and Gradle downloads a matching JDK on first build. That also makes CI work
  with no JDK setup step.
- **No Gradle on the machine.** The wrapper is committed and pinned with a distribution
  checksum, so `./gradlew` is the only entry point anyone needs.

One deliberate divergence from the spec's string catalog: `levels_lives_remaining_format`
ships on iOS as `%d of %d lives remaining`, which Android translators cannot reorder.
It is positional here (`%1$d of %2$d`). Same output, safe for the nine translations.

Libraries added per phase, not up front: `datastore-preferences` (P2),
`firebase-bom` + `database` + `auth` + `analytics` (P2), `coil` (P4),
`glance-appwidget` + `work-runtime` (P5), `zxing` (P6),
`play-services-ads` + `billing-ktx` + `review-ktx` (P7).

---

## 3. Package layout

```
com.ezequielbrrt.domemory
├── DoMemoryApplication.kt          // creates AppContainer
├── MainActivity.kt                 // single activity, Compose host, deep-link intake
├── AppContainer.kt                 // manual DI graph
├── core/
│   ├── model/        Card, Board, MemoryGame, Difficulty, GameMode, LevelContext
│   ├── rng/          SeededGenerator (SplitMix64 + FNV-1a), EmojiPool
│   ├── time/         DayKey, Clock abstraction (tests need a fake clock)
│   └── util/         format helpers
├── data/
│   ├── prefs/        DataStore keys + typed accessors (one file, mirrors §13.2)
│   ├── remote/       FirebaseCatalogSource, SeasonCatalogSource   [stubbed in P1-P2]
│   └── repository/   BoardRepository, CustomBoardRepository, FavouritesRepository
├── services/                        // 1:1 with iOS Services/
│   ├── levels/       LevelCurve, LevelProgressStore(+Service), LevelLivesService,
│   │                 StarWalletService, LevelPowerUp, LevelsIntroGate
│   ├── seasons/      Season, SeasonCatalogService, SeasonProgressService,
│   │                 SeasonLevelProgressStore
│   ├── daily/        DailyChallengeService, DailyChallengeShared
│   ├── ads/          AdsService (+ NoOpAdsService)          [fake until P7]
│   ├── purchases/    PurchaseService (+ FakePurchaseService)[fake until P7]
│   ├── notifications/NotificationService, PrimerContent
│   ├── haptics/      HapticsService
│   ├── stats/        GameStatsService, ProfileStatsService
│   ├── images/       RemoteImageService
│   ├── whatsnew/     WhatsNewManager, WhatsNewContent
│   ├── review/       AppReviews
│   └── analytics/    AnalyticsEvent (sealed), Analytics (+ LoggingAnalytics)
├── feature/
│   ├── launch/       SplashScreen, LaunchSequence
│   ├── onboarding/   FeatureIntroScreen, DifficultyPickerScreen
│   ├── menu/         MenuScreen, MenuViewModel, board grid, tabs
│   ├── game/         GameScreen, GameViewModel, CardView, Pie, PowerUpBar, modals
│   ├── levels/       LevelsScreen, LevelMap (shared), OutOfLivesModal, LevelsIntro
│   ├── seasons/      SeasonLevelsScreen, SeasonLevelsViewModel
│   ├── multiplayer/  MultiplayerService, room + join screens, QR
│   ├── settings/     SettingsScreen, AchievementsScreen
│   └── share/        ShareResultCard
├── navigation/       NavGraph, DeepLinkRouter
└── ui/theme/         Palette (light/dark pairs), Typography, Shapes, Icons map
```

`res/values/strings.xml` carries the full §19 catalog from day one; the nine
translation folders arrive at Phase 8 but the **keys** exist from Phase 1, so no
string is ever hardcoded in a composable.

---

## 4. Phase plan

Each phase is independently buildable and runnable. "Exit" is the gate to the next.

### Phase 0 — Scaffold ✅ *(this session)*
Gradle wrapper, version catalog, `:app` module, Compose + Material 3, theme tokens
from §14.1 (light/dark pairs), `strings.xml` with the English catalog, package
skeleton, `AppContainer`, unit-test harness.
**Exit:** `./gradlew assembleDebug testDebugUnitTest` green; app launches to a
placeholder.

### Phase 1 — The game ✅ *(this session)*
`Card`/`Board`/`MemoryGame` including **both** constructors (`isDoubleItem`
true *and* false — §3.1, keep the unused adjacency pairing), `choose(card)`
matching rules, the three post-tap timers with cancel-on-next-tap, the win guard,
the pie model, `Difficulty` with its time limits, square-ish grid layout,
`GameScreen` + `GameViewModel`, `LevelCurve`, `SeededGenerator`, `EmojiPool`,
free play against a hardcoded board list.
**Tests:** matching rules, flip-back cancellation, single-fire win, star
thresholds, `LevelCurve` interpolation at and between every anchor plus the caps,
RNG determinism.
**Exit:** a playable game with a working clock, on both board constructors.

### Phase 2 — Catalog and menu *(in progress)*
~~Firebase anonymous auth + `/data` read~~ ✅, ~~the menu with its three tabs, difficulty filtering, favourites,
custom memoramas, per-board stats~~ ✅, onboarding carousel + difficulty picker,
Settings, theme switching, DataStore wired for every §13.2 key.
Silent non-fatal network failure (§13.1) is a **test case**, not an afterthought.
**Exit:** full free-play loop over the real catalog; app usable offline.

### Phase 3 — Levels *(largest phase)*
`LevelProgressStore` **as an interface from the start** (this is what makes Phase 4
cheap), `LevelProgressService`, the level map with paging and tile states, stars
with the high-water-mark + improvement-only-credit rules, the two separate star
counters, daily lives (4/day, lazy reset on the shared day key), the mistake
budget with its 0.8 s deferral and first-failure-wins rule, the four power-ups
(freeze as a `frozenUntil` deadline, not a cancelled timer), the lose-screen star
purchases, skip semantics, the intro carousel gated on the launch sequence.
**Exit:** the level ladder is fully playable and the star economy balances.

### Phase 4 — Seasons
`SeasonCatalogService` with synchronous cache read on startup, the §9.3 validation
table (fail closed on structure, fall back on decoration), the §9.5 locale
resolution **with an Android-specific test against real `Locale.toLanguageTag()`
output** (the iOS bug in that section does not translate directly), activation
window on string day-keys, priority + id tie-break, `SeasonLevelProgressStore`,
the season card and map, artwork loading with a ~64 MB LRU disk cache.
**Exit:** a season published to Firebase appears, plays and expires with no app change.

### Phase 5 — Daily Challenge, widget, notifications
Deterministic 6-pair daily board, one-attempt-per-day idempotent recording,
streaks and milestones, Glance widget over a shared DataStore with a midnight
refresh, the three local reminders behind a single `activateReminders()` path,
the permission primer, launch-state sync with OS permission.
**Exit:** streak survives a day rollover; widget updates at midnight.

### Phase 6 — Multiplayer
Room documents, the 6-char code alphabet and its index, create/join/start/turn/
rematch/leave, `onDisconnect()` presence with the 15 s grace, winner and draw
rules, QR + invite share (**Play Store link**, §10.7), deep links
(`domemory://` scheme + `domemory.app` App Links).
**Exit:** an Android device plays an iOS device — the single highest-value
cross-platform behaviour in the app (§18).

### Phase 7 — Monetization
AdMob: 10 placements, the full frequency-cap rule set (per-difficulty interval,
20 s floor, 60 s rewarded suppression, 90 s global gap, 4 h app-open freshness),
unconfigured-placement hiding. Play Billing with mandatory `acknowledgePurchase`,
restore on launch, the rewarded 24-hour ad-free day, and the two invariants:
**rewarded ads stay available to purchasers**, **purchasers still spend lives**.
**Exit:** caps verified by test, not by feel.

### Phase 8 — Engagement polish and localization
What's New with its new-install/upgrade discrimination, Play In-App Review,
achievements, the spoiler-free share card, the haptics table with held generators
and its three silence rules, animations (tile pulse, press-to-0.92 spring,
progress-bar spring, numeric transitions), accessibility announcements, the nine
translations plus the **localization parity test**.
**Exit:** parity test green across all ten locales; release candidate.

---

## 5. Test strategy

The spec names the iOS suites worth mirroring (§18). Pure logic carries the risk,
so it is all written against plain Kotlin with an injected clock:

`MemoryGameTest`, `GameViewModelTest`, `LevelCurveTest`, `LevelProgressServiceTest`,
`LevelLivesServiceTest`, `StarWalletServiceTest`, `LevelsIntroGateTest`,
`SeasonTest` (validation table), `SeasonCatalogServiceTest`,
`SeasonProgressServiceTest`, `SeasonLocaleResolutionTest`, `DayKeyTest`,
`DailyChallengeServiceTest`, `AdFrequencyCapTest`, `WhatsNewManagerTest`,
`SeededGeneratorTest`, `LocalizationParityTest`.

Rule: every constant in §20 that a test can pin, a test pins. That table is the
cross-check for the whole port.

---

## 6. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| **D1 changes a product claim** — "the same board for everyone worldwide" becomes per-platform | Marketing/UX | Either soften the copy on Android, or revisit D1 before Phase 5 while the RNG is still isolated behind `SeededGenerator`. The cost of reversing D1 later is one class plus a fixture test. |
| Season catalog is shared with iOS | A payload valid on iOS but rejected on Android (or vice versa) silently hides a live-ops season | Port §9.3 exactly and add a fixture test using the real `Scripts/seasons.json`. `upload_seasons.py` hardcodes the 12-emoji floor — if the curve is retuned, three places change together. |
| Launch-sequence ordering (§11.4) | Ads landing on top of first-run surfaces; consent resolved after the SDK starts | Model it as an explicit state machine with tests, not as scattered `LaunchedEffect`s. |
| `hapticsEnabled` default | Reads as `false` if fetched carelessly → feature ships silently dead | DataStore accessor returns `?: true`; unit-tested. |
| Cross-platform multiplayer untested | The highest-value shared behaviour breaks quietly | Phase 6 exit criterion is a live Android↔iOS match, not a passing test. |
| API 37 / AGP 9.4 are very new | Toolchain churn | Versions pinned in `gradle/libs.versions.toml`; the wrapper is committed. |
| Artwork URLs are permanent contracts (§9.7) | Overwriting bytes at a live URL poisons CDN caches for both platforms | Never overwrite; publish at a new filename. Applies to the shared catalog, so it is an Android concern too. |

---

## 7. Status

**Phase 0, Phase 1 and Phase 2 are complete.** Phase 3 (Levels) landed its first cut in
PR #43 — `LevelCurve`, the initial level map, an atomic star economy at the DataStore
layer, `LevelProgressService`, `LevelLivesService` and daily-lives storage — and this
branch (`feature/android-levels-hardening`) closed the correctness gaps that cut left
open, in the order the handoff called out:

1. **Lives gate at level entry (spec 7.4).** Tapping a level tile with 0 daily lives now
   goes through `LevelsViewModel.attemptStart(level)`, a suspend gate that checks
   `LevelLivesService.hasLivesRemaining()` before navigation and surfaces the out-of-lives
   prompt instead when it's false. Previously `LevelsScreen` navigated on any tap with no
   check at all.
2. **`LevelProgressService` cache concurrency.** The old cache was a plain
   `mutableMapOf<Int, Int>` and a `var highest`, mutated from coroutines launched on
   `AppContainer.applicationScope` — `Dispatchers.IO`, a real thread pool, not a confined
   thread. Two bugs followed: every call to `stars(level)` for an unloaded level queued
   *another* redundant disk read instead of joining the one already in flight, and the
   backing map was never synchronized against concurrent writers.
   The fix keeps the original "mutation updates the in-memory cache inline" shape but
   makes every piece of it safe: `ratings` is a `ConcurrentHashMap`, `highestUnlockedLevel`
   an `AtomicInteger` raised through a real compare-and-set (`raiseHighestTo`, never a
   plain read-then-write that a second racing completion could turn into a lost update),
   and a `loadingLevels` concurrent set makes `stars(level)`'s fetch-on-miss atomic —
   exactly one disk read is ever in flight per level no matter how many callers ask at
   once. An init-time warm-up loop also starts loading every already-cleared level's
   rating at construction, the same way `highestUnlockedLevel` was already eager, so the
   *first* `stars(level)` call for a previously-cleared level doesn't flash a stale 0.
   `recordCompletion` also now returns the level's high-water-mark rating after recording
   (matching iOS), not just this run's own — a real deviation the old code had (a sloppy
   replay of a 3-star level was reporting 1, not 3).
   A `StateFlow`-per-key design (DataStore's own flow bridged via
   `stateIn(scope, SharingStarted.Eagerly, ...)`) was tried first and is architecturally
   more idiomatic, but proved untestable: its cache update is a *second*,
   independently-scheduled coroutine (observing DataStore's `data` flow) rather than part
   of the same coroutine that performed the write, and that update never reliably became
   visible to a test's `advanceUntilIdle()` — 12 tests failed consistently, not flakily,
   because there is nothing forcing that second coroutine to run before the assertion.
   `StarWalletService` hit the identical wall and got the identical fix (mutators update
   the cache inline; see its class doc). Both classes' docs record this as the reason,
   so nobody rediscovers it by reintroducing a `stateIn` bridge here.
3. **Loss recovery.** The biggest behavioral gap: the old `finish()` committed a Level
   loss (spent a life, recorded progress and stats) the instant `loseReason` would be
   set, before the player ever saw a rescue option — so a "free" mistake-budget rescue or
   a star-bought life could never actually be free, because the life was already gone.
   iOS defers this: `logGameFinishedIfNeeded` for a loss only runs from the lose modal's
   own "Try Again" / "Go to menu" handlers. `GameViewModel` now mirrors that — reaching a
   `Lost` outcome only shows the lose screen; `retry()` and `acknowledgeLossAndQuit()` are
   the "walk away" paths that actually commit it (`commitLossIfNeeded()`, idempotent per
   attempt), while `forgiveMistakesWithStars()` and `buyLifeWithStars()` undo the loss
   without ever committing it. `retry()` also refuses to restart when committing the loss
   leaves the player at 0 lives, the same gate as entry. `skipLevelWithStars()` commits
   (skipping still counts as a loss) and unlocks the next level without stars. This
   deferral is Levels-only — free play and the Daily Challenge keep the original
   immediate-commit behavior, since neither has a lose-screen rescue to protect, and
   changing that was out of scope here.
   Separately, `checkMistakeBudget()` had a real exploit: it cancelled-and-rescheduled the
   0.8 s deferred loss on *every* mismatch once the budget was already busted, so a player
   who kept mismatching every &lt;0.8 s could postpone the loss forever. It's now armed once
   per bust and only re-armed by `pause()`/`resume()` (which also fixed a second bug: a
   pause during the 0.8 s window used to drop the pending loss entirely instead of
   re-arming it on resume).
4. **Power-ups.** All four (`LevelPowerUp.EXTRA_TIME` / `PEEK` / `FREEZE` / `REVEAL_PAIR`)
   are wired into `GameViewModel` (`buyExtraTime` / `buyPeek` / `buyFreeze` /
   `buyRevealPair`), Levels/Seasons-gated, spent through the new `StarWalletService`, and
   rendered in a power-up bar under the HUD (`GameScreen`'s `PowerUpBar`). Freeze is a
   `frozenUntil` deadline the existing tick loop already checked (per this plan's Phase 3
   note) — buying it just writes the deadline and flips `isFrozen` eagerly for instant
   feedback. Peek ends itself on `pause()` (spec 7.6's explicit rule) via the same
   `endPeekIfActive()` the countdown already needed. Reveal pair reuses
   `MemoryGame.findUnmatchedPair()` (already scaffolded, already tested) plus a new
   `faceUp(ids, now)` model method, and re-arms the normal 2 s flip-back. The lose-screen
   purchases from spec 7.7 (buy a life, forgive mistakes, skip level) are also wired, with
   a confirmation dialog only in front of skip, matching the spec's "no confirmation
   step" rule for everything else. Rewarded-ad alternatives for lives/mistakes stay a
   Phase 7 seam — there is no `AdsService` yet.
   PR review fix: `spendOnPowerUp` originally applied a power-up's effect off a cached
   `canAfford()` check and fired `StarWalletService.spend()` afterward without checking
   its result — two buys racing the same stale balance (a double-tap, or two different
   power-ups tapped back-to-back) could both pass `canAfford` and both apply, with only
   the first `spend()` actually succeeding against the real, serialized balance. It now
   awaits `spend()` first and only calls `apply()` once that succeeds, refunding via
   `credit()` if `apply()` itself then reports failure. Pinned by
   `GameViewModelTest`'s "two concurrent buys against one power-up's worth of stars grant
   exactly one effect".
5. **Levels intro.** `LevelsIntroGate` (services/levels) ports the one-shot, mark-on-
   dismiss rule from iOS 1:1, backed by the `levelsIntroShown` DataStore key that was
   already sitting unused in `UserPreferences`. `LevelsViewModel` presents it on first
   load and exposes `presentIntro()` for the map's info button. **Deliberately
   incomplete relative to iOS**: iOS additionally gates the intro on the launch sequence
   (§11.4 — ATT, ads bring-up, the notification primer all finishing first), because
   Levels is the landing tab and would otherwise race their covers. Android has none of
   those surfaces yet (ATT has no Android equivalent decided, ads are a Phase 7 stub, no
   notification primer exists) — there is no launch sequence to gate on. This is a
   documented seam, not a missed requirement: wiring in a real `canPresentIntro` flag
   later is one parameter, the same shape as iOS's `LevelsView.canPresentIntro`.

New/changed services: `StarWalletService` (spendable wallet, `spend`/`credit` go straight
through `UserPreferences`' atomic transactions rather than deciding off a cache),
`LevelPowerUp` (costs and tuning constants, ported verbatim from iOS), `LevelsIntroGate`,
plus `LevelLivesService.hasLivesRemaining()` and `MemoryGame.forgiveFailures` / `.faceUp`.
`LevelsViewModel` and a rebuilt `LevelsScreen` (header with live lives/star pills, the
out-of-lives modal, the tile grid, a simple intro dialog) replace the old placeholder grid
that read `LevelProgressService` directly with no gating and no header at all.

| Suite | Tests | Pins |
|---|---|---|
| `MemoryGameTest` | 9 | matching rules, dead taps, cancel-on-next-tap, win condition |
| `BoardTest` | 5 | both board constructors, per-play shuffle, difficulty fallback |
| `DifficultyTest` | 3 | the §20 time limits including the medium/hard and very-hard oddities |
| `LevelCurveTest` | 10 | every anchor, interpolation, the caps, `maxFailures > pairs` |
| `StarsTest` | 4 | the 3/2/1-star thresholds |
| `SeededGeneratorTest` | 9 | FNV-1a vectors, RNG determinism, generator pair counts |
| `DayKeyTest` | 3 | zero-padding and chronological string ordering |
| `GameViewModelTest` | 43 | the three timers, clock sources, mistake budget, first-failure-wins, mistake-deferral pause/resume re-arming, deferred loss commit, all four power-ups, all three lose-screen purchases, concurrent power-up buys against one power-up's worth of stars |
| `BoardDecoderTest` | 8 | the live 134-board payload, array/map shapes, tolerant field decoding |
| `BoardCatalogRepositoryTest` | 9 | difficulty filtering, custom-board rules, silent catalog failure |
| `LevelProgressServiceTest` | 8 | high-water-mark stars, wallet-crediting completion, skip, cache concurrency (cold-start correctness, concurrent readers/writers) |
| `LevelLivesServiceTest` | 2 | lazy daily reset, loss-only spend, refill cap, the entry gate |
| `StarWalletServiceTest` | 6 | credit/spend/canAfford, authoritative spend over a stale cache, `refresh()` picking up a credit `LevelProgressService` made directly |
| `LevelsIntroGateTest` | 2 | one-shot, seen-on-dismiss-not-on-present |
| `UserPreferencesTest` | 27 | every DataStore key in spec 13.2 |
| `MenuViewModelTest` | 10 | tabs, favourites, difficulty filtering |
| `CreateMemoramaViewModelTest` | 6 | custom-board validation and save |

`GameViewModelTest` has a gotcha worth knowing before adding to it: `advanceUntilIdle()`
is only safe once the game has already reached a terminal outcome — the tick loop is a
`while (isActive) { delay(100); ... }` coroutine, and if it is still running,
`advanceUntilIdle()` will drain every scheduled tick, fast-forwarding the level's clock to
a real timeout instead of just letting a fire-and-forget star spend or life-refill settle.
This file uses `runCurrent()` for that instead (runs only already-ready work, never
touches the tick loop's still-pending `delay`), and it is what silently made several of
this branch's own new tests fail against otherwise-correct production code before the fix
was to the test, not `GameViewModel`.

**Firebase is live.** `app/google-services.json` is committed for project
`domemory-c9211` (client `com.ezequielbrrt.domemory`), and the app reads the real
`/data` catalog — 134 boards — through `FirebaseBoardCatalogSource`, falling back to a
bundled set when the network or the payload disappoints. Decoding is a pure function
pinned against `src/test/resources/data.json`, a verbatim copy of the live node.

Two things that came with it:

- **The debug `applicationIdSuffix` is gone.** The Firebase project has one Android
  client and the google-services plugin fails the build when the applicationId matches
  none of them. Registering a `.debug` client restores side-by-side installs (O7).
- **`app/google-services.json` is committed on purpose.** It ships inside every APK and
  is public config keyed to the package name. The service-account key sitting in
  `~/Downloads` is the actual secret and is git-ignored by pattern; it was not copied
  into the repo and nothing needs it.

**Not verified on a device.** No emulator system image is installed and no device is
attached, so the UI has been compiled and unit-tested but not seen running. Installing
an API 37 system image (~1.5 GB, needs `cmdline-tools`) is the next step if you want a
visual check.

**Deliberately deferred**, all left as seams rather than gaps:

- **Haptics and analytics** — still absent everywhere, carried over from Phase 1 (the
  model already reports what a tap actually did, so the haptics table has somewhere to
  attach; every power-up/purchase call site above is a natural analytics-event site once
  `AnalyticsService`'s Android counterpart exists).
- **Rewarded-ad refills** for out-of-lives and the mistake rescue (spec 7.4, 7.5) — both
  need `AdsService`, a Phase 7 stub today. The star-purchase alternative for each is fully
  wired; the ad button is simply absent rather than present-and-broken.
- **The Levels intro's launch-sequence gate** (spec 7.9, §11.4) — see item 5 above.
  `LevelsIntroGate`'s one-shot rule is complete and tested; the ordering gate against
  ATT/ads/notification-primer has nothing to gate against yet on Android.
- **Level-map presentation polish** — the pulsing current tile, the press-to-0.92-scale
  spring, and the swipeable intro carousel (spec 7.8, 7.9) are all still plain Compose
  layouts with no animation. `LevelsScreen`'s tile grid and `LevelsIntroDialog` are
  functionally complete and spec-accurate but visually a placeholder for these.
- **Seasons** (`SeasonLevelProgressStore`, Phase 4) — `LevelProgressStore` was already an
  interface for exactly this reason; nothing in this branch touches
  `feature/android-seasons`, per the ordering constraint that started this branch.

## 8. Immediate next steps

1. Verify the Levels flow (lives gate, power-ups, lose-screen purchases, the intro) on an
   emulator or device — everything in §7 above is unit-tested but, like the rest of the
   app, has not been seen running.
2. Decide **O1** — deploying `assetlinks.json` and `apple-app-site-association` together
   is cheaper than doing it twice.
3. Start Phase 4 (Seasons) on `feature/android-seasons`, now that Phase 3's gaps are
   closed — `LevelProgressStore` and the deferred-loss-commit model in `GameViewModel`
   are both built so a `SeasonLevelProgressStore` adapter is the only new piece a season
   attempt needs.
