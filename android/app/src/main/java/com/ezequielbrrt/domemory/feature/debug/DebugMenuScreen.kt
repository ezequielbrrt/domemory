package com.ezequielbrrt.domemory.feature.debug

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.ezequielbrrt.domemory.feature.adfree.LocalAdFreeDay
import com.ezequielbrrt.domemory.feature.levels.LevelsIntroOverlay
import com.ezequielbrrt.domemory.feature.notifications.NotificationPrimerHost
import com.ezequielbrrt.domemory.feature.onboarding.OnboardingScreen
import com.ezequielbrrt.domemory.feature.onboarding.OnboardingUiState
import com.ezequielbrrt.domemory.feature.review.DoMemoryReviewInvitationPreview
import com.ezequielbrrt.domemory.feature.whatsnew.DoMemoryWhatsNewScreen
import com.ezequielbrrt.domemory.services.ads.AdFreeDayService
import com.ezequielbrrt.domemory.services.levels.LevelLivesService
import com.ezequielbrrt.domemory.ui.components.BackButton
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch

/**
 * Debug-build QA panel, the Android port of iOS's `DebugMenuView`, reached by tapping the
 * Settings title five times (`debugMenuTapTrigger`). The screen rows present what the app
 * normally shows only when some gate opens — the two intro carousels, the three vendored
 * libraries' screens and Play's native review request — without touching that gate's state:
 * What's New is not marked seen, the review policy records nothing, and the onboarding
 * preview does not re-run `completeOnboarding` (which would reset the player's difficulty).
 * The primer is the one live flow: it can really grant the permission and turn reminders on,
 * exactly as iOS's debug row does. "Restart lives" refills today's Levels lives, as on iOS.
 *
 * Copy is plain English literals on purpose, as on iOS: no player, reviewer or translator
 * can reach this screen, so it stays out of `strings.xml` and `LocalizationParityTest`.
 */
@Composable
fun DebugMenuScreen(
    onBack: () -> Unit,
    onNotificationsAuthorized: () -> Unit,
    /** Refills today's Levels lives and returns the new count — iOS's `restoreFullLives()`. */
    onRestoreLives: suspend () -> Int,
) {
    BackHandler(onBack = onBack)
    val palette = LocalPalette.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var preview by rememberSaveable { mutableStateOf<DebugPreview?>(null) }
    var nativeReviewStatus by remember { mutableStateOf<String?>(null) }
    var livesStatus by remember { mutableStateOf<String?>(null) }
    val adFreeDay = LocalAdFreeDay.current
    val adsDisabled = adFreeDay?.let { service ->
        val expiry by service.expiryMillis.collectAsState()
        (expiry ?: 0L) > System.currentTimeMillis()
    } ?: false
    var adFreeResetStatus by remember { mutableStateOf<String?>(null) }
    val dismissPreview = { preview = null }

    Column(
        Modifier.fillMaxSize().background(palette.appBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackButton(onClick = onBack)
            Text("Debug Menu", style = DoMemoryType.display(26), color = palette.primary)
        }

        // Same rows, order and titles as iOS's DebugMenuView, so QA steps carry across.
        adFreeDay?.let { service ->
            DebugSection("Ads") {
                DebugRow(
                    title = "Disable ads",
                    subtitle = "Grants/clears a year-long rewarded remove-ads window. Now: " +
                        if (adsDisabled) "ads off" else "ads on",
                ) {
                    scope.launch { if (adsDisabled) service.clearGrant() else service.grantForDebug() }
                }
                DebugRow(
                    title = "Reset ad-free day offer",
                    subtitle = adFreeResetStatus
                        ?: "Forgets today's watched ads and replays the apology copy on the next open",
                ) {
                    scope.launch {
                        service.reset()
                        adFreeResetStatus = "Reset: 0/${AdFreeDayService.REQUIRED_ADS} ads, apology copy next open"
                    }
                }
            }
        }

        DebugSection("Screens") {
            DebugRow(
                title = "Start onboarding",
                subtitle = "The first-launch carousel. Preview only: keeps onboarding state and difficulty",
            ) { preview = DebugPreview.ONBOARDING }
            DebugRow(
                title = "Start Levels onboarding",
                subtitle = "The Levels intro carousel, same as the \"?\" button on the level map",
            ) { preview = DebugPreview.LEVELS_INTRO }
            DebugRow(
                title = "Show notifications view",
                subtitle = "NotificationPermissionKit-Android. Live: can grant the permission and turn reminders on. " +
                    "Closes at once when notifications are already allowed; revoke them to see it",
            ) { preview = DebugPreview.NOTIFICATION_PRIMER }
            DebugRow(
                title = "Show ask-for-review view (native)",
                subtitle = nativeReviewStatus
                    ?: "Play In-App Review, bypassing the policy. Play shows nothing for sideloaded builds; this confirms the call completes",
            ) {
                val host = activity ?: return@DebugRow
                nativeReviewStatus = "Requesting…"
                scope.launch {
                    nativeReviewStatus = runCatching {
                        val manager = ReviewManagerFactory.create(context)
                        manager.launchReview(host, manager.requestReview())
                    }.fold(
                        onSuccess = { "Request completed. Play decides whether a sheet appeared" },
                        onFailure = { "Request failed: ${it.message ?: it::class.java.simpleName}" },
                    )
                }
            }
            DebugRow(
                title = "Show ReviewFlow invitation view",
                subtitle = "ReviewFlow-Android. The full-screen invitation, bypassing the 3-win / 7-day policy; records nothing",
            ) { preview = DebugPreview.REVIEW_INVITATION }
            // Android-only: iOS has no What's New row.
            DebugRow(
                title = "Show What's New",
                subtitle = "WhatsNewKit-Android. The current release notes; does not mark the version seen",
            ) { preview = DebugPreview.WHATS_NEW }
        }

        DebugSection("Levels") {
            DebugRow(
                title = "Restart lives",
                subtitle = livesStatus ?: "Refills today's Levels lives budget",
            ) {
                scope.launch {
                    livesStatus = "Restored: ${onRestoreLives()}/${LevelLivesService.MAX_LIVES} lives"
                }
            }
        }
    }

    when (preview) {
        DebugPreview.WHATS_NEW -> DebugFullScreen(onDismiss = dismissPreview, drawsOwnInsets = true) {
            DoMemoryWhatsNewScreen(onDismiss = dismissPreview)
        }
        DebugPreview.REVIEW_INVITATION -> DoMemoryReviewInvitationPreview(onDismiss = dismissPreview)
        DebugPreview.NOTIFICATION_PRIMER -> NotificationPrimerHost(
            visible = true,
            source = "debug_menu",
            onAuthorized = onNotificationsAuthorized,
            onFinished = dismissPreview,
        )
        DebugPreview.ONBOARDING -> DebugFullScreen(onDismiss = dismissPreview, drawsOwnInsets = false) {
            OnboardingScreen(state = OnboardingUiState(), onNext = dismissPreview, onSkip = dismissPreview)
        }
        DebugPreview.LEVELS_INTRO -> DebugFullScreen(onDismiss = dismissPreview, drawsOwnInsets = false) {
            LevelsIntroOverlay(onDismiss = dismissPreview, source = "debug_menu")
        }
        null -> Unit
    }
}

private enum class DebugPreview { WHATS_NEW, REVIEW_INVITATION, NOTIFICATION_PRIMER, ONBOARDING, LEVELS_INTRO }

/**
 * An edge-to-edge dialog window standing in for iOS's `.fullScreenCover`. The two intro
 * carousels normally sit inside `MainActivity`'s Scaffold insets, so they get the safe-area
 * padding here; What's New pads for the system bars itself.
 */
@Composable
private fun DebugFullScreen(onDismiss: () -> Unit, drawsOwnInsets: Boolean, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        if (!drawsOwnInsets) {
            // The dialog window starts with light bar icons, which vanish on the light
            // palette's cream background; follow the app's appearance instead.
            val view = LocalView.current
            val window = (view.parent as? DialogWindowProvider)?.window
            SideEffect {
                window?.let {
                    WindowCompat.getInsetsController(it, view).apply {
                        isAppearanceLightStatusBars = !palette.isDark
                        isAppearanceLightNavigationBars = !palette.isDark
                    }
                }
            }
        }
        val insets = if (drawsOwnInsets) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        Box(Modifier.fillMaxSize().background(palette.appBackground).then(insets)) { content() }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = palette.surfacePrimary,
            border = BorderStroke(1.dp, palette.surfaceBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun DebugRow(title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(subtitle, fontSize = 12.sp, color = palette.textSecondary)
    }
    HorizontalDivider(color = palette.surfaceBorder)
}
