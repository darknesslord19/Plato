package com.kaan.play.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    /** null = henüz yüklenmedi (boş durum mesajı yanıp sönmesin). */
    val favorites: StateFlow<List<FavoriteEntity>?> =
        library.allFavorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<WatchHistoryEntity>?> =
        library.watchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removeFavorite(favorite: FavoriteEntity) {
        viewModelScope.launch { library.removeFavorite(favorite.type, favorite.tmdbId) }
    }

    fun removeHistory(entry: WatchHistoryEntity) {
        viewModelScope.launch { library.removeHistory(entry.id) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }
}
