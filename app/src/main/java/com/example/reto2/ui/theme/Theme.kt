package com.example.reto2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = Green80,
    tertiary = NeutralGray,
    background = Color(0xFF101411),
    surface = Color(0xFF191F1B),
    onSurface = NeutralWhite
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = NeutralWhite,
    secondary = Color(0xFF557561),
    tertiary = NeutralGray,
    background = NeutralWhite,
    surface = NeutralWhite,
    onBackground = NeutralBlack,
    onSurface = NeutralBlack
)

@Composable
fun Reto2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
