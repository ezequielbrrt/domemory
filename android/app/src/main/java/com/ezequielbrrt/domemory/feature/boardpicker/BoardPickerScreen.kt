package com.ezequielbrrt.domemory.feature.boardpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.repository.CatalogStatus
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette

/**
 * Phase 1 stand-in for the Menu (spec 2). It only picks a difficulty and a board so the
 * game loop can be exercised end to end; Phase 2 replaces it with the real menu — tabs,
 * cards, favourites, custom memoramas.
 */
@Composable
fun BoardPickerScreen(
    difficulty: Difficulty,
    boards: List<Board>,
    status: CatalogStatus,
    onDifficultyChange: (Difficulty) -> Unit,
    onBoardSelected: (Board) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxSize()
            .background(palette.appBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.common_app_name),
            style = DoMemoryType.display(30),
            color = palette.primary,
        )
        Text(
            text = when (status) {
                CatalogStatus.LOADING -> stringResource(R.string.common_loading)
                // Catalog failure is silent and non-fatal (spec 13.1) — the app keeps
                // working, it just says which set of boards it is showing.
                CatalogStatus.UNAVAILABLE -> stringResource(R.string.menu_offline_boards)
                else -> stringResource(R.string.home_select_difficulty_prompt)
            },
            color = palette.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { entry ->
                val selected = entry == difficulty
                Text(
                    text = stringResource(entry.labelRes()),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) palette.surfacePrimary else palette.textSecondary,
                    modifier = Modifier
                        .background(
                            color = if (selected) palette.primary else palette.surfaceSecondary,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { onDifficultyChange(entry) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(boards, key = { it.id }) { board ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.surfacePrimary, RoundedCornerShape(14.dp))
                        .border(1.dp, palette.surfaceBorder, RoundedCornerShape(14.dp))
                        .clickable { onBoardSelected(board) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = board.name,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "${board.pairCount} ${stringResource(R.string.game_pairs_label)}",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    }
                    Text(text = board.items.take(4).joinToString(" "), fontSize = 18.sp)
                }
            }
        }
    }
}

private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}
