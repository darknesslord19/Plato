package com.kaan.play.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.tmdb.TmdbException
import com.kaan.play.data.tmdb.TmdbQuery
import com.kaan.play.data.tmdb.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryState(
    val items: List<MediaSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: TmdbException? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

/** Bir ana sayfa sırasının tamamı ("tümü"): ilk sayfa hemen, sonrakiler kaydırdıkça. */
class CategoryViewModel(private val tmdb: TmdbRepository, private val query: TmdbQuery) : ViewModel() {

    private val _state = MutableStateFlow(CategoryState())
    val state: StateFlow<CategoryState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = CategoryState()
        viewModelScope.launch {
            try {
                val first = tmdb.list(query, 1)
                _state.update { it.copy(items = first.items, page = first.page, hasMore = first.hasMore, loading = false) }
            } catch (e: TmdbException) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    /** Listenin sonuna yaklaşılınca sonraki sayfa (varsa) eklenir. Hata listeyi bozmaz; kaydırınca yeniden denenir. */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.error != null || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val next = tmdb.list(query, s.page + 1)
                _state.update {
                    it.copy(
                        items = it.items + next.items.filter { n -> it.items.none { o -> o.id == n.id && o.type == n.type } },
                        page = next.page,
                        hasMore = next.hasMore,
                        loadingMore = false,
                    )
                }
            } catch (e: TmdbException) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
