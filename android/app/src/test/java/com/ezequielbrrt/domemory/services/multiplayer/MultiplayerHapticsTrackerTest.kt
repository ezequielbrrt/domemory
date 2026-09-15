package com.ezequielbrrt.domemory.services.multiplayer

import com.ezequielbrrt.domemory.services.haptics.HapticIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tracker, no ViewModel/Firebase/HapticsService involved — mirrors iOS's
 * `MultiplayerRoomViewModel.fireRoomHaptics` and its three latches. See
 * [MultiplayerHapticsTracker]'s own doc for why each latch exists. */
class MultiplayerHapticsTrackerTest {

    private val isMe: (String) -> Boolean = { it == "me" }
    // 0/1 are a resolved matched pair; 2/3 are a resolved mismatched pair — both already
    // face-up in the snapshot, since intentsFor decides MATCH/MISMATCH purely off each
    // selected card's own isMatched flag (mirrors iOS's `selected.allSatisfy(\.isMatched)`).
    private val cards = listOf(
        MultiplayerCard(0, 0, "A", isMatched = true), MultiplayerCard(1, 0, "A", isMatched = true),
        MultiplayerCard(2, 1, "B"), MultiplayerCard(3, 2, "C"),
    )

    private fun tracker() = MultiplayerHapticsTracker()

    @Test fun `a matched pair fires MATCH exactly once`() {
        val t = tracker()
        val intents = t.intentsFor(
            status = MultiplayerRoomStatus.PLAYING,
            selectedCardIds = listOf(0, 1),
            cards = cards,
            isMyTurnNow = false,
            winnerId = null,
            isCurrentUser = isMe,
        )
        assertEquals(listOf(HapticIntent.MATCH), intents)

        // The identical snapshot repeating (a redundant Firebase event) must not re-fire.
        val again = t.intentsFor(
            status = MultiplayerRoomStatus.PLAYING,
            selectedCardIds = listOf(0, 1),
            cards = cards,
            isMyTurnNow = false,
            winnerId = null,
            isCurrentUser = isMe,
        )
        assertTrue(again.isEmpty())
    }

    @Test fun `a mismatched pair fires MISMATCH`() {
        val t = tracker()
        val intents = t.intentsFor(
            status = MultiplayerRoomStatus.PLAYING,
            selectedCardIds = listOf(0, 2),
            cards = cards,
            isMyTurnNow = false,
            winnerId = null,
            isCurrentUser = isMe,
        )
        assertEquals(listOf(HapticIntent.MISMATCH), intents)
    }

    @Test fun `a new pair after the selection clears fires again`() {
        val t = tracker()
        t.intentsFor(MultiplayerRoomStatus.PLAYING, listOf(0, 1), cards, false, null, isMe)
        // selectedCardIds going empty (the pair cleared) resets the signature latch.
        t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, false, null, isMe)
        val intents = t.intentsFor(MultiplayerRoomStatus.PLAYING, listOf(2, 3), cards, false, null, isMe)
        assertEquals(listOf(HapticIntent.MISMATCH), intents)
    }

    @Test fun `SELECT fires once on the edge of a turn landing on this player, not on every update`() {
        val t = tracker()
        val first = t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, true, null, isMe)
        assertEquals(listOf(HapticIntent.SELECT), first)

        val second = t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, true, null, isMe)
        assertTrue(second.isEmpty())
    }

    @Test fun `SELECT does not fire while it is still the opponent's turn`() {
        val t = tracker()
        val intents = t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, false, null, isMe)
        assertTrue(intents.isEmpty())
    }

    @Test fun `SELECT fires again after the turn passes away and comes back`() {
        val t = tracker()
        t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, true, null, isMe)
        t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, false, null, isMe)
        val intents = t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, true, null, isMe)
        assertEquals(listOf(HapticIntent.SELECT), intents)
    }

    @Test fun `a win fires SUCCESS exactly once`() {
        val t = tracker()
        val intents = t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, "me", isMe)
        assertEquals(listOf(HapticIntent.SUCCESS), intents)

        val again = t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, "me", isMe)
        assertTrue(again.isEmpty())
    }

    @Test fun `a loss fires FAILURE`() {
        val t = tracker()
        val intents = t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, "opponent", isMe)
        assertEquals(listOf(HapticIntent.FAILURE), intents)
    }

    @Test fun `a draw (null winner) fires FAILURE, matching iOS's own winnerId comparison`() {
        val t = tracker()
        val intents = t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, null, isMe)
        assertEquals(listOf(HapticIntent.FAILURE), intents)
    }

    @Test fun `a rematch's transition back to PLAYING resets the finish latch so a later result can fire again`() {
        val t = tracker()
        t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, "me", isMe)
        val duringRematch = t.intentsFor(MultiplayerRoomStatus.PLAYING, emptyList(), cards, false, null, isMe)
        assertTrue(HapticIntent.SUCCESS !in duringRematch)
        assertTrue(HapticIntent.FAILURE !in duringRematch)

        val secondFinish = t.intentsFor(MultiplayerRoomStatus.FINISHED, emptyList(), cards, false, "opponent", isMe)
        assertEquals(listOf(HapticIntent.FAILURE), secondFinish)
    }

    @Test fun `a resolved pair and a turn handover in the same snapshot both fire, in order`() {
        val t = tracker()
        val intents = t.intentsFor(MultiplayerRoomStatus.PLAYING, listOf(0, 1), cards, true, null, isMe)
        assertEquals(listOf(HapticIntent.MATCH, HapticIntent.SELECT), intents)
    }
}
