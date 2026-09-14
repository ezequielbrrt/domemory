package com.ezequielbrrt.domemory.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a deep link that arrived before the UI was ready to route it (spec 11.1) — a cold
 * launch via `domemory://daily` can race the onboarding gate, since the nav graph doesn't
 * pick a start destination until `hasOnboarded` resolves.
 *
 * `MainActivity` feeds every incoming intent's data through [receive]; whoever is ready to
 * act on one calls [consume] exactly once, so the same link is never routed twice (e.g. on
 * a recomposition after a configuration change).
 */
class DeepLinkRouter {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun receive(rawUri: String?) {
        DeepLink.parse(rawUri)?.let { _pending.value = it }
    }

    /** Returns and clears the pending link, or null if there is none. */
    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }
}
