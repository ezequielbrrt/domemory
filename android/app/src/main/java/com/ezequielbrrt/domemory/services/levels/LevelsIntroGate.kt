package com.ezequielbrrt.domemory.services.levels

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Tracks whether the Levels feature intro has been shown yet (spec 7.9). Kept out of the
 * screen so the one-shot rule is testable, mirroring iOS's `LevelsIntroGate` and
 * Android's own `WhatsNewManager`-shaped gates.
 *
 * iOS additionally waits on the launch sequence (§11.4: the tracking prompt, the What's
 * New sheet and the notification primer must all finish first, since Levels is the
 * landing tab and would otherwise race their covers). Android now has that sequence
 * (`services/launch/LaunchSequence`: consent, ads, primer), but this gate does not wait on
 * it yet. [shouldPresent] is only the one-shot half of the rule; call sites take a
 * `canPresent` flag the same shape as iOS's `LevelsView.canPresentIntro`, so waiting for
 * `LaunchSequence.Stage.FINISHED` is a one-line change at the call site, not a new gate.
 */
class LevelsIntroGate(private val prefs: UserPreferences) {

    /** True until the player has dismissed the intro at least once. */
    suspend fun shouldPresent(): Boolean = !prefs.levelsIntroShown.first()

    /**
     * Call this when the intro is dismissed, not when it is presented — a kill mid-intro
     * should leave the player eligible to see it again.
     */
    suspend fun markSeen() {
        prefs.setLevelsIntroShown(true)
    }
}
