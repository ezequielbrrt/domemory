# CLAUDE.md

This file provides guidance to coding agents working anywhere in this
repository. Each platform has its own file with the detail: read
[`ios/CLAUDE.md`](ios/CLAUDE.md) before touching the iOS app. (`ios/AGENTS.md`
is a symlink to it, so Claude Code and Codex read the same guidance.)

## What this repository is

One product — DoMemory, a memory-card (memorama) game — shipped as two native
apps against one backend. Both build `com.ezequielbrrt.domemory` and read the
same Firebase project.

```
ios/        SwiftUI app (shipping, 4.2.0), its screenshots, changelog and plans
android/    Jetpack Compose port (in progress, not yet released)
firebase/   config, database rules, hosted season art, seed scripts — shared
metadata/   App Store release copy, by version and locale
assets/     brand icon source (Domemory.xcf), shared by both platforms
```

iOS is the behavioural source of truth. `android/DOMEMORY_ANDROID_SPEC.md`
describes what the app does in enough detail to build Android without reading
the Swift; `android/ANDROID_PLAN.md` holds the stack and phase decisions. When
the two disagree about behaviour, the spec wins.

## Cross-platform parity

Seven types exist in both languages and **must agree**, because they decide what
a player sees:

| Type | iOS | Android |
|------|-----|---------|
| `LevelCurve` | `ios/DoMemory/DoMemory/Services/Levels/` | `android/…/services/levels/` |
| `LevelProgressStore` | same | same |
| `EmojiPool`, `SeededGenerator` | same | `android/…/core/rng/` |
| `MemoryGame`, `Difficulty` | `…/Modules/Memorize/Model/`, `…/SharedModules/Models/` | `android/…/core/model/` |
| `CardView` | `…/MemorizeView/Views/` | `android/…/feature/game/` |

`LevelCurve` sets pairs, seconds and `maxFailures` per level; if the two
implementations drift, level 40 is a different game on each platform and nothing
reports it. `EmojiPool` is worse: boards are generated from a seeded RNG, so the
**order** of its 48 emoji is part of the contract. Only ever append, and append
in both languages in the same commit. Both are currently in sync — 48 entries,
identical order.

Change these in one commit covering both platforms. That is the main reason this
repository is a monorepo.

## Firebase (`firebase/`)

Run every `firebase` command **from `firebase/`** — `firebase.json` resolves
`"rules"` and `"public"` relative to itself.

Committing a rules or config change does **not** publish it. Deploy explicitly:

```
firebase deploy --only database   # firebase-database.rules.json
firebase deploy --only hosting    # season art + Cache-Control headers
```

Season art under `/seasons/**` is served `immutable` with a one-year max-age, so
a published URL is a permanent contract. Replacing a season's artwork means a
**new** path — never overwrite the bytes at an existing URL.

`firebase/scripts/data.json` and `android/app/src/test/resources/data.json` look
different but are not. The first is pretty-printed and wrapped as
`{"data": [...]}` for RTDB import; the second is the same 134 boards, minified
and unwrapped, matching what the app sees under `/data`. They are byte-different
and value-identical — do not "fix" one to match the other. `BoardDecoderTest.kt`
consumes the Android copy.

## App Store release copy

`metadata/ios/<version>/` is the single source of truth: `release-copy.json`
holds `english_approved` and per-locale review status for ten locales
(`en-US`, `es-MX`, `pt-BR`, `fr-FR`, `de-DE`, `it`, `ja`, `ko`, `hi`, `zh-Hans`),
alongside plain-text exports.

There is **no fastlane**. It was removed because its lane called
`upload_to_app_store` with no arguments, so `deliver` uploaded a stale
`fastlane/metadata/` by default and would have silently reverted live store copy.
Builds are archived and uploaded from Xcode, and **the build number is set by
hand** — nothing derives it any more.

Screenshots live in `ios/screenshots/<device>/<locale>/`. The catalog's App Store
artwork agent owns export and upload; do not reintroduce per-app export or upload
scripts.

## Repository conventions

**Tags are platform-prefixed** going forward — `ios/4.3.0`, `android/1.0.0`.
Tags `2.1.0` through `4.2.0` predate the monorepo and are all iOS.

**`.gitignore` is split three ways** and the split is deliberate: the root holds
cross-cutting rules, `ios/.gitignore` holds Xcode/Tuist/SPM rules whose patterns
are anchored (`DoMemory/*.xcodeproj` only resolves from `ios/`), and
`android/.gitignore` holds Gradle rules. Add a rule to the file whose directory
it is anchored against, not to the root.

**Some directories are deliberately absent from git.** `ios/marketing/`,
`ios/my-video/` and `ios/artifacts/` exist on the developer's machine and are
ignored, not untracked — marketing sources, a Remotion project and video renders.
Do not add them; migrating them in is a deliberate future decision.

**`android/local.properties` must never be committed** — it holds an absolute
path to a local Android SDK.
