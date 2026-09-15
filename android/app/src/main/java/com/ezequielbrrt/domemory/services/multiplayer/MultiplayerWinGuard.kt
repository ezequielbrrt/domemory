package com.ezequielbrrt.domemory.services.multiplayer

/**
 * Pure once-per-room decision for whether a room-status update should record a multiplayer
 * win (spec: `com.ezequielbrrt.domemory.services.stats.ProfileStatsRecorder
 * .recordMultiplayerWin`). Mirrors iOS's `MultiplayerRoomViewModel.hasRecordedMultiplayerWin`
 * latch, and — since iOS never resets that latch on its own — follows this codebase's own
 * `hasFiredFinishHaptic` shape instead (`MultiplayerRoomViewModel.fireRoomHaptics`'s `else`
 * branch, "restartGame puts the same room back to .playing ... without clearing the latch
 * here, every game after the first would finish silently"): latch on the first `FINISHED`
 * update with this client as the winner, and clear the latch the moment the room leaves
 * `FINISHED` (a rematch's transition back to `PLAYING`), so a later rematch can record again.
 *
 * Kept as a small stateful-but-dependency-free class — not a function — because the "once
 * per room, reset on leaving FINISHED" rule needs to remember whether it already fired
 * across repeated room-update events, the same reason [ProfileStatsRecorder]'s caller
 * ([com.ezequielbrrt.domemory.feature.multiplayer.MultiplayerViewModel]) can't just check
 * `room.status == FINISHED` inline — a duplicate `FINISHED` event (recomposition, a
 * redundant Firebase snapshot) must never double-count.
 */
class MultiplayerWinGuard {
    private var recorded = false

    /**
     * Returns true exactly once per `FINISHED`-with-this-client-as-winner streak. Every
     * call while [status] isn't `FINISHED` returns false and resets the latch. A `FINISHED`
     * call with no winner (a draw) or a winner that isn't this client also returns false,
     * without setting the latch — so a later update in the same `FINISHED` room that does
     * resolve a winner (a corrected snapshot) can still record.
     */
    fun shouldRecordWin(status: MultiplayerRoomStatus, winnerId: String?, isCurrentUser: (String) -> Boolean): Boolean {
        if (status != MultiplayerRoomStatus.FINISHED) {
            recorded = false
            return false
        }
        if (recorded) return false
        if (winnerId == null || !isCurrentUser(winnerId)) return false
        recorded = true
        return true
    }
}
