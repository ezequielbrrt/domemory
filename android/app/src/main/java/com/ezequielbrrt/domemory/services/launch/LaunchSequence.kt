package com.ezequielbrrt.domemory.services.launch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The launch sequence of spec 11.4, the counterpart of iOS's `MenuView.task`: ad consent,
 * then the Mobile Ads SDK (only if consent allows it), then the notification primer, then
 * finished. Android has no ATT step. Runs once per process, from the menu's first appearance.
 *
 * Held by `AppContainer` and run on the application scope, not the menu's composition, so
 * navigating away mid-consent (a deep link landing on a game) cannot cancel it and leave ads
 * waiting on a step that never ends. The menu renders the primer while [stage] is
 * [Stage.PRIMER] and reports back through [primerFinished].
 */
class LaunchSequence {
    enum class Stage { NOT_STARTED, CONSENT, PRIMER, FINISHED }

    private val _stage = MutableStateFlow(Stage.NOT_STARTED)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    /**
     * Starts the sequence unless it already has. [gatherConsent] refreshes the consent status
     * and shows the form where required; [startAdsIfAllowed] starts the SDK if consent allows.
     */
    fun start(scope: CoroutineScope, gatherConsent: suspend () -> Unit, startAdsIfAllowed: () -> Unit) {
        if (_stage.value != Stage.NOT_STARTED) return
        _stage.value = Stage.CONSENT
        scope.launch {
            try {
                gatherConsent()
            } finally {
                // A failure is already absorbed inside gatherConsent (the stored status
                // stands); the sequence never stalls on this step either way.
                startAdsIfAllowed()
                _stage.value = Stage.PRIMER
            }
        }
    }

    /** The primer was dismissed, or is not due on this launch. */
    fun primerFinished() {
        if (_stage.value == Stage.PRIMER) _stage.value = Stage.FINISHED
    }
}
