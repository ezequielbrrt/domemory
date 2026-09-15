package com.ezequielbrrt.domemory.feature.game

/** What one slot of the win screen's three-star row should show right now. */
enum class WinStarSlot {
    /** Dim outline: either never earned, or earned but still waiting its turn to pop. */
    DIM,
    /** Earned and mid pop-in — the slot is running the `star-pop` Lottie. */
    POPPING,
    /** Earned and settled: the static filled star. */
    FILLED,
}

/**
 * Port of iOS `WinModal.starView(for:)`'s decision.
 *
 * A slot beyond [starsEarned] never earned a star, so there is nothing to animate. An
 * earned slot starts dim, waiting its turn; once [startedCount] reaches it, it pops, and
 * once its own completion callback raises [completedCount] past it, it settles to filled.
 * Reduce-motion players — and any slot that already finished — skip straight to filled.
 *
 * [startedCount] and [completedCount] are kept separate on purpose: a star can be
 * *playing* (started, not yet completed) while the next one is still waiting its turn,
 * and that gap is what makes the row read as a staggered sequence rather than one
 * simultaneous pop.
 */
fun winStarSlot(
    index: Int,
    starsEarned: Int,
    startedCount: Int,
    completedCount: Int,
    reduceMotion: Boolean,
): WinStarSlot = when {
    index >= starsEarned -> WinStarSlot.DIM
    reduceMotion || index < completedCount -> WinStarSlot.FILLED
    index < startedCount -> WinStarSlot.POPPING
    else -> WinStarSlot.DIM
}

/** iOS paces each earned star's go-ahead 180 ms after the previous one. */
const val WIN_STAR_STAGGER_MILLIS = 180L
