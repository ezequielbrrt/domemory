package com.ezequielbrrt.domemory.services.ads

import com.ezequielbrrt.domemory.core.model.Difficulty

/**
 * Pure policy for involuntary full-screen ads. The SDK adapter owns loading and presentation;
 * this type owns the values that must never drift by placement or UI caller.
 */
data class AdFrequencyState(
    val qualifyingCompletions: Int = 0,
    val lastFullScreenAtMillis: Long? = null,
    val lastRewardedAtMillis: Long? = null,
)

enum class InterstitialDecision { SUPPRESSED, TOO_SHORT, RECENT_REWARDED, DEFERRED, GLOBAL_GAP, REQUEST }

data class InterstitialEvaluation(val decision: InterstitialDecision, val state: AdFrequencyState)

object AdFrequencyCap {
    const val MIN_GAME_DURATION_MILLIS = 20_000L
    const val REWARDED_SUPPRESSION_MILLIS = 60_000L
    const val FULL_SCREEN_GAP_MILLIS = 90_000L
    const val APP_OPEN_FRESHNESS_MILLIS = 4 * 60 * 60 * 1_000L

    fun afterGameCompletion(
        state: AdFrequencyState,
        difficulty: Difficulty,
        gameDurationMillis: Long,
        nowMillis: Long,
        involuntaryAdsSuppressed: Boolean,
    ): InterstitialEvaluation {
        if (involuntaryAdsSuppressed) return InterstitialEvaluation(InterstitialDecision.SUPPRESSED, state)
        if (gameDurationMillis < MIN_GAME_DURATION_MILLIS) return InterstitialEvaluation(InterstitialDecision.TOO_SHORT, state)
        if (state.lastRewardedAtMillis?.let { nowMillis - it < REWARDED_SUPPRESSION_MILLIS } == true) {
            return InterstitialEvaluation(InterstitialDecision.RECENT_REWARDED, state)
        }
        val incremented = state.copy(qualifyingCompletions = state.qualifyingCompletions + 1)
        if (incremented.qualifyingCompletions < difficulty.interstitialEveryNWins) {
            return InterstitialEvaluation(InterstitialDecision.DEFERRED, incremented)
        }
        if (state.lastFullScreenAtMillis?.let { nowMillis - it < FULL_SCREEN_GAP_MILLIS } == true) {
            return InterstitialEvaluation(InterstitialDecision.GLOBAL_GAP, incremented)
        }
        return InterstitialEvaluation(InterstitialDecision.REQUEST, incremented)
    }

    /** Only a successfully presented interstitial resets the counter. */
    fun recordInterstitialPresented(state: AdFrequencyState, nowMillis: Long) =
        state.copy(qualifyingCompletions = 0, lastFullScreenAtMillis = nowMillis)

    fun recordRewardedPresented(state: AdFrequencyState, nowMillis: Long) =
        state.copy(lastRewardedAtMillis = nowMillis, lastFullScreenAtMillis = nowMillis)

    fun isAppOpenFresh(loadedAtMillis: Long?, nowMillis: Long): Boolean =
        loadedAtMillis != null && nowMillis - loadedAtMillis < APP_OPEN_FRESHNESS_MILLIS
}

/**
 * The single call site [AdsService] uses to decide whether a just-finished game should
 * attempt to present [AdPlacement.GAME_FINISHED_INTERSTITIAL] — combines [AdFrequencyCap]'s
 * pure cadence decision with the "never after a paid skip" gate (mirrors iOS's
 * `logGameFinishedIfNeeded(result:allowInterstitial:)`, where charging stars to skip a level
 * and then serving an ad on the way out would be the worst moment in the app to show one).
 * Kept separate from the actual AdMob SDK calls so the decision itself stays unit-testable
 * with an injected clock, the same way [AdFrequencyCap] already is.
 */
object GameFinishedInterstitialTrigger {
    data class Decision(val shouldRequestPresentation: Boolean, val nextState: AdFrequencyState)

    fun evaluate(
        state: AdFrequencyState,
        difficulty: Difficulty,
        gameDurationMillis: Long,
        nowMillis: Long,
        allowInterstitial: Boolean,
        involuntaryAdsSuppressed: Boolean = false,
    ): Decision {
        if (!allowInterstitial) return Decision(shouldRequestPresentation = false, nextState = state)
        val evaluation = AdFrequencyCap.afterGameCompletion(
            state = state,
            difficulty = difficulty,
            gameDurationMillis = gameDurationMillis,
            nowMillis = nowMillis,
            involuntaryAdsSuppressed = involuntaryAdsSuppressed,
        )
        return Decision(
            shouldRequestPresentation = evaluation.decision == InterstitialDecision.REQUEST,
            nextState = evaluation.state,
        )
    }
}
