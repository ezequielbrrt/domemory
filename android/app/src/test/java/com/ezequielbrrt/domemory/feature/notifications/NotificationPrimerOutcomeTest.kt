package com.ezequielbrrt.domemory.feature.notifications

import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationPermissionResult
import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the `outcome` values of `notification_primer_completed` to iOS's, since both log into one Firebase project. */
class NotificationPrimerOutcomeTest {
    @Test fun `every result maps to the iOS outcome string`() {
        assertEquals("authorized", NotificationPermissionResult.Authorized(NotificationPermissionStatus.Authorized).analyticsOutcome())
        assertEquals("denied", NotificationPermissionResult.Denied.analyticsOutcome())
        assertEquals("deferred", NotificationPermissionResult.Deferred.analyticsOutcome())
        assertEquals("failed", NotificationPermissionResult.Failed("no dialog").analyticsOutcome())
    }
}
