# Season Levels artwork — reduce avoidable network calls

## Requested outcome

Season artwork (menu card + level-map background, light/dark) is fetched via
`RemoteImageService` and cached today only in an `NSCache` (memory, cleared on
relaunch) and the session's `URLCache` (disk, but revalidated per the response's
`Cache-Control` and subject to `URLCache` eviction alongside every other network
response in the app). Reduce avoidable re-fetching of season art across app
launches and across season transitions, without changing any UI.

## Evidence observed

Verified by reading the code, not assumed:

- `RemoteImageService` (`DoMemory/DoMemory/Services/RemoteImages/RemoteImageService.swift`)
  already does two-level caching: decoded `UIImage`s in an `NSCache` (line 28),
  raw bytes in a dedicated 64MB-disk/8MB-memory `URLCache` (lines 47-51), plus
  in-flight request de-duping (lines 36, 78-79). `image(for:)` (line 76) checks
  the memory cache, then de-dupes in-flight requests, then falls through to
  `session.data(from:)`, which honors `.useProtocolCachePolicy` (line 52) — a
  cache hit there still costs a conditional-GET round trip once the response's
  own freshness lifetime elapses.
- `firebase.json` (repo root) sets `Cache-Control: public, max-age=86400` on
  `/seasons/**` hosting paths (line 14-21) — a 24-hour freshness window, after
  which every cached asset is revalidated over the network even though the
  bytes never changed.
- `SeasonCatalogService` (`DoMemory/DoMemory/Services/Seasons/SeasonCatalogService.swift`)
  caches the `/seasons` RTDB payload to `UserDefaults` and fetches at most once
  per process launch (`hasLoaded` guard, line 30/45). `refreshActiveSeason`
  (line 86) is synchronous and re-evaluates `activeSeason` from the already-held
  `seasons` array — it is called from `init` (line 37) and from `apply(payload:on:)`
  (line 81), both on the main actor (the class is not marked `@MainActor` itself
  but has no `Sendable` conformance and is only ever touched from the main actor
  in current call sites — `RemoteImageService` being `@MainActor` is compatible).
- Season art URLs follow `seasons/<season-id>/<file>.png`
  (`Scripts/seasons.json`) — a new picture always means a new file path, never
  an overwrite in place. This is the load-bearing assumption behind Phase 1: it
  is safe to cache a given URL's bytes forever once Phase 1 ships, because the
  URL itself is the version.
- `RemoteImage` (`DoMemory/DoMemory/Modules/SharedModules/Views/RemoteImage.swift`)
  is the only consumer of `RemoteImageService.shared.image(for:)`/`cachedImage(for:)`,
  used in exactly two places: `SeasonLevelsView.swift` (level-map background,
  both light/dark variants via `Season.backgroundArtworkURL(for:)`) and
  `MenuView.swift` (season card via `Season.cardArtworkURL`). Both accessors
  live in `Season+Presentation.swift` and take a `ColorScheme` /  no argument
  respectively (`backgroundArtworkURL(for colorScheme: ColorScheme) -> URL?`
  line 37; `cardArtworkURL: URL?` line 46).
- `DoMemory/DoMemoryTests/RemoteImageServiceTests.swift` already exercises
  `RemoteImageService` end-to-end against a `StubURLProtocol` request counter,
  covering memory-cache hit, in-flight de-dup, 404 → nil (not cached), transport
  failure → nil (not cached), non-image body → nil. New tests should extend this
  file and follow its existing stub pattern.

## Assumptions

- A season's art file path is never reused for different bytes (stated above);
  Phase 2's "cache hit skips revalidation forever" design depends on this and
  on Phase 1's header change actually being deployed.
- No product/UI change is in scope; every phase is cache-plumbing only.

## Non-goals

- Phase 4, a TTL guard on `SeasonCatalogService.load()`'s RTDB refetch, was
  discussed and is explicitly deferred. Do not implement it in this plan.
- No change to `/seasons` RTDB read rules or caching (only the Firebase
  *Hosting* `Cache-Control` header for `/seasons/**` static assets changes).
- No new UI, no change to fallback behavior when art is absent or fails to load.

## Success criteria

1. `/seasons/**` hosting assets are served with an immutable, one-year
   `Cache-Control` header (source committed; actual deploy is a documented
   manual step, not performed by this plan).
2. A cold app relaunch after a prior successful load of a given season-art URL
   produces zero additional HTTP requests for that URL's bytes, verified by a
   test that constructs a fresh `RemoteImageService` instance and asserts no
   new requests hit `StubURLProtocol`.
3. All pre-existing `RemoteImageServiceTests` behavior (memory-cache hit,
   in-flight de-dup, 404/transport-failure/non-image-body → nil and not cached)
   continues to pass unmodified in behavior.
4. Once the active season is known, its card and both background variants are
   prefetched into cache with no UI change and no data race (verified by tests
   appropriate to `SeasonCatalogService`'s existing coverage), so
   `SeasonLevelsView`/the menu card do not show a flash of flat colour on first
   paint when art is not yet cached.

## Question ledger

No open questions — the plan was already scoped and approved by the user
before this file was created; the phase breakdown, files touched, and
acceptance criteria below are taken directly from that approval.

## Phases

### Phase 1 — Make season art cache-control immutable at the source

- **Objective:** turn a season-art URL into a hard, permanently-cacheable
  contract at the CDN/hosting layer, which Phase 2's disk cache depends on.
- **Dependencies:** none.
- **Branch:** `feature/season-artwork-immutable-cache`
- **Tasks:**
  - `firebase.json`: change `/seasons/**`'s `Cache-Control` value from
    `public, max-age=86400` to `public, max-age=31536000, immutable`.
  - `CLAUDE.md`: add a note near the existing `Scripts/upload_seasons.py`
    paragraph (Screenshot automation section) documenting that this makes the
    per-asset URL a hard contract — replacing a season's art requires a new
    filename/path, never an in-place overwrite at the same URL — and that the
    header change requires a manual `firebase deploy --only hosting` (not run
    as part of this work), matching the existing `firebase deploy --only
    database` caveat already documented for `/seasons` read rules.
- **Likely components:** `firebase.json`, `CLAUDE.md`.
- **Acceptance criteria:** `firebase.json` diff is exactly the header value
  change; `CLAUDE.md` documents the immutability contract and the undone
  manual deploy step; no source files touched, so no `tuist generate` needed;
  workspace build/tests unaffected (no Swift changes).
- **Validation:** review diff by inspection; optionally
  `python3 -c "import json; json.load(open('firebase.json'))"` to confirm
  valid JSON. No simulator/xcodebuild run required since no app source changes.
- **Validation owner:** ios-feature-engineer, confirmed by the orchestrator.
- **Excluded:** running `firebase deploy`; any Swift/source change.
- **User-visible:** no.
- **PR state:** not opened.

### Phase 2 — Give `RemoteImageService` its own disk cache, independent of `URLCache`

- **Objective:** a cache hit for season art skips HTTP entirely (no conditional
  GET), surviving app relaunch, independent of `NSCache` and `URLCache`
  eviction.
- **Dependencies:** Phase 1 merged (the plan's cache-forever assumption depends
  on the immutable header being committed, even though the deploy itself is
  manual and out of band).
- **Branch:** `feature/season-artwork-disk-cache`
- **Tasks:**
  - Extend `RemoteImageService.swift` with a small on-disk store under the
    app's Caches directory (e.g. `Caches/RemoteImages/<hash-of-url>`), written
    after every successful decode in `image(for:)`.
  - `image(for:)` checks this store before any network access — a hit skips
    HTTP revalidation entirely. Keep `NSCache` as the hot in-memory path and
    the current `URLSession`/`URLCache` fetch as the fallback used only when
    neither the memory cache nor the disk store has the bytes.
  - Add simple bounded eviction (LRU by file modification date is fine),
    capped around the same order of magnitude as the existing 64MB `URLCache`
    disk budget, so `Caches/RemoteImages` does not grow unbounded as seasons
    accumulate over the app's lifetime.
  - Preserve all existing behavior in `RemoteImageServiceTests.swift`:
    memory-cache hit, in-flight de-dup, 404 → nil (not cached), transport
    failure → nil (not cached), non-image body → nil.
- **Likely components:** `DoMemory/DoMemory/Services/RemoteImages/RemoteImageService.swift`,
  `DoMemory/DoMemoryTests/RemoteImageServiceTests.swift`. Check whether the disk
  store warrants its own file (if so, `project.pbxproj` needs `tuist generate
  --no-open` and a staged-diff check); if it's added directly to the existing
  file, no project regeneration is needed — engineer to confirm which.
- **Acceptance criteria:** workspace build clean; new test — construct a
  *new* `RemoteImageService` instance after a prior successful load (simulating
  a fresh app launch / cold `NSCache` and no in-memory state) and assert the
  second instance's `image(for:)` call for the same URL produces zero
  additional requests via the `StubURLProtocol` counter, because it reads
  straight from the on-disk store; all pre-existing tests in the file still
  pass; bounded eviction present and covered by a test if reasonably testable.
- **Validation:** `xcodebuild -workspace DoMemory.xcworkspace -scheme DoMemory
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
  -configuration Debug build` and `test`, run from `DoMemory/`; if any new file
  was added, `tuist generate --no-open` with a staged-diff check on
  `project.pbxproj`.
- **Validation owner:** ios-feature-engineer, re-checked by the orchestrator.
- **Excluded:** any UI change; any change to `RemoteImage.swift`'s public
  behavior beyond what falls out of `RemoteImageService`'s internals; the
  `SeasonCatalogService` prefetch (Phase 3).
- **User-visible:** no.
- **PR state:** not opened.

### Phase 3 — Prefetch season artwork once the active season is known

- **Objective:** warm the cache for the active season's art as soon as it is
  known, so `SeasonLevelsView`/the menu card do not show a flash of flat colour
  on first paint.
- **Dependencies:** Phase 2 merged (prefetching is only worth doing once a hit
  is durable across launches).
- **Branch:** `feature/season-artwork-prefetch`
- **Tasks:**
  - In `SeasonCatalogService`, wherever `activeSeason` is set/changed
    (`refreshActiveSeason`, `DoMemory/DoMemory/Services/Seasons/SeasonCatalogService.swift:86`),
    kick off fire-and-forget `RemoteImageService.shared.image(for:)` calls for
    `cardArtworkURL` and both `backgroundArtworkURL(for: .light)` /
    `.dark` (`Season+Presentation.swift:37,46`). No UI changes — pure
    cache-warm.
  - Check actor isolation carefully before wiring in async calls:
    `RemoteImageService` is `@MainActor`; `SeasonCatalogService` is
    `@Observable` and currently called only from the main actor by its
    existing call sites, but is not itself annotated `@MainActor`. Don't
    introduce data races, and don't block `refreshActiveSeason`'s synchronous
    callers on the prefetch — it must remain fire-and-forget.
  - Only prefetch when `activeSeason` actually changes (avoid redundant calls
    on every `refreshActiveSeason` invocation for the same season) — `image(for:)`
    is idempotent via its own caches regardless, but avoid spawning redundant
    tasks if easy to avoid without overengineering.
  - Add/adjust tests as appropriate given existing `SeasonCatalogServiceTests`
    coverage of active-season selection — e.g. that setting/changing
    `activeSeason` triggers prefetch calls (via a test seam/spy), without
    asserting on `RemoteImageService`'s network internals (Phase 2 already
    covers those).
- **Likely components:**
  `DoMemory/DoMemory/Services/Seasons/SeasonCatalogService.swift`,
  `DoMemory/DoMemoryTests/SeasonCatalogServiceTests.swift`.
- **Acceptance criteria:** workspace build clean; full suite green; no UI file
  touched; prefetch is fire-and-forget and does not block or change the return
  value/timing of `refreshActiveSeason`, `apply(payload:on:)`, or `init`.
- **Validation:** `xcodebuild -workspace DoMemory.xcworkspace -scheme DoMemory
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
  -configuration Debug build` and `test`, run from `DoMemory/`; `tuist generate
  --no-open` with a staged-diff check only if a file was added.
- **Validation owner:** ios-feature-engineer, re-checked by the orchestrator.
- **Excluded:** Phase 4 (TTL guard on `SeasonCatalogService.load()`) — deferred
  by explicit user decision, not to be implemented here.
- **User-visible:** no (pure cache-warm; no perceptible change other than a
  possibly-earlier paint of art that would have appeared anyway).
- **Changelog:** none of these three phases is user-visible in the sense of a
  new feature or behavior change worth a `[Unreleased]` entry — this is
  performance/plumbing work. If the delivery workflow's guidelines call for an
  entry regardless (e.g. "reduced network usage for Season art"), Phase 3 is
  the right phase to add it, since it's the one that completes the observable
  effect (faster/earlier art paint). Confirm against `ios-project-guidelines`
  at delivery time; do not create one near-duplicate entry per phase.
- **PR state:** not opened.

## State ledger

| Phase | State | PR | Merge commit |
|---|---|---|---|
| 1 — Immutable cache-control header | awaiting-pr | — | — |
| 2 — RemoteImageService disk cache | proposed | — | — |
| 3 — Prefetch on active-season change | proposed | — | — |

Overall: approved by the user prior to this plan file's creation ("run the
development agent and start"). No plan-level questions are open.

### Phase 1 validation evidence

Bound to the working tree on `feature/season-artwork-immutable-cache`, base
`23e280b` (master): 2 files changed, 3 insertions, 1 deletion —
`firebase.json` (`Cache-Control` value only) and `CLAUDE.md` (one new
paragraph documenting the immutability contract and the undeployed manual
`firebase deploy --only hosting` step).

- `python3 -c "import json; json.load(open('firebase.json'))"` — valid JSON.
- `git status --porcelain=v1` / `git diff` confirmed by the orchestrator
  directly (not solely on the engineer's report): only `CLAUDE.md` and
  `firebase.json` modified, diff limited to exactly the scoped Cache-Control
  value and the new documentation paragraph. No Swift or project file touched.
- No `tuist generate` needed (no source files added/renamed/deleted) and none
  was run. No `firebase deploy` run, per scope.
- No xcodebuild/test run required for this phase — it has zero Swift changes,
  so the existing test suite is unaffected by construction.

## Conventions

Build the workspace, never the project. Run `tuist generate --no-open` from
`DoMemory/` only after adding, renaming or deleting a source file, and check
the **staged** `project.pbxproj` diff — Xcode rewrites the file whenever it has
the project open. Branch off `master`; never commit to it directly. Only ever
commit the `tuist generate` output of `project.pbxproj`.
