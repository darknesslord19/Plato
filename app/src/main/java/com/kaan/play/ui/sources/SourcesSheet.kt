package com.kaan.play.ui.sources

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.flow.onCompletion

/**
 * Kaynak seçimi: alttan açılan liste. Addon'lar paralel sorulur, kaynaklar geldikçe listeye girer;
 * en uygun olan işaretlidir ve herhangi bir satıra tek dokunuş seçer. Satırda yalnızca kalite ve boyut
 * (altında küçük addon adı) vardır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(request: StreamRequest, onDismiss: () -> Unit, onPicked: (PlayableStream) -> Unit) {
    val container = rememberContainer()
    val state by produceState(SourcesState(), request) {
        value = SourcesState()
        container.streams.streams(request)
            .onCompletion { cause -> if (cause == null) value = value.finished() }
            .collect { value += it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.sources_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // Yükleme çubuğu kalkınca liste zıplamasın diye yer her zaman ayrılır.
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(4.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.streams) { stream ->
                    SourceRow(stream, isRecommended = stream == state.recommended, onClick = { onPicked(stream) })
                }
                items(state.failed) { source ->
                    Text(
                        text = stringResource(R.string.sources_failed, source.addonName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                if (!state.loading && state.streams.isEmpty()) {
                    item {
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

@Composable
private fun SourceRow(stream: PlayableStream, isRecommended: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = sourceLabel(stream),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stream.source.addonName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isRecommended) {
            Icon(PlayIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "1080p · 2,1 GB": yalnızca kalite ve (biliniyorsa) boyut. */
@Composable
fun sourceLabel(stream: PlayableStream): String {
    val quality = when (val h = stream.resolution) {
        null -> stringResource(R.string.sources_quality_unknown)
        2160 -> stringResource(R.string.quality_4k)
        else -> stringResource(R.string.quality_p, h)
    }
    val size = stream.sizeBytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
    return if (size == null) quality else stringResource(R.string.dot_join, quality, size)
}
