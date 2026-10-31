package io.github.bardesss.wick.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WickColorScheme = darkColorScheme(
    primary = WickColors.Amber,
    onPrimary = WickColors.Night,
    secondary = WickColors.Sage,
    onSecondary = WickColors.Paper,
    tertiary = WickColors.Dusk,
    onTertiary = WickColors.Paper,
    background = WickColors.Night,
    onBackground = WickColors.Paper,
    surface = WickColors.Raised,
    onSurface = WickColors.Paper,
    surfaceVariant = WickColors.Raised,
    onSurfaceVariant = WickColors.Meta,
    outline = WickColors.Meta,
    outlineVariant = WickColors.Line,
)

@Composable
fun WickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WickColorScheme,
        typography = WickTypography,
        content = content,
    )
}
