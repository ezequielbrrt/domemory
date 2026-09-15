package com.ezequielbrrt.domemory.services.stats

import com.ezequielbrrt.domemory.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure achievement derivation (`ProfileStatsService.achievements(ProfileStats)`)
 * against iOS's own `ProfileStatsService.achievements()`: three win tiers (10/50/100), two
 * streak tiers (7/30), one perfect-game badge, one multiplayer badge — same
 * `isUnlocked`/`progress` rules, no `UserPreferences`/DataStore/coroutine involved.
 */
class ProfileStatsServiceTest {

    @Test
    fun `zero stats produce seven locked achievements with zero progress except the two count-based ones`() {
        val achievements = ProfileStatsService.achievements(ProfileStats())
        assertEquals(7, achievements.size)
        assertTrue(achievements.none { it.isUnlocked })
        assertEquals(listOf("wins_10", "wins_50", "wins_100", "streak_7", "streak_30", "perfect", "multiplayer"), achievements.map { it.id })
        achievements.forEach { assertEquals(0f, it.progress, 0.001f) }
    }

    @Test
    fun `win tier unlocks exactly at its threshold, not before`() {
        val justUnder = ProfileStatsService.achievements(ProfileStats(totalWon = 9))
        val atThreshold = ProfileStatsService.achievements(ProfileStats(totalWon = 10))
        assertFalse(justUnder.first { it.id == "wins_10" }.isUnlocked)
        assertTrue(atThreshold.first { it.id == "wins_10" }.isUnlocked)
    }

    @Test
    fun `win tier progress is a fraction of its own threshold, capped at 1`() {
        val achievements = ProfileStatsService.achievements(ProfileStats(totalWon = 25))
        assertEquals(1f, achievements.first { it.id == "wins_10" }.progress, 0.001f)
        assertEquals(0.5f, achievements.first { it.id == "wins_50" }.progress, 0.001f)
        assertEquals(0.25f, achievements.first { it.id == "wins_100" }.progress, 0.001f)
    }

    @Test
    fun `a win count far past a threshold never reports progress above 1`() {
        val achievement = ProfileStatsService.achievements(ProfileStats(totalWon = 999)).first { it.id == "wins_10" }
        assertTrue(achievement.isUnlocked)
        assertEquals(1f, achievement.progress, 0.001f)
    }

    @Test
    fun `streak tiers mirror the win tiers but read longestStreak`() {
        val achievements = ProfileStatsService.achievements(ProfileStats(longestStreak = 7))
        val sevenDay = achievements.first { it.id == "streak_7" }
        val thirtyDay = achievements.first { it.id == "streak_30" }
        assertTrue(sevenDay.isUnlocked)
        assertEquals(1f, sevenDay.progress, 0.001f)
        assertFalse(thirtyDay.isUnlocked)
        assertEquals(7f / 30f, thirtyDay.progress, 0.001f)
    }

    @Test
    fun `perfect-game badge unlocks on any count above zero, not a threshold`() {
        val zero = ProfileStatsService.achievements(ProfileStats(perfectGames = 0)).first { it.id == "perfect" }
        val one = ProfileStatsService.achievements(ProfileStats(perfectGames = 1)).first { it.id == "perfect" }
        val many = ProfileStatsService.achievements(ProfileStats(perfectGames = 40)).first { it.id == "perfect" }
        assertFalse(zero.isUnlocked); assertEquals(0f, zero.progress, 0.001f)
        assertTrue(one.isUnlocked); assertEquals(1f, one.progress, 0.001f)
        assertTrue(many.isUnlocked); assertEquals(1f, many.progress, 0.001f)
    }

    @Test
    fun `multiplayer badge unlocks on any win above zero, not a threshold`() {
        val zero = ProfileStatsService.achievements(ProfileStats(multiplayerWins = 0)).first { it.id == "multiplayer" }
        val one = ProfileStatsService.achievements(ProfileStats(multiplayerWins = 1)).first { it.id == "multiplayer" }
        assertFalse(zero.isUnlocked)
        assertTrue(one.isUnlocked)
    }

    @Test
    fun `win rate is zero for no games played, never a divide-by-zero crash`() {
        assertEquals(0f, ProfileStats(totalPlayed = 0, totalWon = 0).winRate, 0.001f)
    }

    @Test
    fun `win rate is the ratio of won to played`() {
        assertEquals(0.5f, ProfileStats(totalPlayed = 10, totalWon = 5).winRate, 0.001f)
    }

    @Test
    fun `format-taking achievements carry their threshold as formatArg, static ones carry null`() {
        val achievements = ProfileStatsService.achievements(ProfileStats())
        assertEquals(10, achievements.first { it.id == "wins_10" }.formatArg)
        assertEquals(7, achievements.first { it.id == "streak_7" }.formatArg)
        assertEquals(null, achievements.first { it.id == "perfect" }.formatArg)
        assertEquals(null, achievements.first { it.id == "multiplayer" }.formatArg)
    }

    @Test
    fun `every achievement resolves to a real string resource id`() {
        // A regression guard against a typo'd R.string reference — every id here must be a
        // non-zero resource id (0 is not a valid Android resource id).
        ProfileStatsService.achievements(ProfileStats()).forEach {
            assertTrue(it.titleRes != 0)
            assertTrue(it.detailRes != 0)
        }
        assertTrue(R.string.ach_wins_title_format != 0)
    }
}
