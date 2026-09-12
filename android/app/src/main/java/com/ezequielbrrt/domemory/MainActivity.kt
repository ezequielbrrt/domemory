package com.ezequielbrrt.domemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ezequielbrrt.domemory.navigation.NavGraph
import com.ezequielbrrt.domemory.ui.theme.DoMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as DoMemoryApplication).container
        setContent {
            val theme by container.prefs.themePreference.collectAsState(initial = com.ezequielbrrt.domemory.ui.theme.ThemePreference.SYSTEM)
            val hasOnboarded by container.prefs.hasOnboarded.collectAsState(initial = null)
            DoMemoryTheme(preference = theme) {
                Scaffold { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        hasOnboarded?.let { NavGraph(container, hasOnboarded = it) }
                    }
                }
            }
        }
    }
}
