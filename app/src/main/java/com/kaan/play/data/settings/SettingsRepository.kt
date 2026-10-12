package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Koyu tema varsayılandır. */
enum class ThemeMode { Dark, Light, System }

/** DataStore'daki uygulama ayarları. Bozuk/bilinmeyen değerler varsayılana düşer. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: DEFAULT_THEME
    }

    /** Addon başına kaynak isteği zaman aşımı (saniye). */
    val sourceTimeoutSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[TIMEOUT]?.takeIf { it in TIMEOUT_OPTIONS } ?: DEFAULT_TIMEOUT_SECONDS
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setSourceTimeoutSeconds(seconds: Int) {
        require(seconds in TIMEOUT_OPTIONS) { "Desteklenmeyen zaman aşımı: $seconds" }
        store.edit { it[TIMEOUT] = seconds }
    }

    companion object {
        val DEFAULT_THEME = ThemeMode.Dark
        const val DEFAULT_TIMEOUT_SECONDS = 15
        val TIMEOUT_OPTIONS = listOf(10, 15, 30, 60)

        private val THEME = stringPreferencesKey("theme_mode")
        private val TIMEOUT = intPreferencesKey("source_timeout_seconds")
    }
}
