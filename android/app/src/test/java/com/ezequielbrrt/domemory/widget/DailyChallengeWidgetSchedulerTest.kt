package com.ezequielbrrt.domemory.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class DailyChallengeWidgetSchedulerTest {

    @Test
    fun `next refresh is the next local midnight`() {
        val zone = ZoneId.of("America/Mexico_City")
        val now = Instant.parse("2026-09-14T17:30:00Z") // 11:30 local

        assertEquals(
            TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30),
            DailyChallengeWidgetScheduler.millisUntilNextMidnight(now, zone),
        )
    }

    @Test
    fun `next refresh follows the local calendar across a daylight-saving boundary`() {
        val zone = ZoneId.of("America/New_York")
        // 23:30 EST on the night before the 2026 spring-forward transition. The next
        // midnight is 30 minutes away; the following refresh must be computed fresh by the
        // worker rather than blindly adding 24 hours to this one.
        val now = Instant.parse("2026-03-08T04:30:00Z")

        assertEquals(
            TimeUnit.MINUTES.toMillis(30),
            DailyChallengeWidgetScheduler.millisUntilNextMidnight(now, zone),
        )
    }
}
