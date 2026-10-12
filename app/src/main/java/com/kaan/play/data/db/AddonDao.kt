package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AddonDao {

    @Query("SELECT * FROM addons WHERE type = :type ORDER BY addedAt, id")
    fun observeOfType(type: AddonType): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons WHERE enabled = 1 AND type = :type ORDER BY addedAt, id")
    suspend fun enabledOfType(type: AddonType): List<AddonEntity>

    @Query("SELECT COUNT(*) FROM addons WHERE sourceUrl = :url")
    suspend fun countBySourceUrl(url: String): Int

    @Query("SELECT COUNT(*) FROM addons WHERE type = :type")
    suspend fun countOfType(type: AddonType): Int

    /** Aynı [AddonEntity.sourceUrl] zaten varsa eklemez ve -1 döner. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AddonEntity): Long

    @Query("UPDATE addons SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun delete(id: Long)
}
