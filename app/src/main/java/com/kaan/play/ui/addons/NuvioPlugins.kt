package com.kaan.play.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.AppContainer
import com.kaan.play.R
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Nuvio JS Pluginleri listesi ekranı. */
@Composable
fun NuvioPluginsScreen(
    onBack: () -> Unit,
    onOpenPlugin: (Long) -> Unit,
    container: AppContainer,
) {
    val pluginsFlow = remember(container) {
        combine(container.plugins.repos, container.plugins.plugins) { repos, plugins ->
            repos.mapNotNull { r ->
                plugins.filter { it.repoId == r.id }.takeIf { it.isNotEmpty() }?.let { PluginGroup(r, it) }
            }
        }
    }
    val groups by pluginsFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    val totalPlugins = groups?.sumOf { it.plugins.size } ?: 0
    val allEnabled = totalPlugins > 0 && groups?.all { g -> g.plugins.all { it.enabled } } == true

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.nuvio_plugins_title),
            onBack = onBack,
            action = if (totalPlugins > 0) {
                {
                    TextButton(onClick = {
                        scope.launch { container.plugins.setAllEnabled(!allEnabled) }
                    }) {
                        Text(
                            text = stringResource(if (allEnabled) R.string.addons_disable_all else R.string.addons_enable_all),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else null,
        )

        when {
            groups == null -> Unit // yükleniyor
            groups!!.isEmpty() -> {
                EmptyMessage(stringResource(R.string.nuvio_plugins_empty))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "note") {
                        Text(
                            text = stringResource(R.string.plugins_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    groups!!.forEach { group ->
                        item(key = "g-${group.repo.id}") {
                            val groupAllEnabled = group.plugins.isNotEmpty() && group.plugins.all { it.enabled }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = group.repo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (group.plugins.size > 1) {
                                    TextButton(onClick = {
                                        scope.launch { container.plugins.setRepoEnabled(group.repo.id, !groupAllEnabled) }
                                    }) {
                                        Text(
                                            text = stringResource(if (groupAllEnabled) R.string.addons_disable_all else R.string.addons_enable_all),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        items(group.plugins, key = { "np-${it.id}" }) { plugin ->
                            PluginRow(
                                plugin = plugin,
                                onToggle = { scope.launch { container.plugins.setPluginEnabled(plugin.id, it) } },
                                onOpen = { onOpenPlugin(plugin.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
