package com.kaan.play.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.flow.map

/** Ayarlar ekranı. Şimdilik "Eklentiler" ve "Dil"; kalan ayarlar 7. adımda eklenecek. */
@Composable
fun SettingsScreen(onOpenAddons: () -> Unit) {
    val container = rememberContainer()
    val addonCount by remember(container) { container.addons.addons.map { it.size } }
        .collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SettingsRow(
            title = stringResource(R.string.addons_title),
            subtitle = addonCount?.let { pluralStringResource(R.plurals.addons_count, it, it) },
            onClick = onOpenAddons,
        )

        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        LanguageSetting()
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageSetting() {
    var selected by remember { mutableStateOf(AppLanguage.choice()) }

    Column(Modifier.selectableGroup()) {
        LanguageChoice.entries.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == choice,
                        role = Role.RadioButton,
                        onClick = {
                            selected = choice
                            AppLanguage.apply(choice) // Etkinlik yeniden oluşturulur, metinler yeni dilde gelir.
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == choice, onClick = null)
                Text(
                    text = stringResource(choice.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

private fun LanguageChoice.labelRes(): Int = when (this) {
    LanguageChoice.System -> R.string.language_system
    LanguageChoice.English -> R.string.language_english
    LanguageChoice.Turkish -> R.string.language_turkish
}
