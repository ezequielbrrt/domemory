package com.ezequielbrrt.domemory.services.levels

/**
 * Star rating for a cleared level (spec 7.2). Awarded on a **win only**.
 *
 * The storage rules that go with this live in the progress store: a level's rating is
 * a high-water mark, and only the *improvement* is credited to the spendable wallet —
 * without that, replaying a cleared level is free money.
 */
object Stars {
    const val MAX = 3
    const val THREE_STAR_TIME_FRACTION = 0.50
    const val THREE_STAR_MAX_FAILURES = 1
    const val TWO_STAR_TIME_FRACTION = 0.25

    fun award(timeRemaining: Double, totalTime: Double, failedTries: Int): Int {
        if (totalTime <= 0.0) return 1
        val fraction = timeRemaining / totalTime
        return when {
            fraction >= THREE_STAR_TIME_FRACTION && failedTries <= THREE_STAR_MAX_FAILURES -> 3
            fraction >= TWO_STAR_TIME_FRACTION -> 2
            else -> 1
        }
    }
}
