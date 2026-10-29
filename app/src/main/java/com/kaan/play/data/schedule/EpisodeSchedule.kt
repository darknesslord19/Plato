package com.kaan.play.data.schedule

import com.kaan.play.data.library.LibraryRepository
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.data.watchlist.WatchlistStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Takip edilen diziler = favorilerdeki ve izleme listesindeki diziler. Her biri için TMDB'den son/sıradaki bölüm
 * alınır (en çok 4 paralel; hata veren dizi atlanır). Sonuç 30 dk bellekte tutulur.
 */
class EpisodeSchedule(
    private val tmdb: TmdbRepository,
    private val library: LibraryRepository,
    private val watchlist: WatchlistStore,
) {
    private var cache: Pair<Long, List<ShowAirInfo>>? = null

    suspend fun followedShowIds(): List<Int> {
        val fav = library.allFavorites.first().filter { it.type == MediaType.Tv }.map { it.tmdbId }
        val wl = watchlist.items.first().filter { it.mediaType == MediaType.Tv }.map { it.tmdbId }
        return (fav + wl).distinct().take(MAX_SHOWS)
    }

    suspend fun load(force: Boolean = false): List<ShowAirInfo> {
        val now = System.currentTimeMillis()
        cache?.takeIf { !force && now - it.first < CACHE_MS }?.let { return it.second }
        val ids = followedShowIds()
        val gate = Semaphore(4)
        val result = coroutineScope {
            ids.map { id -> async { gate.withPermit { runCatching { tmdb.airInfo(id) }.getOrNull() } } }.awaitAll().filterNotNull()
        }
        cache = now to result
        return result
    }

    private companion object {
        const val CACHE_MS = 30 * 60 * 1000L
        const val MAX_SHOWS = 150
    }
}
