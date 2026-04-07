# DoMemory Ads Migration Plan

## Goal
Remove all AppLovin integration and standardize the app on Google AdMob only, with a clean initialization path, centralized ad logic, compliance safeguards, and controlled rollout.

## Current State (Validated in Codebase)
- AppLovin config keys exist in `DoMemory/DoMemory/Info.plist`:
  - `AppLovinSdkKey`
  - `AppLovinConsentFlowInfo`
- AppLovin setup hook exists:
  - `DoMemory/DoMemory/Modules/Main/AppDelegate/AppDelegate.swift` calls `setupAppLovin()`
  - `DoMemory/DoMemory/Modules/Main/AppDelegate/AppDelegate+AppLovin.swift` defines `setupAppLovin()` as empty
- AdMob app ID already exists in `Info.plist`:
  - `GADApplicationIdentifier`
- No active `GoogleMobileAds` import/usages are present yet.
- Project currently uses SPM for Firebase/other dependencies.

## Target State
- No AppLovin code, config, or dead references remain.
- AdMob SDK (`GoogleMobileAds`) is integrated through SPM.
- AdMob initialized once at launch.
- Ads are managed by a single internal service layer (`AdsService`).
- Ad placements are controlled (only at natural UX breakpoints).
- Privacy/compliance settings are explicit and testable.

---

## Phase 1: Remove AppLovin Completely

### 1.1 Remove AppLovin runtime references
1. Delete file:
   - `DoMemory/DoMemory/Modules/Main/AppDelegate/AppDelegate+AppLovin.swift`
2. In `DoMemory/DoMemory/Modules/Main/AppDelegate/AppDelegate.swift`:
   - Remove `setupAppLovin()` call from `didFinishLaunchingWithOptions`.
3. Update `DoMemory/DoMemory.xcodeproj/project.pbxproj`:
   - Remove file reference, build file entry, and group/source references for `AppDelegate+AppLovin.swift`.

### 1.2 Remove AppLovin configuration
1. In `DoMemory/DoMemory/Info.plist`, remove:
   - `AppLovinSdkKey`
   - `AppLovinConsentFlowInfo` dictionary
2. Keep ad-related keys that still apply for AdMob (to be reviewed in Phase 4), including:
   - `NSUserTrackingUsageDescription`
   - `SKAdNetworkItems`
   - `GADApplicationIdentifier`

### 1.3 Remove stale dependency artifacts
1. If any AppLovin package/dependency exists in project settings, remove it.
2. Clean up historical files if not used by build (example: `DoMemory/pod.bck`), only if team agrees.
3. Confirm no AppLovin symbol remains:
   - Search terms: `AppLovin`, `ALSdk`, `MAX`, `MAAd`.

### 1.4 Exit criteria
- `rg` search returns zero AppLovin symbols in active project files.
- Project compiles without `AppDelegate+AppLovin.swift`.

---

## Phase 2: Integrate AdMob SDK

### 2.1 Add dependency
1. Add `Google-Mobile-Ads-SDK` via SPM in Xcode (preferred for this repo).
2. Ensure package product is linked to app target.
3. Verify package resolution and build settings.

### 2.2 Validate base configuration
1. Keep/verify `GADApplicationIdentifier` in `Info.plist`.
2. Ensure the app ID matches the correct environment/account.
3. Confirm no legacy mediation config references AppLovin adapters.

### 2.3 Exit criteria
- `import GoogleMobileAds` compiles.
- App links successfully with AdMob SDK in Debug and Release.

---

## Phase 3: App Launch Initialization

### 3.1 Startup integration
1. In `AppDelegate.swift`:
   - Add `import GoogleMobileAds`.
   - Start SDK in `didFinishLaunchingWithOptions`:
     - `MobileAds.shared.start(completionHandler: nil)`
2. Keep Firebase startup untouched.

### 3.2 Defensive startup behavior
1. Ensure initialization is idempotent and only called once.
2. Log SDK startup status in Debug only (avoid noisy logs in Release).

### 3.3 Exit criteria
- Cold start works with no ad-related crash.
- SDK initialization is visible in debug logs.

---

## Phase 4: Ads Architecture (Single Service Layer)

### 4.1 Create `AdsService`
1. Add new module/service file(s), for example:
   - `DoMemory/DoMemory/Services/Ads/AdsService.swift`
2. Responsibilities:
   - Load interstitial/rewarded ads.
   - Expose readiness state.
   - Present ads safely from current top view controller.
   - Auto-reload after close/failure with bounded retries.

### 4.2 Centralize unit IDs
1. Add `AdUnitConfiguration` with explicit keys per placement.
2. Use test IDs in Debug builds.
3. Use production IDs in Release builds.
4. Keep IDs out of view logic.

### 4.3 Error handling and controls
1. Standardize load/present failure handling.
2. Add cooldown/frequency controls (example: no interstitial more than once every N minutes or gameplay cycles).
3. Prevent duplicate presentations.

### 4.4 Exit criteria
- No view directly instantiates ad SDK objects.
- Ad loading/presentation flows through `AdsService` only.

---

## Phase 5: Placement Strategy

### 5.1 Banner ads
1. Define exact screens where banner is allowed (example: menu/home).
2. Avoid banner on intensive gameplay surface unless explicitly intended.
3. Add safe area and layout checks to avoid overlap.

### 5.2 Interstitial ads
1. Show only at natural breakpoints:
   - After game end
   - On transition out of gameplay
2. Never interrupt active gameplay action.

### 5.3 Rewarded ads (optional but recommended)
1. Gate behind explicit user action.
2. Provide clear reward contract in UI.
3. Grant reward only on `rewardEarned` callback.

### 5.4 Exit criteria
- Every placement maps to a documented trigger and fallback.
- No ad shown in intrusive or accidental contexts.

---

## Phase 6: Privacy, Consent, and Attribution

### 6.1 ATT and tracking
1. Keep `NSUserTrackingUsageDescription` aligned with actual app behavior.
2. Ensure ATT prompt timing is deliberate (not on first frame unless required by product decision).

### 6.2 Consent management
1. Add Google UMP SDK if consent collection is needed for target regions.
2. Gate personalized ad requests based on consent state.
3. Validate EEA/UK behavior in test scenarios.

### 6.3 SKAdNetwork and attribution keys
1. Review `SKAdNetworkItems` against current AdMob requirements.
2. Keep `NSAdvertisingAttributionReportEndpoint` only if actually needed by current ad stack.

### 6.4 Exit criteria
- Consent/ATT flow documented and testable.
- Privacy keys are minimal and accurate.

---

## Phase 7: Observability and Analytics for Ads

### 7.1 Event instrumentation
Track these internal events (existing analytics service can be reused):
- `ad_request_started` (placement, format)
- `ad_loaded` (placement, format, latency)
- `ad_failed_to_load` (placement, format, error_code)
- `ad_shown` (placement, format)
- `ad_dismissed` (placement, format)
- `reward_earned` (placement, reward_type, reward_value)

### 7.2 Monitoring metrics
1. Fill rate by placement.
2. Load error distribution.
3. Show rate and completion rate.
4. Crash-free rate after ad integration.

### 7.3 Exit criteria
- Ad lifecycle is observable in analytics/logs.
- Regression triage is possible from telemetry.

---

## Phase 8: QA Strategy

### 8.1 Functional QA
1. Debug build uses AdMob test IDs only.
2. Validate:
   - App cold start
   - Navigation across ad-enabled screens
   - Interstitial present/dismiss/reload
   - Rewarded reward path
3. Test no-ad path (load failure/offline mode).

### 8.2 Device and state QA
1. Real-device testing for presentation and ATT.
2. Test app background/foreground transitions.
3. Test orientation and safe area behavior for banners.

### 8.3 Release QA
1. Archive and run Release smoke tests.
2. Validate no test IDs ship in Release.
3. Re-scan for removed AppLovin symbols.

### 8.4 Exit criteria
- Critical ad flows pass on at least one physical iPhone.
- No ad-related crash/regression found in smoke tests.

---

## Phase 9: Rollout Plan

### 9.1 Controlled rollout
1. Ship with conservative ad frequency defaults.
2. Monitor first 24-72h:
   - Crash-free rate
   - Ad load/show failures
   - Revenue trend vs baseline

### 9.2 Fast rollback options
1. Keep local runtime toggle/feature flag capability (if available).
2. Be ready to disable specific placements quickly in patch release.

### 9.3 Post-rollout optimization
1. Tune placement frequency based on retention and session length.
2. Evaluate rewarded entry points before adding more interstitial pressure.

---

## Work Breakdown (Suggested Sequence)
1. Remove AppLovin code/config artifacts.
2. Add AdMob SDK + startup init.
3. Implement `AdsService` + test IDs.
4. Wire one placement first (banner or interstitial), verify.
5. Add remaining placements.
6. Complete privacy/consent refinements.
7. Add ad telemetry events.
8. Full QA + release.

## Risks and Mitigations
- Risk: ad crash from presenting on invalid VC.
  - Mitigation: centralized presenter lookup + guard checks.
- Risk: aggressive ad cadence hurts retention.
  - Mitigation: cooldown caps and staged rollout.
- Risk: compliance mismatch (ATT/consent).
  - Mitigation: explicit consent gating and QA scenarios.
- Risk: stale AppLovin leftovers confuse future maintenance.
  - Mitigation: hard removal + CI grep checks.

## Definition of Done
- AppLovin fully removed from runtime code and config.
- AdMob is the only ads SDK integrated and initialized.
- At least one production-ready placement is stable.
- Privacy/consent behavior is documented and verified.
- Ad telemetry exists for load/show/failure lifecycle.
- Release build passes smoke test without ad-related regressions.
