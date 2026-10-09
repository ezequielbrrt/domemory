# App Store Connect: Spooky Season 2026 in-app event (draft)

- **App:** DoMemory (1533115091).
- **Event:** `6819853253`, reference name "Spooky Season 2026". **State: DRAFT, not submitted.**

## Event settings

| Field | Value | Why |
|---|---|---|
| Badge | `NEW_SEASON` | The season is new content |
| Purpose | `APPROPRIATE_FOR_ALL_USERS` | The season is open to everyone |
| Priority | `NORMAL` | It's the only event |
| Purchase requirement | `NO_COST_ASSOCIATED` | The season is free |
| Primary locale | en-US | |
| Publish start / event start | 2026-10-08 00:00 UTC | Leaves a day or two for review. Apple allows up to 14 days of promotion before the start. |
| Event end | 2026-11-02 15:00 UTC | The app keeps the season open through Nov 2 in the player's local day (`Season.swift`, inclusive `endDate`). 15:00 UTC is the end of Nov 2 in Japan and Korea, so no locale promotes the event after its season has closed. Americas lose half a day of promotion. The event lasts 25.6 days, inside Apple's 31-day maximum. |
| Territories | 161 | Exactly the territories where the app is available (`asc pricing availability territory-availabilities`). Apple requires at least one territory to be listed explicitly. |
| Deep link | `domemory://season/spooky-2026` | Set 2026-10-06 after a submit attempt was refused ("You must provide a value for the attribute 'deepLink'"). The app learns this route in branch `feature/season-deep-link` (iOS `SeasonDeepLink`, Android `DeepLink.OpenSeason`); shipped apps up to 4.4.1 ignore it and stay on the menu. |

The first create attempt failed for lack of territories. asc confirmed the schedule wasn't applied and deleted that partial event (`6819853302`) itself, before the retry above.

## Localizations

Ten localizations were created from `event-spooky-copy.json`:
- **Name and short description:** the app's own season title and subtitle (`firebase/scripts/seasons.json`), already approved.
- **Long description:** new copy. Every locale except en-US **needs native review** before submission.
- **Limits:** all within Apple's (name ≤ 30, short ≤ 50, long ≤ 120 characters).

## Media

The same two text-free images were uploaded to every localization with `asc app-events screenshots create`:

| Asset type | File | MD5 |
|---|---|---|
| `EVENT_CARD` | `final/D1-event-card-spooky-3840x2160.jpg` | `5bc68b673104a66eaf5f03865c56fc9b` |
| `EVENT_DETAILS_PAGE` | `final/D2-event-details-spooky-2160x3840.jpg` | `3e81331ad19020cb17bec1d7fae79148` |

**Verified:** every one of the 10 localizations lists exactly one `EVENT_CARD` (3840 × 2160) and one `EVENT_DETAILS_PAGE` (2160 × 3840), both with delivery state `COMPLETE`.

## Submission attempt (2026-10-06)

`asc app-events submit` was refused: Apple requires a deep link. That attempt left an **empty** review submission (`8e9996cd-4837-4001-8808-dd3a315420ee`, READY_FOR_REVIEW, 0 items). It is harmless and the next submission can reuse it.

The decision was to ship the season route in the app first (4.5.0), and submit the event only once that version is approved.

## Remaining

1. **Copy review:** native review of the nine translated long descriptions. Change any locale with `asc app-events localizations update`.
2. **Preview:** check the card and details page in App Store Connect.
3. **Ship the route:** merge `feature/season-deep-link` and release it in 4.5.0.
4. **Move the dates:** the start and publish dates (Oct 8) will likely have passed by then. Move them to the expected approval day before submitting; the end stays 2026-11-02 15:00 UTC.
5. **Submit:** `asc app-events submit --event-id 6819853253 --app 1533115091 --confirm`, or from the web UI.
