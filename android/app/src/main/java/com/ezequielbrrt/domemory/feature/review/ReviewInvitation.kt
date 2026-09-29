package com.ezequielbrrt.domemory.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.reviewflow.LocalReviewInvitationStyle
import com.ezequielbrrt.reviewflow.ReviewInvitationHost
import com.ezequielbrrt.reviewflow.ReviewInvitationStyle
import com.ezequielbrrt.reviewflow.ReviewManager

/**
 * ReviewFlow-Android's full-screen review invitation, wired to the app's one [ReviewManager]
 * (`AppContainer.reviews`, spec 15.2). It presents itself, once, two seconds after the win
 * that makes ReviewFlow's recommended policy eligible — three wins, a week since first use,
 * a 120-day cooldown and one ask per version — and its primary action opens the Play
 * listing, since Google Play has no direct "write a review" link.
 *
 * The artwork is the same `five-star-rating.png` the iOS app bundles as
 * `onboarding-five-star-rating` for its `ReviewInvitationFullScreen`; the copy is the app's
 * own, localized in every supported locale, rather than the library's English defaults.
 *
 * This is the explicit-invitation half of ReviewFlow, chosen over `ReviewRequestEffect` (the
 * Play In-App Review bridge) — the two are mutually exclusive per the library's contract.
 */
@Composable
fun DoMemoryReviewInvitationHost(manager: ReviewManager) {
    val palette = LocalPalette.current
    // Only the face is set: ReviewFlow merges each style onto its Material default, so the
    // headline keeps its size and the button its weight (spec 14.2's display role, as the
    // Settings and Levels headers use it).
    val style = remember { ReviewInvitationStyle(titleTextStyle = DoMemoryType.display(28)) }
    CompositionLocalProvider(LocalReviewInvitationStyle provides style) {
        ReviewInvitationHost(
            manager = manager,
            image = painterResource(R.drawable.review_invitation_five_star_rating),
            title = stringResource(R.string.review_invitation_title),
            message = stringResource(R.string.review_invitation_message),
            accentColor = palette.primary,
            reviewButtonTitle = stringResource(R.string.review_invitation_button),
            dismissButtonTitle = stringResource(R.string.review_invitation_dismiss),
        )
    }
}
