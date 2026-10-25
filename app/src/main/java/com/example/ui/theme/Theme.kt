package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SojibCreamColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = CreamSurfaceVariant,
    onPrimaryContainer = GoldSecondary,
    secondary = TerracottaAccent,
    onSecondary = Color.White,
    background = CreamBackground,
    onBackground = TextDarkPrimary,
    surface = CreamSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = TextDarkSecondary,
    outline = CreamBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    // Explicitly enforce the warm Cream & Gold design requested by user
    MaterialTheme(
        colorScheme = SojibCreamColorScheme,
        typography = Typography,
        content = content
    )
}
