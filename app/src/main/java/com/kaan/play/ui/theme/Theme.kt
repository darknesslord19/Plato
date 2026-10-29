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
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.data.settings.Appearance
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.ThemeMode
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
    PlayTheme(
        brightness = if (darkTheme) PaletteGen.Brightness.Dark else PaletteGen.Brightness.Light,
        palette = Palettes.DEFAULT,
        content = content,
    )
}

/** Seçili parlaklık + palet ile tema. Varsayılan palet Koyu/Açık'ta özgün Play renklerini birebir korur. */
@Composable
fun PlayTheme(brightness: PaletteGen.Brightness, palette: String, content: @Composable () -> Unit) {
    val scheme = remember(brightness, palette) { playColorScheme(brightness, palette) }
    MaterialTheme(colorScheme = scheme, shapes = PlayShapes, typography = PlayTypography) {
        // Odaktaki her tıklanabilir öğe vurgu renginde halka alır (TV/kumanda/klavye); dokunmatikte görünüm aynı kalır.
        val indication = remember(scheme.primary) { PlayIndication(scheme.primary) }
        CompositionLocalProvider(LocalIndication provides indication, content = content)
    }
}

/** Ayardaki moddan parlaklık; "Sistem" cihazın koyu/açık durumuna göre Koyu ya da Açık olur. */
fun ThemeMode.brightness(systemDark: Boolean): PaletteGen.Brightness = when (this) {
    ThemeMode.Dark -> PaletteGen.Brightness.Dark
    ThemeMode.Medium -> PaletteGen.Brightness.Medium
    ThemeMode.Black -> PaletteGen.Brightness.Black
    ThemeMode.Light -> PaletteGen.Brightness.Light
    ThemeMode.System -> if (systemDark) PaletteGen.Brightness.Dark else PaletteGen.Brightness.Light
}

private val schemeCache = java.util.concurrent.ConcurrentHashMap<Pair<PaletteGen.Brightness, String>, ColorScheme>()

fun playColorScheme(brightness: PaletteGen.Brightness, paletteKey: String): ColorScheme {
    val palette = Palettes.get(paletteKey)
    if (palette.key == Palettes.DEFAULT) {
        when (brightness) {
            PaletteGen.Brightness.Dark -> return DarkScheme
            PaletteGen.Brightness.Light -> return LightScheme
            else -> Unit
        }
    }
    return schemeCache.getOrPut(brightness to palette.key) {
        val c = PaletteGen.scheme(palette.seed, brightness)
        // Play paletinde vurgu her modda özgün camgöbeği kalır.
        val primary = if (palette.key == Palettes.DEFAULT) PlayColors.Accent else Color(c.primary)
        if (brightness == PaletteGen.Brightness.Light) {
            lightColorScheme(
                primary = Color(c.primary),
                onPrimary = Color(c.onPrimary),
                primaryContainer = Color(c.primaryContainer),
                onPrimaryContainer = Color(c.onPrimaryContainer),
                background = Color(c.background),
                onBackground = Color(c.onBackground),
                surface = Color(c.surface),
                onSurface = Color(c.onBackground),
                surfaceVariant = Color(c.surfaceVariant),
                onSurfaceVariant = Color(c.onSurfaceVariant),
                surfaceContainer = Color(c.surfaceContainer),
                surfaceContainerHigh = Color(c.surfaceContainerHigh),
                outline = Color(c.outline),
                outlineVariant = Color(c.outline),
                error = PlayColors.Error,
            )
        } else {
            darkColorScheme(
                primary = primary,
                onPrimary = Color(c.onPrimary),
                primaryContainer = Color(c.primaryContainer),
                onPrimaryContainer = primary,
                background = Color(c.background),
                onBackground = Color(c.onBackground),
                surface = Color(c.surface),
                onSurface = Color(c.onBackground),
                surfaceVariant = Color(c.surfaceVariant),
                onSurfaceVariant = Color(c.onSurfaceVariant),
                surfaceContainer = Color(c.surfaceContainer),
                surfaceContainerHigh = Color(c.surfaceContainerHigh),
                outline = Color(c.outline),
                outlineVariant = Color(c.outline),
                error = PlayColors.Error,
            )
        }
    }
}

/**
 * Etkinliklerin ortak teması: ayardaki parlaklık ve palet (anında değişir) + durum/gezinme çubuğu ikonları.
 * [initial] açılışta bir kez okunur ki ilk karede doğru tema çıksın (yanıp sönmesin).
 */
@Composable
fun ComponentActivity.PlayAppTheme(settings: SettingsRepository, initial: Appearance, content: @Composable () -> Unit) {
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = initial)
    val brightness = appearance.mode.brightness(isSystemInDarkTheme())
    val dark = brightness != PaletteGen.Brightness.Light
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        runCatching { enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style) }
        onDispose { }
    }
    PlayTheme(brightness, appearance.palette, content)
}
