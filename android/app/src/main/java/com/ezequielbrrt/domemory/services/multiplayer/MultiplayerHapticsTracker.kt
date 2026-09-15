package com.ezequielbrrt.domemory.services.multiplayer

import com.ezequielbrrt.domemory.services.haptics.HapticIntent

/**
 * Pure, stateful translation of a room-update stream into the haptic moments it implies —
 * the Android counterpart of iOS's `MultiplayerRoomViewModel.fireRoomHaptics`, kept as a
 * standalone class the same way [MultiplayerWinGuard] is, so `MultiplayerViewModel` stays
 * thin wiring and the actual decision (what to fire, and when *not* to re-fire it) is
 * testable with plain values — no Firebase, no `ViewModel`, no `HapticsService`.
 *
 * Multiplayer is the one place a player may not be looking at the screen when something
 * happens (their opponent's move, a disconnect resolving), so remote events need to be felt
 * as well as seen — this is why it's the highest-value haptics gap of the five screens this
 * slice wires (`ANDROID_PLAN.md` §8 items 8 and 10).
 *
 * Three latches, one per intent family, mirror iOS's own three private `var`s exactly:
 * - [lastResolvedPairSignature]: a selected-pair signature already turned into a MATCH/
 *   MISMATCH must not re-fire on every subsequent identical snapshot (a redundant Firebase
 *   event, a recomposition) until the pair actually clears (`selectedCardIds` goes empty).
 * - [wasMyTurn]: SELECT fires once on the *edge* of a turn handover landing on this player,
 *   not on every update while it's still their turn.
 * - [hasFiredFinishHaptic]: SUCCESS/FAILURE fires once per `FINISHED` streak. Reset the
 *   instant the room leaves `FINISHED` — a rematch's `restartGame`/`startNewGame` puts the
 *   same room (and this same, still-alive tracker) back to `PLAYING`, and without the reset
 *   every game after the first would finish silently. This is the identical shape
 *   [MultiplayerWinGuard] uses for the same reason; see that class's doc for the parallel
 *   iOS-bug history — this tracker was written with the reset from the start.
 */
class MultiplayerHapticsTracker {
    private var lastResolvedPairSignature: String? = null
    private var wasMyTurn: Boolean = false
    private var hasFiredFinishHaptic: Boolean = false

    /**
     * Returns the ordered list of intents this room update implies — usually empty or a
     * single intent, but a newly-resolved pair and a turn handover can land in the same
     * snapshot, so callers should fire every intent returned, in order.
     */
    fun intentsFor(
        status: MultiplayerRoomStatus,
        selectedCardIds: List<Int>,
        cards: List<MultiplayerCard>,
        isMyTurnNow: Boolean,
        winnerId: String?,
        isCurrentUser: (String) -> Boolean,
    ): List<HapticIntent> {
        val intents = mutableListOf<HapticIntent>()

        if (status == MultiplayerRoomStatus.PLAYING && selectedCardIds.size == 2) {
            val signature = selectedCardIds.sorted().joinToString("-")
            if (signature != lastResolvedPairSignature) {
                lastResolvedPairSignature = signature
                val selected = cards.filter { it.id in selectedCardIds }
                val matched = selected.isNotEmpty() && selected.all { it.isMatched }
                intents += if (matched) HapticIntent.MATCH else HapticIntent.MISMATCH
            }
        } else if (selectedCardIds.isEmpty()) {
            lastResolvedPairSignature = null
        }

        val isMyTurn = status == MultiplayerRoomStatus.PLAYING && isMyTurnNow
        if (isMyTurn && !wasMyTurn) {
            intents += HapticIntent.SELECT
        }
        wasMyTurn = isMyTurn

        if (status == MultiplayerRoomStatus.FINISHED) {
            if (!hasFiredFinishHaptic) {
                hasFiredFinishHaptic = true
                // A draw (null winnerId) reads as a loss here, matching iOS's own
                // `winnerId == currentUserID` comparison, which is also false for a draw.
                val won = winnerId != null && isCurrentUser(winnerId)
                intents += if (won) HapticIntent.SUCCESS else HapticIntent.FAILURE
            }
        } else {
            hasFiredFinishHaptic = false
        }

        return intents
    }
}
