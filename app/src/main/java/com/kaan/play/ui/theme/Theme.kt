package com.kaan.play.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.kaan.play.ui.tv.PlayIndication

private val DarkScheme = darkColorScheme(
    primary = PlayColors.Accent,
    onPrimary = PlayColors.OnAccent,
    primaryContainer = PlayColors.DarkSurfaceContainer,
    onPrimaryContainer = PlayColors.Accent,
    background = PlayColors.DarkBackground,
    onBackground = PlayColors.DarkOnBackground,
    surface = PlayColors.DarkSurface,
    onSurface = PlayColors.DarkOnBackground,
    surfaceVariant = PlayColors.DarkSurfaceVariant,
    onSurfaceVariant = PlayColors.DarkOnMuted,
    surfaceContainer = PlayColors.DarkSurfaceContainer,
    surfaceContainerHigh = PlayColors.DarkSurfaceVariant,
    outline = PlayColors.DarkOutline,
    outlineVariant = PlayColors.DarkOutline,
    error = PlayColors.Error,
)

private val LightScheme = lightColorScheme(
    primary = PlayColors.LightAccent,
    onPrimary = PlayColors.LightOnAccent,
    primaryContainer = PlayColors.LightSurfaceContainer,
    onPrimaryContainer = PlayColors.LightAccent,
    background = PlayColors.LightBackground,
    onBackground = PlayColors.LightOnBackground,
    surface = PlayColors.LightSurface,
    onSurface = PlayColors.LightOnBackground,
    surfaceVariant = PlayColors.LightSurfaceVariant,
    onSurfaceVariant = PlayColors.LightOnMuted,
    surfaceContainer = PlayColors.LightSurfaceContainer,
    surfaceContainerHigh = PlayColors.LightSurfaceVariant,
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
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, shapes = PlayShapes, typography = PlayTypography) {
        // Odaktaki her tıklanabilir öğe turkuaz halka alır (TV/kumanda/klavye); dokunmatikte görünüm aynı kalır.
        val indication = remember(scheme.primary) { PlayIndication(scheme.primary) }
        CompositionLocalProvider(LocalIndication provides indication, content = content)
    }
}
