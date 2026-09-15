package com.ezequielbrrt.domemory.services.share

import android.content.Context

/**
 * The Play Store listing URL for this app, keyed off the running `applicationId` — Android's
 * equivalent of iOS's `InviteLink.appStoreURL`. Shared by two call sites so the URL is built
 * in exactly one place: `feature/settings/SettingsScreen.kt`'s "Rate DoMemory" row (its
 * `market://` intent's fallback when the Play Store app can't handle it) and the share-card
 * caption's closing line (`feature/share/ShareResultCard.kt`, mirroring iOS's
 * `ResultShare.caption`, whose final line is `InviteLink.appStoreURL.absoluteString`).
 */
object PlayStoreLinks {
    fun listingUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"
}
