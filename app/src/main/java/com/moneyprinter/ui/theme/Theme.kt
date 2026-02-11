package com.moneyprinter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    secondary = Color(0xFFFFD700),
    onSecondary = Color.Black,
    background = Color(0xFF0D1B2A),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1B2838),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF243447),
    onSurfaceVariant = Color(0xFFB0BEC5),
)

@Composable
fun MoneyPrinterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
