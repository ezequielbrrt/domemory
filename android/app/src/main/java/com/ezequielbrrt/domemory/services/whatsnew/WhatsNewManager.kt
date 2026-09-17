package com.ezequielbrrt.domemory.services.whatsnew

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import kotlinx.coroutines.flow.first

/** Version gate shared by launch and the Settings entry point. */
class WhatsNewManager(
    private val prefs: UserPreferences,
    private val currentVersion: String,
) {
    suspend fun shouldShowAfterLaunch(hasOnboarded: Boolean): Boolean {
        val seen = prefs.whatsNewLastSeenVersion.first()
        val shouldShow = hasOnboarded && seen != currentVersion
        // A new install has nothing to announce. Persist the version immediately so the
        // sheet only appears on a real update (or a migration from an older build).
        if (!hasOnboarded && seen == null) prefs.setWhatsNewLastSeenVersion(currentVersion)
        // Mirrors iOS's `WhatsNewManager.init`, which logs the moment `shouldShow` becomes
        // true — only the automatic launch path, not a Settings-triggered reopen (that one
        // logs `whats_new_opened_from_settings` instead, from `NavGraph.kt`'s own tap site).
        if (shouldShow) AnalyticsService.log(AnalyticsEvent.WhatsNewShown(version = currentVersion))
        return shouldShow
    }

    suspend fun markSeen() {
        AnalyticsService.log(AnalyticsEvent.WhatsNewDismissed(version = currentVersion))
        prefs.setWhatsNewLastSeenVersion(currentVersion)
    }
}
