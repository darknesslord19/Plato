package com.kaan.play.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.cardSubtitle
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.rememberContainer

private val CardWidth = 132.dp
private val ScreenPadding = 20.dp

/**
 * Ana sayfa. Hiç eklenti yokken tek cümle + tek buton; eklenti varken "devam et" ve TMDB satırları.
 */
@Composable
fun HomeScreen(
    onOpenDetail: (MediaType, Int) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onAddAddon: () -> Unit,
) {
    val container = rememberContainer()
    val addons by container.addons.addons.collectAsStateWithLifecycle(initialValue = null)

    when (val list = addons) {
        null -> Box(Modifier.fillMaxSize()) // eklenti listesi yükleniyor: boş durum yanıp sönmesin
        else -> if (list.isEmpty()) HomeEmptyState(onAddAddon) else HomeContent(onOpenDetail, onContinue)
    }
}

@Composable
private fun HomeEmptyState(onAddAddon: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddAddon) { Text(stringResource(R.string.home_empty_action)) }
    }
}

@Composable
private fun HomeContent(onOpenDetail: (MediaType, Int) -> Unit, onContinue: (ContinueItem) -> Unit) {
    // Dil değişince (etkinlik yeniden kurulur) yeni dille taze bir ViewModel: satırlar yeni dilde yüklenir.
    val vm = containerViewModel(key = "home-${AppLanguage.effective().iso}") { HomeViewModel(it.tmdb, it.continueWatching) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    // Durum kompozisyon kapsamında okunur (LazyColumn DSL bloğunda değil): değişince bu ekran yeniden çizilir.
    val continueWatching = vm.continueWatching.collectAsStateWithLifecycle().value

    // LazyColumn anahtar bazlı konumu korur: en üste sonradan eklenen "devam et" satırı ekranın üstüne itilirdi.
    val listState = rememberLazyListState()
    val hasContinue = continueWatching.isNotEmpty()
    LaunchedEffect(hasContinue) { if (hasContinue) listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Section(title = stringResource(R.string.home_row_continue)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { "${it.entry.type}-${it.entry.tmdbId}" }) { item ->
                            ContinueCard(item) { onContinue(item) }
                        }
                    }
                }
            }
        }
        HomeRow.entries.forEach { row ->
            item(key = row.name) {
                Section(title = stringResource(row.titleRes())) {
                    when (val state = rows.getValue(row)) {
                        Loadable.Loading -> SkeletonRow()
                        is Loadable.Failed -> ErrorMessage(state.error, onRetry = { vm.load(row) }, modifier = Modifier.fillMaxWidth())
                        is Loadable.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.value, key = { "${it.type}-${it.id}" }) { media ->
                                PosterCard(
                                    title = media.title,
                                    subtitle = media.cardSubtitle(),
                                    imageUrl = media.posterUrl(),
                                    onClick = { onOpenDetail(media.type, media.id) },
                                    modifier = Modifier.width(CardWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
        content()
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit) {
    val entry = item.entry
    val subtitle: String
    val progress: Float?
    when (item) {
        is ContinueItem.Resume -> {
            val minutesLeft = (((entry.durationMs - entry.positionMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            subtitle = if (entry.type == MediaType.Tv) {
                stringResource(R.string.continue_left_episode, entry.season, entry.episode, minutesLeft)
            } else {
                stringResource(R.string.continue_left_movie, minutesLeft)
            }
            progress = entry.positionMs.toFloat() / entry.durationMs.toFloat()
        }
        // Sıradaki bölüm önerisi: henüz izlenmedi, ilerleme çubuğu yok.
        is ContinueItem.Next -> {
            subtitle = stringResource(R.string.continue_next_episode, item.season, item.episode)
            progress = null
        }
    }
    PosterCard(
        title = entry.title,
        subtitle = subtitle,
        imageUrl = TmdbImages.url(entry.posterPath, TmdbImages.Size.Poster),
        onClick = onClick,
        modifier = Modifier.width(CardWidth),
        progress = progress,
    )
}

/** Yüklenirken afiş biçiminde sönük kutular: yerleşim yüklenince zıplamaz. */
@Composable
private fun SkeletonRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Spacer(
                Modifier
                    .width(CardWidth)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

private fun HomeRow.titleRes(): Int = when (this) {
    HomeRow.Trending -> R.string.home_row_trending
    HomeRow.PopularMovies -> R.string.home_row_popular_movies
    HomeRow.PopularSeries -> R.string.home_row_popular_series
}
