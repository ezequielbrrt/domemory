package com.ezequielbrrt.domemory.feature.multiplayer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.feature.game.CardView
import com.ezequielbrrt.domemory.services.ads.AdMobNativeAdView
import com.ezequielbrrt.domemory.services.ads.AdPlacement
import com.ezequielbrrt.domemory.services.ads.AdsService
import com.ezequielbrrt.domemory.services.multiplayer.*
import com.ezequielbrrt.domemory.services.stats.ProfileStatsRecorder
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
 */
class MultiplayerViewModel(
    private val service: MultiplayerService,
    private val profileStats: ProfileStatsRecorder? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(MultiplayerUiState()); val state = _state.asStateFlow()
    private var observer: kotlinx.coroutines.Job? = null
    private var reconnectDeadline: kotlinx.coroutines.Job? = null
    private val winGuard = MultiplayerWinGuard()
    fun create(board: Board) = launchRoomAction { service.createRoom(board).id }
    fun join(code: String) = launchRoomAction { service.joinRoom(code) }
    fun joinScannedInvite(rawValue: String?) {
        MultiplayerQrScan.roomCode(rawValue)?.let(::join) ?: run {
            _state.value = _state.value.copy(error = "This QR code is not a DoMemory room.")
        }
    }
    fun reportScanFailure(error: Throwable) {
        _state.value = _state.value.copy(error = error.message ?: "Could not scan the QR code.")
    }
    fun start(room: MultiplayerRoom, boards: List<Board>) = launchRoomAction {
        val board = boards.firstOrNull { it.id == room.gameId }
            ?: room.customGamePayload?.asBoard(room.gameId, room.difficulty)
            ?: throw MultiplayerException.Decoding
        service.start(room.id, board)
        room.id
    }
    fun isHost(room: MultiplayerRoom) = service.isCurrentUser(room.hostId)
    fun isCurrentUser(userId: String) = service.isCurrentUser(userId)
    fun restart(roomId: String) = viewModelScope.launch { runCatching { service.restart(roomId) }.onFailure(::fail) }
    fun choose(roomId: String, cardId: Int) = viewModelScope.launch {
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
                    scheduleMismatchClear(room)
                    reconcilePresence(room)
                    recordMultiplayerWinIfNeeded(room)
                }.onFailure(::fail)
            }
        }
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

    companion object { private const val MISMATCH_VISIBLE_MS = 2_000L }
}
@Composable fun MultiplayerScreen(boards: List<Board>, vm: MultiplayerViewModel, initialCode: String = "", onBack: () -> Unit) {
    val state by vm.state.collectAsState(); var code by rememberSaveable { mutableStateOf(initialCode) }; val p = LocalPalette.current; val context = LocalContext.current
    LaunchedEffect(initialCode) { if (initialCode.isNotBlank() && state.room == null) vm.join(initialCode) }
    Column(Modifier.fillMaxSize().background(p.appBackground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("‹") }; Text(stringResource(R.string.multiplayer_title))
        state.room?.let { room ->
            if (room.status in setOf(MultiplayerRoomStatus.PLAYING, MultiplayerRoomStatus.RECONNECTING, MultiplayerRoomStatus.FINISHED)) {
                MultiplayerGameBoard(room, vm, Modifier.weight(1f))
                return@Column
            }
            val message = when (room.status) {
                MultiplayerRoomStatus.WAITING -> stringResource(R.string.multiplayer_waiting_for_player)
                MultiplayerRoomStatus.READY -> stringResource(R.string.multiplayer_ready_to_start)
                MultiplayerRoomStatus.PLAYING -> stringResource(R.string.multiplayer_waiting_for_host)
                MultiplayerRoomStatus.RECONNECTING -> stringResource(R.string.multiplayer_reconnecting)
                MultiplayerRoomStatus.FINISHED -> stringResource(R.string.multiplayer_final_score)
                MultiplayerRoomStatus.ABANDONED -> stringResource(R.string.multiplayer_room_closed)
            }
            Text("$message\n${room.code}")
            if (room.status in setOf(MultiplayerRoomStatus.WAITING, MultiplayerRoomStatus.READY)) {
                MultiplayerQrCode(room.code)
                Button(
                    onClick = {
                        val caption = context.getString(R.string.multiplayer_invite_message, room.code)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, MultiplayerInvite.shareText(caption, room.code))
                        }, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.multiplayer_invite_friend)) }
            }
            if (room.status == MultiplayerRoomStatus.READY && vm.isHost(room)) {
                Button(onClick = { vm.start(room, boards) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.multiplayer_start_game))
                }
            }
        } ?: run {
            OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.multiplayer_code_placeholder)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.join(code) }, enabled = code.isNotBlank() && !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.multiplayer_join_room)) }
            OutlinedButton(
                onClick = {
                    launchMultiplayerQrScanner(
                        context = context,
                        onScanned = vm::joinScannedInvite,
                        onFailure = vm::reportScanFailure,
                    )
                },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.multiplayer_scan_qr_code)) }
            boards.firstOrNull()?.let { board -> Button(onClick = { vm.create(board) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.multiplayer_create_room)) } }
        }
        if (state.loading) CircularProgressIndicator(color = p.primary)
        state.error?.let { Text(it, color = p.secondary) }
    }
}

@Composable
private fun MultiplayerGameBoard(room: MultiplayerRoom, vm: MultiplayerViewModel, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val myTurn = room.currentPlayerId?.let(vm::isCurrentUser) == true
    val columns = ceil(kotlin.math.sqrt(room.cards.size.toDouble())).toInt().coerceAtLeast(1)
    val rows = room.cards.chunked(columns)

    val statusText = when (room.status) {
        MultiplayerRoomStatus.FINISHED -> when {
            room.winnerId == null -> stringResource(R.string.multiplayer_draw)
            vm.isCurrentUser(room.winnerId) -> stringResource(R.string.multiplayer_you_won)
            else -> stringResource(R.string.multiplayer_you_lost)
        }
        MultiplayerRoomStatus.RECONNECTING -> stringResource(R.string.multiplayer_reconnecting)
        else -> stringResource(if (myTurn) R.string.multiplayer_your_turn else R.string.multiplayer_opponent_turn)
    }
    Text(
        text = statusText,
        color = if (myTurn) palette.easyGreen else palette.textSecondary,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${stringResource(R.string.multiplayer_you)}: ${room.players.values.firstOrNull { vm.isCurrentUser(it.id) }?.score ?: 0}")
        Text("${stringResource(R.string.multiplayer_opponent)}: ${room.players.values.firstOrNull { !vm.isCurrentUser(it.id) }?.score ?: 0}")
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
            onClick = { vm.restart(room.id) },
            enabled = vm.isHost(room),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (vm.isHost(room)) R.string.multiplayer_play_again else R.string.multiplayer_waiting_for_rematch))
        }
    }
}
