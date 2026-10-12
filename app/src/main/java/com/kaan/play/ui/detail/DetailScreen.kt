package com.kaan.play.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.rememberPlayLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.kaan.play.ui.components.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.kaan.play.ui.components.OutlinedButton
import com.kaan.play.ui.components.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaan.play.R
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons

/**
 * Detay. Film: tek ana iş "Oynat". Dizi: ana iş bölüm seçmek (sezon seçici + bölüm listesi).
 * [onPlay]: kaynak isteğiyle çağrılır (film: sezon/bölüm yok; dizi: seçilen bölüm).
 */
@Composable
fun DetailScreen(type: MediaType, id: Int, onBack: () -> Unit, onPlay: (PlayTarget) -> Unit) {
    val vm = containerViewModel(key = "detail-${type.tmdbPath}-$id-${AppLanguage.effective().iso}") {
        DetailViewModel(it.tmdb, it.library, type, id)
    }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val layout = rememberPlayLayout()

    Box(Modifier.fillMaxSize()) {
        when (val d = detail) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Failed -> ErrorMessage(d.error, onRetry = vm::loadDetail, modifier = Modifier.align(Alignment.Center))
            is Loadable.Ready -> DetailContent(
                detail = d.value,
                twoPane = layout.twoPane,
                isFavorite = isFavorite,
                selectedSeason = season,
                episodes = episodes,
                onToggleFavorite = vm::toggleFavorite,
                onSelectSeason = vm::selectSeason,
                onRetryEpisodes = vm::retryEpisodes,
                onPlay = { s, e ->
                    val media = d.value.summary
                    onPlay(PlayTarget(StreamRequest(media.id, d.value.imdbId, media.type, s, e), media.title, media.posterPath))
                },
            )
        }
        // Geri oku: kapağın üstünde de okunsun diye yarı saydam daire içinde.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back), tint = Color.White)
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    isFavorite: Boolean,
    selectedSeason: Int?,
    episodes: Loadable<List<Episode>>,
    twoPane: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRetryEpisodes: () -> Unit,
    onPlay: (Int?, Int?) -> Unit,
) {
    val media = detail.summary
    val isSeries = media.type == MediaType.Tv
    // Kumanda: başlangıç odağı ana işte (film: Oynat, dizi: sezon seçici).
    val primaryFocus = rememberInitialFocus()
    val info: @Composable () -> Unit = {
        InfoBlock(detail, isFavorite, selectedSeason, primaryFocus, onToggleFavorite, onSelectSeason, onPlay)
    }

    if (twoPane) {
        // Geniş/yatay ekran: solda afiş, sağda bilgiler + bölümler (kapak ekranı kaplamaz).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val posterWidth = minOf(maxWidth * 0.3f, (maxHeight - 96.dp) / 1.5f, 320.dp).coerceAtLeast(120.dp)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.statusBarsPadding().padding(start = 24.dp, end = 8.dp, top = 56.dp)) {
                    Poster(media.posterPath ?: media.backdropPath, posterWidth)
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    item { Box(Modifier.statusBarsPadding().padding(top = 16.dp)) { info() } }
                    if (isSeries) episodeSection(episodes, selectedSeason, onRetryEpisodes, onPlay)
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item { Backdrop(media.backdropPath ?: media.posterPath) }
            item { info() }
            if (isSeries) episodeSection(episodes, selectedSeason, onRetryEpisodes, onPlay)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Başlık, künye, özet ve ana işler (film: Oynat + favori; dizi: sezon seçici + favori). */
@Composable
private fun InfoBlock(
    detail: MediaDetail,
    isFavorite: Boolean,
    selectedSeason: Int?,
    primaryFocus: FocusRequester,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlay: (Int?, Int?) -> Unit,
) {
    val media = detail.summary
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(media.title, style = MaterialTheme.typography.titleLarge)
        metaLine(detail)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (media.overview.isNotBlank()) ExpandableText(media.overview)

        if (media.type == MediaType.Tv) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeasonPicker(
                    seasons = detail.seasons,
                    selected = selectedSeason,
                    onSelect = onSelectSeason,
                    modifier = Modifier.weight(1f),
                    focus = primaryFocus,
                )
                Spacer(Modifier.width(12.dp))
                FavoriteButton(isFavorite, onToggleFavorite)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onPlay(null, null) },
                    modifier = Modifier.weight(1f).height(48.dp).focusRequester(primaryFocus),
                ) {
                    Icon(PlayIcons.PlayTriangle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.detail_play), modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.width(12.dp))
                FavoriteButton(isFavorite, onToggleFavorite)
            }
        }
    }
}

private fun LazyListScope.episodeSection(
    episodes: Loadable<List<Episode>>,
    selectedSeason: Int?,
    onRetryEpisodes: () -> Unit,
    onPlay: (Int?, Int?) -> Unit,
) {
    item { Spacer(Modifier.height(8.dp)) }
    when (episodes) {
        Loadable.Loading -> item { CenteredLoadingRow() }
        is Loadable.Failed -> item {
            ErrorMessage(episodes.error, onRetry = onRetryEpisodes, modifier = Modifier.fillMaxWidth())
        }
        is Loadable.Ready -> if (episodes.value.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.detail_no_episodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            items(episodes.value, key = { it.number }) { episode ->
                EpisodeRow(episode) { selectedSeason?.let { onPlay(it, episode.number) } }
            }
        }
    }
}

/** Geniş ekranda sol sütundaki 2:3 afiş. */
@Composable
private fun Poster(path: String?, width: Dp) {
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        TmdbImages.url(path, TmdbImages.Size.PosterLarge)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Backdrop(path: String?) {
    val url = TmdbImages.url(path, TmdbImages.Size.BackdropLarge)
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Alt kenar sayfa rengine erir: başlık okunur, kapak "kesilmiş" görünmez.
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to background)),
        )
    }
}

/** Yıl · süre · en çok 3 tür. */
@Composable
private fun metaLine(detail: MediaDetail): String? {
    val parts = buildList {
        detail.summary.year?.let { add(it.toString()) }
        detail.runtimeMinutes?.let { add(formatRuntime(it)) }
        if (detail.genres.isNotEmpty()) add(detail.genres.take(3).joinToString(", "))
    }
    if (parts.isEmpty()) return null
    return parts.reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}

@Composable
private fun formatRuntime(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.runtime_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.runtime_minutes, minutes)

/** Özet 4 satıra kısaltılır; dokununca açılır/kapanır. */
@Composable
private fun ExpandableText(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { expanded = !expanded },
    )
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    OutlinedIconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = if (isFavorite) PlayIcons.HeartFilled else PlayIcons.Heart,
            contentDescription = stringResource(if (isFavorite) R.string.detail_favorite_remove else R.string.detail_favorite_add),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<SeasonInfo>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(48.dp).focusRequester(focus)) {
            Text(
                text = stringResource(R.string.detail_season, selected ?: seasons.firstOrNull()?.number ?: 1),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(PlayIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            seasons.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_season, s.number)) },
                    onClick = { open = false; onSelect(s.number) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    val title = if (episode.name.isBlank()) {
        stringResource(R.string.detail_episode_fallback, episode.number)
    } else {
        stringResource(R.string.detail_episode_title, episode.number, episode.name)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            TmdbImages.url(episode.stillPath, TmdbImages.Size.Still)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.runtimeMinutes?.takeIf { it > 0 }?.let {
                Text(formatRuntime(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    episode.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenteredLoadingRow() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}
