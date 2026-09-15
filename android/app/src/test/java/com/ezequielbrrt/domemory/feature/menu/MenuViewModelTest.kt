package com.ezequielbrrt.domemory.feature.menu

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.CatalogStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `MenuViewModel` combines the catalog repository (spec 13.1/13.3) with `UserPreferences`
 * (favourites, difficulty, custom memoramas). Uses a real [UserPreferences] over a
 * throwaway file, matching `UserPreferencesTest`, and a real [BoardCatalogRepository]
 * with a fake remote source, matching `BoardCatalogRepositoryTest` — no mocking
 * framework, per `android/CLAUDE.md`'s test conventions.
 *
 * Every mutating call runs on the [TestScope] passed as `scope` (the same pattern
 * `GameViewModelTest` uses for `GameViewModel`), so [advanceUntilIdle] deterministically
 * drains it before assertions — no reliance on `Dispatchers.setMain` or on winning a
 * race against `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModelTest {

    private fun board(id: String, difficulty: Difficulty) = Board(
        id = id,
        name = id,
        difficulty = difficulty.key,
        items = listOf("A", "B", "C"),
    )

    private val catalogBoards = listOf(
        board("easy_1", Difficulty.EASY),
        board("medium_1", Difficulty.MEDIUM),
        board("medium_2", Difficulty.MEDIUM),
    )

    private fun TestScope.newPrefs(): UserPreferences {
        val file = File.createTempFile("menu_view_model_test", ".preferences_pb")
        file.deleteOnExit()
        // Keep DataStore's work on runTest's virtual dispatcher. Its production default is
        // Dispatchers.IO, which would leave MenuViewModel's initial read pending after
        // advanceUntilIdle and turn these unit tests into a race with real disk I/O.
        return UserPreferences(PreferenceDataStoreFactory.create(scope = this, produceFile = { file }))
    }

    /** Wires custom boards to the *real* prefs flow, matching production `AppContainer`. */
    private fun TestScope.newViewModel(prefs: UserPreferences = newPrefs()): MenuViewModel {
        val catalog = BoardCatalogRepository(
            remote = { catalogBoards },
            customBoardsSource = { prefs.customMemoramas.first() },
        )
        return MenuViewModel(catalog, prefs, scope = this)
    }

    @Test
    fun `starts on the Levels tab and loads catalog boards filtered to the default difficulty`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(MenuTab.LEVELS, vm.state.value.selectedTab)
        assertEquals(CatalogStatus.LOADED, vm.state.value.catalogStatus)
        assertEquals(Difficulty.MEDIUM, vm.state.value.difficulty)
        assertEquals(listOf("medium_1", "medium_2"), vm.state.value.allBoards.map { it.id }.sorted())
        vm.stop()
    }

    @Test
    fun `selecting a tab updates state synchronously`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.selectTab(MenuTab.ALL)
        assertEquals(MenuTab.ALL, vm.state.value.selectedTab)
        vm.selectTab(MenuTab.MINE)
        assertEquals(MenuTab.MINE, vm.state.value.selectedTab)
        vm.stop()
    }

    @Test
    fun `changing difficulty re-filters the All tab and persists`() = runTest {
        val prefs = newPrefs()
        val vm = newViewModel(prefs)
        advanceUntilIdle()

        vm.setDifficulty(Difficulty.EASY)
        advanceUntilIdle()

        assertEquals(Difficulty.EASY, vm.state.value.difficulty)
        assertEquals(listOf("easy_1"), vm.state.value.allBoards.map { it.id })
        assertEquals(Difficulty.EASY, prefs.playerDifficulty.first())
        vm.stop()
    }

    @Test
    fun `catalog card stats follow the stored played and won counters`() = runTest {
        val prefs = newPrefs()
        val vm = newViewModel(prefs)
        advanceUntilIdle()

        prefs.recordBoardPlayed("medium_1")
        prefs.recordBoardPlayed("medium_1")
        prefs.recordBoardWon("medium_1")
        advanceUntilIdle()

        assertEquals(BoardStats(played = 2, won = 1), vm.state.value.boardStats["medium_1"])
        vm.stop()
    }

    @Test
    fun `setting the same difficulty again is a no-op`() = runTest {
        val prefs = newPrefs()
        val vm = newViewModel(prefs)
        advanceUntilIdle()

        vm.setDifficulty(Difficulty.MEDIUM) // already the default
        advanceUntilIdle()
        assertEquals(Difficulty.MEDIUM, vm.state.value.difficulty)
        vm.stop()
    }

    @Test
    fun `My memoramas ignores difficulty filtering`() = runTest {
        val prefs = newPrefs()
        prefs.addCustomMemorama(board("custom_1", Difficulty.HARD))
        val vm = newViewModel(prefs)
        advanceUntilIdle()

        // Default difficulty is medium, which "custom_1" (hard) would fail if filtered.
        assertEquals(listOf("custom_1"), vm.state.value.myBoards.map { it.id })
        vm.stop()
    }

    @Test
    fun `favourites sort to the top of a tab`() = runTest {
        val prefs = newPrefs()
        val vm = newViewModel(prefs)
        advanceUntilIdle()

        vm.toggleFavorite("medium_2")
        advanceUntilIdle()

        assertEquals("medium_2", vm.state.value.allBoards.first().id)
        assertTrue(vm.state.value.isFavorite("medium_2"))
        assertFalse(vm.state.value.isFavorite("medium_1"))
        vm.stop()
    }

    @Test
    fun `toggling a favourite flips it`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.toggleFavorite("medium_1")
        advanceUntilIdle()
        assertTrue(vm.state.value.isFavorite("medium_1"))

        vm.toggleFavorite("medium_1")
        advanceUntilIdle()
        assertFalse(vm.state.value.isFavorite("medium_1"))
        vm.stop()
    }

    @Test
    fun `deleting a custom memorama removes it from My memoramas and clears its stats`() = runTest {
        val prefs = newPrefs()
        prefs.addCustomMemorama(board("custom_delete_me", Difficulty.EASY))
        prefs.recordBoardPlayed("custom_delete_me")
        val vm = newViewModel(prefs)
        advanceUntilIdle()
        assertEquals(listOf("custom_delete_me"), vm.state.value.myBoards.map { it.id })

        vm.deleteCustomMemorama("custom_delete_me")
        advanceUntilIdle()

        assertTrue(vm.state.value.myBoards.isEmpty())
        assertEquals(0, prefs.boardPlayedCount("custom_delete_me").first())
        vm.stop()
    }

    @Test
    fun `onCustomMemoramaChanged picks up a memorama added elsewhere`() = runTest {
        val prefs = newPrefs()
        val vm = newViewModel(prefs)
        advanceUntilIdle()
        assertTrue(vm.state.value.myBoards.isEmpty())

        // Simulates the create screen calling prefs directly, then notifying the menu.
        prefs.addCustomMemorama(board("custom_new", Difficulty.EASY))
        vm.onCustomMemoramaChanged()
        advanceUntilIdle()

        assertEquals(listOf("custom_new"), vm.state.value.myBoards.map { it.id })
        vm.stop()
    }

    @Test
    fun `board lookup delegates to the catalog repository`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals("medium_1", vm.board("medium_1")?.id)
        assertNull(vm.board("missing"))
        vm.stop()
    }
}
