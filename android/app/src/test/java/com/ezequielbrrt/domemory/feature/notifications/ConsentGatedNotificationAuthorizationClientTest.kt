package com.ezequielbrrt.domemory.feature.notifications

import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationAuthorizationClient
import com.ezequielbrrt.notificationpermissionkit.authorization.NotificationPermissionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pre-API-33 consent gate: an enabled system toggle reads as `NotDetermined` until
 * "Turn On Reminders" is tapped, and as `Authorized` right after — the library's model
 * re-reads the status after the request, so a mask that never lifted would report `Denied`.
 */
class ConsentGatedNotificationAuthorizationClientTest {
    private class FakeSystemClient(private val status: NotificationPermissionStatus) : NotificationAuthorizationClient {
        var requests = 0

        override suspend fun authorizationStatus(): NotificationPermissionStatus = status

        override suspend fun requestAuthorization(): Boolean {
            requests++
            return status == NotificationPermissionStatus.Authorized
        }
    }

    @Test fun `below API 33 an enabled toggle is NotDetermined until the tap, then Authorized`() = runTest {
        val system = FakeSystemClient(NotificationPermissionStatus.Authorized)
        val client = ConsentGatedNotificationAuthorizationClient(system, sdkInt = 32)

        assertEquals(NotificationPermissionStatus.NotDetermined, client.authorizationStatus())
        assertTrue(client.requestAuthorization())
        assertEquals(1, system.requests)
        assertEquals(NotificationPermissionStatus.Authorized, client.authorizationStatus())
    }

    @Test fun `below API 33 a disabled toggle passes through as PermanentlyDenied`() = runTest {
        val client = ConsentGatedNotificationAuthorizationClient(
            FakeSystemClient(NotificationPermissionStatus.PermanentlyDenied),
            sdkInt = 32,
        )

        assertEquals(NotificationPermissionStatus.PermanentlyDenied, client.authorizationStatus())
    }

    @Test fun `API 33 and later pass every status through untouched`() = runTest {
        NotificationPermissionStatus.entries.forEach { status ->
            val client = ConsentGatedNotificationAuthorizationClient(FakeSystemClient(status), sdkInt = 33)
            assertEquals(status, client.authorizationStatus())
        }
    }
}
