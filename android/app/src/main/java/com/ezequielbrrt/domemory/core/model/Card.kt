package com.ezequielbrrt.domemory.core.model

/**
 * One card on the board (spec 3.1).
 *
 * [itemId] is the pair key: two cards match iff their [itemId] is equal. It is not
 * the same as [id], which is unique per card.
 *
 * The bonus-time fields drive the pie indicator (spec 3.4). The pie is a pure
 * "you're taking too long on this card" visual and is worth no points.
 */
data class Card(
    val id: Int,
    val itemId: Int,
    val content: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false,
    val bonusTimeLimit: Double = BONUS_TIME_LIMIT,
    val lastFaceUpTime: Long? = null,
    val pastFaceUpTime: Double = 0.0,
) {
    /** Seconds this card has spent face up and unmatched. */
    fun faceUpTime(now: Long): Double =
        lastFaceUpTime?.let { pastFaceUpTime + (now - it) / 1000.0 } ?: pastFaceUpTime

    fun bonusTimeRemaining(now: Long): Double =
        (bonusTimeLimit - faceUpTime(now)).coerceAtLeast(0.0)

    /** 1.0 when the window is untouched, 0.0 when it has expired. */
    fun bonusRemainingFraction(now: Long): Float =
        if (bonusTimeLimit > 0) (bonusTimeRemaining(now) / bonusTimeLimit).toFloat() else 0f

    fun startUsingBonusTime(now: Long): Card =
        if (isMatched || lastFaceUpTime != null) this else copy(lastFaceUpTime = now)

    fun stopUsingBonusTime(now: Long): Card =
        if (lastFaceUpTime == null) this
        else copy(pastFaceUpTime = faceUpTime(now), lastFaceUpTime = null)

    companion object {
        const val BONUS_TIME_LIMIT = 2.0
    }
}
