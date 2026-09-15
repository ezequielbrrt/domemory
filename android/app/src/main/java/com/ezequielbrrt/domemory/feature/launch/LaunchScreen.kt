package com.ezequielbrrt.domemory.feature.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.ui.theme.DoMemoryType
import com.ezequielbrrt.domemory.ui.theme.ThemePreference

/**
 * Android counterpart to iOS's [LaunchScreenView]: a brief branded overlay shown after the
 * system launch window while the Compose hierarchy and persisted preferences become available.
 */
@Composable
fun LaunchScreen(preference: ThemePreference, modifier: Modifier = Modifier) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val background = if (dark) {
        listOf(Color(0xFF2A2156), Color(0xFF201A45), Color(0xFF14102E))
    } else {
        listOf(Color(0xFF5346D6), Color(0xFF4B3FC8), Color(0xFF36298F))
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = background,
                    center = Offset(size.width / 2f, size.height * 0.38f),
                    radius = 520.dp.toPx(),
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(1.dp))
            LaunchCardMark(dark = dark)
            androidx.compose.material3.Text(
                text = stringResource(R.string.common_app_name),
                color = Color.White,
                style = DoMemoryType.display(34).copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                ),
                modifier = Modifier.offset(y = (-96).dp),
            )
        }
    }
}

@Composable
private fun LaunchCardMark(dark: Boolean) {
    val indigo = Color(0xFF4B3FC8)
    val orange = Color(0xFFFF6340)
    val frontFill = if (dark) orange else Color.White
    val backFill = if (dark) indigo else orange
    val questionColor = if (dark) Color.White else indigo

    Box(contentAlignment = Alignment.Center) {
        Surface(
            color = backFill,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .width(150.dp)
                .height(188.dp)
                .offset(x = (-19).dp, y = 22.dp)
                .rotate(-9f)
                .shadow(25.dp, RoundedCornerShape(22.dp), clip = false),
        ) {}
        Surface(
            color = frontFill,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .width(158.dp)
                .height(198.dp)
                .offset(x = 15.dp, y = (-19).dp)
                .rotate(5f)
                .shadow(30.dp, RoundedCornerShape(24.dp), clip = false)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(
                    text = "?",
                    color = questionColor,
                    style = DoMemoryType.display(120).copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 120.sp,
                    ),
                )
            }
        }
    }
}
