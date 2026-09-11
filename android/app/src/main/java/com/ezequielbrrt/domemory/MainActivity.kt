package com.ezequielbrrt.domemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.ezequielbrrt.domemory.navigation.NavGraph
import com.ezequielbrrt.domemory.ui.theme.DoMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as DoMemoryApplication).container
        setContent {
            DoMemoryTheme {
                Scaffold { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                        NavGraph(container)
                    }
                }
            }
        }
    }
}
