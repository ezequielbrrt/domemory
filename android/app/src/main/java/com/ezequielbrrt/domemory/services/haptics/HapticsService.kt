package com.ezequielbrrt.domemory.services.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ezequielbrrt.domemory.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for Android haptic feedback — the counterpart of
 * `ios/DoMemory/DoMemory/Services/Haptics/HapticsService.swift`. Call sites name the
 * *moment* ([HapticIntent]) rather than a vibration recipe, so the whole feel of the app can
 * be retuned from [feedbackFor]/[vibrationSpec] below instead of chasing scattered
 * `Vibrator.vibrate(...)` calls. [fire] is the *only* sanctioned way to reach the vibrator —
 * nothing else in this codebase should call `Vibrator`/`VibrationEffect` directly, matching
 * the iOS class doc's own rule about `UIImpactFeedbackGenerator` call sites.
 *
 * An `object`, not a constructed instance held in [com.ezequielbrrt.domemory.AppContainer] —
 * mirrors [com.ezequielbrrt.domemory.services.ads.AdsService], the other singleton in this
 * codebase that must be reachable both from a framework-free `ViewModel` (via a bare
 * callback, e.g. `GameViewModel.onHaptic`) *and* directly from a composable's own click
 * handler ("view-only taps" — see `GameViewModel`'s class doc for that split). [initialize]
 * is called once from `DoMemoryApplication.onCreate`, the same shape as
 * `AdsService.initialize`.
 *
 * **Why `Vibrator`/`VibrationEffect` and not Compose's `LocalHapticFeedback`/
 * `HapticFeedbackType`:** Compose's haptic API only exists inside composition (it reads
 * `LocalView.current` under the hood), so a `ViewModel`-driven moment — a card match decided
 * inside [com.ezequielbrrt.domemory.feature.game.GameViewModel], with no `View` or
 * composable frame to call it from — could never reach it. Routing every intent through one
 * mechanism, reachable from both call-site shapes, is what keeps [fire] a single gated
 * entry point instead of two parallel systems (Compose haptics for view-only taps, `Vibrator`
 * for everything view-model-driven) that could drift out of feel with each other. `Vibrator`
 * has been public since API 26 (this app's `minSdk`), so no API-level branching is needed for
 * *availability* — only for which system service getter is current (see [systemVibrator]).
 *
 * **Mapping table** (mirrors the iOS class doc's own table — [HapticIntent] case,
 * [Feedback] this maps to, and the concrete Android recipe [vibrationSpec] returns for it).
 * Android's public `VibrationEffect` API has no built-in three-tier "notification" family the
 * way `UINotificationFeedbackGenerator` does (`.success`/`.warning`/`.error`), and no named
 * `.soft`/`.rigid` impact styles the way `UIImpactFeedbackGenerator.FeedbackStyle` does — both
 * are approximated below with hand-tuned `createOneShot`/`createWaveform` recipes (duration +
 * amplitude, or a short pulse pattern) rather than `VibrationEffect.createPredefined`, whose
 * effect ids (`EFFECT_CLICK`, `EFFECT_TICK`, `EFFECT_HEAVY_CLICK`, ...) only cover a light/
 * medium/heavy click family and were added in API 29 — three API levels above this app's
 * `minSdk`. Hand-tuned recipes work uniformly from API 26 with no fallback branch, and let
 * `success`/`warning`/`failure` be distinguished by pulse *pattern* (how many, how far apart),
 * the same way iOS's own private notification-haptic implementation differs by pattern, not
 * just amplitude, even though the two platforms' actual taps feel different by construction:
 *
 * | Intent      | iOS feedback         | Android [Feedback]          | Recipe |
 * |-------------|-----------------------|------------------------------|--------|
 * | `TAP`       | `.impact(.light)`     | `Impact(LIGHT)`              | 12 ms, amplitude 70 |
 * | `SELECT`    | `.selection`          | `Selection`                  | 8 ms, amplitude 40 (shorter/lighter than TAP — a picker tick, not a press) |
 * | `CARD_FLIP` | `.impact(.soft)`      | `Impact(SOFT)`                | 18 ms, amplitude 50 (longer, lower amplitude than TAP — "soft" reads as more diffuse, not sharper) |
 * | `MATCH`     | `.impact(.medium)`   | `Impact(MEDIUM)`              | 20 ms, amplitude 140 |
 * | `MISMATCH`  | `.impact(.rigid)`    | `Impact(RIGID)`               | 12 ms, amplitude 210 (short and sharp, distinct from MATCH's longer/softer pulse) |
 * | `REWARD`    | `.impact(.heavy)`    | `Impact(HEAVY)`               | 30 ms, amplitude 255 (the strongest single pulse in the table) |
 * | `SUCCESS`   | `.notification(.success)` | `Notification(SUCCESS)`  | two quick light pulses, 40 ms apart |
 * | `WARNING`   | `.notification(.warning)` | `Notification(WARNING)`  | two medium pulses, 90 ms apart (same pulse count as SUCCESS, wider gap, heavier amplitude — reads as more deliberate, not celebratory) |
 * | `FAILURE`   | `.notification(.error)`   | `Notification(ERROR)`    | three heavy pulses — the longest, most emphatic pattern, reserved for "this is bad" |
 *
 * **`isEnabled` defaults to true**, same rule and same reasoning as iOS's own comment:
 * [UserPreferences.hapticsEnabled] already resolves an unwritten key to `true` rather than
 * `false` (see that accessor's own doc, and the risk register entry in `ANDROID_PLAN.md` §6
 * naming this key specifically) — [cachedEnabled] just mirrors that Flow synchronously so
 * [fire] can be called from a plain (non-`suspend`) `ViewModel` function or a Compose click
 * handler without awaiting a collection.
 *
 * There is deliberately no Android `prepare(for:)` — iOS's own doc explains that call exists
 * because "a cold Taptic Engine adds latency you can feel on the first tap"; `Vibrator` has no
 * equivalent warm-up cost, so porting that method here would be ceremony with nothing behind it.
 */
object HapticsService {
    private val initialized = AtomicBoolean(false)
    private var vibrator: Vibrator? = null

    @Volatile
    private var cachedEnabled = true

    /** Called once from `DoMemoryApplication.onCreate`, after [UserPreferences] exists. */
    fun initialize(context: Context, prefs: UserPreferences, scope: CoroutineScope) {
        if (!initialized.compareAndSet(false, true)) return
        vibrator = systemVibrator(context.applicationContext)
        prefs.hapticsEnabled.onEach { cachedEnabled = it }.launchIn(scope)
    }

    val isEnabled: Boolean get() = cachedEnabled

    /** The single gate. Every call site — view model callback or composable click handler —
     * goes through here, so no caller has to remember to check the setting. */
    fun fire(intent: HapticIntent) {
        if (!isEnabled) return
        perform(vibrationSpec(feedbackFor(intent)))
    }

    /** Split out from [fire] so a test can assert the mapping without a real `Vibrator` —
     * mirrors iOS's own `static func feedback(for:)` doc comment exactly. */
    fun feedbackFor(intent: HapticIntent): Feedback = when (intent) {
        HapticIntent.TAP -> Feedback.Impact(ImpactStyle.LIGHT)
        HapticIntent.SELECT -> Feedback.Selection
        HapticIntent.CARD_FLIP -> Feedback.Impact(ImpactStyle.SOFT)
        HapticIntent.MATCH -> Feedback.Impact(ImpactStyle.MEDIUM)
        HapticIntent.MISMATCH -> Feedback.Impact(ImpactStyle.RIGID)
        HapticIntent.SUCCESS -> Feedback.Notification(NotificationType.SUCCESS)
        HapticIntent.FAILURE -> Feedback.Notification(NotificationType.ERROR)
        HapticIntent.WARNING -> Feedback.Notification(NotificationType.WARNING)
        HapticIntent.REWARD -> Feedback.Impact(ImpactStyle.HEAVY)
    }

    /** The concrete Android vibration recipe for [feedback] — the Android-specific half of
     * the mapping table in the class doc (iOS's `Feedback` stops at the UIKit generator
     * style; Android needs one more step down to an actual `VibrationEffect`). Pure and
     * Android-framework-free in its *inputs and outputs* (a [VibrationSpec] is plain data),
     * so it is unit-testable without touching a real `Vibrator`. */
    fun vibrationSpec(feedback: Feedback): VibrationSpec = when (feedback) {
        is Feedback.Impact -> when (feedback.style) {
            ImpactStyle.LIGHT -> VibrationSpec.OneShot(durationMillis = 12, amplitude = 70)
            ImpactStyle.SOFT -> VibrationSpec.OneShot(durationMillis = 18, amplitude = 50)
            ImpactStyle.MEDIUM -> VibrationSpec.OneShot(durationMillis = 20, amplitude = 140)
            ImpactStyle.RIGID -> VibrationSpec.OneShot(durationMillis = 12, amplitude = 210)
            ImpactStyle.HEAVY -> VibrationSpec.OneShot(durationMillis = 30, amplitude = 255)
        }
        Feedback.Selection -> VibrationSpec.OneShot(durationMillis = 8, amplitude = 40)
        is Feedback.Notification -> when (feedback.type) {
            NotificationType.SUCCESS -> VibrationSpec.Waveform(
                timings = longArrayOf(0, 40, 40, 40),
                amplitudes = intArrayOf(0, 150, 0, 150),
            )
            NotificationType.WARNING -> VibrationSpec.Waveform(
                timings = longArrayOf(0, 50, 90, 50),
                amplitudes = intArrayOf(0, 190, 0, 190),
            )
            NotificationType.ERROR -> VibrationSpec.Waveform(
                timings = longArrayOf(0, 60, 60, 60, 60, 60),
                amplitudes = intArrayOf(0, 220, 0, 220, 0, 220),
            )
        }
    }

    private fun perform(spec: VibrationSpec) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (spec) {
            is VibrationSpec.OneShot -> VibrationEffect.createOneShot(spec.durationMillis, spec.amplitude)
            is VibrationSpec.Waveform -> VibrationEffect.createWaveform(spec.timings, spec.amplitudes, -1)
        }
        v.vibrate(effect)
    }

    private fun systemVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/** The intermediate feedback *category* [HapticIntent] maps to, before it becomes a concrete
 * [VibrationSpec] — mirrors iOS's own `Feedback` enum (`impact(style)` / `selection` /
 * `notification(type)`), kept as a deliberate two-step mapping ([HapticIntent] -> [Feedback]
 * -> [VibrationSpec]) even though Android has no built-in taxonomy that matches it, purely so
 * the *design* — which intents feel alike — stays legible independent of the Android-specific
 * vibration recipe underneath it. */
sealed class Feedback {
    data class Impact(val style: ImpactStyle) : Feedback()
    data object Selection : Feedback()
    data class Notification(val type: NotificationType) : Feedback()
}

enum class ImpactStyle { LIGHT, SOFT, MEDIUM, RIGID, HEAVY }

enum class NotificationType { SUCCESS, WARNING, ERROR }

/** A concrete `Vibrator` recipe, kept as plain data (no `android.os.VibrationEffect`
 * reference) so [HapticsService.vibrationSpec] is testable without an Android runtime —
 * [HapticsService.perform] is the one place this turns into a real [VibrationEffect]. */
sealed class VibrationSpec {
    /** [amplitude] is 1..255, never [VibrationEffect.DEFAULT_AMPLITUDE] — every recipe here
     * is hand-tuned, not left to the OS default, the same reason iOS holds one distinct
     * generator instance per impact style rather than a single generic one. */
    data class OneShot(val durationMillis: Long, val amplitude: Int) : VibrationSpec()

    /** [amplitudes] pairs 1:1 with [timings]; index 0 is always a silent lead-in (amplitude 0)
     * so the first real pulse starts at its own timing slot rather than buzzing through t=0. */
    data class Waveform(val timings: LongArray, val amplitudes: IntArray) : VibrationSpec()
}
