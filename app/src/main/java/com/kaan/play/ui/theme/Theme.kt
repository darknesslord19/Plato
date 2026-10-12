package com.kaan.play.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = PlayColors.Accent,
    onPrimary = PlayColors.OnAccent,
    primaryContainer = PlayColors.DarkSurfaceHigh,
    onPrimaryContainer = PlayColors.Accent,
    background = PlayColors.DarkBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.DarkSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.DarkSurfaceHigh,
    onSurfaceVariant = PlayColors.DarkOnMuted,
    surfaceContainer = PlayColors.DarkSurface,
    surfaceContainerHigh = PlayColors.DarkSurfaceHigh,
    outline = PlayColors.DarkOutline,
    outlineVariant = PlayColors.DarkOutline,
    error = PlayColors.Error,
)

private val LightScheme = lightColorScheme(
    primary = PlayColors.LightAccent,
    onPrimary = PlayColors.LightSurface,
    primaryContainer = PlayColors.LightSurfaceHigh,
    onPrimaryContainer = PlayColors.LightAccent,
    background = PlayColors.LightBackground,
    onBackground = PlayColors.LightOnBackground,
    surface = PlayColors.LightSurface,
    onSurface = PlayColors.LightOnBackground,
    surfaceVariant = PlayColors.LightSurfaceHigh,
    onSurfaceVariant = PlayColors.LightOnMuted,
    surfaceContainer = PlayColors.LightSurface,
    surfaceContainerHigh = PlayColors.LightSurfaceHigh,
    outline = PlayColors.LightOutline,
    outlineVariant = PlayColors.LightOutline,
    error = PlayColors.Error,
)

private val PlayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PlayTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = PlayShapes,
        typography = PlayTypography,
        content = content,
    )
}
