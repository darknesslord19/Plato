package com.kaan.play.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons

private val CardMinWidth = 132.dp
private val Gutter = 20.dp

/**
 * Kütüphane: tek ana iş "benim listem". İki bölüm: Favoriler (kapak ızgarası, sütun sayısı ekran genişliğinden)
 * ve Geçmiş (son izlenen üstte, ilerleme çubuğuyla). Her silme işlemi bir düğmedir; dokunma dışı hareket gerekmez.
 */
@Composable
fun LibraryScreen(onOpenDetail: (MediaType, Int) -> Unit, onPlayEntry: (WatchHistoryEntity) -> Unit) {
    val vm = containerViewModel { LibraryViewModel(it.library) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardMinWidth),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidth {
            Text(
                text = stringResource(R.string.tab_library),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Yüklenirken (null) boş durum mesajı yanıp sönmesin.
        val favs = favorites
        fullWidth { SectionTitle(stringResource(R.string.library_favorites)) }
        if (favs != null && favs.isEmpty()) {
            fullWidth { EmptyText(stringResource(R.string.library_favorites_empty)) }
        } else if (favs != null) {
            items(favs, key = { "fav-${it.type}-${it.tmdbId}" }) { fav ->
                val removeLabel = stringResource(R.string.detail_favorite_remove)
                PosterCard(
                    title = fav.title,
                    subtitle = stringResource(if (fav.type == MediaType.Movie) R.string.type_movie else R.string.type_series),
                    imageUrl = TmdbImages.url(fav.posterPath, TmdbImages.Size.Poster),
                    onClick = { onOpenDetail(fav.type, fav.tmdbId) },
                    overlay = {
                        IconButton(
                            onClick = { vm.removeFavorite(fav) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) {
                            Icon(PlayIcons.HeartFilled, contentDescription = removeLabel, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }

        val hist = history
        fullWidth {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(R.string.library_history), Modifier.weight(1f))
                if (!hist.isNullOrEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.library_clear_history)) }
                }
            }
        }
        if (hist != null && hist.isEmpty()) {
            fullWidth { EmptyText(stringResource(R.string.library_history_empty)) }
        } else if (hist != null) {
            items(hist, key = { "hist-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                HistoryRow(entry, onClick = { onPlayEntry(entry) }, onRemove = { vm.removeHistory(entry) })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.library_clear_title)) },
            text = { Text(stringResource(R.string.library_clear_message)) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.library_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun HistoryRow(entry: WatchHistoryEntity, onClick: () -> Unit, onRemove: () -> Unit) {
    val watched = WatchRules.isWatched(entry.positionMs, entry.durationMs)
    val minutesLeft = (((entry.durationMs - entry.positionMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    val isSeries = entry.type == MediaType.Tv
    val subtitle = when {
        isSeries && watched -> stringResource(R.string.library_episode_watched, entry.season, entry.episode)
        isSeries -> stringResource(R.string.continue_left_episode, entry.season, entry.episode, minutesLeft)
        watched -> stringResource(R.string.library_watched)
        else -> stringResource(R.string.continue_left_movie, minutesLeft)
    }
    val progress = (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                PlayIcons.Close,
                contentDescription = stringResource(R.string.library_remove_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
