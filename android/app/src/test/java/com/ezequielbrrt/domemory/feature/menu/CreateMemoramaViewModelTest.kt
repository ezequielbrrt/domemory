package com.ezequielbrrt.domemory.feature.menu

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezequielbrrt.domemory.core.model.Board
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreateMemoramaViewModelTest {

    private fun newPrefs(): UserPreferences {
        val file = File.createTempFile("create_memorama_test", ".preferences_pb")
        file.deleteOnExit()
        return UserPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `cannot save below two items`() = runTest {
        val vm = CreateMemoramaViewModel(newPrefs())
        assertFalse(vm.state.value.canSave)

        vm.updateEmojiInput("😀")
        vm.addEmoji()
        assertEquals(listOf("😀"), vm.state.value.items)
        assertFalse(vm.state.value.canSave)

        vm.updateEmojiInput("😀")
        vm.addEmoji()
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `emoji are added one at a time and clear the input`() = runTest {
        val vm = CreateMemoramaViewModel(newPrefs())
        vm.updateEmojiInput("🚗")
        vm.addEmoji()
        assertEquals(listOf("🚗"), vm.state.value.items)
        assertEquals("", vm.state.value.emojiInput)

        vm.updateEmojiInput("🚕")
        vm.addEmoji()
        assertEquals(listOf("🚗", "🚕"), vm.state.value.items)
    }

    @Test
    fun `adding a blank emoji is a no-op`() = runTest {
        val vm = CreateMemoramaViewModel(newPrefs())
        vm.updateEmojiInput("   ")
        vm.addEmoji()
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `removing an emoji drops it by index`() = runTest {
        val vm = CreateMemoramaViewModel(newPrefs())
        listOf("🚗", "🚕", "🚙").forEach {
            vm.updateEmojiInput(it)
            vm.addEmoji()
        }
        vm.removeEmoji(1)
        assertEquals(listOf("🚗", "🚙"), vm.state.value.items)
    }

    @Test
    fun `save writes a custom-prefixed board and reports success`() = runTest {
        val prefs = newPrefs()
        val vm = CreateMemoramaViewModel(prefs)
        vm.updateName("Road trip")
        listOf("🚗", "🛣️").forEach {
            vm.updateEmojiInput(it)
            vm.addEmoji()
        }

        assertTrue(vm.save())
        assertFalse(vm.state.value.rejected)

        val saved = prefs.customMemoramas.first().single()
        assertTrue(saved.id.startsWith(Board.CUSTOM_ID_PREFIX))
        assertEquals("Road trip", saved.name)
        assertEquals(listOf("🚗", "🛣️"), saved.items)
    }

    @Test
    fun `save below two items is refused and does not write anything`() = runTest {
        val prefs = newPrefs()
        val vm = CreateMemoramaViewModel(prefs)
        vm.updateName("Too few")
        vm.updateEmojiInput("🚗")
        vm.addEmoji()

        assertFalse(vm.save())
        assertTrue(prefs.customMemoramas.first().isEmpty())
    }
}
