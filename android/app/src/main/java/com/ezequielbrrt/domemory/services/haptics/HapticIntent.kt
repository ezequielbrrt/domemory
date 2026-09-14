package com.ezequielbrrt.domemory.services.haptics

/**
 * What just happened, in the player's terms — the Android counterpart of
 * `ios/DoMemory/DoMemory/Services/Haptics/HapticsService.swift`'s `Intent` enum, case for
 * case. Adding a case here is the only place a new haptic moment is designed; [HapticsService]
 * owns turning it into an actual vibration.
 *
 * Deliberately its own dependency-free file rather than nested inside [HapticsService]: every
 * other Android-framework-touching call this app makes stays out of
 * `feature/game/GameViewModel.kt` (see that class's own doc — it has no Android import
 * anywhere), and a callback typed on a plain enum is what keeps that true for haptics too,
 * the same way `core.model.Difficulty` — not `services.ads.AdFrequencyCap` — is what crosses
 * into `GameViewModel`'s `onCompletionInterstitial` callback signature.
 */
enum class HapticIntent {
    TAP, // any button or row
    SELECT, // picker change, level tile, turn handover
    CARD_FLIP, // first card of a pair turned over
    MATCH, // pair resolved
    MISMATCH, // pair missed
    SUCCESS, // level cleared, game won
    FAILURE, // level lost, out of lives
    WARNING, // action refused, purchase failed
    REWARD, // stars earned, life granted, ad rewarded
}
