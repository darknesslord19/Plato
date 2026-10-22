package com.kaan.play.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.home.HomeCatalog
import com.kaan.play.data.home.HomeSection
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbQuery
import com.kaan.play.ui.rememberContainer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.ScreenHeader
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.rememberFocusMemory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Ana sayfadaki bir satırın tamamı ("tümü"): afiş ızgarası, kaydırdıkça sonraki sayfalar. */
@Composable
fun CategoryScreen(sectionId: String, onBack: () -> Unit, onOpenDetail: (MediaType, Int) -> Unit) {
    val container = rememberContainer()
    val layout by container.homeLayout.layout.collectAsStateWithLifecycle(initialValue = null)
    // Düzenden kaldırılmış hazır kategori de (geri yığınında kalmışsa) açılabilsin.
    val section = layout?.firstOrNull { it.id == sectionId }
        ?: HomeSection(sectionId).takeIf { layout != null && HomeCatalog.preset(sectionId) != null }
    val query = section?.let(HomeCatalog::query)
    if (section == null || query == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = "", onBack = onBack)
            if (layout == null) CenteredLoading()
        }
        return
    }
    CategoryGrid(section.title(), section.rowKey(), query, onBack, onOpenDetail)
}

@Composable
private fun CategoryGrid(
    title: String,
    key: String,
    query: TmdbQuery,
    onBack: () -> Unit,
    onOpenDetail: (MediaType, Int) -> Unit,
) {
    val vm = containerViewModel(key = "category-$key-${AppLanguage.effective().iso}") { CategoryViewModel(it.tmdb, query) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Kumanda: içerik gelince ilk afiş başlangıç odağı; geri dönünce son odaklanan afiş.
    val focus = rememberFocusMemory(enabled = state.items.isNotEmpty())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = title, onBack = onBack, initialFocus = state.items.isEmpty())
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> CenteredLoading()
                state.items.isEmpty() && state.error == null -> Text(
                    text = stringResource(R.string.home_category_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
                state.error != null -> ErrorMessage(state.error!!, onRetry = vm::reload, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val grid = rememberLazyGridState()
                    // Sondan ~6 öğe kala sonraki sayfayı iste.
                    LaunchedEffect(grid, state.items.size) {
                        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged()
                            .filter { it >= state.items.size - 6 }
                            .collect { vm.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = grid,
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { "${it.type}-${it.id}" }) { media ->
                            PosterCard(
                                title = media.title,
                                subtitle = media.year?.toString(),
                                imageUrl = media.posterUrl(),
                                onClick = { onOpenDetail(media.type, media.id) },
                                modifier = Modifier.focusMemory(focus, "${media.type}-${media.id}", isDefault = media == state.items.first()),
                            )
                        }
                    }
                }
            }
        }
    }
}
