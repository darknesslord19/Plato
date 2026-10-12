package com.kaan.play.ui.sources

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.components.FilterPill
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.rememberInitialFocus
import kotlinx.coroutines.flow.onCompletion

/**
 * Kaynak seçimi: alttan açılan liste. Addon'lar paralel sorulur, kaynaklar geldikçe listeye girer; her addon kendi
 * başlığı altında toplanır (cevabı sürenlerde "Alınıyor…"). Üstte yenile düğmesi ve (birden çok addon varsa) addon
 * süzgeci vardır. En uygun kaynak işaretlidir ve herhangi bir satıra tek dokunuş seçer. Satırda addon adı ile
 * "kalite · boyut · dil · altyazı" bulunur (codec/HDR yok).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(request: StreamRequest, onDismiss: () -> Unit, onPicked: (PlayableStream) -> Unit) {
    val container = rememberContainer()
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf<Long?>(null) }
    val state by produceState(SourcesState(), request, refresh) {
        value = SourcesState()
        value = value.copy(asked = container.streams.sources(request))
        container.streams.streams(request)
            .onCompletion { cause -> if (cause == null) value = value.finished() }
            .collect { value += it }
    }
    val addons = state.addons
    // Yenilemede ya da süzülen addon artık listede yokken "Tümü"ne dön.
    val activeFilter = filter?.takeIf { id -> addons.any { it.addonId == id } }
    val visible = addons.filter { activeFilter == null || it.addonId == activeFilter }
    val visibleStreams = visible.flatMap { state.streamsOf(it.addonId) }

    // Kumanda: kaynaklar gelince önerilen satır (yoksa ilk satır) odak alır; Tamam seçer.
    val focus = rememberInitialFocus(enabled = state.streams.isNotEmpty())
    val focusStream = state.recommended?.takeIf { it in visibleStreams } ?: visibleStreams.firstOrNull()
    val pendingVisible = state.pending.filter { activeFilter == null || it.addonId == activeFilter }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        // Sürükleme tutamacı yalnızca dokunmada: kumandada odak ona takılmasın, doğrudan kaynak satırına gelsin.
        dragHandle = if (LocalInputModeManager.current.inputMode == InputMode.Touch) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sources_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { filter = null; refresh++ }) {
                    Icon(PlayIcons.Refresh, contentDescription = stringResource(R.string.sources_refresh))
                }
            }
            if (addons.size >= 2) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") { FilterPill(stringResource(R.string.sources_all), activeFilter == null, onClick = { filter = null }) }
                    items(addons, key = { it.addonId }) { addon ->
                        FilterPill(addon.addonName, activeFilter == addon.addonId, onClick = { filter = addon.addonId })
                    }
                }
            }
            // Yükleme çubuğu kalkınca liste zıplamasın diye yer her zaman ayrılır.
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(4.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(min = 300.dp, max = 460.dp)) {
                visible.forEach { addon ->
                    val own = state.streamsOf(addon.addonId)
                    val waiting = pendingVisible.any { it.addonId == addon.addonId }
                    if (own.isEmpty() && !waiting) return@forEach
                    item(key = "h-${addon.addonId}") { AddonHeader(addon.addonName, waiting) }
                    items(own) { stream ->
                        SourceRow(
                            stream,
                            isRecommended = stream == state.recommended,
                            onClick = { onPicked(stream) },
                            modifier = if (stream == focusStream) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
                if (pendingVisible.isNotEmpty() && visibleStreams.isNotEmpty()) {
                    item(key = "more") { SoftLine(stringResource(R.string.sources_checking_more)) }
                }
                items(state.failed.filter { activeFilter == null || it.addonId == activeFilter }) { source ->
                    SoftLine(stringResource(R.string.sources_failed, source.addonName))
                }
                if (!state.loading && visibleStreams.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.sources_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Addon başlığı; cevabı sürüyorsa altında "Alınıyor…". */
@Composable
private fun AddonHeader(name: String, waiting: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (waiting) {
            Text(
                text = stringResource(R.string.sources_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SoftLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SourceRow(stream: PlayableStream, isRecommended: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stream.source.addonName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sourceLabel(stream),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isRecommended) {
            Icon(PlayIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "1080p · 2,1 GB · EN · Altyazılı": kalite, (biliniyorsa) boyut, ses dili ve altyazı var mı. Codec/HDR yok. */
@Composable
fun sourceLabel(stream: PlayableStream): String {
    val quality = when (val h = stream.resolution) {
        null -> stringResource(R.string.sources_quality_unknown)
        2160 -> stringResource(R.string.quality_4k)
        else -> stringResource(R.string.quality_p, h)
    }
    val size = stream.sizeBytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
    val subtitled = if (stream.subtitles.isNotEmpty()) stringResource(R.string.sources_subtitled) else null
    return listOfNotNull(quality, size, stream.language, subtitled)
        .reduce { acc, part -> stringResource(R.string.dot_join, acc, part) }
}
