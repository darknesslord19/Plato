package com.kaan.play.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.launch

private enum class OpenDialog { Theme, Language }

/**
 * Ayarlar: tam beş kalem (Eklentiler, Oynatıcı, Tema, Dil, Hakkında), kart düzeninde
 * (koyu mavi yarı saydam kartlar, sol ikon, sağ ok).
 */
@Composable
fun SettingsScreen(onOpenAddons: () -> Unit, onOpenPlayer: () -> Unit, onOpenAbout: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val addonCount by container.installedSourcesCount
        .collectAsStateWithLifecycle(initialValue = null)
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.Dark)
    var language by remember { mutableStateOf(AppLanguage.choice()) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }
    val focus = rememberFocusMemory()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        SettingsCardRow(
            title = stringResource(R.string.addons_title),
            badge = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) },
            icon = PlayIcons.Extension,
            iconTint = Color(0xFF00E5FF),
            modifier = Modifier.focusMemory(focus, "addons", isDefault = true),
            onClick = onOpenAddons,
        )
        SettingsCardRow(
            title = stringResource(R.string.settings_player),
            icon = PlayIcons.PlayCircle,
            iconTint = Color(0xFF00E5FF),
            modifier = Modifier.focusMemory(focus, "player"),
            onClick = onOpenPlayer,
        )
        SettingsCardRow(
            title = stringResource(R.string.settings_theme),
            value = stringResource(theme.labelRes()),
            icon = PlayIcons.Palette,
            iconTint = Color(0xFF818CF8),
            modifier = Modifier.focusMemory(focus, "theme"),
            onClick = { dialog = OpenDialog.Theme },
        )
        SettingsCardRow(
            title = stringResource(R.string.settings_language),
            value = stringResource(language.labelRes()),
            icon = PlayIcons.Globe,
            iconTint = Color(0xFF00E5FF),
            modifier = Modifier.focusMemory(focus, "language"),
            onClick = { dialog = OpenDialog.Language },
        )
        SettingsCardRow(
            title = stringResource(R.string.settings_about),
            value = BuildConfig.VERSION_NAME,
            icon = PlayIcons.InfoCircle,
            iconTint = Color(0xFF00E5FF),
            modifier = Modifier.focusMemory(focus, "about"),
            onClick = onOpenAbout,
        )
    }

    when (dialog) {
        OpenDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = theme,
            label = { stringResource(it.labelRes()) },
            onSelect = { scope.launch { container.settings.setThemeMode(it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        OpenDialog.Language -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageChoice.entries,
            selected = language,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                language = it
                dialog = null
                AppLanguage.apply(it)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsCardRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    badge: String? = null,
    iconTint: Color = Color(0xFF00E5FF),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .focusRing(shape),
        shape = shape,
        color = Color(0xFF0F1E2E).copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF062B3D), shape = RoundedCornerShape(50))
                        .border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)), shape = RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        color = Color(0xFF00E5FF),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else if (!value.isNullOrEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8FA7B8),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Icon(
                imageVector = PlayIcons.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF8FA7B8),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.System -> R.string.common_system
}

private fun LanguageChoice.labelRes(): Int = when (this) {
    LanguageChoice.System -> R.string.common_system
    LanguageChoice.English -> R.string.language_english
    LanguageChoice.Turkish -> R.string.language_turkish
}
