package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MovieHubDarkColorScheme = darkColorScheme(
    primary = MhAccent,
    onPrimary = MhBg,
    primaryContainer = MhCardHover,
    onPrimaryContainer = MhText,
    secondary = MhAccent2,
    onSecondary = MhBg,
    secondaryContainer = MhCard,
    onSecondaryContainer = MhText,
    tertiary = MhGold,
    onTertiary = MhBg,
    background = MhBg,
    onBackground = MhText,
    surface = MhBgSoft,
    onSurface = MhText,
    surfaceVariant = MhCard,
    onSurfaceVariant = MhTextDim,
    outline = MhBorder,
    error = MhDanger,
    onError = MhBg
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MovieHubDarkColorScheme,
        typography = Typography,
        content = content
    )
}
