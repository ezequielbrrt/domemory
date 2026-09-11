package com.ezequielbrrt.domemory.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

@Composable
fun OnboardingScreen(state: OnboardingUiState, onNext: () -> Unit, onSkip: () -> Unit, onDifficulty: (Difficulty) -> Unit, onFinish: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        if (state.step == OnboardingStep.INTRO) {
            val pages = listOf("Match the pairs", "Play at your pace", "Make it yours")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("DoMemory", style = DoMemoryType.display(34), color = p.primary)
                Spacer(Modifier.size(24.dp))
                Text(pages[state.page], style = DoMemoryType.display(28), color = p.textPrimary)
                Text(listOf("Find every matching pair before time runs out.", "Choose a difficulty that feels right for you.", "Create your own memoramas and keep favourites close.")[state.page], color = p.textSecondary, fontSize = 17.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip) { Text("Skip", color = p.textSecondary) }
                Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = p.primary)) { Text(if (state.page == 2) "Choose difficulty" else "Continue") }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose your pace", style = DoMemoryType.display(28), color = p.textPrimary)
                Text("You can change this anytime in Settings.", color = p.textSecondary)
                Difficulty.entries.forEach { difficulty ->
                    val selected = state.difficulty == difficulty
                    Text(difficulty.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) p.surfacePrimary else p.textPrimary, modifier = Modifier.fillMaxWidth().background(if (selected) p.primary else p.surfacePrimary, RoundedCornerShape(14.dp)).clickable { onDifficulty(difficulty) }.padding(18.dp))
                }
            }
            Button(onClick = onFinish, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = p.primary)) { Text("Start playing") }
        }
    }
}
