package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    /**
     * Her içeriğin (film ya da dizi) en son izlenen kaydı, yeni izleme üstte, en çok 30 kayıt.
     * İzlendi/izleniyor ayrımı burada değil, [com.kaan.play.data.library.WatchRules]'ta yapılır.
     */
    @Query(
        """
        SELECT * FROM watch_history h
        WHERE h.lastWatchedAt = (
            SELECT MAX(lastWatchedAt) FROM watch_history WHERE tmdbId = h.tmdbId AND type = h.type
        )
        AND h.durationMs > 0
        ORDER BY h.lastWatchedAt DESC
        LIMIT 30
        """,
    )
    fun observeLatestPerContent(): Flow<List<WatchHistoryEntity>>

    /** (tmdbId, type, season, episode) benzersizdir: aynı bölüm yeniden kaydedilince satır yenilenir. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId AND type = :type AND season = :season AND episode = :episode")
    suspend fun find(tmdbId: Int, type: MediaType, season: Int, episode: Int): WatchHistoryEntity?
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE tmdbId = :tmdbId AND type = :type")
    fun observeIsFavorite(tmdbId: Int, type: MediaType): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE tmdbId = :tmdbId AND type = :type")
    suspend fun remove(tmdbId: Int, type: MediaType)
}
