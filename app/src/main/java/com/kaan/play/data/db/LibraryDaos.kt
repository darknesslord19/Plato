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

    /** Kütüphane > Geçmiş: tüm kayıtlar (film ve her bölüm ayrı), son izlenen üstte. */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC, id DESC LIMIT 200")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    /** (tmdbId, type, season, episode) benzersizdir: aynı bölüm yeniden kaydedilince satır yenilenir. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId AND type = :type AND season = :season AND episode = :episode")
    suspend fun find(tmdbId: Int, type: MediaType, season: Int, episode: Int): WatchHistoryEntity?
}

@Dao
interface SubtitleDao {

    @Query("SELECT language FROM subtitle_language WHERE contentKey = :contentKey")
    suspend fun language(contentKey: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLanguage(entity: SubtitleLanguageEntity)

    @Query("SELECT offsetMs FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey")
    suspend fun offset(contentKey: String, subtitleKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOffset(entity: SubtitleOffsetEntity)

    @Query("DELETE FROM subtitle_offset WHERE contentKey = :contentKey AND subtitleKey = :subtitleKey")
    suspend fun clearOffset(contentKey: String, subtitleKey: String)

    /** Tablo sınırsız büyümesin: en yeni [keep] kaydırma kalır. */
    @Query(
        "DELETE FROM subtitle_offset WHERE rowid NOT IN " +
            "(SELECT rowid FROM subtitle_offset ORDER BY updatedAt DESC LIMIT :keep)",
    )
    suspend fun pruneOffsets(keep: Int)
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
