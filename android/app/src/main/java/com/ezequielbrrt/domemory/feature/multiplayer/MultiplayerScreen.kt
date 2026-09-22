package com.ezequielbrrt.domemory.feature.multiplayer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.feature.game.CardView
import com.ezequielbrrt.domemory.services.ads.AdMobNativeAdView
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.analytics.AnalyticsEvent
import com.ezequielbrrt.domemory.services.analytics.AnalyticsService
import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import com.ezequielbrrt.domemory.services.haptics.HapticsService
import com.ezequielbrrt.domemory.ui.components.BackButton
import com.ezequielbrrt.domemory.services.multiplayer.*
import com.ezequielbrrt.domemory.services.stats.ProfileStatsRecorder
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.LocalPalette
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.ceil

data class MultiplayerUiState(val room: MultiplayerRoom? = null, val error: String? = null, val loading: Boolean = false)

/**
 * [profileStats] is null-default (like [com.ezequielbrrt.domemory.feature.game
 * .GameViewModel]'s own `stats`/`profileStats` params) so every existing test/preview call
 * site is unaffected. Wiring it records a multiplayer win exactly once per room, only for
 * the actual winner, guarded by [MultiplayerWinGuard] — see that class's doc for why the
 * guard is a separate, dependency-free object rather than a boolean field here.
 *
 * [onHaptic] is the same bare-callback shape as [com.ezequielbrrt.domemory.feature.game
 * .GameViewModel.onHaptic] — null-default so every existing test/preview call site is
 * unaffected, wired to the real [HapticsService.fire] only from `NavGraph.kt`. Every remote
 * room-update moment (card flip resolution, turn handover, finish) goes through
 * [MultiplayerHapticsTracker]; [choose] fires the one *local*, optimistic haptic
 * ([HapticIntent.CARD_FLIP]) itself, the moment the tap happens, mirroring iOS's
 * `MultiplayerRoomViewModel.choose(card:)`.
 */
class MultiplayerViewModel(
    private val service: MultiplayerService,
    private val profileStats: ProfileStatsRecorder? = null,
    private val onHaptic: ((HapticIntent) -> Unit)? = null,
    /** Same bare-callback shape as [onHaptic], for the same reason — see
     * [com.ezequielbrrt.domemory.feature.game.GameViewModel]'s `onAnalytics` doc. */
    private val onAnalytics: ((AnalyticsEvent) -> Unit)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(MultiplayerUiState()); val state = _state.asStateFlow()
    private var observer: kotlinx.coroutines.Job? = null
    private var reconnectDeadline: kotlinx.coroutines.Job? = null
    private val winGuard = MultiplayerWinGuard()
    private val hapticsTracker = MultiplayerHapticsTracker()

    /** Tracks the last room status a `multiplayer_game_finished` was logged for, so a
     * rematch (status leaves and re-enters `FINISHED`) logs a fresh event instead of
     * re-firing on every unrelated snapshot of an already-finished room. */
    private var lastLoggedFinishedStatus: MultiplayerRoomStatus? = null

    // The catalog list the UI has in scope (container.boardCatalog.boards, merged with custom
    // boards at the call site) — the auto-start trigger below needs it to resolve a
    // MultiplayerRoom.gameId back to a Board, but observe()'s collect block otherwise has no
    // access to it. Set once from the Composable via setBoards; a plain var rather than a
    // constructor param because the boards StateFlow isn't available yet when NavGraph builds
    // this ViewModel.
    private var boards: List<Board> = emptyList()
    fun setBoards(boards: List<Board>) { this.boards = boards }

    // Spec 10.4 step 5's "guard against re-triggering while an earlier start write is still in
    // flight" — mirrors iOS's `hasTriggeredAutoStart` in `MultiplayerRoomViewModel
    // .handleRoomUpdate`. Reset whenever status leaves READY, so a genuinely new ready-window
    // (e.g. after the host changes the game and both players ready up again) can trigger again.
    private var hasTriggeredAutoStart = false

    fun create() = launchRoomAction {
        // Android's host-first protocol (spec 10.4 step 1) creates the room *empty* — no
        // game chosen yet — unlike iOS, which creates a room from an already-selected
        // memorama. `game_id` is therefore blank at this exact moment; it's the actual room
        // being created, so this fires here rather than waiting for [selectGame].
        val room = service.createRoom()
        onAnalytics?.invoke(AnalyticsEvent.MultiplayerRoomCreated(gameId = room.gameId, isCustom = false))
        room.id
    }
    fun join(code: String) = launchRoomAction {
        val roomId = service.joinRoom(code)
        onAnalytics?.invoke(AnalyticsEvent.MultiplayerRoomJoined)
        roomId
    }
    fun joinScannedInvite(rawValue: String?) {
        MultiplayerQrScan.roomCode(rawValue)?.let(::join) ?: run {
            _state.value = _state.value.copy(error = "This QR code is not a DoMemory room.")
        }
    }
    fun reportScanFailure(error: Throwable) {
        _state.value = _state.value.copy(error = error.message ?: "Could not scan the QR code.")
    }
    /** Spec 10.4 step 3: host-only. Resets both players' [MultiplayerPlayer.isReady]. */
    fun selectGame(room: MultiplayerRoom, board: Board) = viewModelScope.launch {
        runCatching { service.selectGame(room.id, board) }.onFailure(::fail)
    }
    /** Spec 10.4 step 4: marks the caller ready. Never starts the match itself — see
     * [triggerAutoStartIfNeeded]. */
    fun markReady(room: MultiplayerRoom) = viewModelScope.launch {
        runCatching { service.setReady(room.id, true) }.onFailure(::fail)
    }
    fun isHost(room: MultiplayerRoom) = service.isCurrentUser(room.hostId)
    fun isCurrentUser(userId: String) = service.isCurrentUser(userId)
    /** Whether *I* have already marked myself ready for the currently-picked game. */
    fun isSelfReady(room: MultiplayerRoom): Boolean =
        room.players.values.firstOrNull { service.isCurrentUser(it.id) }?.isReady == true
    /** Host-only, and only while there is no in-flight/dealt match to disturb — the game
     * picker's entry point (both "choose a game" and "change game") gates on this. */
    fun canPickGame(room: MultiplayerRoom): Boolean =
        isHost(room) && room.status in setOf(MultiplayerRoomStatus.WAITING, MultiplayerRoomStatus.READY)
    fun restart(roomId: String) = viewModelScope.launch { runCatching { service.restart(roomId) }.onFailure(::fail) }
    fun choose(roomId: String, cardId: Int) = viewModelScope.launch {
        // Optimistic and local — fired on the tap itself, before the transaction that
        // actually moves the card lands, exactly like iOS's own `isInteractionEnabled`
        // guard in front of its `.cardFlip` fire.
        if (_state.value.room?.canFlipNow(service::isCurrentUser) == true) {
            onHaptic?.invoke(HapticIntent.CARD_FLIP)
        }
        runCatching { service.choose(roomId, cardId) }.onFailure(::fail)
    }
    private fun launchRoomAction(action: suspend () -> String) = viewModelScope.launch {
        _state.value = _state.value.copy(error = null, loading = true)
        try {
            observe(action())
        } catch (error: Throwable) {
            fail(error)
        }
    }
    private fun observe(id: String) {
        observer?.cancel()
        observer = viewModelScope.launch {
            service.observe(id).collect { result ->
                result.onSuccess { room ->
                    _state.value = _state.value.copy(room = room, loading = false)
                    fireRoomHaptics(room)
                    scheduleMismatchClear(room)
                    reconcilePresence(room)
                    recordMultiplayerWinIfNeeded(room)
                    logGameFinishedIfNeeded(room)
                    triggerAutoStartIfNeeded(room)
                }.onFailure(::fail)
            }
        }
    }
    /** Spec: iOS's `MultiplayerRoomViewModel.fireRoomHaptics` — every haptic a *remote* room
     * update can imply (a resolved pair, a turn landing on this player, the match finishing),
     * as opposed to [choose]'s own local, optimistic CARD_FLIP. */
    private fun fireRoomHaptics(room: MultiplayerRoom) {
        val onHaptic = onHaptic ?: return
        val isMyTurnNow = room.currentPlayerId?.let(service::isCurrentUser) == true
        hapticsTracker.intentsFor(
            status = room.status,
            selectedCardIds = room.selectedCardIds,
            cards = room.cards,
            isMyTurnNow = isMyTurnNow,
            winnerId = room.winnerId,
            isCurrentUser = service::isCurrentUser,
        ).forEach(onHaptic)
    }
    private fun scheduleMismatchClear(room: MultiplayerRoom) {
        val selected = room.selectedCardIds
        val selectedCards = room.cards.filter { it.id in selected }
        if (selected.size == 2 && selectedCards.size == 2 && selectedCards[0].itemId != selectedCards[1].itemId) {
            viewModelScope.launch {
                delay(MISMATCH_VISIBLE_MS)
                runCatching { service.clearMismatch(room.id, selected) }
            }
        }
    }
    private fun reconcilePresence(room: MultiplayerRoom) {
        if (room.status !in setOf(MultiplayerRoomStatus.PLAYING, MultiplayerRoomStatus.RECONNECTING)) {
            reconnectDeadline?.cancel()
            reconnectDeadline = null
            return
        }
        if (room.status == MultiplayerRoomStatus.RECONNECTING || room.players.values.any { !it.connected }) {
            viewModelScope.launch { runCatching { service.reconcilePresence(room.id) } }
        }
        val startedAt = room.disconnectStartedAt ?: return
        reconnectDeadline?.cancel()
        val delayMillis = (MultiplayerService.RECONNECT_GRACE_SECONDS - (System.currentTimeMillis() / 1_000 - startedAt)).coerceAtLeast(0) * 1_000 + 100
        reconnectDeadline = viewModelScope.launch {
            delay(delayMillis)
            runCatching { service.reconcilePresence(room.id) }
        }
    }
    private fun fail(t: Throwable) { _state.value = _state.value.copy(error = t.message ?: "Multiplayer is unavailable.", loading = false) }

    /** Spec: iOS's `MultiplayerRoomViewModel.handleRoomUpdate` (`hasRecordedMultiplayerWin`).
     * See [MultiplayerWinGuard] for the once-per-room, reset-on-rematch shape. */
    private fun recordMultiplayerWinIfNeeded(room: MultiplayerRoom) {
        if (!winGuard.shouldRecordWin(room.status, room.winnerId, service::isCurrentUser)) return
        val recorder = profileStats ?: return
        viewModelScope.launch { recorder.recordMultiplayerWin() }
    }

    /** `multiplayer_game_finished`, once per transition *into* [MultiplayerRoomStatus.FINISHED]
     * — a rematch (status leaves FINISHED for PLAYING, then returns) logs a fresh event, but
     * repeated snapshots of an already-finished room don't re-fire. `result` distinguishes a
     * normal finish from [MultiplayerService.reconcilePresence]'s reconnect-grace forfeit,
     * matching iOS's own "completed"/"disconnect" values. */
    private fun logGameFinishedIfNeeded(room: MultiplayerRoom) {
        if (room.status == MultiplayerRoomStatus.FINISHED && lastLoggedFinishedStatus != MultiplayerRoomStatus.FINISHED) {
            val result = if (room.disconnectPlayerId != null) "disconnect" else "completed"
            onAnalytics?.invoke(AnalyticsEvent.MultiplayerGameFinished(result = result))
        }
        lastLoggedFinishedStatus = room.status
    }

    /** Spec 10.4 step 5: reactive, host-only start once [MultiplayerRoom.readyToAutoStart].
     * Latches on the write, not the room snapshot, so an unrelated snapshot arriving while
     * `service.start` is still in flight (e.g. a presence heartbeat) can't fire it twice; the
     * latch resets as soon as status leaves READY (a fresh ready-window, or a failed start). */
    private fun triggerAutoStartIfNeeded(room: MultiplayerRoom) {
        if (room.status != MultiplayerRoomStatus.READY) { hasTriggeredAutoStart = false; return }
        if (hasTriggeredAutoStart) return
        if (!isHost(room) || !room.readyToAutoStart) return
        val board = boards.firstOrNull { it.id == room.gameId }
            ?: room.customGamePayload?.asBoard(room.gameId, room.difficulty)
            ?: return
        hasTriggeredAutoStart = true
        viewModelScope.launch {
            runCatching { service.start(room.id, board) }
                .onSuccess { onAnalytics?.invoke(AnalyticsEvent.MultiplayerGameStarted(gameId = board.id)) }
                .onFailure {
                    hasTriggeredAutoStart = false
                    fail(it)
                }
        }
    }

    companion object { private const val MISMATCH_VISIBLE_MS = 2_000L }
}
@Composable fun MultiplayerScreen(boards: List<Board>, vm: MultiplayerViewModel, initialCode: String = "", onBack: () -> Unit) {
    val state by vm.state.collectAsState(); var code by rememberSaveable { mutableStateOf(initialCode) }; val p = LocalPalette.current; val context = LocalContext.current
    var showGamePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        AnalyticsService.log(AnalyticsEvent.ScreenView(screenName = "multiplayer_room", screenClass = "MultiplayerScreen"))
        // A non-blank initialCode only ever arrives via the domemory://join/<code> deep link
        // (`NavGraph.kt`'s `DeepLink.Join` route) — mirrors iOS's `InviteLink.swift` logging
        // this the moment a shared invite link is opened, before the join attempt itself.
        if (initialCode.isNotBlank()) AnalyticsService.log(AnalyticsEvent.MultiplayerInviteOpened)
    }
    LaunchedEffect(boards) { vm.setBoards(boards) }
    LaunchedEffect(initialCode) { if (initialCode.isNotBlank() && state.room == null) vm.join(initialCode) }
    Column(
        Modifier.fillMaxSize().background(p.appBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackButton(onClick = { HapticsService.fire(HapticIntent.TAP); onBack() }, modifier = Modifier.align(Alignment.Start))
        Text(stringResource(R.string.multiplayer_title), textAlign = TextAlign.Center)
        state.room?.let { room ->
            if (room.status in setOf(MultiplayerRoomStatus.PLAYING, MultiplayerRoomStatus.RECONNECTING, MultiplayerRoomStatus.FINISHED)) {
                MultiplayerGameBoard(room, vm, Modifier.weight(1f))
                return@Column
            }
            // Spec 10.4 steps 1-4: before a game is picked, the lobby's status message is
            // about *who* is missing (host still choosing / guest still waiting on the host);
            // once a game is picked it's about the mutual ready-check instead. Mirrors iOS's
            // `MultiplayerRoomViewModel.statusText`/`noGameChosenStatusText`.
            val message = when (room.status) {
                MultiplayerRoomStatus.WAITING -> if (room.hasSelectedGame) {
                    stringResource(R.string.multiplayer_waiting_for_player)
                } else {
                    stringResource(if (vm.isHost(room)) R.string.multiplayer_host_choose_game_prompt else R.string.multiplayer_waiting_for_game)
                }
                MultiplayerRoomStatus.READY -> when {
                    !room.hasSelectedGame -> stringResource(if (vm.isHost(room)) R.string.multiplayer_host_choose_game_prompt else R.string.multiplayer_waiting_for_game)
                    vm.isSelfReady(room) -> stringResource(R.string.multiplayer_waiting_for_opponent_ready)
                    else -> stringResource(R.string.multiplayer_tap_start_when_ready)
                }
                MultiplayerRoomStatus.ABANDONED -> stringResource(R.string.multiplayer_room_closed)
                // Unreachable here — PLAYING/RECONNECTING/FINISHED already returned above.
                else -> stringResource(R.string.multiplayer_reconnecting)
            }
            Text("$message\n${room.code}", textAlign = TextAlign.Center)
            if (room.status in setOf(MultiplayerRoomStatus.WAITING, MultiplayerRoomStatus.READY)) {
                MultiplayerQrCode(room.code)
                Button(
                    onClick = {
                        AnalyticsService.log(AnalyticsEvent.MultiplayerInviteSent(source = "lobby"))
                        val caption = context.getString(R.string.multiplayer_invite_message, room.code)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, MultiplayerInvite.shareText(caption, room.code))
                        }, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.multiplayer_invite_friend)) }

                // Spec 10.4 step 3: host-only entry point, any time before the match starts.
                // Guests see nothing here — the status message above already tells them the
                // host hasn't picked, or what was picked.
                if (room.hasSelectedGame) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(room.gameName, fontWeight = FontWeight.Bold, color = p.textPrimary)
                        if (vm.canPickGame(room)) {
                            Text(
                                text = stringResource(R.string.multiplayer_choose_another_game),
                                color = p.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    HapticsService.fire(HapticIntent.TAP)
                                    showGamePicker = true
                                },
                            )
                        }
                    }
                } else if (vm.canPickGame(room)) {
                    Button(
                        onClick = { HapticsService.fire(HapticIntent.TAP); showGamePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.multiplayer_choose_game)) }
                }

                // Spec 10.4 step 4: available to either player once a game is picked and both
                // have joined; marks only the caller ready — the match starts on its own once
                // both are (see MultiplayerViewModel.triggerAutoStartIfNeeded).
                if (room.hasSelectedGame && room.players.size == 2) {
                    val selfReady = vm.isSelfReady(room)
                    Button(
                        onClick = { HapticsService.fire(HapticIntent.TAP); vm.markReady(room) },
                        enabled = !selfReady && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (selfReady) R.string.multiplayer_waiting_for_opponent_ready else R.string.multiplayer_start_game))
                    }
                }
            }
        } ?: run {
            OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.multiplayer_code_placeholder)) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { HapticsService.fire(HapticIntent.TAP); vm.join(code) },
                enabled = code.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.multiplayer_join_room)) }
            OutlinedButton(
                onClick = {
                    HapticsService.fire(HapticIntent.TAP)
                    launchMultiplayerQrScanner(
                        context = context,
                        onScanned = vm::joinScannedInvite,
                        onFailure = vm::reportScanFailure,
                    )
                },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.multiplayer_scan_qr_code)) }
            // Spec 10.4 step 1: host-first — a room is created empty, no board required.
            Button(
                onClick = { HapticsService.fire(HapticIntent.TAP); vm.create() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.multiplayer_create_room)) }
        }
        if (state.loading) CircularProgressIndicator(color = p.primary)
        state.error?.let { Text(it, color = p.secondary, textAlign = TextAlign.Center) }
    }

    if (showGamePicker) {
        state.room?.let { room ->
            MultiplayerGamePickerDialog(
                title = stringResource(if (room.hasSelectedGame) R.string.multiplayer_choose_another_game else R.string.multiplayer_choose_game),
                boards = boards,
                onSelect = { board ->
                    HapticsService.fire(HapticIntent.TAP)
                    showGamePicker = false
                    vm.selectGame(room, board)
                },
                onDismiss = { showGamePicker = false },
            )
        }
    }
}

/**
 * Spec 10.4 step 3: the game picker's own difficulty filter, independent of whatever
 * difficulty the All tab's catalog filter happens to be showing, across every difficulty;
 * custom boards always show regardless of the filter. Reuses `MenuScreen.kt`'s `AllTab`
 * difficulty-pill visual pattern (duplicated rather than shared — this codebase already
 * duplicates a small private `Difficulty.labelRes()` per screen rather than extracting one).
 */
@Composable
private fun MultiplayerGamePickerDialog(
    title: String,
    boards: List<Board>,
    onSelect: (Board) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var selectedDifficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }
    val filtered = boards.filter { it.isCustom || Difficulty.parse(it.difficulty) == selectedDifficulty }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { entry ->
                        val selected = entry == selectedDifficulty
                        Text(
                            text = stringResource(entry.pickerLabelRes()),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) palette.surfacePrimary else palette.textSecondary,
                            modifier = Modifier
                                .background(
                                    color = if (selected) palette.primary else palette.surfaceSecondary,
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable {
                                    HapticsService.fire(HapticIntent.SELECT)
                                    selectedDifficulty = entry
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.multiplayer_no_games_for_difficulty),
                        color = palette.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { board ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(palette.surfaceSecondary, RoundedCornerShape(12.dp))
                                    .clickable { onSelect(board) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(board.items.firstOrNull().orEmpty(), fontSize = 24.sp)
                                Text(board.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun Difficulty.pickerLabelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}

/** How the match ended for the current player. Kept as the single source of truth for both
 * [titleRes] and [MultiplayerResultBanner], so they can never disagree about the outcome. */
private enum class MultiplayerResultKind { WON, LOST, DRAW }

private fun MultiplayerRoom.resultKind(vm: MultiplayerViewModel): MultiplayerResultKind? {
    if (status != MultiplayerRoomStatus.FINISHED) return null
    return when {
        winnerId == null -> MultiplayerResultKind.DRAW
        vm.isCurrentUser(winnerId) -> MultiplayerResultKind.WON
        else -> MultiplayerResultKind.LOST
    }
}

private fun MultiplayerResultKind.titleRes(): Int = when (this) {
    MultiplayerResultKind.WON -> R.string.multiplayer_you_won
    MultiplayerResultKind.LOST -> R.string.multiplayer_you_lost
    MultiplayerResultKind.DRAW -> R.string.multiplayer_draw
}

/** Shown once the room is finished, replacing the mid-game turn-indicator label with the same
 * big-emoji-plus-display-headline language the single-player win/lose screens use (see
 * `GameScreen.kt`'s `WinOutcomeContent`/lose branch), so a match's result reads as clearly here
 * as it does everywhere else in the app. */
@Composable
private fun MultiplayerResultBanner(kind: MultiplayerResultKind, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val (emoji, color) = when (kind) {
        MultiplayerResultKind.WON -> "😎" to palette.primary
        MultiplayerResultKind.LOST -> "😳" to palette.secondary
        MultiplayerResultKind.DRAW -> "🤝" to palette.textPrimary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(emoji, fontSize = 44.sp)
        Text(
            text = stringResource(kind.titleRes()),
            style = DoMemoryType.display(28),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.multiplayer_final_score),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun MultiplayerGameBoard(room: MultiplayerRoom, vm: MultiplayerViewModel, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val myTurn = room.currentPlayerId?.let(vm::isCurrentUser) == true
    val columns = ceil(kotlin.math.sqrt(room.cards.size.toDouble())).toInt().coerceAtLeast(1)
    val rows = room.cards.chunked(columns)
    val resultKind = room.resultKind(vm)

    if (resultKind == null) {
        val statusText = when (room.status) {
            MultiplayerRoomStatus.RECONNECTING -> stringResource(R.string.multiplayer_reconnecting)
            else -> stringResource(if (myTurn) R.string.multiplayer_your_turn else R.string.multiplayer_opponent_turn)
        }
        Text(
            text = statusText,
            color = if (myTurn) palette.easyGreen else palette.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${stringResource(R.string.multiplayer_you)}: ${room.players.values.firstOrNull { vm.isCurrentUser(it.id) }?.score ?: 0}")
        Text("${stringResource(R.string.multiplayer_opponent)}: ${room.players.values.firstOrNull { !vm.isCurrentUser(it.id) }?.score ?: 0}")
    }
    if (resultKind != null) {
        MultiplayerResultBanner(kind = resultKind, modifier = Modifier.padding(vertical = 8.dp))
    }
    // Spec: iOS swaps the finished board for the native ad placement entirely rather than
    // showing both — a finished room's grid carries no further interaction anyway.
    if (room.status == MultiplayerRoomStatus.FINISHED && AdsService.isNativeConfigured(AdPlacement.MULTIPLAYER_FINISHED_NATIVE)) {
        AdMobNativeAdView(
            placement = AdPlacement.MULTIPLAYER_FINISHED_NATIVE,
            modifier = modifier.fillMaxWidth().height(270.dp),
        )
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { multiplayerCard ->
                        CardView(
                            card = multiplayerCard.asCard(),
                            isHidden = multiplayerCard.isMatched,
                            showsPie = false,
                            pieFraction = 0f,
                            onClick = { vm.choose(room.id, multiplayerCard.id) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (room.status == MultiplayerRoomStatus.FINISHED) {
        Button(
            onClick = { HapticsService.fire(HapticIntent.TAP); vm.restart(room.id) },
            enabled = vm.isHost(room),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (vm.isHost(room)) R.string.multiplayer_play_again else R.string.multiplayer_waiting_for_rematch))
        }
    }
}
