package com.ezequielbrrt.domemory.core.model

/**
 * The matching rules (spec 3.2). Pure model — no timers, no coroutines, no clock of
 * its own beyond the `now` it is handed, so the whole thing is unit-testable.
 *
 * `failedTries` is the app's error counter and feeds star ratings, the mistake budget,
 * the win screen and analytics.
 */
class MemoryGame(cards: List<Card>) {

    var cards: List<Card> = cards
        private set

    var failedTries: Int = 0
        private set

    /** Every card matched. The win must fire exactly once on the transition. */
    val isWon: Boolean get() = cards.all { it.isMatched }

    val matchedPairCount: Int get() = cards.count { it.isMatched } / 2

    /**
     * The index of the single face-up, unmatched card, or null when there are zero or
     * two of them. Two face-up unmatched cards means a mismatch is on screen awaiting
     * the flip-back timer.
     */
    private val indexOfTheOneAndOnlyFaceUpCard: Int?
        get() = cards.indices.filter { cards[it].isFaceUp && !cards[it].isMatched }
            .singleOrNull()

    fun choose(cardId: Int, now: Long): ChoiceOutcome {
        val chosenIndex = cards.indexOfFirst { it.id == cardId }
        if (chosenIndex < 0) return ChoiceOutcome.IGNORED

        val chosen = cards[chosenIndex]
        // A dead tap changes nothing — and must not buzz (spec 14.4).
        if (chosen.isFaceUp || chosen.isMatched) return ChoiceOutcome.IGNORED

        val potentialMatchIndex = indexOfTheOneAndOnlyFaceUpCard
        val working = cards.toMutableList()

        return if (potentialMatchIndex != null) {
            val other = working[potentialMatchIndex]
            working[chosenIndex] = chosen.copy(isFaceUp = true).startUsingBonusTime(now)

            if (other.itemId == chosen.itemId) {
                // Match: both stay face up, both marked matched.
                working[potentialMatchIndex] = other.copy(isMatched = true)
                    .stopUsingBonusTime(now)
                working[chosenIndex] = working[chosenIndex].copy(isMatched = true)
                    .stopUsingBonusTime(now)
                cards = working
                ChoiceOutcome.MATCH
            } else {
                // Mismatch: both stay face up until the flip-back timer or the next tap.
                failedTries += 1
                cards = working
                ChoiceOutcome.MISMATCH
            }
        } else {
            // Zero or two face-up unmatched cards: turn every other unmatched card down
            // and start this one. This is what makes a third tap resolve a mismatched
            // pair immediately instead of waiting out the flip-back timer.
            for (index in working.indices) {
                val card = working[index]
                if (index == chosenIndex || card.isMatched) continue
                if (card.isFaceUp) {
                    working[index] = card.copy(isFaceUp = false).stopUsingBonusTime(now)
                }
            }
            working[chosenIndex] = chosen.copy(isFaceUp = true).startUsingBonusTime(now)
            cards = working
            ChoiceOutcome.FLIPPED_UP
        }
    }

    /** The 2.0 s flip-back (spec 3.3): turn down every face-up unmatched card. */
    fun flipDownUnmatched(now: Long) {
        cards = cards.map { card ->
            if (card.isFaceUp && !card.isMatched) {
                card.copy(isFaceUp = false).stopUsingBonusTime(now)
            } else {
                card
            }
        }
    }

    /** Peek / reveal support (spec 7.6). Turns every unmatched card face up. */
    fun faceUpAllUnmatched(now: Long) {
        cards = cards.map { card ->
            if (!card.isMatched && !card.isFaceUp) {
                card.copy(isFaceUp = true).startUsingBonusTime(now)
            } else {
                card
            }
        }
    }

    /** Reveal-pair support (spec 7.6): turns up exactly the given unmatched, face-down
     * cards. Callers flip everything else down first with [flipDownUnmatched] — this
     * only ever adds face-up cards, it never removes one. */
    fun faceUp(ids: Set<Int>, now: Long) {
        cards = cards.map { card ->
            if (card.id in ids && !card.isMatched && !card.isFaceUp) {
                card.copy(isFaceUp = true).startUsingBonusTime(now)
            } else {
                card
            }
        }
    }

    /** The two card ids of one unmatched pair, or null when none is left. */
    fun findUnmatchedPair(): Pair<Int, Int>? {
        val unmatched = cards.filter { !it.isMatched }
        val group = unmatched.groupBy { it.itemId }.values.firstOrNull { it.size >= 2 }
            ?: return null
        return group[0].id to group[1].id
    }

    /**
     * Refunds failed matches after a "forgive mistakes" rescue (spec 7.5). Floors at
     * zero — this must never make `failedTries` negative and read as a bonus budget.
     */
    fun forgiveFailures(count: Int) {
        if (count <= 0) return
        failedTries = (failedTries - count).coerceAtLeast(0)
    }
}

/** What a tap actually did — drives haptics, which must stay silent on a dead tap. */
enum class ChoiceOutcome { IGNORED, FLIPPED_UP, MATCH, MISMATCH }
