package com.freestudio.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0B1030),
    secondary = Color(0xFFFF8FB1),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF17171E),
    onBackground = Color(0xFFEDEDF2),
    onSurface = Color(0xFFEDEDF2),
)

@Composable
fun FreeStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
