package com.ezequielbrrt.domemory.services.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 11.4's ordering: consent, then the ads SDK, then the primer, then finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchSequenceTest {
    @Test
    fun adsStartOnlyAfterConsentThenPrimerThenFinished() = runTest {
        val sequence = LaunchSequence()
        val steps = mutableListOf<String>()
        val consent = CompletableDeferred<Unit>()

        sequence.start(this, gatherConsent = { steps += "consent"; consent.await() }, startAdsIfAllowed = { steps += "ads" })
        runCurrent()
        assertEquals(LaunchSequence.Stage.CONSENT, sequence.stage.value)
        assertEquals(listOf("consent"), steps)

        sequence.primerFinished() // too early: ignored
        consent.complete(Unit)
        runCurrent()
        assertEquals(listOf("consent", "ads"), steps)
        assertEquals(LaunchSequence.Stage.PRIMER, sequence.stage.value)

        sequence.primerFinished()
        assertEquals(LaunchSequence.Stage.FINISHED, sequence.stage.value)
    }

    @Test
    fun runsOncePerProcess() = runTest {
        val sequence = LaunchSequence()
        var consentRuns = 0
        repeat(3) { sequence.start(this, gatherConsent = { consentRuns++ }, startAdsIfAllowed = {}) }
        runCurrent()
        assertEquals(1, consentRuns)
    }

    @Test
    fun aFailingConsentStepStillReachesThePrimer() = runTest {
        val sequence = LaunchSequence()
        var adsOffered = false
        val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
        sequence.start(
            kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob() + handler),
            gatherConsent = { error("network") },
            startAdsIfAllowed = { adsOffered = true },
        )
        runCurrent()
        assertEquals(true, adsOffered)
        assertEquals(LaunchSequence.Stage.PRIMER, sequence.stage.value)
    }
}
