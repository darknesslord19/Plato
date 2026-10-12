package com.kaan.play.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch

/**
 * Ayarlar > Oynatıcı: altyazı görünümü (üstte canlı önizleme) ve "her zaman kaldığım yerden devam et".
 * Değişiklikler anında DataStore'a yazılır; oynatıcı aynı akıştan okur.
 */
@Composable
fun PlayerSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val style by container.settings.subtitleStyle.collectAsStateWithLifecycle(initialValue = SubtitleStyle())
    val alwaysResume by container.settings.alwaysResume
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_ALWAYS_RESUME)

    val downloadWifiOnly by container.settings.downloadWifiOnly
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_DOWNLOAD_WIFI_ONLY)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.settings_player), onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SubtitlePreview(style)
            SubtitleStyleControls(style) { transform -> scope.launch { container.settings.updateSubtitleStyle(transform) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow(stringResource(R.string.settings_always_resume), alwaysResume) {
                scope.launch { container.settings.setAlwaysResume(it) }
            }
            // İndirmeler (Faz 4/B): Ayarlar'a yeni ana kalem eklenmez, bu ayar Oynatıcı'nın içindedir.
            SwitchRow(stringResource(R.string.settings_download_wifi_only), downloadWifiOnly) {
                scope.launch { container.settings.setDownloadWifiOnly(it) }
            }
        }
    }
}

/** Tek satırlık açma/kapama: satırın tamamı dokunulur ve kumandayla odaklanır (anahtar yalnızca gösterir). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 0.dp),
        )
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}
