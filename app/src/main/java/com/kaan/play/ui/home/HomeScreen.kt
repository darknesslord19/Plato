package com.kaan.play.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.R
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.provider.ProviderRules
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.ErrorMessage
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.PosterCard
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.components.posterUrl
import com.kaan.play.ui.containerViewModel
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.provider.ProviderSelectorSheet
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.focusMemory
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.rememberFocusMemory
import com.kaan.play.ui.tv.rememberInitialFocus
import com.kaan.play.ui.tv.rememberPlayLayout
import kotlinx.coroutines.launch

private val ScreenPadding = 20.dp

/**
 * Ana sayfa. Hiç eklenti yokken tek cümle + tek buton; eklenti varken kapak karuseli, "devam et" ve TMDB satırları.
 * Sağlayıcı seçildiğinde (Aşama A) seçili sağlayıcı için iskelet yer tutucu ve "Katalog yakında" gösterilir.
 */
@Composable
fun HomeScreen(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
    onAddAddon: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val hasSource by container.hasAnySource.collectAsStateWithLifecycle(initialValue = null)
    val selectedProviders by container.providers.selectedProviders.collectAsStateWithLifecycle(initialValue = emptySet())
    val allProviders by container.providers.allProviders.collectAsStateWithLifecycle(initialValue = emptyList())
    var showProviderSelector by remember { mutableStateOf(false) }

    val isAllSelected = ProviderRules.isAllProviders(selectedProviders)
    val validSelected = remember(allProviders, selectedProviders) {
        allProviders.filter { it.id in selectedProviders }
    }
    val pillText = when {
        validSelected.isEmpty() -> stringResource(R.string.provider_pill_select)
        validSelected.size == 1 -> validSelected.first().name
        else -> stringResource(R.string.provider_pill_multiple, validSelected.first().name, validSelected.size - 1)
    }

    Box(Modifier.fillMaxSize()) {
        when {
            hasSource == null -> Box(Modifier.fillMaxSize())
            !isAllSelected -> ProviderCatalogPlaceholder(providerNames = validSelected.map { it.name })
            hasSource == false -> HomeEmptyState(onAddAddon)
            else -> HomeContent(onOpenDetail, onOpenCategory, onContinue)
        }

        StatusBarScrim()

        // Yüzen hap buton (sağ altta, alt menünün hemen üzerinde)
        ProviderPillButton(
            text = pillText,
            isSelected = !isAllSelected,
            onClick = { showProviderSelector = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )

        if (showProviderSelector) {
            ProviderSelectorSheet(
                providers = allProviders,
                selectedIds = selectedProviders,
                onSelectSingle = { id ->
                    scope.launch { container.providers.setSelectedProviders(ProviderRules.singleSelect(id)) }
                },
                onApplyMulti = { ids ->
                    scope.launch { container.providers.setSelectedProviders(ids) }
                },
                onTogglePin = { id ->
                    scope.launch { container.providers.togglePin(id) }
                },
                onDismiss = { showProviderSelector = false },
                onAddExtension = onAddAddon,
            )
        }
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
        val focus = rememberInitialFocus()
        Button(onClick = onAddAddon, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.home_empty_action)) }
    }
}

@Composable
private fun HomeContent(
    onOpenDetail: (MediaType, Int) -> Unit,
    onOpenCategory: (HomeRow) -> Unit,
    onContinue: (ContinueItem) -> Unit,
) {
    // Dil değişince (etkinlik yeniden kurulur) yeni dille taze bir ViewModel: satırlar yeni dilde yüklenir.
    val vm = containerViewModel(key = "home-${AppLanguage.effective().iso}") { HomeViewModel(it.tmdb, it.continueWatching) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    // Durum kompozisyon kapsamında okunur (LazyColumn DSL bloğunda değil): değişince bu ekran yeniden çizilir.
    val continueWatching = vm.continueWatching.collectAsStateWithLifecycle().value
    val layout = rememberPlayLayout()
    val cardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp
    val heroHeight = heroHeightFor(layout.widthDp, layout.heightDp)

    val heroItems = (hero as? Loadable.Ready)?.value.orEmpty()
    val hasHero = heroItems.isNotEmpty()
    val hasContinue = continueWatching.isNotEmpty()
    // Kumanda: başlangıç odağı kapak düğmesinde (yoksa devam et / ilk satırın ilk kartında); geri dönünce son odaklanan öğe.
    // Kapak yüklenirken beklenir ki odak sonradan eklenen öğenin altına düşmesin.
    val firstReady = hero !is Loadable.Loading && (hasHero || hasContinue || rows.getValue(HomeRow.TrendingMovies) is Loadable.Ready)
    val focus = rememberFocusMemory(enabled = firstReady)

    // LazyColumn anahtar bazlı konumu korur: en üste sonradan eklenen "devam et" satırı ekranın üstüne itilirdi.
    val listState = rememberLazyListState()
    LaunchedEffect(hasContinue) { if (hasContinue) listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "hero") {
            when {
                hasHero -> HeroCarousel(
                    items = heroItems,
                    height = heroHeight,
                    focus = focus,
                    isDefaultFocus = true,
                    onOpen = onOpenDetail,
                )
                // Yükleniyor: aynı boyutta sönük kutu (yerleşim zıplamasın). Başarısızsa kapak hiç çıkmaz.
                hero is Loadable.Loading -> Box(Modifier.fillMaxWidth().height(heroHeight).background(MaterialTheme.colorScheme.surfaceVariant))
                else -> Spacer(Modifier.statusBarsPadding().height(8.dp))
            }
        }
        if (hasContinue) {
            item(key = "continue") {
                Section(title = stringResource(R.string.home_row_continue)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { "${it.entry.type}-${it.entry.tmdbId}" }) { item ->
                            ContinueCard(
                                item,
                                modifier = Modifier.focusMemory(
                                    focus, "c-${item.entry.type}-${item.entry.tmdbId}",
                                    isDefault = !hasHero && item === continueWatching.first(),
                                ),
                                width = cardWidth,
                            ) { onContinue(item) }
                        }
                    }
                }
            }
        }
        HomeRow.entries.forEach { row ->
            item(key = row.name) {
                Section(
                    title = stringResource(row.titleRes()),
                    onSeeAll = { onOpenCategory(row) },
                    headerModifier = Modifier.focusMemory(focus, "h-${row.name}"),
                ) {
                    when (val state = rows.getValue(row)) {
                        Loadable.Loading -> SkeletonRow(cardWidth)
                        is Loadable.Failed -> ErrorMessage(state.error, onRetry = { vm.load(row) }, modifier = Modifier.fillMaxWidth())
                        is Loadable.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.value, key = { "${it.type}-${it.id}" }) { media ->
                                PosterCard(
                                    title = media.title,
                                    subtitle = media.year?.toString(),
                                    imageUrl = media.posterUrl(),
                                    onClick = { onOpenDetail(media.type, media.id) },
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .focusMemory(
                                            focus,
                                            "${row.name}-${media.type}-${media.id}",
                                            isDefault = !hasHero && !hasContinue && row == HomeRow.TrendingMovies && media == state.value.first(),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Kapak yüksekliği (dp): dik telefonda genişliğe yakın, geniş/yatay ekranda alçak ve en çok ekran yüksekliğinin
 * ~%62'si; çok alçak yatay ekranda bile içeriğe (logo, satır, düğme) yetecek kadar.
 */
internal fun heroHeightFor(widthDp: Int, heightDp: Int): Dp {
    val byWidth = if (widthDp < 600) widthDp * 1.1f else widthDp * 0.42f
    return byWidth.coerceAtMost(heightDp * 0.62f).coerceIn(260f, 520f).dp
}

@Composable
private fun Section(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val seeAll = stringResource(R.string.home_see_all)
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .then(if (onSeeAll != null) Modifier.clickable(onClickLabel = seeAll, onClick = onSeeAll) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Icon(PlayIcons.Forward, contentDescription = seeAll, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        modifier = modifier.width(width),
        progress = progress,
    )
}

/** Yüklenirken afiş biçiminde sönük kutular: yerleşim yüklenince zıplamaz. */
@Composable
private fun SkeletonRow(cardWidth: Dp) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            Spacer(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

internal fun HomeRow.titleRes(): Int = when (this) {
    HomeRow.TrendingMovies -> R.string.home_row_trending_movies
    HomeRow.TrendingSeries -> R.string.home_row_trending_series
    HomeRow.PopularMovies -> R.string.home_row_popular_movies
    HomeRow.PopularSeries -> R.string.home_row_popular_series
}

/**
 * Sağlayıcı seçildiğinde (Aşama A) gösterilen katalog iskelet yer tutucu ekranı.
 * Gerçek katalog Aşama B'de eklenecektir; siyah ekranda bırakmaz.
 */
@Composable
private fun ProviderCatalogPlaceholder(
    providerNames: List<String>,
    modifier: Modifier = Modifier,
) {
    val layout = rememberPlayLayout()
    val cardWidth = if (layout.widthDp >= 600) 152.dp else 132.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val title = if (providerNames.size <= 1) {
                    providerNames.firstOrNull() ?: stringResource(R.string.provider_selector_title)
                } else {
                    providerNames.joinToString(", ")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.provider_catalog_coming_soon),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.provider_catalog_coming_soon_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(3) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = ScreenPadding)
                        .width(130.dp)
                        .height(18.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                SkeletonRow(cardWidth = cardWidth)
            }
        }
    }
}

/**
 * Ana sayfanın sağ altında yüzen hap buton. Seçili eklentinin adını veya "Eklenti seç" yazar.
 */
@Composable
private fun ProviderPillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)

    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .focusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = PlayIcons.ChevronDown,
            contentDescription = null,
            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

