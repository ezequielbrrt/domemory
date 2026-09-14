package com.ezequielbrrt.domemory.services.whatsnew

import com.ezequielbrrt.domemory.data.prefs.UserPreferences
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
        return shouldShow
    }

    suspend fun markSeen() = prefs.setWhatsNewLastSeenVersion(currentVersion)
}
