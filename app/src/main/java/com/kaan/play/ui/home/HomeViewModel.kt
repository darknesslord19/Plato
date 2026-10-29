package com.kaan.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.ContinueWatching
import com.kaan.play.data.model.HeroItem
import com.kaan.play.data.home.HomeCatalog
import com.kaan.play.data.home.HomeLayoutStore
import com.kaan.play.data.home.HomeSection
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Satır durumlarının anahtarı: özel kategori düzenlenince (sorgu/tür değişince) yeniden yüklensin. */
internal fun HomeSection.rowKey(): String = if (isCustom) "$id|$query|$type" else id

typealias RowState = Loadable<List<MediaSummary>>

/**
 * Her satır (ve kapak karuseli) bağımsız yüklenir ve bağımsız hata verir; biri başarısız olunca diğerleri etkilenmez.
 * Satırlar ekrana geldikçe ([ensure]) yüklenir; sıra ve görünürlük [HomeLayoutStore]'dan gelir.
 */
class HomeViewModel(
    private val tmdb: TmdbRepository,
    continueWatching: ContinueWatching,
    layoutStore: HomeLayoutStore,
) : ViewModel() {

    private val _rows = MutableStateFlow<Map<String, RowState>>(emptyMap())
    val rows: StateFlow<Map<String, RowState>> = _rows.asStateFlow()

    private val _hero = MutableStateFlow<Loadable<List<HeroItem>>>(Loadable.Loading)
    val hero: StateFlow<Loadable<List<HeroItem>>> = _hero.asStateFlow()

    val continueWatching: StateFlow<List<ContinueItem>> =
        continueWatching.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null: düzen henüz okunmadı (gizlenmiş varsayılan satırlar bir an görünüp boşuna yüklenmesin). */
    val layout: StateFlow<List<HomeSection>?> =
        layoutStore.layout.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        loadHero()
    }

    fun loadHero() {
        _hero.value = Loadable.Loading
        viewModelScope.launch { _hero.value = loadable { tmdb.hero() } }
    }

    /** Henüz yüklenmemişse yükler (satır ekrana gelince çağrılır). */
    fun ensure(section: HomeSection) {
        if (section.rowKey() !in _rows.value) load(section)
    }

    fun load(section: HomeSection) {
        val query = HomeCatalog.query(section) ?: return
        val key = section.rowKey()
        _rows.update { it + (key to Loadable.Loading) }
        viewModelScope.launch {
            val result = loadable { tmdb.list(query, 1).items }
            _rows.update { it + (key to result) }
        }
    }
}
