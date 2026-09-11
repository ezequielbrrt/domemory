package com.ezequielbrrt.domemory.data

import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.core.model.Difficulty
import com.ezequielbrrt.domemory.data.repository.BoardCatalogRepository
import com.ezequielbrrt.domemory.data.repository.BoardCatalogSource
import com.ezequielbrrt.domemory.data.repository.CatalogStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCatalogRepositoryTest {

    private fun board(id: String, difficulty: Difficulty?) = Board(
        id = id,
        name = id,
        difficulty = difficulty?.key,
        items = listOf("A", "B", "C"),
    )

    private val remoteBoards = listOf(
        board("1", Difficulty.EASY),
        board("2", Difficulty.MEDIUM),
        board("3", Difficulty.MEDIUM),
        board("4", Difficulty.VERY_HARD),
    )

    @Test
    fun `a successful load filters to the player difficulty`() = runTest {
        val repo = BoardCatalogRepository(remote = { remoteBoards })
        assertEquals(CatalogStatus.LOADED, repo.refresh())
        assertEquals(listOf("2", "3"), repo.boards(Difficulty.MEDIUM).map { it.id }.sorted())
        assertEquals(listOf("1"), repo.boards(Difficulty.EASY).map { it.id })
        assertTrue(repo.boards(Difficulty.HARD).isEmpty())
    }

    @Test
    fun `catalog failure is silent and leaves the app playable`() = runTest {
        // The source's contract is to return empty rather than throw; the repository's
        // job is to make sure that still leaves boards on screen.
        val repo = BoardCatalogRepository(remote = { emptyList() })
        assertEquals(CatalogStatus.UNAVAILABLE, repo.refresh())
        assertTrue(repo.boards(Difficulty.EASY).isNotEmpty())
        assertTrue(repo.boards(Difficulty.VERY_HARD).isNotEmpty())
    }

    @Test
    fun `boards with an unparseable difficulty are filtered out, not crashed on`() = runTest {
        val repo = BoardCatalogRepository(
            remote = { remoteBoards + board("99", null) + board("98", null) },
        )
        repo.refresh()
        val everything = Difficulty.entries.flatMap { repo.boards(it) }.map { it.id }
        assertTrue("99" !in everything)
    }

    @Test
    fun `custom boards ignore difficulty filtering`() = runTest {
        val custom = board("custom_1", Difficulty.HARD)
        val repo = BoardCatalogRepository(remote = { remoteBoards })
        repo.refresh()
        repo.setCustomBoards(listOf(custom))

        Difficulty.entries.forEach { difficulty ->
            assertTrue(
                "missing from $difficulty",
                repo.boards(difficulty).any { it.id == "custom_1" },
            )
        }
        // ...but they stay out of the catalog-only view.
        assertTrue(repo.catalogBoards(Difficulty.HARD).none { it.id == "custom_1" })
    }

    @Test
    fun `only custom-prefixed boards are accepted as custom`() = runTest {
        val repo = BoardCatalogRepository(remote = { remoteBoards })
        repo.refresh()
        repo.setCustomBoards(listOf(board("not_custom", Difficulty.EASY)))
        assertTrue(repo.customBoards().isEmpty())
    }

    @Test
    fun `lookup finds catalog and custom boards, and misses cleanly`() = runTest {
        val repo = BoardCatalogRepository(remote = { remoteBoards })
        repo.refresh()
        repo.setCustomBoards(listOf(board("custom_7", Difficulty.EASY)))

        assertEquals("2", repo.board("2")?.id)
        assertEquals("custom_7", repo.board("custom_7")?.id)
        assertNull(repo.board("nope"))
    }

    @Test
    fun `a failing source does not propagate its exception`() = runTest {
        val throwing = BoardCatalogSource { error("network down") }
        val repo = BoardCatalogRepository(
            remote = { runCatching { throwing.load() }.getOrElse { emptyList() } },
        )
        assertEquals(CatalogStatus.UNAVAILABLE, repo.refresh())
    }
}
