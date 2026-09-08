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
  amend that same entry rather than adding near-duplicates.**
- **PR state:** not opened.

### Phase 4 — Localization and analytics

- **Objective:** app-side chrome in ten languages, and comparable funnels.
- **Dependencies:** Phase 3 merged.
- **Branch:** `feature/season-levels-localization`
- **Tasks:**
  - Add `season_ends_in_format`, `season_ends_today`, `season_completed`,
    `season_progress_format`, `season_fallback_title` and anything Phase 3
    surfaced, to `Strings.swift` and to all ten `Localizable.strings`.
  - **Assert 235 + N key parity across all ten files.**
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
- **Dependencies:** Phase 4 merged.
- **Branch:** `feature/season-levels-tooling`
- **Tasks:** `Scripts/upload_seasons.py` mirroring `Scripts/upload_games.py`'s
  `--dry-run` / `--credentials` contract; a seeded `spooky-2026` season; document
  the `enabled` kill switch.
- **Acceptance criteria:** `--dry-run` validates the seeded season without
  credentials and rejects a sub-12 emoji pool the same way the app does.
- **User-visible:** yes — amends the Phase 3 changelog entry.
- **PR state:** not opened.

## State ledger

| Phase | State | PR | Merge commit |
|---|---|---|---|
| 1 — Model, catalog, progress, board generation | validated, held local (`475106f`) | — | — |
| 2 — `LevelProgressStore` + `GameMode` refactor | validated, held local | — | — |
| 3 — UI | ready | — | — |
| 4 — Localization + analytics | ready | — | — |
| 5 — Tooling + seeded season | ready | — | — |

Overall: approved. Phases 1 and 2 are implemented and validated but deliberately
**undelivered** — by user decision they are held as local commits on
`feature/season-levels-catalog` and will be pushed and reviewed together as one
PR. Nothing is pushed; `master` remains untouched at `bd02fd6`.

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

## Conventions

Build the workspace, never the project. Run `tuist generate --no-open` from
`DoMemory/` after adding, renaming or deleting a source file, and check the
**staged** `project.pbxproj` diff — Xcode rewrites the file whenever it has the
project open. Branch off `master`; never commit to it directly. All user-facing
strings go through `Strings.swift`. `AGENTS.md`, `DoMemory/.DS_Store`,
`artifacts/` and `marketing/` stay untracked.
