package com.kaan.play.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AddonEntity::class, PluginEntity::class, WatchHistoryEntity::class, FavoriteEntity::class,
        SubtitleLanguageEntity::class, SubtitleOffsetEntity::class, DownloadEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class PlayDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun pluginDao(): PluginDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun subtitleDao(): SubtitleDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /**
         * 4 -> 5 (Faz D / Aşama 2): watch_history tablosuna eklenti doğrudan izleme alanları
         * (`pluginId`, `dataUrl`, `contentUrl`). Mevcut kayıtlarda null kalır.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `pluginId` TEXT")
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `dataUrl` TEXT")
                db.execSQL("ALTER TABLE `watch_history` ADD COLUMN `contentUrl` TEXT")
            }
        }

        /**
         * 3 -> 4 (Faz 4/B): indirilenler tablosu. Mevcut tablolara dokunulmaz. SQL, Room'un dışa aktardığı `schemas/4.json`
         * ile birebir aynıdır.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tmdbId` INTEGER NOT NULL, `imdbId` TEXT, `type` TEXT NOT NULL, `season` INTEGER NOT NULL, " +
                        "`episode` INTEGER NOT NULL, `title` TEXT NOT NULL, `posterPath` TEXT, `sourceName` TEXT NOT NULL, " +
                        "`sourceUrl` TEXT NOT NULL, `headersJson` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`subtitlesJson` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_tmdbId_type_season_episode` " +
                        "ON `downloads` (`tmdbId`, `type`, `season`, `episode`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
            }
        }

        /**
         * 2 -> 3 (Faz 4/A): altyazı dili ve senkron kaydırması için iki yeni tablo. Mevcut tablolara dokunulmaz.
         * SQL, Room'un dışa aktardığı `schemas/3.json` ile birebir aynıdır.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_language` (`contentKey` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`contentKey`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_offset` (`contentKey` TEXT NOT NULL, `subtitleKey` TEXT NOT NULL, " +
                        "`offsetMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`contentKey`, `subtitleKey`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_offset_updatedAt` ON `subtitle_offset` (`updatedAt`)")
            }
        }

        /**
         * 1 -> 2 (Faz 2): `plugins` tablosu genişledi. Tablo Faz 1'de hiç kullanılmadığı için boştur; veri taşımaya gerek
         * yok, tablo yeniden kurulur. Diğer tablolara dokunulmaz (geçmiş, favoriler, addon'lar korunur).
         * SQL, Room'un dışa aktardığı `schemas/2.json` ile birebir aynıdır.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `plugins`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`repoId` INTEGER NOT NULL, `pluginId` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT, " +
                        "`description` TEXT, `filename` TEXT NOT NULL, `scriptUrl` TEXT NOT NULL, " +
                        "`supportedTypes` TEXT NOT NULL, `contentLanguage` TEXT NOT NULL, `hasSettings` INTEGER NOT NULL, " +
                        "`sha256` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`installedAt` INTEGER NOT NULL, FOREIGN KEY(`repoId`) REFERENCES `addons`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plugins_repoId` ON `plugins` (`repoId`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plugins_repoId_pluginId` ON `plugins` (`repoId`, `pluginId`)",
                )
            }
        }
    }
}
