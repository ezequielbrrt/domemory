package com.ezequielbrrt.domemory.ui.lottie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shared clips under `assets/lottie/` at the repository root are hand-authored JSON.
 * Lottie's own decoder only runs on a device, so this checks the structural contract both
 * apps rely on — canvas, frame rate, a positive length, and a `tint` shape in every clip
 * `BundledLottie(tint = ...)` is handed — the same way iOS's `LottieAssetsTests` does with
 * the real parser.
 */
class LottieAssetsTest {

    private val root = File("../../assets/lottie")

    private fun clip(name: String): JSONObject {
        val file = File(root, "$name.json")
        assertTrue("$name.json is missing from the shared assets directory", file.isFile)
        return JSONObject(file.readText())
    }

    @Test fun `every clip is a 60 fps Lottie document with a positive length`() {
        for (name in ALL_CLIPS) {
            val json = clip(name)
            assertEquals(name, 60, json.getInt("fr"))
            assertTrue(name, json.getInt("op") > 0)
            assertTrue(name, json.getJSONArray("layers").length() > 0)
        }
    }

    @Test fun `every tinted clip carries the tint shape name the wrapper targets`() {
        for (name in TINTED_CLIPS) {
            assertTrue("$name.json has no shape named $TINT_SHAPE_NAME", clip(name).toString().contains("\"nm\":\"$TINT_SHAPE_NAME\""))
        }
    }

    @Test fun `transient overlays and lose heroes stay at or under a second`() {
        for (name in TINTED_CLIPS) {
            val json = clip(name)
            assertTrue(name, json.getInt("op") <= json.getInt("fr"))
        }
    }

    private companion object {
        val TINTED_CLIPS = listOf("clock-crack", "x-shake", "heart-break", "heart-refill", "freeze-thaw", "star-sparkle")
        val ALL_CLIPS = TINTED_CLIPS + listOf("confetti-burst", "star-pop")
    }
}
