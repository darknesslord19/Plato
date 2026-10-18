package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.SettingsRow
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class OpenDialog { Theme, Language }

/**
 * Ayarlar: tam beş kalem (Eklentiler, Oynatıcı, Tema, Dil, Hakkında), her biri tek satır
 * (başlık solda, değer sağda). Yeni kalem eklenmez (arayüz ilkesi 4).
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

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        SettingsRow(
            title = stringResource(R.string.addons_title),
            value = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) }.orEmpty(),
            modifier = Modifier.focusMemory(focus, "addons", isDefault = true),
            onClick = onOpenAddons,
        )
        SettingsRow(
            title = stringResource(R.string.settings_player),
            modifier = Modifier.focusMemory(focus, "player"),
            onClick = onOpenPlayer,
        )
        SettingsRow(
            stringResource(R.string.settings_theme),
            stringResource(theme.labelRes()),
            modifier = Modifier.focusMemory(focus, "theme"),
        ) { dialog = OpenDialog.Theme }
        SettingsRow(
            stringResource(R.string.settings_language),
            stringResource(language.labelRes()),
            modifier = Modifier.focusMemory(focus, "language"),
        ) { dialog = OpenDialog.Language }
        SettingsRow(
            stringResource(R.string.settings_about),
            BuildConfig.VERSION_NAME,
            divider = false,
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
                AppLanguage.apply(it) // Etkinlik yeniden oluşturulur, metinler yeni dilde gelir.
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
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
