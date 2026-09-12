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

### Phase 2 — Catalog and menu ✅
Firebase anonymous auth + `/data` read, menu/nav graph, tabs, difficulty filtering,
favourites, custom memoramas, per-board stats, onboarding, Settings/theme switching,
and the DataStore surface are complete.
Silent non-fatal network failure (§13.1) is a **test case**, not an afterthought.
**Exit:** full free-play loop over the real catalog; app usable offline.

### Phase 3 — Levels *(in progress)*
`LevelProgressStore` **as an interface from the start** (this is what makes Phase 4
cheap), `LevelProgressService`, the level map with paging and tile states, stars
with the high-water-mark + improvement-only-credit rules, the two separate star
counters, daily lives (4/day, lazy reset on the shared day key), the mistake
budget with its 0.8 s deferral and first-failure-wins rule, the four power-ups
(freeze as a `frozenUntil` deadline, not a cancelled timer), the lose-screen star
purchases, skip semantics, the intro carousel gated on the launch sequence.
**Exit:** the level ladder is fully playable and the star economy balances.

### Phase 4 — Seasons *(in progress)*
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

**Phases 0–2 are complete and green.** Phase 3 has the initial endless-level map,
atomic star economy, progress service and daily-lives storage — its correctness gaps
(zero-lives gating, cache concurrency, loss recovery, power-ups, `LevelsIntroGate`,
`StarWalletService`) landed separately on `feature/android-levels-hardening`
(`e8f859e`, off `master`, not yet merged into this branch or `master`). Phase 4 is
feature-complete: a live Firebase-backed `/seasons` source, a finite per-season
progress store, Android's own locale-resolution test against real
`Locale.toLanguageTag()` output, a season card + level-map screen, and Coil-backed
artwork loading with a 64 MB LRU disk cache. **Phase 5 is partially done**: Daily
Challenge (deterministic board, idempotent one-attempt-per-day recording, streaks,
milestones) and deep links (`domemory://daily`, plus `join/CODE` parsing ready for
Phase 6) are implemented; the Glance widget and local notifications are not. All of
this is on `feature/android-seasons`, not yet merged.

### Handoff ledger (update this with every migration slice)

| Area | State | Branch / commit | Next owner action |
|---|---|---|---|
| Phase 2 | merged | `c1faf11` / PR #43 | Device/emulator visual verification. |
| Phase 3 | merged, incomplete here | `c1faf11` / PR #43; hardening on `feature/android-levels-hardening` (`e8f859e`, unmerged) | Merge the hardening branch when ready; expect a conflict in `GameViewModel.kt`/`LevelsScreen.kt`/`NavGraph.kt` against this branch's season/daily routes — resolve by keeping both sets of routes/wiring, not by dropping either. |
| Phase 4 | feature-complete on this branch, not yet merged | `feature/android-seasons` | Verify on an emulator/device (season card renders, level map plays, expiry at the day boundary); consider deploying `firebase/firebase-database.rules.json`'s `/seasons` read rule and a real season to Firebase to exercise the exit criterion live. |
| Phase 5 | Daily Challenge + deep links done; widget and notifications not started | `feature/android-seasons` | Build the Glance `AppWidget` + `WorkManager` midnight refresh, and the notification permission primer + reminder scheduling (§11.2/11.3) — see the deferred-items note below for why these were left for a follow-up slice rather than rushed here. |

| Suite | Tests | Pins |
|---|---|---|
| `MemoryGameTest` | 9 | matching rules, dead taps, cancel-on-next-tap, win condition |
| `BoardTest` | 5 | both board constructors, per-play shuffle, difficulty fallback |
| `DifficultyTest` | 3 | the §20 time limits including the medium/hard and very-hard oddities |
| `LevelCurveTest` | 10 | every anchor, interpolation, the caps, `maxFailures > pairs` |
| `StarsTest` | 4 | the 3/2/1-star thresholds |
| `SeededGeneratorTest` | 9 | FNV-1a vectors, RNG determinism, generator pair counts |
| `DayKeyTest` | 3 | zero-padding and chronological string ordering |
| `GameViewModelTest` | 12 | the three timers, clock sources, mistake budget, first-failure-wins |
| `BoardDecoderTest` | 8 | the live 134-board payload, array/map shapes, tolerant field decoding |
| `BoardCatalogRepositoryTest` | 7 | difficulty filtering, custom-board rules, silent catalog failure |
| `SeasonTest` | 22 | the full §9.3 validation table: fail-closed structure rules and fall-back decoration rules, one test per row |
| `SeasonCatalogServiceTest` | 6 | cache-before-network, a non-empty remote payload correcting and persisting over the cache, a malformed payload leaving the cache untouched, `activeSeason` priority/id tie-break re-evaluating with no network round trip, and the real `firebase/scripts/seasons.json` fixture decoding end to end |
| `SeasonProgressServiceTest` | 12 | fresh-season defaults, win/loss, improvement-only wallet credit (never `levels.lifetimeStars`), skip, `levelCount + 1` completion, `nextLevel` null past the end, on-demand bounded `totalStars`, cross-season and cross-mode namespacing |
| `SeasonLocaleResolutionTest` | 11 | the candidate-shortening algorithm, and real `java.util.Locale.toLanguageTag()` output for `es-419`, `es-MX`, `pt-BR` and `zh-Hans-CN` — the last one is the case where Android's reported identifier (keeps the script subtag) sidesteps the exact iOS bug spec 9.5 documents |
| `SeasonPayloadJsonTest` | 3 | the pure `DataSnapshot.value` (nested `Map`/`List`) → JSON-text conversion `FirebaseSeasonCatalogSource` depends on, isolated from any live Firebase connection the same way `BoardDecoderTest` is |
| `DailyChallengeServiceTest` | 9 | deterministic per-day board, idempotent one-attempt-per-day recording, streak continuation/gap/loss rules, longest-streak monotonicity, milestone reporting |
| `DeepLinkTest` | 10 | every link form in spec 11.1's table, the custom-scheme-only host inclusion rule, code normalization (uppercase, strip non-alphanumerics), the exact-6-characters requirement |

**Phase 4 implementation, this session:** `Season.kt` gained the decoration fields
(`accentColor`, `backgroundImageURL(Dark)`, `cardImageURL`, all validated at decode
time per §9.3) and `SeasonLocaleResolver` (the §9.5 candidate-shortening lookup, pure
and Android-resource-free so it stays unit-testable without Robolectric).
`SeasonCatalogService` gained an `activeSeason: StateFlow<Season?>` re-evaluated by
`refreshActive()` — called on every `MainActivity.onResume()` so a long-lived
foreground process doesn't keep showing a season that ended at local midnight (§9.4).
`FirebaseSeasonCatalogSource` (`data/remote/`) mirrors `FirebaseBoardCatalogSource`
exactly: anonymous auth warmup, single snapshot, silent non-fatal failure; the
Map→JSON conversion its `/seasons` (dictionary, not array) shape needs is split into
the pure, testable `SeasonPayloadJson`. `SeasonProgressService` +
`SeasonLevelProgressStore` (`services/seasons/`) are the finite twin of
`LevelProgressService`/its endless store, namespaced `season.<id>.*` via two new
`UserPreferences` transactions (`recordSeasonLevelCompletion`,
`unlockSeasonLevelAtLeast`) that mirror `recordLevelCompletion`'s atomicity. A season
card (`feature/seasons/SeasonCard.kt`) renders above the endless map on the Levels tab
when a season is active, and `SeasonLevelsScreen.kt` is the finite level map (header
with icon/title/spring-eased progress bar/star total/countdown/completion state, then
a bounded `1..levelCount` grid) — both wired into `NavGraph.kt` (`season/{seasonId}`,
`season/{seasonId}/level/{level}`) and `AppContainer.kt` (`seasonCatalog`,
`seasonProgressStore(season)`, memoized per season id). Coil 3
(`coil-compose` + `coil-network-okhttp`) is wired as the `SingletonImageLoader.Factory`
in `DoMemoryApplication`, with a dedicated ~64 MB LRU disk cache directory for season
artwork; the season card enqueues a background-artwork prefetch when the active season
id changes, mirroring iOS's "prefetch on change, not on every read" guard.

**Deliberately deferred / left as seams** (spec-conformant but intentionally not
built out further this session — flag before assuming they're gaps):
- **Season/Daily card compacting** (spec 9.1: the Daily card "shrinks to a compact
  layout" when it shares the row with an active season) is not implemented — both
  cards now sit in one `Row` with `weight(1f)` each when a season is active (see the
  Phase 5 note below), which resizes them but doesn't restyle either card's internal
  layout for the narrower width. Cosmetic; the row itself is correctly wired.
- **Season nav-bar transparency** (spec 9.1: transparent when the season carries
  artwork, opaque otherwise) is not implemented — `MainActivity` doesn't yet do
  per-screen system-bar styling. Cosmetic; doesn't affect the exit criterion.
  `SeasonLevelsScreen` does already show/hide background artwork and pick the
  light/dark URL correctly, it just doesn't extend under the status bar.
- **No win/lose modal exists for level play at all yet** (endless or season) — Phase 3
  ships those as placeholders per its own status notes, so a season level currently
  ends the same way an endless one does today: no "advance to next level" button is
  wired. `LevelContext.isFinalLevel`/`store.nextLevel(after:)` are in place for whoever
  builds that modal to consume.
- **Coil version (`3.5.0`) and the network-okhttp companion artifact were not
  present in the local Gradle cache** and were resolved from Maven Central during this
  session's build — first build after a fresh checkout will need network access once
  to populate the cache, same as every other dependency here.
- **No emulator/device verification** — same limitation as every prior phase in this
  repo; the season card, map header, artwork loading and expiry logic are unit-tested
  and compiled, not seen running.

**Phase 5 implementation, this session:** `services/daily/DailyChallengeService.kt`
generates the deterministic 6-pair board (`BoardGenerators.daily`, already ported) and
wraps a new atomic `UserPreferences.recordDailyCompletion` transaction — same
one-transaction-per-finish shape as `recordLevelCompletion`/`recordSeasonLevelCompletion`
— implementing the idempotent one-attempt-per-day lock, streak
continue/reset/gap rules (`DayKey.isConsecutiveDay`, a new pure helper), and the
milestone-threshold check (`{3, 7, 14, 30, 100}`; Android has no analytics wiring yet,
so this only *reports* a hit for a future caller to log). `GameViewModel` gained an
optional `dailyChallenge` param and records the finish exactly once, from the same
single-fire `finish()` path Levels already uses. `feature/daily/DailyChallengeCard.kt`
is the menu entry point, now sharing a `Row` with the season card (moved out of the
Levels-tab-only placement Phase 4 initially used, to actually match spec 9.1's "one
row" intent) — tapping it while today is locked is a no-op, matching the deep link's
own rule below. `core/deeplink/DeepLink.kt` (pure parser, `java.net.URI`-based so it's
plain-JVM testable) and `DeepLinkRouter.kt` (holds a link that arrives before the nav
graph can act on it) implement spec 11.1's link table; `MainActivity` now has
`android:launchMode="singleTop"` and feeds `onCreate`/`onNewIntent` data through the
router, and `NavGraph` consumes a pending `Daily` link once onboarding resolves.
`Join(code)` parses correctly (tested against every link form spec 11.1 lists) but is
not routed anywhere yet — there is no multiplayer screen to send it to (Phase 6).

**Deliberately not attempted this session — flagged explicitly, not silently
dropped:** the Glance **widget** and **local notifications** (spec 8.1, 11.2, 11.3),
even though Phase 5's own exit criterion names both ("streak survives a day rollover;
widget updates at midnight"). Reasoning: both need new Gradle dependencies
(`glance-appwidget`, `work-runtime`) and Android-framework surfaces this repo cannot
verify without an emulator (`AppWidgetProvider`/`GlanceAppWidgetReceiver` XML,
`WorkManager` periodic scheduling, the API 33+ `POST_NOTIFICATIONS` runtime permission
flow and its OS-authorization-vs-app-flag sync bug iOS already hit once — spec 11.2's
"the permission bug worth not repeating"). Shipping that untested carries more risk
than the value of a same-session claim of "done." The exit criterion is therefore
**not yet met**; the next owner should build the widget and notification scheduler as
their own slice, verify the midnight refresh and the permission-sync rule on a real
device or emulator, and only then consider Phase 5 complete.

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
visual check before Phase 2.

**Deliberately deferred inside Phase 1**, all left as seams rather than gaps: haptics
(the model already reports what a tap actually did, so the table has somewhere to
attach), power-ups (the countdown already ticks against a `frozenUntil` deadline),
analytics, and the win/lose modals, which are placeholders rather than the designed
screens.

## 8. Immediate next steps

1. Merge `feature/android-levels-hardening` (Phase 3 correctness gaps) — expect a
   conflict against this branch's `NavGraph.kt`/`LevelsScreen.kt` changes; resolve by
   keeping both, not by dropping either side's wiring.
2. Merge `feature/android-seasons` (Phase 4 is feature-complete: remote source, finite
   progress store, locale resolution, card/map UI, Coil artwork cache; Phase 5's Daily
   Challenge and deep links are also done — see §7).
3. Finish Phase 5: the Glance widget and local notifications (permission primer,
   reminder scheduling, the OS-authorization-vs-app-flag sync rule) — deliberately not
   attempted this session; see the §7 note for why.
4. Verify the merged Android UI on an emulator or device, including a season played
   through Firebase end to end (publish a short test season, confirm it appears, plays,
   and stops appearing once `endDate` passes) and a Daily Challenge streak carried
   across a simulated day rollover.
