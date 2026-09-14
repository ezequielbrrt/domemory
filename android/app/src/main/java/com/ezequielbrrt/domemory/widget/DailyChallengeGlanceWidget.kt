package com.ezequielbrrt.domemory.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.ezequielbrrt.domemory.DoMemoryApplication
import com.ezequielbrrt.domemory.MainActivity
import com.ezequielbrrt.domemory.R

/**
 * Home-screen widget for the Daily Challenge streak (spec 8.1). Android's counterpart to
 * `ios/DoMemory/DoMemoryWidget/DailyChallengeWidget.swift`.
 *
 * iOS reads a snapshot through an App Group-shared `UserDefaults` because the widget
 * extension is a **separate process** from the host app. Android has no such split — a
 * Glance widget's `provideGlance` runs inside the app's own process — so this reads
 * [com.ezequielbrrt.domemory.services.daily.DailyChallengeService] directly off the same
 * [DoMemoryApplication.container] the app itself uses, off the same DataStore file
 * (`data/prefs/UserPreferences.kt`). Spec 8.1's Android note calls this out explicitly: "No
 * App Group concept is needed — same process, same storage."
 *
 * Content refresh happens two ways, mirroring iOS's timeline policy:
 *  - **Force refresh on every daily completion** — [com.ezequielbrrt.domemory.AppContainer]
 *    calls [updateAll] right after [com.ezequielbrrt.domemory.services.daily
 *    .DailyChallengeService.recordCompletion] finishes, the same trigger iOS's
 *    `WidgetReloader.reloadDailyChallenge()` responds to.
 *  - **Midnight refresh via WorkManager** ([DailyChallengeWidgetScheduler]) — so the
 *    "completed" lock clears for the new day even if the app never comes to the foreground,
 *    mirroring iOS's `Timeline(..., policy: .after(nextMidnight))`.
 */
class DailyChallengeGlanceWidget : GlanceAppWidget() {

    // Matches iOS's `.supportedFamilies([.systemSmall, .systemMedium])` — one layout that
    // reads fine at either width rather than two distinct Glance layouts, since the content
    // (an icon, a streak badge, a title, a subtitle) is already compact.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as DoMemoryApplication).container
        val streak = container.dailyChallenge.currentStreak()
        val isCompleted = container.dailyChallenge.isCompletedToday()

        provideContent {
            DailyChallengeWidgetContent(streak = streak, isCompleted = isCompleted)
        }
    }
}

@androidx.compose.runtime.Composable
private fun DailyChallengeWidgetContent(streak: Int, isCompleted: Boolean) {
    val openDaily = actionStartActivity(
        Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
            .setData(Uri.parse("domemory://daily"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(openDaily),
        verticalAlignment = Alignment.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isCompleted) "✅" else "📅",
                style = TextStyle(fontSize = 16.sp),
            )
            if (streak > 0) {
                Spacer(modifier = GlanceModifier.size(6.dp, 1.dp))
                Text(
                    text = "🔥 $streak",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WidgetColors.textPrimary,
                    ),
                )
            }
        }
        Spacer(modifier = GlanceModifier.size(1.dp, 8.dp))
        Text(
            text = titleText(),
            maxLines = 1,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WidgetColors.textPrimary,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Text(
            text = subtitleText(isCompleted),
            maxLines = 2,
            style = TextStyle(fontSize = 12.sp, color = WidgetColors.textSecondary),
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}

// Glance's provideContent runs outside a normal Compose UI tree, so this can't reach for
// androidx.compose.ui.res.stringResource the way every other screen in this app does — the
// closest equivalent, LocalContext.current.getString(...), is what the two helpers below use.
@androidx.compose.runtime.Composable
private fun titleText(): String =
    androidx.glance.LocalContext.current.getString(R.string.daily_challenge_title)

@androidx.compose.runtime.Composable
private fun subtitleText(isCompleted: Boolean): String {
    val context = androidx.glance.LocalContext.current
    return context.getString(
        if (isCompleted) R.string.daily_challenge_completed else R.string.daily_challenge_subtitle,
    )
}

/**
 * Widget-local copy of the spec 14.1 light/dark tokens this widget needs. Glance composables
 * run in their own composition (`androidx.glance.*`, not `androidx.compose.*`) and cannot
 * read [com.ezequielbrrt.domemory.ui.theme.LocalPalette] — that `CompositionLocal` is
 * provided by `DoMemoryTheme` inside the app's own Compose tree, which a home-screen widget
 * is not part of. Values are copied from spec 14.1, not re-derived, so a palette change there
 * is a two-place update, not a source of silent widget/app drift.
 */
private object WidgetColors {
    val surface = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        night = androidx.compose.ui.graphics.Color(0xFF1E2234),
    )
    val textPrimary = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFF1C1830),
        night = androidx.compose.ui.graphics.Color(0xFFF4F0FF),
    )
    val textSecondary = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFF7A7291),
        night = androidx.compose.ui.graphics.Color(0xFFA4ABC4),
    )
}
