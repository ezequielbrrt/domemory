package com.ezequielbrrt.domemory.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The single place a `POST_NOTIFICATIONS` grant can happen, so every caller that wants
 * reminders on goes through the same request → [onGranted] shape — see
 * [com.ezequielbrrt.domemory.services.notifications.NotificationService]'s class doc for
 * why that "one path" discipline matters (the exact permission-sync bug iOS already shipped
 * once, spec 11.2). Both the primer ([NotificationPrimerDialog]'s host) and the Settings
 * reminders toggle call this rather than requesting the permission inline themselves.
 *
 * Below API 33 there is no runtime notification permission to request — the returned
 * requester calls [onGranted] immediately, matching how `POST_NOTIFICATIONS` behaves as
 * implicitly granted pre-Tiramisu.
 *
 * Returns a plain `() -> Unit` rather than exposing the launcher directly, so a call site
 * never has to know whether a system dialog is about to appear.
 */
@Composable
fun rememberNotificationPermissionRequester(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else onDenied()
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) onGranted() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onGranted()
        }
    }
}
