# Season Levels

Seasonal themed level progressions ("Spooky Season", "Christmas Season") that
appear in the menu only while their season is running, controlled entirely from
Firebase Realtime Database.

## Requested outcome

The menu's daily-challenge area currently holds a single full-width card. Split
it into two half-width cards side by side — the existing Daily Challenge, and a
new Season Levels entry point. Season Levels is a sequential level map built
from a Firebase-supplied emoji pool, unlocked in order, with star ratings,
reusing the existing `LevelCurve` / `StarWalletService` / `LevelLivesService`
systems. Firebase supplies the pool, the length, and the theming — not the
individual boards. Flipping `enabled` to `false` in Firebase is the kill switch.

## Evidence observed

Verified by reading the code, not assumed:

- `LevelCurve.pairAnchors` is `[(1,3),(5,4),(10,6),(25,9),(50,12)]` — it tops out
  at 12 pairs (`DoMemory/DoMemory/Services/Levels/LevelCurve.swift:15`). A season
  whose `emojiPool` has fewer than 12 entries cannot fill its own late boards, so
  decoding must reject it.
- Only **four** sites in `MemorizeViewModel.swift` hard-code
  `LevelProgressService`: lines 128, 429, 513, 712. Every other level branch
  reads the derived `levelNumber` computed property.
- All 20 consumers outside `MemorizeViewModel` — `WinModal`, `LoseModal`,
  `PauseModal`, `MemorizeView` and their listener protocols — consume only
  `levelNumber: Int?` and `isDailyChallenge: Bool`. Keeping both derived
  properties' signatures identical means the refactor touches no consumer.
- `GameMode` is declared `Equatable`, and `isDailyChallenge` is implemented as
  `mode == .dailyChallenge` (`MemorizeViewModel.swift:38`). A protocol existential
  in the associated value kills the synthesized conformance.
- `firebase-database.rules.json` (repo root) grants `.read` to `data` and the
  multiplayer nodes only. `/seasons` has no rule, so it defaults to denied.
- `advanceToNextLevel` (`MemorizeViewModel.swift:509`) increments without a
  ceiling, and `WinModal.primaryAction` (`WinModal.swift:35`) routes to "Next
  Level" whenever `levelNumber != nil`.
- All ten `Localizable.strings` files hold exactly 235 keys.
- `LevelLivesService` and `StarWalletService` are already global singletons keyed
  on `levels.lives.*` and `levels.wallet.balance`.

## Firebase payload shape

```json
{ "seasons": { "spooky-2026": {
    "enabled": true, "startDate": "2026-10-01", "endDate": "2026-11-02",
    "priority": 10, "levelCount": 20, "icon": "🎃", "accentColor": "#FF6B1A",
    "emojiPool": ["👻","🎃","🕷️","🦇","🧛","⚰️","🕸️","🔮","🍬","🧟","🪦","😱"],
    "strings": {
      "en":     { "title": "Spooky Season", "subtitle": "20 haunted levels" },
      "es-419": { "title": "Temporada de Sustos", "subtitle": "20 niveles embrujados" }
    } } } }
```

Active season = `enabled && today ∈ [startDate, endDate]`; highest `priority`
wins a tie.

## Assumptions

- `/seasons` is read the same way `/data` already is: one-shot RTDB read after
  anonymous auth. No new SDK dependency, no Remote Config.
- Season progress is namespaced per season ID, so a season ending cannot corrupt
  endless-Levels progress and a returning season resumes where it left off.
- Board generation is deterministic per `(season, level)` so the same player sees
  the same board across reinstalls and devices.

## Non-goals

- No completion reward: no star bonus, no Achievements badge. A one-line hook may
  be left as a comment, never as an implementation.
- No season-themed Daily Challenge. `DailyChallengeService.boardForToday` keeps
  sourcing from `EmojiPool.all`; the "identical board for every user" property
  documented at `DailyChallengeService.swift:18-23` is preserved deliberately, so
  players on a stale season cache or offline cannot diverge. That file is not to
  be touched.
- No separate season difficulty curve and no offset field. `LevelCurve` is reused
  as-is; a season's level 1 is a 3-pair/90s board even for a veteran.
- No separate lives budget or star wallet for seasons.
- No `MARKETING_VERSION` bump and no App Store Connect work. This feature is not
  being shipped in this session.

## Success criteria

1. With an active season in Firebase, the menu shows two half-width cards and the
   season card opens a working level map whose boards draw from the season pool.
2. With no active season — or `enabled: false`, or a date outside the window, or
   no network and no cache — the daily card renders full width exactly as it does
   today, and no season UI appears anywhere.
3. Season play earns stars, spends lives, and uses power-ups through the same
   services as endless Levels, from one shared budget.
4. A finite season ends: clearing its last level shows a season-complete state and
   never offers a level past `levelCount`.
5. Endless-Levels progress is unaffected by any season activity.
6. All ten `Localizable.strings` files stay at equal key counts.

## Question ledger

| # | Question | Owner | Status | Answer | Blocks |
|---|---|---|---|---|---|
| 1 | Lives and stars shared between Levels and Seasons, or separate per mode? | user | answered | **Shared.** One daily 4-life budget, one star wallet. `LevelLivesService` and `StarWalletService` stay global singletons, untouched; no namespaced twins. | 1, 2 |
| 2 | Is a season finite or endless? | user | answered | **Finite.** `levelCount` from Firebase with a season-complete terminal state. Makes the `advanceToNextLevel` / `WinModal.primaryAction` ceiling mandatory. Menu card renders "7 / 20". | 1, 2, 3 |
| 3 | Does completing a season grant a reward? | user | answered | **None this session.** No star bonus, no Achievements badge, no badge surface. A hook may be left as a comment only. | 3 |
| 4 | Reuse `LevelCurve` for season difficulty, or a separate curve? | user | answered | **Reuse as-is.** No offset field, no season anchor table. `maxFailures`, the pie threshold and the time branch keep working untouched. | 1, 2 |
| 5 | Should the Daily Challenge draw from the season's emoji pool? | user | answered | **No.** Preserve the identical-board-for-every-user property. Do not touch `DailyChallengeService` or its doc comment. | 3 |

No open questions. Any decision surfacing in a later phase stops that phase and
is reported rather than guessed.

## Phases

### Phase 1 — Season model, catalog service, progress service, board generation

- **Objective:** the data layer, with no UI and no reachable behaviour change.
- **Dependencies:** none.
- **Branch:** `feature/season-levels-catalog`
- **Tasks:**
  - `DoMemory/DoMemory/Services/Seasons/Season.swift` — Codable model plus locale
    resolution (`es-419` → `es` → `en` → app-side fallback string). Decoding
    rejects a season whose `emojiPool` has fewer than 12 entries.
  - `SeasonCatalogService.swift` — `@Observable`, one-shot `/seasons` read,
    caches the decoded payload to `UserDefaults` so the card renders instantly on
    cold launch and offline. Exposes `activeSeason: Season?`. Fails closed: no
    cache and no network means no season.
  - `SeasonProgressService.swift` — mirrors `LevelProgressService`, namespaced
    `season.<id>.highestUnlocked` and `season.<id>.stars.<n>`. Stars credit
    through the shared `StarWalletService`.
  - Board generation: `SeededGenerator` seeded `"season-<id>-<level>"`, drawing
    from `season.emojiPool`, pair count from `LevelCurve.pairs(for:)`.
  - Add `"seasons": { ".read": true, ".write": false }` to the repo-root
    `firebase-database.rules.json`.
- **Acceptance criteria:** workspace build clean; new unit tests pass; existing
  suite still green; no UI or menu file touched; `project.pbxproj` diff contains
  only the new Season sources.
- **Validation:** `xcodebuild -workspace DoMemory.xcworkspace -scheme DoMemory
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
  -configuration Debug build` and `test`, run from `DoMemory/`; `tuist generate
  --no-open` with a staged-diff check on `project.pbxproj`.
- **Validation owner:** ios-feature-engineer, re-checked by the orchestrator.
- **Tests:** locale fallback chain, date-window activation, `enabled` kill switch,
  priority tie-break, sub-12 emoji-pool rejection, board determinism, and season
  progress isolation from endless-Levels keys.
- **Excluded:** any UI, any `GameMode` change, any localization or analytics work.
- **User-visible:** no. The changelog entry is named by Phase 3.
- **Note for the PR description:** the rules file lives at repo root and
  deploying it is a manual Firebase CLI/console step that the commit alone does
  not perform.
- **PR state:** not opened.

### Phase 2 — `LevelProgressStore` protocol and the `GameMode` refactor

- **Objective:** let season play inherit stars, power-ups, skip and the mistake
  budget without a parallel case.
- **Dependencies:** Phase 1 merged.
- **Branch:** `feature/season-levels-gamemode`
- **Tasks:**
  - Introduce `LevelProgressStore`; conform both `LevelProgressService` and
    `SeasonProgressService`.
  - Change `case level(Int)` to `case level(LevelContext)` carrying the level
    number, the store witness, an optional `seasonID`, and an optional
    `levelCount`.
  - Rewrite the four hard-coded service sites (lines 128, 429, 513, 712) against
    the context's store.
  - **Hand-write `Equatable` for `GameMode`** comparing `(number, seasonID)`, or
    rewrite `isDailyChallenge` as `if case .dailyChallenge = mode`. The protocol
    existential kills the synthesized conformance that `MemorizeViewModel.swift:38`
    depends on. Without this the build breaks.
  - Season-complete terminal state: `advanceToNextLevel` must not pass
    `levelCount`, and `WinModal.primaryAction` must not offer "Next Level" on a
    season's final board.
  - Keep `levelNumber: Int?` and `isDailyChallenge: Bool` signatures identical so
    no consumer changes.
- **Acceptance criteria:** build and full suite green; `MemorizeViewModelTests`
  passes unchanged as the regression gate; no consumer file modified except the
  win-modal terminal state.
- **Excluded:** UI, localization, analytics.
- **User-visible:** no.
- **PR state:** not opened.

### Phase 3 — UI

- **Objective:** the card split and the season level map.
- **Dependencies:** Phase 2 merged.
- **Branch:** `feature/season-levels-ui`
- **Tasks:**
  - `MenuView.swift:129` becomes an `HStack` of two half-width cards; compact
    vertical variants (icon, title, badge) of both, since the existing horizontal
    `DailyChallengeCard` (`MenuView.swift:449`) squeezes badly at half width.
    With no active season the daily card returns to full width.
  - Extract `LevelMapView(tiles:onSelect:header:)` from `LevelsView.swift:27-51`.
    Levels keeps its stars/lives header; Season gets title, days remaining and
    "n / levelCount" progress.
  - `SeasonLevelsView` plus its view model; season-complete state.
  - Push via `.navigationDestination` alongside the daily destination at
    `MenuView.swift:224`.
- **Acceptance criteria:** both layouts verified in the simulator (season active
  and season absent); Levels map unchanged after the extraction.
- **User-visible:** yes. **This phase names the changelog entry; Phases 4 and 5
  amend that same entry rather than adding near-duplicates.** Entry written under
  `[Unreleased]`, following the file's convention of versioning that heading at
  release time (see `8d4c481`). `MARKETING_VERSION` deliberately not bumped.
- **PR state:** not opened; implemented and validated, held local.

### Phase 4 — Localization and analytics

- **Objective:** app-side chrome in ten languages, and comparable funnels.
- **Dependencies:** Phase 3 merged.
- **Branch:** `feature/season-levels-localization`
- **Tasks:**
  - **Corrected task list.** The key names originally written here
    (`season_ends_in_format`, `season_ends_today`, `season_completed`) were
    guessed before Phase 3 was written and do not exist. The seven keys Phase 3
    actually shipped — currently English copy in all ten locales, flagged by a
    `Season levels — English copy pending translation (Phase 4)` comment at
    line 263 of each file — are:
    `season_progress_format` (`"%1$d / %2$d"`), `season_days_left_format`
    (`"%d days left"`, only ever called with 2+), `season_one_day_left`,
    `season_last_day`, `season_complete_badge`, `season_complete_title`,
    `season_complete_message`.
  - Translate those seven into the nine non-English locales, reusing how each
    locale already renders "level" and "stars" in the Levels keys so the season
    UI does not read as a different product. Keep `%1$d` / `%2$d` positional
    specifiers intact. Remove the pending-translation comment per file as it is
    completed.
  - Add `season_fallback_title` in all ten locales and replace the plain constant
    at `Season.swift:81` that its `// Phase 4:` marker points at.
  - Add a VoiceOver label key for the season progress bar in
    `SeasonLevelsView.swift`, whose accessibility label is currently the raw
    `"7 / 20"` string. Phase 3 deferred it here because it needs ten locales.
  - **Assert key parity across all ten files.** Verified baseline at the start of
    Phase 4: **242 keys each** (235 pre-season + Phase 3's 7). Ends at 242 + N.
    Add a test guarding this invariant — it is now load-bearing and unguarded.
  - Add an optional `seasonID` to the existing `levelStarted` / `levelFinished` /
    `levelUnlocked` events (`AppConfiguration.swift:50-52` and `:241-250`). No
    parallel event set. Phase 2 left `// Phase 4:` markers at each call site.
  - **Surfaced by Phase 2:** on a season's final win, `levelUnlocked` fires for
    `levelCount + 1`, because `recordCompletion` uses `highestUnlocked =
    levelCount + 1` as its completion marker and `logGameFinishedIfNeeded` logs
    the unlock whenever `didWin && !alreadyUnlockedNext`. Harmless today and
    correctly left alone by Phase 2 (analytics were out of its scope), but Phase 4
    must decide: suppress it for a season's last level, or let the `seasonID`
    dimension make it separable and keep it as a completion signal. The same
    artifact reaches `tapOnConfirmSkipLevel`, which logs `levelUnlocked` directly.
- **Acceptance criteria:** every locale has equal key count; no hard-coded
  user-facing string in the season UI.
- **User-visible:** yes — amends the Phase 3 changelog entry.
- **PR state:** not opened.

### Phase 5 — Tooling and seeded season

- **Objective:** a repeatable way to publish and kill a season.
- **Dependencies:** Phase 4 complete.
- **Branch:** ~~`feature/season-levels-tooling`~~ — **superseded.** By user
  decision Phases 4 and 5 ship together as one closing PR, so Phase 5 stacks as
  a separate commit on `feature/season-levels-localization` on top of `789b4a4`.
- **Tasks:** `Scripts/upload_seasons.py` mirroring `Scripts/upload_games.py`'s
  `--dry-run` / `--credentials` contract; `Scripts/seasons.json` (canonical,
  hand-edited, keyed by season id) as the seeded `spooky-2026` catalog;
  `Scripts/seasons_data.json` as its generated `--dry-run` output, tracked the
  same way `Scripts/data.json` already is for games; document the `enabled`
  kill switch and the still-unpublished `firebase-database.rules.json` rule in
  the script's own header.
- **Acceptance criteria:** `--dry-run` validates the seeded season without
  credentials and rejects a sub-12 *distinct* emoji pool the same way the app
  does — including 12 raw entries with a duplicate, which the app also rejects
  because it deduplicates before measuring.
- **User-visible:** yes — amends the Phase 3 changelog entry.
- **PR state:** not opened; implemented and validated, held local.

## State ledger

| Phase | State | PR | Merge commit |
|---|---|---|---|
| 1 — Model, catalog, progress, board generation | merged (`475106f`) | #27 | `de328f9` |
| 2 — `LevelProgressStore` + `GameMode` refactor | merged (`f4d75cd`) | #27 | `de328f9` |
| 3 — UI | merged (`8e10052`, `b0947d9`) | #28 | `2b30d36` |
| 4 — Localization + analytics | validated, held local | — | — |
| 5 — Tooling + seeded season | validated, held local (`<uncommitted>`) | — | — |

Overall: approved. Phases 1 and 2 shipped together as PR #27, merged at
`de328f9` — the user chose to hold Phase 1 rather than deliver it alone, then
push both once Phase 2 was validated. Phase 3 is implemented and validated on
`feature/season-levels-ui`, branched from `de328f9`, and is held local pending
the same delivery decision.

### Phase 3 validation evidence

Bound to the tree on `feature/season-levels-ui`, base `de328f9`: 19 source and
test files, including three new season view files, the extracted `LevelMapView`,
and one new test file, plus `CHANGELOG.md`, `project.pbxproj` and this plan.

- `tuist generate --no-open` — success.
- `xcodebuild -workspace DoMemory.xcworkspace -scheme DoMemory -sdk iphonesimulator
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -configuration Debug test`
  — `** TEST SUCCEEDED **`, 167 passed, 0 failed, 0 skipped.
- 15 new tests (`SeasonLevelsViewModelTests`) over the 152 baseline, covering the
  bounded map's cleared/current/locked split, a completed season leaving no
  current tile, a non-positive `levelCount` yielding an empty map rather than a
  crash, the countdown's inclusive last day and its clamp past the end date, and
  the icon and accent-colour fallbacks a malformed payload reaches first.
- `project.pbxproj`: 44 added lines, 0 removed — only the new file and group
  entries, and re-running `tuist generate` reproduces the committed file. Still
  `objectVersion = 55`, zero `expectedSignature` occurrences. No Xcode rewrite
  signature.
- The whole pre-existing suite still passes, which is the regression gate for the
  `LevelMapView` extraction: `LevelsView` and `LevelsViewModel` were changed only
  to consume the shared map and the now-top-level `LevelTile`, with the tile
  styling moved across unmodified.

**Carried into Phase 4:** the seven new keys are present in all ten
`Localizable.strings` files but hold English copy in every locale, marked with a
`Season levels — English copy pending translation (Phase 4)` comment. Phase 4
owns the real translation pass. The keys are `season_progress_format`,
`season_days_left_format`, `season_one_day_left`, `season_last_day`,
`season_complete_badge`, `season_complete_title` and `season_complete_message` —
note these differ from the names this plan originally guessed, and
`season_fallback_title` is still unwritten (`Season.swift` carries the marker).

**Not verified:** no simulator run of the two card layouts. The acceptance
criterion asked for both states checked visually; the test suite covers the view
model and the presentation fallbacks, but the half-width card layout and the
full-width no-season fallback have not been looked at on a device.

**PR self-review, fixed in #28:** `SeasonCard` seeded its `SeasonProgressService`
into `@State` from its `init`. SwiftUI does not re-run a `State(initialValue:)`
initializer when a view is rebuilt in the same structural position, so a season
*handover* — `refreshActiveSeason()` crossing local midnight into the next
season, or `load()` correcting a stale cache to a different one — would have
rendered the incoming season's title and `levelCount` against the outgoing
season's progress store. Now derived from `season` on each evaluation.

**PR self-review, left for Phase 4:** the season progress bar's VoiceOver label
is the raw `season_progress_format` string (`"7 / 20"`), which reads poorly.
A proper label needs a new key in all ten locales, so it belongs to Phase 4's
translation pass rather than adding an English-only key now.

### Phase 1 validation evidence

Bound to the staged tree on `feature/season-levels-catalog`, base `bd02fd6`:
9 files, 1249 insertions, 0 deletions.

- `tuist generate --no-open` — success.
- `xcodebuild -workspace DoMemory.xcworkspace -scheme DoMemory -sdk iphonesimulator
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -configuration Debug test`
  — `** TEST SUCCEEDED **`, 0 errors. Run by the engineer and **re-run
  independently by the orchestrator** against this same staged state.
- 58 new tests (`SeasonTests` 23, `SeasonCatalogServiceTests` 13,
  `SeasonProgressServiceTests` 22); the entire pre-existing suite still passes,
  including `MemorizeViewModelTests`, `LevelProgressServiceTests` and
  `StarWalletServiceTests`.
- `project.pbxproj`: staged diff is 32 added lines, 0 removed, containing only
  the six new source/test file entries and the new `Seasons` group. Zero
  occurrences of `objectVersion`, `expectedSignature`, `OTHER_SWIFT_FLAGS` or
  `SWIFT_ACTIVE_COMPILATION_CONDITIONS`; the file still reads
  `objectVersion = 55`. No Xcode rewrite signature.
- No runtime/simulator check: Phase 1 adds no UI and nothing calls
  `SeasonCatalogService.shared.load()` yet, so there is no reachable behaviour to
  observe. "No reachable behaviour change" is the acceptance criterion.
- Not covered by tests: the live Firebase read path, which needs a real backend.
  It mirrors `MenuViewModel.getData()` (`MenuViewModel.swift:167-219`), differing
  only in node name and in treating the payload as `[String: Any]`. Everything
  downstream of the snapshot is pure and fully tested.

Implementation notes worth carrying forward: the 12-emoji floor is *derived*
(`LevelCurve.pairs(for: .max)`) rather than typed, so retuning the curve moves it
automatically; the pool is deduplicated before measuring, because a repeated
emoji would deal four matching cards; `enabled` defaults to `false` when absent;
and `season.*` writes were tested not to inflate `levels.lifetimeStars` or touch
any `levels.highestUnlocked` / `levels.stars.<n>` key, while the shared wallet is
credited as designed.

### Phase 2 validation evidence

Bound to the staged tree on `feature/season-levels-catalog`, base `475106f`:
7 files, 519 insertions, 20 deletions.

- `tuist generate --no-open` — success.
- `xcodebuild … -workspace … test` — `** TEST SUCCEEDED **`, exit 0, 0 failures.
  Run by the engineer and **re-run independently by the orchestrator** against
  this same staged state.
- 152 tests, up from the Phase 1 baseline of 140; the 12 added are
  `SeasonGameModeTests`.
- **Regression gate holds:** `MemorizeViewModelTests` is unmodified (confirmed by
  `git diff --cached --name-only`) and passes. A failing test would have flipped
  the run to `TEST FAILED`, so the green result covers every pre-existing suite.
- `project.pbxproj`: staged diff is 12 added lines, 0 removed — only the three
  new file entries. Still `objectVersion = 55`, no Xcode rewrite signature.
- No do-not-touch file modified: `DailyChallengeService`, `EmojiPool`,
  `LevelCurve`, `MenuView`, `LevelsView`, `Strings.swift`, every
  `Localizable.strings`, `AppConfiguration.swift` and `CHANGELOG.md` are all
  absent from the staged list. No new user-facing strings were added.
- No runtime check, and none is warranted: nothing constructs a season
  `LevelContext` yet (that is Phase 3's entry point), so the only reachable path
  is endless Levels, where `hasNextLevel` is always true and `WinModal` behaves
  identically to before.

Design decisions worth carrying forward:

- **Board accessor:** the protocol keeps one uniform `board(for:)`. The season
  side binds its pool in a `SeasonLevelProgressStore` adapter rather than storing
  an optional pool on `SeasonProgressService`, which would let a pool-less
  instance silently deal an empty board. Phase 1's API and tests were untouched.
- **`Equatable`:** hand-written on `GameMode`; `.level` compares
  `(number, seasonID)` only, since the store is identity rather than value.
  `isDailyChallenge` keeps its `mode == .dailyChallenge` form.
- **Terminal state:** `LevelContext.nextLevelNumber` is the single ceiling.
  `advanceToNextLevel` guards on it, and `WinModal.offersNextLevel` drives both
  the primary button's label and the suppression of the now-redundant secondary
  button. The final-level button reuses the existing `Strings.backToLevels`.
- **Phase 3 entry point:** `LevelContext.season(_:level:progress:)` is the single
  place a season's id, pool, length and store are read together.

### Phase 4 validation evidence

Bound to the staged tree on `feature/season-levels-localization`, base `2b30d36`:
20 files, 323 insertions, 105 deletions.

- `xcodebuild … -workspace … test` — `** TEST SUCCEEDED **`, exit 0,
  **170 tests, 0 failures**. Re-run independently by the orchestrator.
- **All ten locales verified at 244 keys** (242 + `season_fallback_title` +
  `season_progress_accessibility_format`).
- All four `// Phase 4:` Swift markers removed; no `pending translation` comment
  survives in any locale file.
- `project.pbxproj`: 4 added lines, only the new test file's entries. Still
  `objectVersion = 55`, no Xcode rewrite signature.
- Runtime: ja and ko verified in the simulator — the two half-width menu cards,
  the season map header, and the season-complete banner all render without
  mojibake or truncation; the ko completion message wraps to two lines inside its
  card. The `season_fallback_title` path was exercised with an empty `strings`
  map.

**A note for future runs:** the first independent full-suite run failed all three
`LocalizationParityTests`, then passed in isolation and in two subsequent full
runs. The cause was a stale app bundle left on the simulator by the screenshot
fixture session — the parity tests read the *built* bundle, so they compare
whatever was last installed. If they fail unexpectedly, rebuild before believing
it.

### Phase 5 validation evidence

Stacked as a separate commit on `feature/season-levels-localization`, on top of
`789b4a4`. No new commit hash yet — recorded here, committed together.

- `Scripts/upload_seasons.py` (436 lines), `Scripts/seasons.json` (the canonical,
  hand-edited catalog — one season, `spooky-2026`), `Scripts/seasons_data.json`
  (its generated `{"seasons": {...}}` output, tracked the same way
  `Scripts/data.json` already is for games), plus the `Season.swift`
  cross-reference comment and the changelog amendment.
- `python3 Scripts/upload_seasons.py --dry-run` against the seeded catalog —
  succeeds, exit 0, writes `seasons_data.json`, uploads nothing.
- **Confirmed `seasons_data.json` is not a redundant duplicate of
  `seasons.json`**: unwrapping the generated file's `"seasons"` key and
  comparing produces an identical structure to the canonical input — one is the
  hand-edited source, the other the build artifact, exactly parallel to
  `games.csv` → `data.json`.
- **Rejection path exercised twice, both by hand, not only by reading the code:**
  a pool truncated to 11 distinct entries is rejected (exit 1, "emojiPool has 11
  distinct emoji; 12 are required"); separately, a pool of 12 *raw* entries
  containing one duplicate (11 distinct) is rejected with the same message —
  confirming the script deduplicates before comparing to the floor, matching the
  app's rule exactly rather than checking the raw count.
- `project.pbxproj`: staged diff is 0 lines. Correct — `Scripts/` is outside the
  Xcode target, so nothing here should touch it.
- `xcodebuild … -workspace … test` — `** TEST SUCCEEDED **`, exit 0 (checked via
  redirection, not a pipe, so the exit code is genuinely xcodebuild's — see the
  Phase 4 note on this trap below). **170 passed, 0 failed, 0 skipped** — identical
  to the Phase 4 count, as expected: this phase is Python-only, and the one Swift
  change is a comment.

## Deferred follow-up work

Surfaced during this plan, **explicitly deferred by the user** and deliberately
not addressed in Phase 5. Recorded here in enough detail to pick up cold.

### 1. Four analytics events carry no `seasonID`

`levelSkipped`, `levelPowerUpUsed`, `levelFailedByMistakes` and
`levelMistakesForgiven` (declared in `AppConfiguration.swift`) all fire during
season play but were not extended in Phase 4, which covered only `levelStarted`,
`levelFinished`, `levelUnlocked` and `levelStarsCredited`.

Consequence: skip-rate, power-up-spend and failure-mode funnels cannot be split
by mode, so season play is silently mixed into endless-Levels numbers for those
four events. Everything needed is already in place — `LevelContext.seasonID` is
in scope at each call site, and `AnalyticsEvent.tagged(_:seasonID:)` already
omits the key when nil, so endless-Levels event shapes stay unchanged. This is
a mechanical extension of the Phase 4 pattern, not a design problem.

### 2. `hi` — `levels_lives_remaining_format` swaps its argument roles

`hi.lproj/Localizable.strings` has `"%d में से %d जीवन शेष"` with
**non-positional** specifiers, while `Strings.livesRemainingFormat(remaining, total)`
passes `remaining` first. Hindi's "X में से Y" reads as "Y out of X", so with
2 of 4 lives left the label reads **"4 lives remaining out of 2"** — confirmed
independently. Fix by making the specifiers positional (`%2$d में से %1$d जीवन शेष`)
so the total lands in the "out of" slot.

**The Phase 4 format-specifier parity test does not catch this**, and cannot:
`en` and `hi` use the same specifier set, and only the argument *meaning* is
reversed. Any future audit of positional correctness has to be done by reading,
not by that test. Pre-existing Levels copy, unrelated to seasons.

### 3. Menu difficulty pill is untranslated

The difficulty badge on the menu renders the raw `Difficulty.rawValue`
capitalized (`"Medium"`) rather than the localized `Strings.medium` / `.easy` /
`.hard` / `.veryHard`, which already exist in all ten locales. Visible in the
Japanese menu screenshot taken during Phase 4. Pre-existing, unrelated to
seasons.

## Conventions

Build the workspace, never the project. Run `tuist generate --no-open` from
`DoMemory/` after adding, renaming or deleting a source file, and check the
**staged** `project.pbxproj` diff — Xcode rewrites the file whenever it has the
project open. Branch off `master`; never commit to it directly. All user-facing
strings go through `Strings.swift`. `AGENTS.md`, `DoMemory/.DS_Store`,
`artifacts/` and `marketing/` stay untracked.
