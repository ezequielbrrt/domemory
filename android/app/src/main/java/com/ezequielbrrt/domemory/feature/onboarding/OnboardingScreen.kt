package com.ezequielbrrt.domemory.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

@Composable
fun OnboardingScreen(state: OnboardingUiState, onNext: () -> Unit, onSkip: () -> Unit) {
    LaunchedEffect(Unit) {
        AnalyticsService.log(AnalyticsEvent.ScreenView(screenName = "onboarding_intro", screenClass = "OnboardingScreen"))
    }
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        val titles = listOf(
            stringResource(R.string.onboarding_pairs_title),
            stringResource(R.string.onboarding_pace_title),
            stringResource(R.string.onboarding_custom_title),
        )
        val subtitles = listOf(
            stringResource(R.string.onboarding_pairs_subtitle),
            stringResource(R.string.onboarding_pace_subtitle),
            stringResource(R.string.onboarding_custom_subtitle),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.common_app_name), style = DoMemoryType.display(34), color = p.primary)
            Spacer(Modifier.size(24.dp))
            Text(titles[state.page], style = DoMemoryType.display(28), color = p.textPrimary)
            Text(subtitles[state.page], color = p.textSecondary, fontSize = 17.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip, enabled = !state.isSaving) { Text(stringResource(R.string.onboarding_skip), color = p.textSecondary) }
            Button(onClick = onNext, enabled = !state.isSaving, colors = ButtonDefaults.buttonColors(containerColor = p.primary)) {
                Text(if (state.page == 2) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_continue))
            }
        }
    }
}
