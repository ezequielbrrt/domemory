package com.ezequielbrrt.domemory.feature.levels

/**
 * A one-shot effect the lives row plays over a single heart — port of iOS's
 * `LivesRowEffect` (`ios/.../Modules/SharedModules/Views/LivesRow.swift`).
 *
 * Android differs from iOS in *where* a loss animates: iOS breaks the heart on its lose
 * modal, whose hearts show the post-loss count. Android's lose overlay shows no hearts and
 * defers spending the life until the player leaves (see `GameViewModel.commitLossIfNeeded`),
 * so the map header is the first place the drop is visible, and it breaks the heart there.
 */
sealed interface LivesEffect {
    val slot: Int

    /** The first now-empty heart splits and falls. */
    data class Lost(override val slot: Int) : LivesEffect

    /** The last now-filled heart bursts back in. */
    data class Gained(override val slot: Int) : LivesEffect
}

/**
 * The effect a change in the remaining count deserves, if any. A day-reset refill from 0
 * to 4 animates a single heart rather than four — one burst is a nicety, four is a
 * fireworks show.
 */
fun livesEffect(previous: Int, current: Int): LivesEffect? = when {
    current < previous -> LivesEffect.Lost(current)
    current > previous -> LivesEffect.Gained(current - 1)
    else -> null
}

/** Whether the star chip should sparkle: only a credit, never a spend. */
fun starsCredited(previous: Int, current: Int): Boolean = current > previous
