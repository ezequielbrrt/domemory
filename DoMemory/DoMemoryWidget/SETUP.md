# Daily Challenge Widget — Xcode setup

The widget **source** is ready in this folder. Creating the extension *target* must
be done in Xcode (it generates many interlinked project entries that can't be
safely hand-edited). ~5 minutes:

## 1. Add the Widget Extension target
1. **File ▸ New ▸ Target… ▸ Widget Extension**.
2. Product Name: **DoMemoryWidget**. Uncheck "Include Configuration App Intent"
   (this widget is a `StaticConfiguration`). Finish; do **not** activate the scheme
   prompt is optional.
3. Xcode creates a `DoMemoryWidget` group with template files. **Delete** the
   template `.swift` files it generated (keep the target). Then **Add Files…** and
   add the three files already in this folder:
   - `DoMemoryWidgetBundle.swift`
   - `DailyChallengeWidget.swift`
   - (use the generated `Info.plist`, or replace with the one here)
   Make sure they're checked for the **DoMemoryWidget** target only.

## 2. Share the daily-challenge data file with the widget
- Select `DoMemory/Services/DailyChallenge/DailyChallengeShared.swift` in the
  navigator. In the File Inspector ▸ **Target Membership**, check **both**
  `DoMemory` and `DoMemoryWidget`.

## 3. Enable the App Group on BOTH targets
- For target **DoMemory** and target **DoMemoryWidget**:
  Signing & Capabilities ▸ **+ Capability ▸ App Groups** ▸ add
  **`group.com.ezequielbrrt.domemory`** (must match `dailyChallengeAppGroupID`).
- This is what lets the widget read the streak/completion the app writes. Until
  this is enabled the app still works (it falls back to standard defaults), but
  the widget will show zeros.

## 4. Build & run
- Run the **DoMemory** scheme once (writes shared state), then long-press the home
  screen ▸ add the **DoMemory** widget. Completing the daily challenge calls
  `WidgetReloader.reloadDailyChallenge()` and the widget updates. Tapping the
  widget opens the app via `domemory://daily` and starts today's challenge.

## Notes
- Widget kind string `DoMemoryDailyWidget` must stay in sync between
  `DailyChallengeWidget.swift` and `WidgetReloader.dailyChallengeKind`.
- The widget falls back to English strings via `NSLocalizedString(_, value:)`. To
  localize it, add the app's `Localizable.strings` to the widget target too.
- Brand color is a literal in `DailyChallengeWidgetView.brand`; tweak to match.
