package com.ezequielbrrt.domemory.core.model

/**
 * Spec 4. Note that `MEDIUM` and `HARD` share a time limit — difficulty is expressed
 * through board size rather than the clock alone — and that `VERY_HARD` gets *more*
 * time than `HARD` because its boards are larger.
 *
 * Two different "current difficulty" values exist in the app and are not
 * interchangeable: the **player's setting** drives the clock and the pie, while the
 * **board's own difficulty** drives analytics, interstitial frequency, the win-screen
 * label and lifetime stat recording. See [Board.resolvedDifficulty].
 */
enum class Difficulty(
    val id: Int,
    val key: String,
    val timeLimitSeconds: Double,
    val interstitialEveryNWins: Int,
    val showsPie: Boolean,
) {
    EASY(0, "easy", 110.0, 3, false),
    MEDIUM(1, "medium", 60.0, 3, false),
    HARD(2, "hard", 60.0, 2, true),
    VERY_HARD(3, "veryHard", 70.0, 2, true);

    companion object {
        /** Tolerant of casing and of `very_hard` / `very hard` spellings. */
        fun parse(raw: String?): Difficulty? {
            val normalized = raw?.trim()?.lowercase()?.replace("_", "")?.replace(" ", "")
                ?: return null
            return entries.firstOrNull { it.key.lowercase() == normalized }
        }

        fun parseOrDefault(raw: String?, fallback: Difficulty = MEDIUM): Difficulty =
            parse(raw) ?: fallback
    }
}
