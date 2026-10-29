package com.kaan.play.ui.addons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.health.HealthItem
import com.kaan.play.data.health.HealthKind
import com.kaan.play.data.health.HealthRules
import com.kaan.play.data.health.HealthStatus
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory

/**
 * Ayarlar > Eklentiler > Eklenti sağlığı: açık addon'lar, depolar ve CloudStream eklentileri tek listede;
 * sorunlular üstte. Ekran açılınca bir kez denetlenir, "Yeniden denetle" ile tekrarlanır.
 */
@Composable
fun SourceHealthScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    var items by remember { mutableStateOf<List<HealthItem>?>(null) }
    var run by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val focus = rememberFocusMemory()

    LaunchedEffect(run) {
        running = true
        items = null
        container.sourceHealth.check().collect { items = it }
        running = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.health_title),
            onBack = onBack,
            action = {
                TextButton(
                    onClick = { if (!running) run++ },
                    enabled = !running,
                    modifier = Modifier.focusMemory(focus, "recheck", isDefault = true),
                ) { Text(stringResource(R.string.health_recheck)) }
            },
        )
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                text = stringResource(R.string.health_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    val problems = HealthRules.problemCount(list)
                    val ok = list.count { it.status == HealthStatus.Ok || it.status == HealthStatus.Slow }
                    Text(
                        text = if (running) {
                            stringResource(R.string.health_checking)
                        } else {
                            stringResource(R.string.health_summary, ok, problems)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.key }) { item -> HealthRow(item, Modifier.focusMemory(focus, item.key)) }
            }
        }
    }
}

@Composable
private fun HealthRow(item: HealthItem, modifier: Modifier = Modifier) {
    val bad = item.status == HealthStatus.Unreachable || item.status == HealthStatus.LoadFailed
    val color = when (item.status) {
        HealthStatus.Ok -> MaterialTheme.colorScheme.primary
        HealthStatus.Slow -> Color(0xFFFFB74D)
        HealthStatus.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    // Satır yalnızca bilgi verir; kumandayla kaydırılabilsin diye odaklanabilir.
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (bad) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(item.kind.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(item.status.labelRes()), style = MaterialTheme.typography.labelLarge, color = color)
                item.millis?.takeIf { item.status == HealthStatus.Ok || item.status == HealthStatus.Slow }?.let {
                    Text(
                        stringResource(R.string.health_millis, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun HealthKind.labelRes(): Int = when (this) {
    HealthKind.Addon -> R.string.health_kind_addon
    HealthKind.NuvioRepo -> R.string.health_kind_nuvio_repo
    HealthKind.CsRepo -> R.string.health_kind_cs_repo
    HealthKind.CsPlugin -> R.string.health_kind_cs_plugin
}

private fun HealthStatus.labelRes(): Int = when (this) {
    HealthStatus.Checking -> R.string.health_status_checking
    HealthStatus.Ok -> R.string.health_status_ok
    HealthStatus.Slow -> R.string.health_status_slow
    HealthStatus.Unreachable -> R.string.health_status_unreachable
    HealthStatus.LoadFailed -> R.string.health_status_load_failed
}
