package com.ezequielbrrt.domemory.services.levels

/**
 * In-game assists bought with stars during Levels play (spec 7.6). Costs live here and
 * nowhere else so the economy can be retuned in one place — mirrors iOS's
 * `LevelPowerUp.swift` exactly, including the tuning constants in the companion object,
 * which also cover the lose-screen purchases (spec 7.7) that are not power-ups
 * themselves but share the same star economy.
 */
enum class LevelPowerUp(val cost: Int) {
    /** `timeRemaining += EXTRA_TIME_SECONDS`. */
    EXTRA_TIME(cost = 3),

    /** Flips every unmatched card face up for [PEEK_DURATION_SECONDS]. */
    PEEK(cost = 4),

    /** Holds the countdown for [FREEZE_DURATION_SECONDS], via a `frozenUntil` deadline. */
    FREEZE(cost = 5),

    /** Turns up one unmatched matching pair; everything else flips back down. */
    REVEAL_PAIR(cost = 6),
    ;

    companion object {
        /** Seconds added to the clock by [EXTRA_TIME]. */
        const val EXTRA_TIME_SECONDS = 15.0

        /** How long [PEEK] keeps the board face up. */
        const val PEEK_DURATION_SECONDS = 1.5

        /** How long [FREEZE] holds the countdown. */
        const val FREEZE_DURATION_SECONDS = 10.0

        /** Star price of one extra life, bought from the out-of-lives / lose-screen prompt. */
        const val LIFE_COST = 10

        /** Star price of forgiving mistakes after busting the budget. */
        const val FORGIVE_COST = 8

        /** How many mistakes a rescue refunds, from stars (or, later, a rewarded ad). */
        const val FORGIVE_AMOUNT = 3

        /**
         * Seconds the board is guaranteed to have left after a forgive rescue. Without a
         * floor the rescue is worthless when the budget runs out late: at 0 seconds the
         * countdown can't restart at all, and at 3 seconds the player loses again before
         * they can use the refunded mistakes.
         */
        const val FORGIVE_MINIMUM_SECONDS = 15.0

        /** Star price of skipping a level you're stuck on. */
        const val SKIP_LEVEL_COST = 15
    }
}
