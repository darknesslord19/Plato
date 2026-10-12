package com.kaan.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.library.ContinueItem
import com.kaan.play.data.library.ContinueWatching
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
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

enum class HomeRow { Trending, PopularMovies, PopularSeries }

typealias RowState = Loadable<List<MediaSummary>>

/** Her satır bağımsız yüklenir ve bağımsız hata verir; biri başarısız olunca diğerleri etkilenmez. */
class HomeViewModel(
    private val tmdb: TmdbRepository,
    continueWatching: ContinueWatching,
) : ViewModel() {

    private val _rows = MutableStateFlow<Map<HomeRow, RowState>>(HomeRow.entries.associateWith { Loadable.Loading })
    val rows: StateFlow<Map<HomeRow, RowState>> = _rows.asStateFlow()

    val continueWatching: StateFlow<List<ContinueItem>> =
        continueWatching.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        HomeRow.entries.forEach(::load)
    }

    fun load(row: HomeRow) {
        _rows.update { it + (row to Loadable.Loading) }
        viewModelScope.launch {
            val result = loadable {
                when (row) {
                    HomeRow.Trending -> tmdb.trending().items
                    HomeRow.PopularMovies -> tmdb.popular(MediaType.Movie).items
                    HomeRow.PopularSeries -> tmdb.popular(MediaType.Tv).items
                }
            }
            _rows.update { it + (row to result) }
        }
    }
}
