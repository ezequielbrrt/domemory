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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

@Composable
fun OnboardingScreen(state: OnboardingUiState, onNext: () -> Unit, onSkip: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        val pages = listOf("Match the pairs", "Play at your pace", "Make it yours")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("DoMemory", style = DoMemoryType.display(34), color = p.primary)
            Spacer(Modifier.size(24.dp))
            Text(pages[state.page], style = DoMemoryType.display(28), color = p.textPrimary)
            Text(listOf("Find every matching pair before time runs out.", "Games start at a comfortable pace you can fine-tune anytime from Settings.", "Create your own memoramas and keep favourites close.")[state.page], color = p.textSecondary, fontSize = 17.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip, enabled = !state.isSaving) { Text("Skip", color = p.textSecondary) }
            Button(onClick = onNext, enabled = !state.isSaving, colors = ButtonDefaults.buttonColors(containerColor = p.primary)) { Text(if (state.page == 2) "Get started" else "Continue") }
        }
    }
}
