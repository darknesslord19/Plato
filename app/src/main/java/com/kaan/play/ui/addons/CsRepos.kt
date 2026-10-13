package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.data.cloudstream.CsRepo
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.tv.focusRing

/** CloudStream .cs3 depoları listesi ekranı. */
@Composable
fun CsReposScreen(
    onBack: () -> Unit,
    container: AppContainer,
) {
    val vm = remember { CsReposViewModel(container.csRepos) }
    val repos by vm.repos.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.cs_repos_title), onBack = onBack)

        when {
            repos == null -> Unit // yükleniyor
            repos!!.isEmpty() -> {
                Text(
                    text = stringResource(R.string.cs_repos_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.plugins_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(repos!!, key = { it.id }) { repo ->
                        CsRepoRow(repo = repo, onRemove = { vm.remove(repo) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CsRepoRow(repo: CsRepo, onRemove: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.cs_plugins_count, repo.pluginCount, repo.pluginCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            icon = PlayIcons.Delete,
            contentDescription = stringResource(R.string.remove),
            modifier = Modifier.focusRing(),
        ) { confirmDelete = true }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.cs_repo_remove_title)) },
            text = { Text(stringResource(R.string.cs_repo_remove_body, repo.name)) },
            confirmButton = {
                com.kaan.play.ui.components.TextButton(
                    text = stringResource(R.string.remove),
                    onClick = { confirmDelete = false; onRemove() },
                )
            },
            dismissButton = {
                com.kaan.play.ui.components.TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmDelete = false },
                )
            },
        )
    }
}

/**
 * CS eklentileri satırı — [InstalledAddonsScreen] içinde CS depo grubu olarak kullanılır.
 * [onToggle]: eklentiyi aç/kapat.
 */
@Composable
fun CsPluginRow(plugin: InstalledCsPlugin, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!plugin.description.isNullOrBlank()) {
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = plugin.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.focusRing(),
        )
    }
}
