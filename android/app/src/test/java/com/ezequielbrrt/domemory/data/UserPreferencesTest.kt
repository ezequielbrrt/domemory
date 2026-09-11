package com.ezequielbrrt.domemory.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.ui.theme.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Unit tests for the DataStore Preferences surface (spec 13.2). Backed by a real
 * [PreferenceDataStoreFactory]-built store pointed at a throwaway temp file per test —
 * no Robolectric, no Android `Context`, matching the rest of this test suite.
 */
class UserPreferencesTest {

    private fun newPrefs(): UserPreferences {
        val file = File.createTempFile("user_preferences_test", ".preferences_pb")
        file.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return UserPreferences(dataStore)
    }

    // -- hapticsEnabled: the named risk-register default -----------------------------

    @Test
    fun `hapticsEnabled defaults to true when unset`() = runTest {
        val prefs = newPrefs()
        assertTrue(prefs.hapticsEnabled.first())
    }

    @Test
    fun `hapticsEnabled persists an explicit false`() = runTest {
        val prefs = newPrefs()
        prefs.setHapticsEnabled(false)
        assertFalse(prefs.hapticsEnabled.first())
        prefs.setHapticsEnabled(true)
        assertTrue(prefs.hapticsEnabled.first())
    }

    // -- Other booleans default false, unlike haptics --------------------------------

    @Test
    fun `notificationsEnabled and one-shot flags default false`() = runTest {
        val prefs = newPrefs()
        assertFalse(prefs.notificationsEnabled.first())
        assertFalse(prefs.notificationPrimerShown.first())
        assertFalse(prefs.onboardingIntroShown.first())
        assertFalse(prefs.levelsIntroShown.first())
        assertFalse(prefs.hasOnboarded.first())
    }

    @Test
    fun `hasOnboarded is an explicit flag, independent of every other key`() = runTest {
        val prefs = newPrefs()
        prefs.setHasOnboarded(true)
        assertTrue(prefs.hasOnboarded.first())
        // Nothing else flips just because onboarding did.
        assertFalse(prefs.notificationsEnabled.first())
    }

    // -- Favourites -------------------------------------------------------------------

    @Test
    fun `favourites toggle and persist as a set`() = runTest {
        val prefs = newPrefs()
        assertEquals(emptySet<String>(), prefs.favoriteIds.first())

        prefs.toggleFavorite("42")
        prefs.setFavorite("7", true)
        assertEquals(setOf("42", "7"), prefs.favoriteIds.first())

        prefs.toggleFavorite("42")
        assertEquals(setOf("7"), prefs.favoriteIds.first())
    }

    // -- Custom memoramas: JSON round trip ---------------------------------------------

    @Test
    fun `custom memoramas round-trip through JSON`() = runTest {
        val prefs = newPrefs()
        val board = Board(
            id = "custom_abc123",
            name = "Road trip",
            items = listOf("🚗", "🚗", "🛣️", "🛣️"),
        )
        prefs.addCustomMemorama(board)

        val loaded = prefs.customMemoramas.first()
        assertEquals(1, loaded.size)
        assertEquals(board, loaded.single())
    }

    @Test
    fun `deleting a custom memorama also clears its stats`() = runTest {
        val prefs = newPrefs()
        val board = Board(id = "custom_delete_me", name = "Gone soon", items = listOf("a", "a", "b", "b"))
        prefs.addCustomMemorama(board)
        prefs.recordBoardPlayed(board.id)
        prefs.recordBoardWon(board.id)
        assertEquals(1, prefs.boardPlayedCount(board.id).first())

        prefs.removeCustomMemorama(board.id)

        assertTrue(prefs.customMemoramas.first().isEmpty())
        assertEquals(0, prefs.boardPlayedCount(board.id).first())
        assertEquals(0, prefs.boardWonCount(board.id).first())
    }

    @Test
    fun `custom memoramas drop entries with fewer than two items on decode`() = runTest {
        // Mirrors BoardDecoder's tolerance: a malformed entry is dropped, not a crash.
        val prefs = newPrefs()
        val tooFewItems = Board(id = "custom_bad", name = "Bad", items = listOf("only-one"))
        val valid = Board(id = "custom_good", name = "Good", items = listOf("a", "a"))
        prefs.setCustomMemoramas(listOf(tooFewItems, valid))

        assertEquals(listOf(valid), prefs.customMemoramas.first())
    }

    @Test
    fun `custom memoramas write of an empty list reads back empty`() = runTest {
        val prefs = newPrefs()
        prefs.setCustomMemoramas(emptyList())
        assertTrue(prefs.customMemoramas.first().isEmpty())
    }

    // -- Season catalog cache: opaque JSON passthrough ---------------------------------

    @Test
    fun `season catalog cache stores the raw payload untouched`() = runTest {
        val prefs = newPrefs()
        assertNull(prefs.seasonCatalogJson.first())

        val raw = """{"seasons":[{"id":"autumn-2026","priority":1}]}"""
        prefs.setSeasonCatalogJson(raw)
        assertEquals(raw, prefs.seasonCatalogJson.first())

        prefs.setSeasonCatalogJson(null)
        assertNull(prefs.seasonCatalogJson.first())
    }

    // -- Theme preference ---------------------------------------------------------------

    @Test
    fun `theme preference defaults to system and is tolerant of garbage`() = runTest {
        val prefs = newPrefs()
        assertEquals(ThemePreference.SYSTEM, prefs.themePreference.first())

        prefs.setThemePreference(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, prefs.themePreference.first())
    }

    // -- Per-board stats: typed accessor, not string concatenation at call sites -------

    @Test
    fun `per-board stats are independent per board id`() = runTest {
        val prefs = newPrefs()
        prefs.recordBoardPlayed("1")
        prefs.recordBoardPlayed("1")
        prefs.recordBoardWon("1")
        prefs.recordBoardPlayed("2")

        assertEquals(2, prefs.boardPlayedCount("1").first())
        assertEquals(1, prefs.boardWonCount("1").first())
        assertEquals(1, prefs.boardPlayedCount("2").first())
        assertEquals(0, prefs.boardWonCount("2").first())
    }

    // -- Profile aggregates --------------------------------------------------------------

    @Test
    fun `profile counters increment independently`() = runTest {
        val prefs = newPrefs()
        prefs.incrementTotalPlayed()
        prefs.incrementTotalPlayed()
        prefs.incrementTotalWon()
        prefs.incrementPerfectGames()
        prefs.incrementMultiplayerWins()

        assertEquals(2, prefs.totalPlayed.first())
        assertEquals(1, prefs.totalWon.first())
        assertEquals(1, prefs.perfectGames.first())
        assertEquals(1, prefs.multiplayerWins.first())
    }

    @Test
    fun `best remaining is null until recorded, then per-difficulty`() = runTest {
        val prefs = newPrefs()
        assertNull(prefs.bestRemaining(Difficulty.EASY).first())

        prefs.setBestRemaining(Difficulty.EASY, 5)
        prefs.setBestRemaining(Difficulty.HARD, 1)

        assertEquals(5, prefs.bestRemaining(Difficulty.EASY).first())
        assertEquals(1, prefs.bestRemaining(Difficulty.HARD).first())
        assertNull(prefs.bestRemaining(Difficulty.MEDIUM).first())
    }

    // -- Levels ---------------------------------------------------------------------------

    @Test
    fun `level stars are keyed per level, not shared`() = runTest {
        val prefs = newPrefs()
        prefs.setLevelStars(1, 3)
        prefs.setLevelStars(2, 1)

        assertEquals(3, prefs.levelStars(1).first())
        assertEquals(1, prefs.levelStars(2).first())
        assertEquals(0, prefs.levelStars(3).first())
    }

    @Test
    fun `lifetime stars and wallet balance accumulate`() = runTest {
        val prefs = newPrefs()
        prefs.addLifetimeStars(3)
        prefs.addLifetimeStars(2)
        assertEquals(5, prefs.levelsLifetimeStars.first())

        prefs.addToWallet(10)
        assertEquals(10, prefs.levelsWalletBalance.first())
    }

    @Test
    fun `wallet spend is atomic and refuses to go negative`() = runTest {
        val prefs = newPrefs()
        prefs.addToWallet(5)

        assertFalse(prefs.trySpendFromWallet(10))
        assertEquals(5, prefs.levelsWalletBalance.first())

        assertTrue(prefs.trySpendFromWallet(5))
        assertEquals(0, prefs.levelsWalletBalance.first())
    }

    @Test
    fun `daily lives remaining and last reset day persist`() = runTest {
        val prefs = newPrefs()
        prefs.setLevelsLivesRemaining(4)
        prefs.setLevelsLivesLastResetDay("20260911")

        assertEquals(4, prefs.levelsLivesRemaining.first())
        assertEquals("20260911", prefs.levelsLivesLastResetDay.first())
    }

    // -- Seasons: per-season-id typed accessor ---------------------------------------------

    @Test
    fun `season progress is independent per season id and per level`() = runTest {
        val prefs = newPrefs()
        prefs.setSeasonHighestUnlocked("autumn-2026", 4)
        prefs.setSeasonHighestUnlocked("winter-2026", 1)
        prefs.setSeasonStars("autumn-2026", 1, 3)

        assertEquals(4, prefs.seasonHighestUnlocked("autumn-2026").first())
        assertEquals(1, prefs.seasonHighestUnlocked("winter-2026").first())
        assertEquals(3, prefs.seasonStars("autumn-2026", 1).first())
        assertEquals(0, prefs.seasonStars("winter-2026", 1).first())
    }

    // -- Daily Challenge ----------------------------------------------------------------------

    @Test
    fun `daily challenge streak and day keys persist`() = runTest {
        val prefs = newPrefs()
        prefs.setDailyStreakCurrent(3)
        prefs.setDailyStreakLongest(9)
        prefs.setDailyLastCompletedDay("20260910")
        prefs.setDailyLastAttemptDay("20260911")

        assertEquals(3, prefs.dailyStreakCurrent.first())
        assertEquals(9, prefs.dailyStreakLongest.first())
        assertEquals("20260910", prefs.dailyLastCompletedDay.first())
        assertEquals("20260911", prefs.dailyLastAttemptDay.first())
    }

    // -- Purchases --------------------------------------------------------------------------

    @Test
    fun `purchase entitlement and rewarded expiry persist independently`() = runTest {
        val prefs = newPrefs()
        assertFalse(prefs.hasRemovedAdsPurchased.first())
        assertNull(prefs.rewardedRemoveAdsExpirationEpochMillis.first())

        prefs.setHasRemovedAdsPurchased(true)
        prefs.setRewardedRemoveAdsExpirationEpochMillis(1_800_000_000_000L)

        assertTrue(prefs.hasRemovedAdsPurchased.first())
        assertEquals(1_800_000_000_000L, prefs.rewardedRemoveAdsExpirationEpochMillis.first())

        prefs.setRewardedRemoveAdsExpirationEpochMillis(null)
        assertNull(prefs.rewardedRemoveAdsExpirationEpochMillis.first())
    }

    // -- Ads frequency capping ----------------------------------------------------------------

    @Test
    fun `interstitial completion counter increments and resets`() = runTest {
        val prefs = newPrefs()
        prefs.incrementInterstitialCompletionCount()
        prefs.incrementInterstitialCompletionCount()
        assertEquals(2, prefs.gameFinishedInterstitialCompletionCount.first())

        prefs.resetInterstitialCompletionCount()
        assertEquals(0, prefs.gameFinishedInterstitialCompletionCount.first())
    }
}
