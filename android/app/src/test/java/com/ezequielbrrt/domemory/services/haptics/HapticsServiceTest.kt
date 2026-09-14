package com.ezequielbrrt.domemory.services.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [HapticsService.feedbackFor] and [HapticsService.vibrationSpec] — the two pure
 * mapping functions — the same way `ios/DoMemory/DoMemory/DoMemoryTests/
 * HapticsServiceTests.swift` pins iOS's `HapticsService.feedback(for:)` without a real
 * Taptic Engine. Neither function touches `android.os.Vibrator`, so this needs no
 * Robolectric or instrumentation, matching every other test in this suite.
 */
class HapticsServiceTest {

    @Test fun `every intent maps to its documented feedback`() {
        assertEquals(Feedback.Impact(ImpactStyle.LIGHT), HapticsService.feedbackFor(HapticIntent.TAP))
        assertEquals(Feedback.Selection, HapticsService.feedbackFor(HapticIntent.SELECT))
        assertEquals(Feedback.Impact(ImpactStyle.SOFT), HapticsService.feedbackFor(HapticIntent.CARD_FLIP))
        assertEquals(Feedback.Impact(ImpactStyle.MEDIUM), HapticsService.feedbackFor(HapticIntent.MATCH))
        assertEquals(Feedback.Impact(ImpactStyle.RIGID), HapticsService.feedbackFor(HapticIntent.MISMATCH))
        assertEquals(Feedback.Notification(NotificationType.SUCCESS), HapticsService.feedbackFor(HapticIntent.SUCCESS))
        assertEquals(Feedback.Notification(NotificationType.ERROR), HapticsService.feedbackFor(HapticIntent.FAILURE))
        assertEquals(Feedback.Notification(NotificationType.WARNING), HapticsService.feedbackFor(HapticIntent.WARNING))
        assertEquals(Feedback.Impact(ImpactStyle.HEAVY), HapticsService.feedbackFor(HapticIntent.REWARD))
    }

    @Test fun `every HapticIntent case is covered by feedbackFor`() {
        // Guards against a case silently falling through to no mapping if the enum ever
        // grows — feedbackFor's own `when` is already exhaustive at compile time, but this
        // keeps the test suite failing loudly too if that ever changes.
        HapticIntent.entries.forEach { intent -> HapticsService.feedbackFor(intent) }
        assertEquals(9, HapticIntent.entries.size)
    }

    @Test fun `impact styles map to distinct one-shot recipes`() {
        val light = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.LIGHT)) as VibrationSpec.OneShot
        val soft = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.SOFT)) as VibrationSpec.OneShot
        val medium = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.MEDIUM)) as VibrationSpec.OneShot
        val rigid = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.RIGID)) as VibrationSpec.OneShot
        val heavy = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.HEAVY)) as VibrationSpec.OneShot

        assertEquals(12L, light.durationMillis); assertEquals(70, light.amplitude)
        assertEquals(18L, soft.durationMillis); assertEquals(50, soft.amplitude)
        assertEquals(20L, medium.durationMillis); assertEquals(140, medium.amplitude)
        assertEquals(12L, rigid.durationMillis); assertEquals(210, rigid.amplitude)
        assertEquals(30L, heavy.durationMillis); assertEquals(255, heavy.amplitude)

        // Every amplitude is a hand-tuned value in 1..255, never the OS default sentinel
        // (see VibrationSpec.OneShot's own doc).
        listOf(light, soft, medium, rigid, heavy).forEach {
            assertTrue(it.amplitude in 1..255)
        }
        // Soft reads as more diffuse than a plain tap: lower amplitude, longer pulse.
        assertTrue(soft.amplitude < light.amplitude)
        assertTrue(soft.durationMillis > light.durationMillis)
        // Rigid is short and sharp — same duration as light, noticeably stronger.
        assertTrue(rigid.amplitude > medium.amplitude)
        // Heavy (reward) is the strongest single pulse in the whole table.
        assertTrue(heavy.amplitude >= listOf(light, soft, medium, rigid).maxOf { it.amplitude })
    }

    @Test fun `selection is lighter and shorter than a plain tap`() {
        val selection = HapticsService.vibrationSpec(Feedback.Selection) as VibrationSpec.OneShot
        val tap = HapticsService.vibrationSpec(Feedback.Impact(ImpactStyle.LIGHT)) as VibrationSpec.OneShot
        assertTrue(selection.durationMillis <= tap.durationMillis)
        assertTrue(selection.amplitude < tap.amplitude)
    }

    @Test fun `notification feedback is a distinct pulse pattern per severity`() {
        val success = HapticsService.vibrationSpec(Feedback.Notification(NotificationType.SUCCESS)) as VibrationSpec.Waveform
        val warning = HapticsService.vibrationSpec(Feedback.Notification(NotificationType.WARNING)) as VibrationSpec.Waveform
        val error = HapticsService.vibrationSpec(Feedback.Notification(NotificationType.ERROR)) as VibrationSpec.Waveform

        // Every waveform's amplitudes array pairs 1:1 with its timings array, and index 0
        // is a silent lead-in (see VibrationSpec.Waveform's own doc).
        listOf(success, warning, error).forEach { spec ->
            assertEquals(spec.timings.size, spec.amplitudes.size)
            assertEquals(0, spec.amplitudes[0])
        }

        // Success and warning share the same pulse count (two) but differ in gap and
        // amplitude; error is the longest, most emphatic pattern of the three.
        assertEquals(4, success.timings.size)
        assertEquals(4, warning.timings.size)
        assertEquals(6, error.timings.size)
        assertTrue(warning.timings[2] > success.timings[2]) // the gap between pulses
        assertTrue(warning.amplitudes[1] > success.amplitudes[1])
        assertTrue(error.amplitudes.filter { it > 0 }.size > success.amplitudes.filter { it > 0 }.size)
    }

    @Test fun `haptics default to enabled`() {
        // Mirrors UserPreferences.hapticsEnabled's own "unwritten key must not silently
        // disable the feature" rule (see its doc, and the risk register entry in
        // ANDROID_PLAN.md naming this key specifically) — HapticsService.isEnabled starts
        // true before initialize() ever runs, the same way the DataStore accessor itself
        // resolves a missing key to true rather than false.
        assertTrue(HapticsService.isEnabled)
    }
}
