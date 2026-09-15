package com.ezequielbrrt.domemory.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.repository.CatalogStatus
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import com.ezequielbrrt.domemory.feature.daily.DailyChallengeCard
import com.ezequielbrrt.domemory.feature.levels.LevelsScreen
import com.ezequielbrrt.domemory.feature.levels.LevelsViewModel
import com.ezequielbrrt.domemory.feature.seasons.SeasonCard
import com.ezequielbrrt.domemory.services.seasons.Season
import com.ezequielbrrt.domemory.services.ads.AdMobBanner
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService

/**
 * The real menu (spec 2): header, three tabs (`Levels` / `My memoramas` / `All`),
 * favourites and custom-memorama create/delete. Replaces `BoardPickerScreen`.
 */
@Composable
fun MenuScreen(
    state: MenuUiState,
    onSelectTab: (MenuTab) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteCustomMemorama: (String) -> Unit,
    onBoardSelected: (Board) -> Unit,
    onRandomGame: () -> Unit,
    onCreateMemorama: () -> Unit,
    onMultiplayer: () -> Unit,
    onSettings: () -> Unit,
    levelsViewModel: LevelsViewModel,
    onLevelSelected: (Int) -> Unit,
    activeSeason: Season? = null,
    todayKey: String = "",
    onSeasonSelected: (Season) -> Unit = {},
    dailyStreak: Int = 0,
    isDailyChallengeCompletedToday: Boolean = false,
    onDailyChallengeSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxSize().background(palette.appBackground)) {
        MenuHeader(onCreateMemorama = onCreateMemorama, onMultiplayer = onMultiplayer, onSettings = onSettings)
        // Spec 9.1: while a season is active it shares this row with the Daily card, which
        // shrinks; with no active season the Daily card keeps its full-width layout.
        Row(Modifier.fillMaxWidth()) {
            DailyChallengeCard(
                streak = dailyStreak,
                isCompletedToday = isDailyChallengeCompletedToday,
                onClick = onDailyChallengeSelected,
                modifier = if (activeSeason != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
            activeSeason?.let { season ->
                SeasonCard(
                    season = season,
                    todayKey = todayKey,
                    onClick = { HapticsService.fire(HapticIntent.TAP); onSeasonSelected(season) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AdMobBanner(AdPlacement.HOME_BANNER, Modifier.fillMaxWidth())
        MenuTabRow(selected = state.selectedTab, onSelectTab = onSelectTab)
        when (state.selectedTab) {
            MenuTab.LEVELS -> LevelsScreen(levelsViewModel, onLevelSelected)
            MenuTab.MINE -> MineTab(
                state = state,
                onToggleFavorite = onToggleFavorite,
                onDeleteCustomMemorama = onDeleteCustomMemorama,
                onBoardSelected = onBoardSelected,
                onCreateMemorama = onCreateMemorama,
            )
            MenuTab.ALL -> AllTab(
                state = state,
                onDifficultyChange = onDifficultyChange,
                onToggleFavorite = onToggleFavorite,
                onBoardSelected = onBoardSelected,
                onRandomGame = onRandomGame,
            )
        }
    }
}

@Composable
private fun MenuHeader(onCreateMemorama: () -> Unit, onMultiplayer: () -> Unit, onSettings: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.common_app_name),
            style = DoMemoryType.display(26),
            color = palette.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("♟", color = palette.primary, fontSize = 22.sp, modifier = Modifier.size(44.dp).clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onMultiplayer() }).padding(10.dp))
            Text(text = stringResource(R.string.menu_create_title), color = palette.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier
                .background(palette.surfaceSecondary, RoundedCornerShape(999.dp))
                .clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onCreateMemorama() })
                .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(text = "⚙", color = palette.primary, fontSize = 22.sp, modifier = Modifier.size(44.dp).clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onSettings() }).padding(10.dp))
        }
    }
}

@Composable
private fun MenuTabRow(selected: MenuTab, onSelectTab: (MenuTab) -> Unit) {
    val palette = LocalPalette.current
    val tabs = listOf(
        MenuTab.LEVELS to R.string.menu_tab_levels,
        MenuTab.MINE to R.string.menu_tab_mine,
        MenuTab.ALL to R.string.menu_tab_all,
    )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        containerColor = palette.appBackground,
        contentColor = palette.primary,
    ) {
        tabs.forEach { (tab, labelRes) ->
            Tab(
                selected = tab == selected,
                onClick = { onSelectTab(tab) },
                text = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun LevelsPlaceholderTab() {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.surfaceSecondary, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_levels_coming_soon_title),
                style = DoMemoryType.display(20),
                color = palette.textSecondary,
            )
            Text(
                text = stringResource(R.string.menu_levels_coming_soon_subtitle),
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun MineTab(
    state: MenuUiState,
    onToggleFavorite: (String) -> Unit,
    onDeleteCustomMemorama: (String) -> Unit,
    onBoardSelected: (Board) -> Unit,
    onCreateMemorama: () -> Unit,
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    if (state.myBoards.isEmpty()) {
        EmptyMineState(onCreateMemorama = onCreateMemorama)
    } else {
        BoardGrid(
            boards = state.myBoards,
            favoriteIds = state.favoriteIds,
            boardStats = state.boardStats,
            onToggleFavorite = onToggleFavorite,
            onBoardSelected = onBoardSelected,
            onDelete = { pendingDeleteId = it },
        )
    }

    pendingDeleteId?.let { boardId ->
        DeleteConfirmDialog(
            onConfirm = {
                onDeleteCustomMemorama(boardId)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun EmptyMineState(onCreateMemorama: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.menu_empty_mine_message),
                color = palette.textSecondary,
            )
            Text(
                text = stringResource(R.string.menu_empty_mine_action),
                color = palette.surfacePrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(palette.primary, RoundedCornerShape(999.dp))
                    .clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onCreateMemorama() })
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AllTab(
    state: MenuUiState,
    onDifficultyChange: (Difficulty) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBoardSelected: (Board) -> Unit,
    onRandomGame: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.menu_difficulty_label),
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { entry ->
                    val selected = entry == state.difficulty
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
                            // Android-only UI (iOS's Menu only ever shows a read-only
                            // difficulty badge here, never a picker) — SELECT matches
                            // HapticIntent's own "picker change" definition, the closest
                            // iOS moment to what this control actually is.
                            .clickable {
                                HapticsService.fire(HapticIntent.SELECT)
                                onDifficultyChange(entry)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            when (state.catalogStatus) {
                CatalogStatus.LOADING -> Text(stringResource(R.string.common_loading), color = palette.textSecondary, fontSize = 12.sp)
                CatalogStatus.UNAVAILABLE -> Text(stringResource(R.string.menu_offline_boards), color = palette.textSecondary, fontSize = 12.sp)
                else -> Unit
            }
            Spacer(Modifier.size(12.dp))
            // Mirrors iOS's All-tab "Random game" action: pick a board from the current
            // difficulty-filtered catalog, rather than from every catalog entry.
            Button(
                onClick = {
                    HapticsService.fire(HapticIntent.TAP)
                    onRandomGame()
                },
                enabled = state.allBoards.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⇄", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.menu_random_game),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        BoardGrid(
            boards = state.allBoards,
            favoriteIds = state.favoriteIds,
            boardStats = state.boardStats,
            onToggleFavorite = onToggleFavorite,
            onBoardSelected = onBoardSelected,
            onDelete = null,
        )
    }
}

@Composable
private fun BoardGrid(
    boards: List<Board>,
    favoriteIds: Set<String>,
    boardStats: Map<String, BoardStats>,
    onToggleFavorite: (String) -> Unit,
    onBoardSelected: (Board) -> Unit,
    onDelete: ((String) -> Unit)?,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(boards, key = { it.id }) { board ->
            BoardCell(
                board = board,
                isFavorite = board.id in favoriteIds,
                stats = boardStats[board.id] ?: BoardStats(),
                onToggleFavorite = { onToggleFavorite(board.id) },
                onClick = { onBoardSelected(board) },
                onDelete = onDelete?.let { { it(board.id) } },
            )
        }
    }
}

@Composable
private fun BoardCell(
    board: Board,
    isFavorite: Boolean,
    stats: BoardStats,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val palette = LocalPalette.current
    val favoriteDescription = if (isFavorite) {
        stringResource(R.string.menu_favorite_remove)
    } else {
        stringResource(R.string.menu_favorite_add)
    }
    val deleteDescription = stringResource(R.string.menu_delete_memorama)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surfacePrimary, RoundedCornerShape(14.dp))
            .border(1.dp, palette.surfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onClick() })
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = board.items.take(3).joinToString(" "), fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isFavorite) "★" else "☆",
                    color = if (isFavorite) palette.hardAmber else palette.textSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable(onClick = { HapticsService.fire(HapticIntent.TAP); onToggleFavorite() })
                        .semantics { contentDescription = favoriteDescription },
                )
                if (onDelete != null) {
                    Text(
                        text = "✕",
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable(onClick = onDelete)
                            .semantics { contentDescription = deleteDescription },
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = board.name,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardStatBadge(
                label = stringResource(R.string.stats_played_label),
                value = stats.played,
                color = palette.primary,
            )
            BoardStatBadge(
                label = stringResource(R.string.stats_won_label),
                value = stats.won,
                color = palette.easyGreen,
            )
        }
    }
}

@Composable
private fun BoardStatBadge(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    val palette = LocalPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(50)),
    ) {
        Text(
            text = value.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = color,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
            modifier = Modifier.padding(end = 8.dp, top = 5.dp, bottom = 5.dp),
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_delete_confirm_title)) },
        text = { Text(stringResource(R.string.menu_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}
