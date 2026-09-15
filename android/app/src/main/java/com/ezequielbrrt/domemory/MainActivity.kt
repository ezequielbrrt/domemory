package com.ezequielbrrt.domemory

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.ezequielbrrt.domemory.feature.launch.LaunchScreen
import com.ezequielbrrt.domemory.navigation.NavGraph
import com.ezequielbrrt.domemory.feature.whatsnew.WhatsNewDialog
import com.ezequielbrrt.domemory.ui.theme.DoMemoryTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    /**
     * Re-evaluates the active season on every foreground (spec 9.4) — otherwise an app
     * left open across local midnight would keep showing a season that ended yesterday.
     * `onResume` also fires on first launch, right after `onCreate` constructs [container].
     */
    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) {
            container.refreshActiveSeason()
            // Spec 11.2: "On launch, sync the flag with OS state" — also re-checked on every
            // foreground (a player can revoke notification access from system Settings
            // without the app ever seeing an OS callback for it).
            container.applicationScope.launch {
                container.notifications.syncAuthorizationStatus()
                // Spec 11.2: the streak-at-risk reminder is refreshed on launch and every
                // foreground, so a day rollover while the app was backgrounded cannot leave
                // yesterday's pending 20:00 notification armed.
                container.notifications.refreshStreakAtRiskReminder()
            }
        }
    }

    /** `singleTop` (manifest) routes a link tapped while already running here instead of a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        container.deepLinkRouter.receive(intent.data?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        container = (application as DoMemoryApplication).container
        container.deepLinkRouter.receive(intent?.data?.toString())
        setContent {
            val theme by container.prefs.themePreference.collectAsState(initial = com.ezequielbrrt.domemory.ui.theme.ThemePreference.SYSTEM)
            val hasOnboarded by container.prefs.hasOnboarded.collectAsState(initial = null)
            var showWhatsNew by remember { mutableStateOf(false) }
            var showLaunchScreen by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1_200)
                showLaunchScreen = false
            }
            LaunchedEffect(hasOnboarded) {
                hasOnboarded?.let { showWhatsNew = container.whatsNew.shouldShowAfterLaunch(it) }
            }
            DoMemoryTheme(preference = theme) {
                Scaffold { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        hasOnboarded?.let { NavGraph(container, hasOnboarded = it) }
                        if (showWhatsNew) {
                            WhatsNewDialog {
                                showWhatsNew = false
                                container.applicationScope.launch { container.whatsNew.markSeen() }
                            }
                        }
                        AnimatedVisibility(
                            visible = showLaunchScreen,
                            exit = fadeOut(),
                        ) {
                            LaunchScreen(preference = theme)
                        }
                    }
                }
            }
        }
    }
}
