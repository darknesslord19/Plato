package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.addon.Addon
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import kotlinx.coroutines.launch

/** Nuvio Depoları (Nuvio Addon manifest kaynakları) listesi ekranı. */
@Composable
fun NuvioReposScreen(
    onBack: () -> Unit,
    container: AppContainer,
) {
    val addons by container.addons.addons.collectAsStateWithLifecycle(initialValue = null)
    var toRemove by remember { mutableStateOf<Addon?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.nuvio_repos_title), onBack = onBack)

        when {
            addons == null -> Unit // yükleniyor
            addons!!.isEmpty() -> {
                EmptyMessage(stringResource(R.string.nuvio_repos_empty))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(addons!!, key = { "na-${it.id}" }) { addon ->
                        AddonRow(
                            addon = addon,
                            onToggle = { scope.launch { container.addons.setEnabled(addon.id, it) } },
                            onRemove = { toRemove = addon },
                        )
                    }
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
                TextButton(onClick = {
                    scope.launch { container.addons.remove(addon.id) }
                    toRemove = null
                }) {
                    Text(stringResource(R.string.addons_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
