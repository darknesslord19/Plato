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
     * "Devam et": her içeriğin (film ya da dizi) en son izlenen kaydı; %3'ten fazla ve %95'ten az
     * izlenmişse. Yeni izleme üstte, en çok 20 kayıt.
     */
    @Query(
        """
        SELECT * FROM watch_history h
        WHERE h.lastWatchedAt = (
            SELECT MAX(lastWatchedAt) FROM watch_history WHERE tmdbId = h.tmdbId AND type = h.type
        )
        AND h.durationMs > 0
        AND h.positionMs * 100 >= h.durationMs * 3
        AND h.positionMs * 100 <= h.durationMs * 95
        ORDER BY h.lastWatchedAt DESC
        LIMIT 20
        """,
    )
    fun observeContinueWatching(): Flow<List<WatchHistoryEntity>>

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
