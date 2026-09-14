package com.ezequielbrrt.domemory.services.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Android's counterpart to `ios/DoMemory/DoMemory/Services/ReviewRequest/AppReviews.swift`.
 *
 * **Deliberate divergence from iOS, not a straight port.** iOS's `AppReviews` wraps a
 * third-party `ReviewFlow` package that owns its own local eligibility policy (win-count
 * gates, per-version request caps, cooldowns) plus a one-time migration
 * (`ReviewHistoryMigration`) that carries an older, retired `ReviewRequestService`'s history
 * into that package's store so an existing player isn't asked again the moment they clear
 * three wins on a fresh policy. Neither applies here:
 *
 * - Android's own in-app review API already owns its quota/frequency decision **server-side**,
 *   inside Play Core's `ReviewManager` — the app requests a review flow and Google decides
 *   whether the dialog actually appears, unobservably, the same way iOS's `StoreKit` decides
 *   once `ReviewFlow`'s local policy clears a request. There is nothing left for this object
 *   to gate locally the way `ReviewFlow`'s `.recommended` configuration does; reimplementing a
 *   win-count/cooldown policy on top would just be a second, redundant gate in front of one
 *   Google already runs, not a missing requirement.
 * - There is no legacy history to carry over — Android has shipped nothing before this slice
 *   that ever prompted for a review, so there is no `ReviewHistoryMigration` equivalent to
 *   write. A fresh install starts clean, which is exactly what Play Core's own store already
 *   assumes.
 *
 * This stays a thin request/launch wrapper as a result — not a second copy of `ReviewFlow`'s
 * policy engine.
 */
object AppReviews {
    /**
     * Records a genuine success and asks Play Core for a review flow. A no-op, silently, when
     * [activity] is null or the request itself fails — mirrors iOS's "StoreKit decides, and a
     * denial is silent" shape exactly; there is no error path worth surfacing to the player.
     *
     * Play Core's own contract never tells the caller whether the dialog was actually shown
     * (the quota decision is opaque by design, the same way `SKStoreReviewController`'s was) —
     * [launchReviewFlow]'s own task result is intentionally not awaited any further than
     * confirming it completed; there is nothing more here to branch on.
     */
    fun recordSuccessfulGameWin(activity: Activity?) {
        if (activity == null) return
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            manager.launchReviewFlow(activity, task.result)
        }
    }
}
