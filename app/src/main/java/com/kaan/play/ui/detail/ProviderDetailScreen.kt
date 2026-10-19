package com.kaan.play.ui.detail

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.rememberPlayLayout
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private sealed interface ProviderDetailUiState {
    data object Loading : ProviderDetailUiState
    data class Error(val message: String) : ProviderDetailUiState
    data class Ready(
        val detail: MediaDetail,
        val isSeries: Boolean,
        val csEpisodes: List<com.lagradost.cloudstream3.Episode>,
        val movieDataUrl: String?,
        val syntheticId: Int,
    ) : ProviderDetailUiState
}

/**
 * Sağlayıcı Detay Ekranı (Faz D / Aşama 2):
 * Eklentinin load(url) sonucunu mevcut Detay bileşenleriyle gösterir.
 * TMDB'ye gitmez. Oynat basılınca doğrudan eklentinin loadLinks akışına gider.
 */
@Composable
fun ProviderDetailScreen(
    providerId: String,
    url: String,
    onBack: () -> Unit,
    onPlay: (PlayTarget) -> Unit,
) {
    val container = rememberContainer()
    val layout = rememberPlayLayout()

    var state by remember(providerId, url) { mutableStateOf<ProviderDetailUiState>(ProviderDetailUiState.Loading) }
    var selectedSeason by remember(providerId, url) { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val syntheticId = remember(providerId, url) {
        -(abs("$providerId:$url".hashCode()).coerceAtLeast(1))
    }

    LaunchedEffect(providerId, url, refreshKey) {
        state = ProviderDetailUiState.Loading
        val pluginRowId = providerId.removePrefix("cs:").toLongOrNull() ?: 0L
        val loadedState: ProviderDetailUiState = withContext(Dispatchers.IO) {
            try {
                val apis: List<MainAPI> = container.csRepos.loadApis(pluginRowId)
                val api = apis.firstOrNull()
                if (api == null) {
                    return@withContext ProviderDetailUiState.Error("Eklenti API'si bulunamadı")
                }

                val loadResp: LoadResponse? = try {
                    api.load(url)
                } catch (t: Throwable) {
                    Log.e("ProviderDetail", "api.load failed: ${t.message}", t)
                    null
                }

                if (loadResp == null) {
                    return@withContext ProviderDetailUiState.Error("İçerik yüklenemedi")
                }

                val isSeries = loadResp is TvSeriesLoadResponse || loadResp is AnimeLoadResponse
                val csEpisodes: List<com.lagradost.cloudstream3.Episode> = when (loadResp) {
                    is TvSeriesLoadResponse -> loadResp.episodes
                    is AnimeLoadResponse -> loadResp.episodes.values.flatten()
                    else -> emptyList()
                }

                val movieDataUrl = when (loadResp) {
                    is MovieLoadResponse -> loadResp.dataUrl
                    is LiveStreamLoadResponse -> loadResp.dataUrl
                    else -> url
                }

                val seasonNums = if (isSeries) {
                    csEpisodes.map { it.season ?: 1 }.distinct().sorted()
                } else {
                    emptyList()
                }

                val seasonInfos = seasonNums.map { sNum ->
                    SeasonInfo(
                        number = sNum,
                        name = "Sezon $sNum",
                        episodeCount = csEpisodes.count { (it.season ?: 1) == sNum },
                        posterPath = null,
                    )
                }

                val mediaType = if (isSeries) MediaType.Tv else MediaType.Movie
                val summary = MediaSummary(
                    id = syntheticId,
                    type = mediaType,
                    title = loadResp.name,
                    overview = loadResp.plot.orEmpty(),
                    posterPath = loadResp.posterUrl,
                    backdropPath = loadResp.posterUrl,
                    year = loadResp.year,
                    rating = null,
                )

                val detail = MediaDetail(
                    summary = summary,
                    imdbId = null,
                    tagline = "",
                    genres = loadResp.tags ?: emptyList(),
                    runtimeMinutes = null,
                    seasons = seasonInfos,
                )

                if (selectedSeason == null && seasonNums.isNotEmpty()) {
                    selectedSeason = seasonNums.first()
                }

                ProviderDetailUiState.Ready(
                    detail = detail,
                    isSeries = isSeries,
                    csEpisodes = csEpisodes,
                    movieDataUrl = movieDataUrl,
                    syntheticId = syntheticId,
                )
            } catch (t: Throwable) {
                Log.e("ProviderDetail", "Error in ProviderDetailScreen", t)
                ProviderDetailUiState.Error(t.message ?: "Beklenmeyen hata")
            }
        }
        state = loadedState
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is ProviderDetailUiState.Loading -> CenteredLoading()
            is ProviderDetailUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { refreshKey++ },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            is ProviderDetailUiState.Ready -> {
                val currentEpisodes = if (s.isSeries) {
                    val sNum = selectedSeason ?: 1
                    val filtered = s.csEpisodes.filter { (it.season ?: 1) == sNum }
                    filtered.mapIndexed { idx, ep ->
                        val epNumber = ep.episode ?: (idx + 1)
                        Episode(
                            number = epNumber,
                            name = ep.name.orEmpty(),
                            overview = ep.description.orEmpty(),
                            stillPath = ep.posterUrl,
                            runtimeMinutes = ep.runTime,
                            airDate = null,
                        )
                    }
                } else {
                    emptyList()
                }

                DetailContent(
                    detail = s.detail,
                    isFavorite = false,
                    selectedSeason = selectedSeason,
                    episodes = Loadable.Ready(currentEpisodes),
                    twoPane = layout.twoPane,
                    screenHeight = layout.heightDp.dp,
                    onToggleFavorite = { /* Favori */ },
                    onSelectSeason = { sNum -> selectedSeason = sNum },
                    onRetryEpisodes = { refreshKey++ },
                    onPlay = { season, episode ->
                        val dataUrl = if (s.isSeries) {
                            val sNum = season ?: selectedSeason ?: 1
                            val ep = s.csEpisodes.firstOrNull {
                                (it.season ?: 1) == sNum && (it.episode == episode || (episode == null))
                            } ?: s.csEpisodes.firstOrNull()
                            ep?.data ?: url
                        } else {
                            s.movieDataUrl ?: url
                        }

                        val target = PlayTarget(
                            request = StreamRequest(
                                tmdbId = s.syntheticId,
                                imdbId = null,
                                type = if (s.isSeries) MediaType.Tv else MediaType.Movie,
                                season = season ?: selectedSeason,
                                episode = episode,
                                pluginId = providerId,
                                dataUrl = dataUrl,
                                contentUrl = url,
                            ),
                            title = s.detail.summary.title,
                            posterPath = s.detail.summary.posterPath,
                        )
                        onPlay(target)
                    },
                )
            }
        }

        StatusBarScrim()

        // Geri oku: kapağın üstünde de okunsun diye yarı saydam daire içinde modern buton.
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .size(40.dp)
                .focusRing(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PlayIcons.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
