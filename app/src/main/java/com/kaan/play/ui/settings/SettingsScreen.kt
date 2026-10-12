package com.kaan.play.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class OpenDialog { Theme, Language }

/**
 * Ayarlar: en çok beş kalem, her biri tek satır (başlık solda, değer sağda).
 * Şimdilik dört kalem var; "Oynatıcı" satırı oynatıcı (6. adım) gelince eklenecek.
 */
@Composable
fun SettingsScreen(onOpenAddons: () -> Unit, onOpenAbout: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val addonCount by remember(container) { container.addons.addons.map { it.size } }
        .collectAsStateWithLifecycle(initialValue = null)
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.Dark)
    var language by remember { mutableStateOf(AppLanguage.choice()) }
    var dialog by remember { mutableStateOf<OpenDialog?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        SettingsRow(
            title = stringResource(R.string.addons_title),
            value = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) }.orEmpty(),
            onClick = onOpenAddons,
        )
        SettingsRow(stringResource(R.string.settings_theme), stringResource(theme.labelRes())) { dialog = OpenDialog.Theme }
        SettingsRow(stringResource(R.string.settings_language), stringResource(language.labelRes())) { dialog = OpenDialog.Language }
        SettingsRow(stringResource(R.string.settings_about), BuildConfig.VERSION_NAME, divider = false, onClick = onOpenAbout)
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
                AppLanguage.apply(it) // Etkinlik yeniden oluşturulur, metinler yeni dilde gelir.
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SettingsRow(title: String, value: String, divider: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        if (divider) HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
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
