package com.ezequielbrrt.domemory.services.stats

import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.services.daily.DailyChallengeService
import kotlinx.coroutines.flow.first

/** Lifetime aggregate stats, mirroring iOS's `ProfileStats`. */
data class ProfileStats(
    val totalPlayed: Int = 0,
    val totalWon: Int = 0,
    val perfectGames: Int = 0,
    val multiplayerWins: Int = 0,
    val longestStreak: Int = 0,
) {
    val winRate: Float get() = if (totalPlayed > 0) totalWon.toFloat() / totalPlayed else 0f
}

/**
 * One derived badge. [titleRes]/[detailRes] are string resources rather than raw text —
 * both the win and streak tiers share one `%d`-format resource
 * ([android.text.format]-style), passed as [formatArg]; the perfect-game and multiplayer
 * badges take neither, so [formatArg] stays null for them. [emoji] stands in for iOS's SF
 * Symbol (`symbol`) — this app has no icon library and none should be added for this (see
 * `ANDROID_PLAN.md`'s Phase 8 note); the locked visual state is rendered as 🔒 by the
 * composable, not stored here, mirroring iOS's own `achievement.isUnlocked ? achievement
 * .symbol : "lock.fill"` swap at the view layer.
 */
data class Achievement(
    val id: String,
    val titleRes: Int,
    val detailRes: Int,
    val formatArg: Int? = null,
    val emoji: String,
    val isUnlocked: Boolean,
    /** 0f..1f progress toward unlocking, capped at 1f. */
    val progress: Float,
)

/**
 * Lifetime aggregates and derived achievements, backed by [UserPreferences] (spec 13.2).
 * Mirrors iOS's `ProfileStatsService`, minus the singleton shape — this is constructed once
 * in `AppContainer`, like every other service here (decision D4).
 *
 * [UserPreferences] has no synchronous read (unlike iOS's `UserDefaults`), so [stats] is a
 * suspend snapshot rather than a live value — `AchievementsViewModel` takes one snapshot per
 * screen visit, the same "explicit pull" convention `DailyChallengeService`'s own readers
 * already use, rather than a continuously-collected combine of five flows.
 */
class ProfileStatsService(
    private val prefs: UserPreferences,
    private val dailyChallenge: DailyChallengeService,
) {
    suspend fun stats(): ProfileStats = ProfileStats(
        totalPlayed = prefs.totalPlayed.first(),
        totalWon = prefs.totalWon.first(),
        perfectGames = prefs.perfectGames.first(),
        multiplayerWins = prefs.multiplayerWins.first(),
        longestStreak = dailyChallenge.longestStreak(),
    )

    suspend fun achievements(): List<Achievement> = achievements(stats())

    companion object {
        val WIN_TIERS = listOf(10, 50, 100)
        val STREAK_TIERS = listOf(7, 30)

        /**
         * The pure derivation — given a [ProfileStats] snapshot, no [UserPreferences] or
         * coroutine involved, so it is exactly as unit-testable as iOS's own
         * `achievements()` (which reads `self.stats`, a synchronous `UserDefaults`
         * snapshot, and does the rest with no I/O either). Matches iOS's ordering and
         * thresholds exactly: three win tiers, two streak tiers, one perfect-game badge,
         * one multiplayer badge.
         */
        fun achievements(stats: ProfileStats): List<Achievement> {
            val winTiers = WIN_TIERS.map { threshold ->
                Achievement(
                    id = "wins_$threshold",
                    titleRes = R.string.ach_wins_title_format,
                    detailRes = R.string.ach_wins_detail_format,
                    formatArg = threshold,
                    emoji = "🏅",
                    isUnlocked = stats.totalWon >= threshold,
                    progress = (stats.totalWon.toFloat() / threshold).coerceIn(0f, 1f),
                )
            }
            val streakTiers = STREAK_TIERS.map { threshold ->
                Achievement(
                    id = "streak_$threshold",
                    titleRes = R.string.ach_streak_title_format,
                    detailRes = R.string.ach_streak_detail_format,
                    formatArg = threshold,
                    emoji = "🔥",
                    isUnlocked = stats.longestStreak >= threshold,
                    progress = (stats.longestStreak.toFloat() / threshold).coerceIn(0f, 1f),
                )
            }
            val perfect = Achievement(
                id = "perfect",
                titleRes = R.string.ach_perfect_title,
                detailRes = R.string.ach_perfect_detail,
                emoji = "✨",
                isUnlocked = stats.perfectGames > 0,
                progress = if (stats.perfectGames > 0) 1f else 0f,
            )
            val multiplayer = Achievement(
                id = "multiplayer",
                titleRes = R.string.ach_multiplayer_title,
                detailRes = R.string.ach_multiplayer_detail,
                emoji = "🏆",
                isUnlocked = stats.multiplayerWins > 0,
                progress = if (stats.multiplayerWins > 0) 1f else 0f,
            )
            return winTiers + streakTiers + listOf(perfect, multiplayer)
        }
    }
}
