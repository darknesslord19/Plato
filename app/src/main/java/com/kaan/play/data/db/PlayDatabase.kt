package com.kaan.play.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AddonEntity::class, PluginEntity::class, WatchHistoryEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PlayDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
}
