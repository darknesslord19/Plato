package com.kaan.play.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.cardSubtitle
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Ara: tek büyük arama kutusu ve altında sonuç ızgarası. Filtre/çip yok (kural 5). */
@Composable
fun SearchScreen(onOpenDetail: (MediaType, Int) -> Unit) {
    val vm = containerViewModel(key = "search-${AppLanguage.effective().iso}") { SearchViewModel(it.tmdb) }
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(PlayIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(PlayIcons.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CenteredLoading()
                state.error != null -> ErrorMessage(state.error!!, onRetry = vm::retry, modifier = Modifier.align(Alignment.Center))
                state.activeQuery.isEmpty() -> Hint(stringResource(R.string.search_idle))
                state.items.isEmpty() -> Hint(stringResource(R.string.search_no_results, state.activeQuery))
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { "${it.type}-${it.id}" }) { media ->
                            PosterCard(
                                title = media.title,
                                subtitle = media.cardSubtitle(),
                                imageUrl = media.posterUrl(),
                                onClick = { onOpenDetail(media.type, media.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
