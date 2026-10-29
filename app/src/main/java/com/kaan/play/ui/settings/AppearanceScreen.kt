package com.kaan.play.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.settings.Appearance
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.theme.Palettes
import com.kaan.play.ui.theme.brightness
import com.kaan.play.ui.theme.playColorScheme
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

/** Parlaklık sırası arayüzde: açıktan koyuya, sonra sistem. */
private val MODES = listOf(ThemeMode.Light, ThemeMode.Medium, ThemeMode.Dark, ThemeMode.Black, ThemeMode.System)

/**
 * Ayarlar > Görünüm: parlaklık (açık / orta karanlık / koyu / simsiyah / sistem), 40 renk paleti ve
 * "Ana sayfa kategorileri". Seçim anında uygulanır.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit, onEditHome: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(
        initialValue = Appearance(SettingsRepository.DEFAULT_THEME, SettingsRepository.DEFAULT_PALETTE),
    )
    val brightness = appearance.mode.brightness(isSystemInDarkTheme())
    val focus = rememberFocusMemory()
    val chipShape = RoundedCornerShape(20.dp)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.settings_appearance), onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "home", span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    onClick = onEditHome,
                    modifier = Modifier.fillMaxWidth().focusMemory(focus, "home", isDefault = true),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.appearance_home_categories),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(PlayIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item(key = "mode_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.appearance_brightness))
            }
            item(key = "modes", span = { GridItemSpan(maxLineSpan) }) {
                // İki satıra bölünür (uzun Türkçe adlar ve büyük yazıda taşmasın).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MODES.chunked(3).forEach { rowModes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowModes.forEach { mode ->
                                val selected = mode == appearance.mode
                                Text(
                                    text = stringResource(mode.labelRes()),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusMemory(focus, "mode-${mode.name}")
                                        .focusRing(chipShape)
                                        .clip(chipShape)
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, chipShape)
                                        .selectable(selected = selected, role = Role.RadioButton) {
                                            scope.launch { container.settings.setThemeMode(mode) }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "palette_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.appearance_palette))
            }
            items(Palettes.ALL, key = { it.key }) { palette ->
                val scheme = remember(brightness, palette.key) { playColorScheme(brightness, palette.key) }
                val selected = palette.key == Palettes.get(appearance.palette).key
                val shape = RoundedCornerShape(14.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .focusMemory(focus, "p-${palette.key}")
                        .focusRing(shape)
                        .clip(shape)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            scope.launch { container.settings.setThemePalette(palette.key) }
                        }
                        .padding(vertical = 6.dp),
                ) {
                    // Önizleme: temanın kendi zemini, kartı, düğmesi ve köşe yapısıyla küçük bir ekran.
                    val pShape = com.kaan.play.ui.theme.paletteShapes(palette.shape)
                    val frame = pShape.medium
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .aspectRatio(0.78f)
                            .clip(frame)
                            .background(scheme.background)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else scheme.outline,
                                frame,
                            )
                            .padding(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(26.dp).clip(pShape.small).background(scheme.surfaceVariant))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f).height(18.dp).clip(pShape.small).background(scheme.surfaceContainer))
                                Box(Modifier.weight(1f).height(18.dp).clip(pShape.small).background(scheme.surface))
                            }
                            Box(
                                Modifier.fillMaxWidth(0.7f).height(16.dp).clip(pShape.small).background(scheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(PlayIcons.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(palette.nameRes),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}
