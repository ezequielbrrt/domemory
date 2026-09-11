package com.ezequielbrrt.domemory.feature.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.services.levels.LevelProgressService
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

@Composable
fun LevelsScreen(progress: LevelProgressService, onLevelSelected: (Int) -> Unit) {
    progress.revision.collectAsState().value
    val p = LocalPalette.current
    val levels = (1..(progress.highestUnlockedLevel + 20)).toList()
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(top = 16.dp)) {
        Text("Levels", style = DoMemoryType.display(24), color = p.textPrimary, modifier = Modifier.padding(horizontal = 20.dp))
        Text("${progress.highestUnlockedLevel - 1} cleared", color = p.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items(levels) { level ->
                val unlocked = progress.isUnlocked(level); val stars = progress.stars(level)
                Column(Modifier.fillMaxWidth().background(if (unlocked) p.surfacePrimary else p.surfaceSecondary, RoundedCornerShape(16.dp)).clickable(enabled = unlocked) { onLevelSelected(level) }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (unlocked) level.toString() else "🔒", fontWeight = FontWeight.Bold, color = if (unlocked) p.primary else p.textSecondary)
                    Text(if (stars > 0) "★".repeat(stars) else "", fontSize = 11.sp, color = p.hardAmber)
                }
            }
        }
    }
}
