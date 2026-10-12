package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.ui.components.ChoiceDialog
import com.kaan.play.ui.components.SettingsRow
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Ayarlar > Eklentiler: yalnızca üç satır (ekle, depolar, yüklü eklentiler). Kaynak zaman aşımı
 * ikincil bir ayar olarak altta küçük durur.
 */
@Composable
fun AddonsScreen(
    startAdding: Boolean,
    onBack: () -> Unit,
    onOpenRepos: () -> Unit,
    onOpenInstalled: () -> Unit,
) {
    val vm = containerViewModel { AddonsViewModel(it.addons, it.pendingAddonUrl) }
    val addons by vm.addons.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val timeout by container.settings.sourceTimeoutSeconds
        .collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_TIMEOUT_SECONDS)
    var showTimeout by remember { mutableStateOf(false) }

    // Ana sayfadaki "eklenti ekle" düğmesinden geldiyse pencere hemen açılır.
    LaunchedEffect(startAdding) { if (startAdding) vm.startAddingOnce() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_title), onBack = onBack)

        SettingsRow(title = stringResource(R.string.addons_add), onClick = { vm.openAdd() })
        SettingsRow(title = stringResource(R.string.addons_repos), onClick = onOpenRepos)
        SettingsRow(
            title = stringResource(R.string.addons_installed),
            value = addons?.let { pluralStringResource(R.plurals.addons_count, it.size, it.size) }.orEmpty(),
            divider = false,
            onClick = onOpenInstalled,
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            // İkincil ayar: altta, küçük ve soluk.
            TextButton(
                onClick = { showTimeout = true },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.addons_timeout, timeout),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showTimeout) {
        ChoiceDialog(
            title = stringResource(R.string.addons_timeout_title),
            options = SettingsRepository.TIMEOUT_OPTIONS,
            selected = timeout,
            label = { stringResource(R.string.addons_timeout_option, it) },
            onSelect = { scope.launch { container.settings.setSourceTimeoutSeconds(it) }; showTimeout = false },
            onDismiss = { showTimeout = false },
        )
    }

    dialog?.let { state ->
        AddAddonDialog(
            state = state,
            onInputChange = vm::onInputChange,
            onConfirm = vm::submit,
            onDismiss = vm::dismissAdd,
        )
    }
}

/** "Kurulu tüm depolar": depolar (Faz 2/3) gelene kadar boştur. */
@Composable
fun RepositoriesScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_repos), onBack = onBack)
        EmptyMessage(stringResource(R.string.addons_repos_empty))
    }
}

/** "Yüklü eklentiler": aç/kapat ve kaldır. */
@Composable
fun InstalledAddonsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { InstalledAddonsViewModel(it.addons) }
    val addons by vm.addons.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<Addon?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.addons_installed), onBack = onBack)

        val list = addons
        when {
            list == null -> Unit // yükleniyor: boş mesaj yanıp sönmesin
            list.isEmpty() -> EmptyMessage(stringResource(R.string.addons_empty))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(list, key = { it.id }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggle = { vm.setEnabled(addon, it) },
                        onRemove = { toRemove = addon },
                    )
                }
            }
        }
    }

    toRemove?.let { addon ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.addons_remove_title)) },
            text = { Text(stringResource(R.string.addons_remove_message, addon.manifest.name)) },
            confirmButton = {
                TextButton(onClick = { vm.remove(addon); toRemove = null }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back))
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddonRow(addon: Addon, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    val name = addon.manifest.name
    val host = remember(addon.sourceUrl) { addon.sourceUrl.toHttpUrlOrNull()?.host ?: addon.sourceUrl }
    val toggleLabel = stringResource(R.string.addons_enabled_toggle, name)

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).alpha(if (addon.enabled) 1f else 0.5f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.addons_row_info, addon.manifest.version ?: "–", host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = addon.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = toggleLabel },
        )
        IconButton(onClick = onRemove) {
            Icon(
                PlayIcons.Trash,
                contentDescription = stringResource(R.string.addons_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddAddonDialog(
    state: AddDialogState,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addons_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(R.string.addons_add_field)) },
                    placeholder = { Text(stringResource(R.string.addons_add_hint)) },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.addons_add_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    state.error != null -> Text(
                        text = state.error.userMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.busy && state.input.isNotBlank()) {
                Text(stringResource(R.string.addons_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
