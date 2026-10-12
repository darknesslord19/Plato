package com.kaan.play.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kaan.play.data.model.MediaType

/**
 * Eklenti kaynağının türü. Faz 1'de yalnızca [NUVIO_ADDON] çalışır; diğer ikisi şema olarak hazır
 * (Faz 2: Nuvio plugin deposu, Faz 3: CloudStream deposu).
 */
enum class AddonType { NUVIO_ADDON, NUVIO_PLUGIN_REPO, CLOUDSTREAM_REPO }

/**
 * Kullanıcının eklediği kaynak. [sourceUrl]: addon için manifest.json adresi, depolar için depo adresi.
 * [manifestJson]: doğrulanmış manifest'in kendi modelimizle kodlanmış hali (her zaman çözülebilir).
 */
@Entity(tableName = "addons", indices = [Index(value = ["sourceUrl"], unique = true)])
data class AddonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AddonType,
    val sourceUrl: String,
    val addonId: String,
    val name: String,
    val version: String?,
    val description: String?,
    val logoUrl: String?,
    val enabled: Boolean = true,
    val manifestJson: String,
    val addedAt: Long,
)

/**
 * Bir depo ([AddonType.NUVIO_PLUGIN_REPO] / [AddonType.CLOUDSTREAM_REPO]) içindeki tek tek plugin'ler.
 * Faz 1'de tablo boş kalır ve kullanılmaz; Faz 2/3 sütunları gerekirse genişletir.
 */
@Entity(
    tableName = "plugins",
    foreignKeys = [
        ForeignKey(
            entity = AddonEntity::class,
            parentColumns = ["id"],
            childColumns = ["repoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("repoId"), Index(value = ["repoId", "pluginId"], unique = true)],
)
data class PluginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val pluginId: String,
    val name: String,
    val version: String?,
    val scriptUrl: String,
    val enabled: Boolean = true,
    val installedAt: Long,
)

/**
 * İzleme geçmişi: içerik başına tek satır. Filmde [season] ve [episode] 0'dır (NULL, benzersiz
 * indekste ayrı sayılacağı için kullanılmaz). [title]/[posterPath], "devam et" kartı için anlık kopyadır.
 * Davranışı 7. adımda gelir; şema şimdiden sabit.
 */
@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["tmdbId", "type", "season", "episode"], unique = true),
        Index("lastWatchedAt"),
    ],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val type: MediaType,
    val season: Int = 0,
    val episode: Int = 0,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val title: String,
    val posterPath: String?,
)

@Entity(tableName = "favorites", primaryKeys = ["tmdbId", "type"])
data class FavoriteEntity(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val addedAt: Long,
)
