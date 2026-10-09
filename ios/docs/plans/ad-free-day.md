# Ad-Free Day

A visible, mascot-fronted offer: watch two rewarded ads and involuntary
advertising (banners, natives, interstitials, app-open) is off for 24 hours.
Reached from a floating "No Ads" pill on every screen that shows involuntary
ads, and from the existing Settings row.

## Requested outcome

The developer wants to raise ad revenue without adding more interstitials. The
proposal: a dedicated screen with a Flippo illustration and a short pitch, a
two-ad chain, and a 24-hour grant once both ads are watched. The first time a
player sees the screen it apologises for the ads ("they keep DoMemory free")
and offers the way out; later visits use a shorter, neutral pitch. The entry
point is a floating pill visible in every section, because every mode shows
ads.

iOS ships first. Android follows later from the spec update in this plan.

## Evidence observed

Verified by reading the code, not assumed:

- iOS already has the grant. `PurchaseService.grantRewardedRemoveAds(duration:)`
  extends `rewardedRemoveAdsExpirationDate` by 24 hours and `hasRemovedAds` is
  `purchased || rewardedExpiry > now` (`Services/Purchases/PurchaseService.swift`).
  `AdsService.suppressesInvoluntaryAds` reads that property, so the grant already
  silences banners, natives, interstitials and app-open.
- The only entry point is a Settings row ("Free ad-free day", one ad) in
  `SettingsView.swift:155-170`, gated on
  `AdsService.shared.isRewardedConfigured(for: .settingsRewardedRemoveAds)`.
- **That placement's release ad-unit ID is an empty string**
  (`AdsService.swift:68-72`), and `configuredUnitID` treats an empty release ID
  as unconfigured. The Settings row has therefore never appeared in a release
  build. Shipping this feature needs a real rewarded unit ID from AdMob.
- `AdsService.presentRewardedAd` calls `completion(false)` synchronously when
  no ad is loaded, and starts a load. `loadRewardedAd` has no completion, so a
  caller cannot know when the ad becomes ready. After presenting, the service
  drops the ad and immediately loads the next one for the same placement, which
  is exactly the preload the second ad in the chain needs.
- `grantRewardedRemoveAds` sets `purchaseAlert`, which only `SettingsView`
  binds to an `.alert`. A grant made anywhere else would leave the alert queued
  until the player next opens Settings.
- Involuntary ads appear on: the menu (`home_banner`, all three tabs),
  gameplay (`game_banner`, `game_finished_interstitial`), the multiplayer end
  screen (`multiplayer_finished_native`) and app foreground (`app_open`).
  The Levels tab lives inside the menu's `TabView`; the season map
  (`SeasonLevelsView`) and multiplayer room (`MultiplayerRoomView`) are pushed
  destinations with their own `ZStack` roots.
- Rewarded ads are deliberately not gated on Remove Ads, but the Settings row
  hides itself for purchasers because this one reward is worthless to them.
  The pill and sheet inherit that rule.
- `LevelLivesService` shows the day-boundary pattern: `dailyChallengeSeed(for:)`
  as the day key, injectable `UserDefaults`, lazy reset on first read.
- All ten `Localizable.strings` files must carry identical key sets
  (`LocalizationParityTests`).
- Flippo art: five poses exist (`FlippoWin`, `FlippoPause`, `FlippoQuit`,
  `FlippoLoseTime`, `FlippoLoseMistakes`), each 834 × 880 RGBA, generated from
  `assets/images/flippo/flippo-idle.png` with the prompt recipe in
  `assets/proposals/2026-09-app-pet-flippo-pause/README.md`.

## Decisions

- **Two ads, both required.** One ad grants nothing. Progress (0, 1 or 2 ads
  watched) persists for the local day and resets at midnight, using the same
  day seed as lives and the Daily Challenge.
- **Each ad needs its own tap.** The second ad is never auto-launched after the
  first closes; AdMob policy requires user-initiated rewarded ads, and it keeps
  the sheet honest about what is happening.
- **One placement, renamed.** `settingsRewardedRemoveAds` becomes
  `adFreeDayRewarded` (`ad_free_day_rewarded`). Settings and the pill open the
  same sheet, so one unit ID and one analytics placement cover both.
- **Grant reuses `grantRewardedRemoveAds`.** The sheet passes
  `showsAlert: false` so the queued-alert problem does not reach Settings; the
  sheet's own active state is the confirmation.
- **Hidden for purchasers and when unconfigured.** Same gate as the Settings
  row: shown when the rewarded placement is configured or a grant is active,
  and never when `hasPurchasedRemoveAds`.
- **Upsell on the sheet.** A secondary "Remove ads forever · price" link runs
  the existing StoreKit purchase. No new products.
- **First-open apology, then neutral copy.** A one-shot flag switches the
  title, message and primary button after the first presentation.
- **Two new Flippo poses.** `FlippoSorry` (first open) and `FlippoNoAds`
  (return visits and the granted state), generated in a Codex image session
  from the idle sprite and framed like the rest of the family. The view reads
  both names from a single `AdFreeDayArt` enum.

## Non-goals

- No post-interstitial toast/nudge in this phase. It is the obvious next step
  and is listed under deferred work.
- No Remote Config experiment on the ad count. The count is a constant
  (`AdFreeDayService.requiredAds`) so it can become remote later.
- No pill during gameplay, on the pause/win/lose modals, or on the multiplayer
  board — those surfaces already carry their own rewarded offers.
- No Android implementation. The spec is updated so the port is a faithful
  copy.

## Success criteria

- A player on the menu, Levels, Mine, All, a season map or the multiplayer
  lobby sees the pill unless they bought Remove Ads.
- Tapping the pill opens the sheet; the first open shows the apology copy and
  logs `ad_free_day_offer_shown{is_intro: true}`.
- Watching one ad moves the stepper to 1/2, the pill to "No Ads 1/2", and
  grants nothing. Killing the app keeps the 1/2 until local midnight.
- Watching the second ad grants 24 hours, shows the active state, hides all
  involuntary ads, and the pill reads the remaining time.
- With no ad fill the sheet says so and offers a retry without losing progress.
- The Settings row still works and opens the same sheet.
- `AdFreeDayServiceTests` cover progress, completion reset, day rollover and
  the intro flag; `LocalizationParityTests` stay green with the new keys.

## Phases

### Phase 1 — Service and ad plumbing (iOS)

- `Services/Purchases/AdFreeDayService.swift`: `requiredAds = 2`,
  `adsWatched(for:)`, `recordAdWatched(for:) -> Bool` (true when the chain
  completes, which also resets progress), `hasSeenIntro` / `markIntroSeen()`,
  `reset()` for debug. Keys `adFreeDay.adsWatched`, `adFreeDay.progressDay`,
  `adFreeDay.introShown`.
- `AdPlacement.settingsRewardedRemoveAds` → `.adFreeDayRewarded`.
- `AdsService.loadRewardedAd(for:completion:)` gains an optional completion
  reporting readiness, so the sheet can show "Loading ad…" and enable the
  button when the ad is ready instead of tapping into `completion(false)`.
- `PurchaseService.grantRewardedRemoveAds(duration:showsAlert:)`.
- Tests: `DoMemoryTests/AdFreeDayServiceTests.swift`.

### Phase 2 — Sheet and pill (iOS)

- `Modules/AdFreeDay/AdFreeDayOfferViewModel.swift`: owns the phase
  (`idle`, `loading`, `ready`, `presenting`, `noFill`, `active`), the
  intro/neutral copy choice, ad presentation, the grant, analytics.
- `Modules/AdFreeDay/AdFreeDayOfferView.swift`: the sheet (medium detent):
  Flippo, title, message, three-node stepper, primary button, purchase link,
  "Not now". Capped at `ContentWidth.modal`.
- `Modules/AdFreeDay/AdFreeDayPill.swift`: the floating pill and an
  `adFreeDayEntryPoint()` view modifier that overlays it bottom-trailing and
  presents the sheet. Applied to the three menu tab contents, the season map
  and the multiplayer lobby.
- Settings row: action opens the sheet; description updated to two ads.
- Debug menu: "Reset ad-free day offer" (clears progress and the intro flag).
- Strings: 18 new keys in all ten locales; one existing description reworded.
- Analytics: `adFreeDayOfferShown(source:isIntro:)`,
  `adFreeDayGranted(source:)`; the ad itself keeps using `adLifecycle`.
- `tuist generate --no-open` for the new files.

### Phase 3 — Art and release configuration

- Art: done. Both poses were generated with the recipe below (Orca run
  `run_fb15208e5553`, one Codex generation each) and live in
  `assets/images/flippo/` and the two imagesets; `AdFreeDayArt` points at them.
- AdMob: done. The `ad_free_day_rewarded` placement has a release rewarded
  unit in `AdUnitConfiguration.rewardedUnitID`; debug builds keep Google's
  test unit.

### Phase 4 — Android (later)

Port from the spec section 12.3 once iOS has shipped. Same placement name,
same analytics events, same 24-hour and two-ad constants.

## Copy

First open:

- Title: "Sorry about the ads"
- Message: "They keep DoMemory free, but here's a way out: watch 2 short ads
  and we turn them off for the next 24 hours."
- Primary: "Sounds fair, watch ad 1 of 2"

Return visits:

- Title: "Play a day without ads"
- Message: "Watch 2 short ads and we turn off banners and interstitials for
  24 hours."
- Primary: "Watch ad 1 of 2" / "Watch ad 2 of 2"

Shared: "One more and you're done", "No ad available right now. Your progress
is saved.", "Try again", "Ads are off until %@", "Back to the game",
"Remove ads forever · %@", "Not now". Pill: "No Ads", "No Ads %d/%d",
"%@ left".

## Flippo art recipe

Follow `assets/proposals/2026-09-app-pet-flippo-pause/README.md`: use
`assets/images/flippo/flippo-idle.png` as the identity reference, keep the
same boilerplate (chibi sticker, cel shading, transparent square canvas, 10%
padding, no text), frame the result at 834 × 880.

**FlippoSorry** — "Give Flippo a sheepish, apologetic expression: eyebrows
raised in the middle, small closed-mouth smile, soft pink blush. Both lavender
mittens pressed together in front of the card body at chest height, feet
turned slightly inward. Add only a TINY cue: two small sweat-drop marks beside
the upper-left corner, each smaller than one eye. Flippo is warm and humble,
NOT sad, NOT crying."

**FlippoNoAds** — "Give Flippo a proud, relieved grin with bright open eyes.
Flippo holds up in one lavender mitten a small rounded sign, the sign no
taller than Flippo's face, showing a simple rectangle 'ad' glyph with a
diagonal coral strike-through — NO letters or words on it. The other mitten
rests on the hip. Add only a TINY sparkle above the sign, smaller than one
eye. Flippo is cheerful and confident."

## Deferred follow-up work

- Post-interstitial nudge: a one-line toast on the menu after an interstitial
  closes, at most once per day, opening the sheet.
- Remote Config for `requiredAds` and the grant duration, with an A/B on ad
  revenue per daily active user.
- A "wants the offer again" reminder when the grant expires while the app is
  open.
