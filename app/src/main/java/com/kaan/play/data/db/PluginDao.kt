package com.kaan.play.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {

    @Query("SELECT * FROM plugins ORDER BY repoId, name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE repoId = :repoId ORDER BY name COLLATE NOCASE, id")
    suspend fun ofRepo(repoId: Long): List<PluginEntity>

    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun byId(id: Long): PluginEntity?

    /** Açık (kullanıcının bilinçle açtığı) kazıyıcılar; 2. turda kaynak toplama bunları sorar. */
    @Query("SELECT * FROM plugins WHERE enabled = 1 ORDER BY repoId, name COLLATE NOCASE, id")
    suspend fun enabled(): List<PluginEntity>

    /** Belirli türdeki depolara ait tüm eklentileri gözlemler (Faz 3: CLOUDSTREAM_REPO). */
    @Query(
        "SELECT p.* FROM plugins p " +
            "INNER JOIN addons a ON a.id = p.repoId " +
            "WHERE a.type = :type " +
            "ORDER BY p.repoId, p.name COLLATE NOCASE, p.id",
    )
    fun observeOfType(type: AddonType): Flow<List<PluginEntity>>

    /** Belirli türdeki depolara ait açık eklentiler (Faz 3: CS çalışma zamanı). */
    @Query(
        "SELECT p.* FROM plugins p " +
            "INNER JOIN addons a ON a.id = p.repoId " +
            "WHERE a.type = :type AND p.enabled = 1 " +
            "ORDER BY p.repoId, p.name COLLATE NOCASE, p.id",
    )
    suspend fun enabledOfType(type: AddonType): List<PluginEntity>

    /** Aynı (depo, kazıyıcı kimliği) varsa eklemez ve -1 döner. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PluginEntity): Long

    /** Güncellemede üst bilgi ve özet değişir, [PluginEntity.enabled] ve kimlik korunur. */
    @Query(
        "UPDATE plugins SET name = :name, version = :version, description = :description, filename = :filename, " +
            "scriptUrl = :scriptUrl, supportedTypes = :supportedTypes, contentLanguage = :contentLanguage, " +
            "hasSettings = :hasSettings, sha256 = :sha256, sizeBytes = :sizeBytes WHERE id = :id",
    )
    suspend fun updateInfo(
        id: Long,
        name: String,
        version: String?,
        description: String?,
        filename: String,
        scriptUrl: String,
        supportedTypes: String,
        contentLanguage: String,
        hasSettings: Boolean,
        sha256: String,
        sizeBytes: Long,
    )

    @Query("UPDATE plugins SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE plugins SET enabled = :enabled WHERE repoId = :repoId")
    suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean)

    @Query("UPDATE plugins SET enabled = :enabled WHERE repoId IN (SELECT id FROM addons WHERE type = :type)")
    suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean)

    @Query("UPDATE plugins SET enabled = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun delete(id: Long)
}
