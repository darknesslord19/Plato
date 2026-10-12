package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** Altyazı görünümü; oynatıcı ve Ayarlar aynı akıştan okur, değişiklik anında yansır. */
    val subtitleStyle: Flow<SubtitleStyle> = store.data.map { it.toSubtitleStyle() }.distinctUntilChanged()

    /** Açıkken yarım kalan içerik soru sorulmadan kaldığı yerden başlar. Varsayılan açık. */
    val alwaysResume: Flow<Boolean> = store.data.map { it[ALWAYS_RESUME] ?: DEFAULT_ALWAYS_RESUME }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setSourceTimeoutSeconds(seconds: Int) {
        require(seconds in TIMEOUT_OPTIONS) { "Desteklenmeyen zaman aşımı: $seconds" }
        store.edit { it[TIMEOUT] = seconds }
    }

    /** Mevcut görünümü okuyup değiştirir ve tek yazmada kaydeder (aynı anda iki değişiklik birbirini ezmez). */
    suspend fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        store.edit { prefs ->
            val next = transform(prefs.toSubtitleStyle())
            prefs[SUB_SIZE] = next.size.name
            prefs[SUB_BOLD] = next.bold
            prefs[SUB_ITALIC] = next.italic
            prefs[SUB_COLOR] = next.color.name
            prefs[SUB_BACKGROUND] = next.background.name
        }
    }

    suspend fun setAlwaysResume(enabled: Boolean) {
        store.edit { it[ALWAYS_RESUME] = enabled }
    }

    private fun Preferences.toSubtitleStyle(): SubtitleStyle {
        val d = SubtitleStyle()
        return SubtitleStyle(
            size = SubtitleSize.entries.firstOrNull { it.name == this[SUB_SIZE] } ?: d.size,
            bold = this[SUB_BOLD] ?: d.bold,
            italic = this[SUB_ITALIC] ?: d.italic,
            color = SubtitleColor.entries.firstOrNull { it.name == this[SUB_COLOR] } ?: d.color,
            background = SubtitleBackground.entries.firstOrNull { it.name == this[SUB_BACKGROUND] } ?: d.background,
        )
    }

    companion object {
        val DEFAULT_THEME = ThemeMode.Dark
        const val DEFAULT_TIMEOUT_SECONDS = 15
        const val DEFAULT_ALWAYS_RESUME = true
        val TIMEOUT_OPTIONS = listOf(10, 15, 30, 60)

        private val THEME = stringPreferencesKey("theme_mode")
        private val TIMEOUT = intPreferencesKey("source_timeout_seconds")
        private val ALWAYS_RESUME = booleanPreferencesKey("always_resume")
        private val SUB_SIZE = stringPreferencesKey("subtitle_size")
        private val SUB_BOLD = booleanPreferencesKey("subtitle_bold")
        private val SUB_ITALIC = booleanPreferencesKey("subtitle_italic")
        private val SUB_COLOR = stringPreferencesKey("subtitle_color")
        private val SUB_BACKGROUND = stringPreferencesKey("subtitle_background")
    }
}
