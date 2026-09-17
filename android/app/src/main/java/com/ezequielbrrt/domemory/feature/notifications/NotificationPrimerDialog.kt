package com.ezequielbrrt.domemory.feature.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * The custom explainer shown before the OS permission dialog (spec 11.3): "the OS dialog
 * can only be shown once, ever. Spending it on a cold ask wastes it." Shown once per install
 * from the menu ([NotificationPrimerHost]), and reused by the Settings reminders toggle
 * through the same [rememberNotificationPermissionRequester] path.
 *
 * Visual shape mirrors this codebase's other full-screen modals (`LevelsScreen`'s
 * `OutOfLivesModal`): a backdrop-dimmed `Box` behind a rounded
 * `Surface` card, not a platform `AlertDialog` — keeping one card style across the app
 * rather than mixing Material's default dialog chrome into an otherwise fully custom UI.
 */
@Composable
fun NotificationPrimerHost(visible: Boolean, onEnable: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    // Spec 11.3: "shown once per install on the menu" — the only call site, so `source` is
    // fixed rather than threaded in as a parameter.
    LaunchedEffect(Unit) {
        AnalyticsService.log(AnalyticsEvent.NotificationPrimerShown(source = "menu"))
    }
    // onEnable only ever fires after a confirmed grant (or "not needed", pre-API 33) —
    // see rememberNotificationPermissionRequester's doc. Either outcome dismisses the
    // primer; spec 11.3 shows it once per install regardless of the answer given.
    val requestPermission = rememberNotificationPermissionRequester(
        onGranted = {
            AnalyticsService.log(AnalyticsEvent.NotificationPrimerCompleted(source = "menu", outcome = "authorized"))
            onEnable()
            onDismiss()
        },
        onDenied = {
            AnalyticsService.log(AnalyticsEvent.NotificationPrimerCompleted(source = "menu", outcome = "denied"))
            onDismiss()
        },
    )
    NotificationPrimerDialog(
        onEnable = requestPermission,
        onLater = {
            // "Later" dismisses without ever asking the OS — the primer can still be shown
            // again on a future install/session, matching iOS's `.deferred` outcome (as
            // opposed to `.denied`, which is a real OS refusal).
            AnalyticsService.log(AnalyticsEvent.NotificationPrimerCompleted(source = "menu", outcome = "deferred"))
            onDismiss()
        },
    )
}

@Composable
private fun NotificationPrimerDialog(onEnable: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().background(palette.overlayBackdrop), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.surfacePrimary,
            border = BorderStroke(1.dp, palette.surfaceBorder),
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.notification_primer_title),
                    style = DoMemoryType.display(20),
                    color = palette.textPrimary,
                )
                Text(
                    stringResource(R.string.notification_primer_message),
                    color = palette.textSecondary,
                )
                Benefit(stringResource(R.string.notification_primer_benefit_streak))
                Benefit(stringResource(R.string.notification_primer_benefit_inactive))
                Benefit(stringResource(R.string.notification_primer_benefit_no_spam))

                // The fake notification preview — a mock of what a reminder actually looks
                // like, so "Turn On Reminders" is an informed choice rather than a blind one.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceSecondary)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.notification_primer_preview_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                    )
                    Text(
                        stringResource(R.string.notification_primer_preview_message),
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                }

                Button(
                    onClick = onEnable,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.notification_primer_enable))
                }
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notification_primer_later), color = palette.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = palette.primary, fontWeight = FontWeight.Bold)
        Text(text, color = palette.textPrimary, fontSize = 13.sp)
    }
}
