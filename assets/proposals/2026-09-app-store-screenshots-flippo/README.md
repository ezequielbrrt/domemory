# Flippo-led App Store screenshots (proposal)

A proposed replacement for the iPhone 6.9" set in `assets/screenshots/6.9/en-US/`. The seven images are 1290 × 2796 JPEGs, the same size as the current set. `before-after.jpg` puts the current set (top row) above this proposal (bottom row). This is a proposal only: nothing in `assets/screenshots/` has changed.

## Direction

The indigo background, Righteous headlines and handwritten captions stay, so the listing still looks like DoMemory. The change is that Flippo now appears in every shot, and the set shows the Flippo modals and the other recent features: the Flippo win modal, the multiplayer ready check, and the Daily Challenge. The one-line captions also stay, because they give the store's search indexing something to read.

## The shots

| # | File | Headline | What it shows | Replaces |
|---|------|----------|---------------|----------|
| 1 | `01_hero_flip_match.jpg` | MEET FLIPPO · FLIP & MATCH. | A real device frame at the moment two suns match, a MATCH! stamp, and Flippo (win pose) saying "Find the pairs!" | 01 hero (flat board mock) |
| 2 | `02_win_celebrate.jpg` | LEVEL CLEARED · CELEBRATE EVERY WIN. | The new Flippo win modal in the app's dark palette, over a dimmed real board. It uses the app's real strings: Level Cleared!, pairs / remaining / errors, Next Level, Back to levels | 02 play / create / challenge (feature list) |
| 3 | `03_meet_flippo.jpg` | MEET YOUR CARD BUDDY · FLIPPO FEELS EVERY FLIP. | Four mood tiles: win, out of time, too many mistakes, pause | 06 choose challenge level |
| 4 | `04_levels.jpg` | NEW · LEVELS · CLIMB FOREVER. | The current level-map panel, with Flippo saying "Level 10 next!" | 07 levels |
| 5 | `05_multiplayer.jpg` | NEW · MULTIPLAYER · BATTLE A FRIEND. | The Play with Friends onboarding scene, the current VS panel, and the new ready check ("You ready" / "Mia ready") | 03 multiplayer |
| 6 | `06_create_your_own.jpg` | YOUR DECK, YOUR RULES · CREATE YOUR OWN. | The Make It Yours onboarding scene and the current deck-builder panel | 05 create your own |
| 7 | `07_daily_themes.jpg` | DAILY CHALLENGE · A NEW BOARD EVERY DAY. | The Come Back Daily onboarding scene, with the theme grid fading out below it | 04 play dozens |

## Sources

- **Gameplay frames:** real device captures taken from `assets/videos/hard.mp4` (17.9s) and `medium.mp4` (7.3s).
- **Flippo art:** the six shipped sprites in `assets/images/flippo/`.
- **Scenes:** the shipped onboarding art in `assets/onboarding/step{1,2,3}/*-light.png`.
- **Panels:** the levels, VS, deck builder and theme grid panels are cut from the current store screenshots. They are recreations of the UI, the same as today's set.
- **Recreated in code:** the win modal and the ready chips. They follow `WinModal.swift`'s layout and the app's dark-mode colours.
- **Source code:** everything is rendered by the `StoreShotProposal` still in `assets/my-video/src/StoreShotsProposal.tsx`:
  `npx remotion still StoreShotProposal out/store-proposal/01.png --props='{"shot":1}'`

## Open points before production

- **Locales:** this is en-US only. The headlines, captions, speech bubbles and the win modal's text would need all ten locales, as they had for the current set.
- **iPad:** there is no iPad 12.9" set yet.
- **Theme coverage:** shot 7 drops the full-height "Play dozens of themes" shot to make room for the Daily Challenge. If theme coverage matters more, swap it back in and move Daily into shot 4 or 6.
- **Cut panels:** the panels cut from the current set carry today's UI. Recapturing them from the current build would pick up any drift.
- **Production:** export, validation and upload stay with the catalog's App Store artwork agent (Paper). This proposal is the brief for it.
