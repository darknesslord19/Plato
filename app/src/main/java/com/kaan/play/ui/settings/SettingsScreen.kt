package com.kaan.play.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.i18n.LanguageChoice
import com.kaan.play.ui.theme.PlayColors

/** Ayarlar ekranı. Şimdilik yalnızca "Dil"; kalan ayarlar 7. adımda eklenecek. */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        LanguageSetting()
    }
}

@Composable
private fun LanguageSetting() {
    var selected by remember { mutableStateOf(AppLanguage.choice()) }

    Text(
        text = stringResource(R.string.language_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
