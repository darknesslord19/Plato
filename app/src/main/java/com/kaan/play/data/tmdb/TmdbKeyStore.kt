package com.kaan.play.data.tmdb

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** TMDB v3 API anahtarı kuralları (Android'den bağımsız, birim testli). */
object TmdbKeyRules {
    private val V3_KEY = Regex("^[0-9a-fA-F]{32}$")

    /**
     * Yapıştırılan metni anahtara çevirir; geçersizse null. Kullanıcı bazen tüm adresi ya da `api_key=` ile
     * yapıştırır; içinden 32 haneli anahtar çıkarılır. v4 "okuma erişim belirteci" (eyJ...) kabul edilmez:
     * Play istekleri v3 `api_key` parametresiyle yapar.
     */
    fun normalize(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (V3_KEY.matches(text)) return text.lowercase()
        val fromParam = Regex("api_key=([0-9a-fA-F]{32})").find(text)?.groupValues?.get(1)
        return fromParam?.lowercase()
    }

    /** Ekranda anahtarın tamamı gösterilmez: "a608…6450". */
    fun mask(key: String): String = if (key.length <= 8) "••••" else key.take(4) + "…" + key.takeLast(4)
}

/**
 * Kullanıcının uygulama içinden girdiği TMDB anahtarı (Ayarlar > Eklentiler). Girilmişse derlemedeki anahtarın yerine
 * geçer; boşsa derlemedeki ([builtIn]) kullanılır. Anahtar DataStore'da saklanır, hiçbir yere loglanmaz; eklentilere
 * yine yalnızca yer tutucu görünür (gerçek anahtar ağ köprüsünde konur).
 */
class TmdbKeyStore(
    private val store: DataStore<Preferences>,
    private val builtIn: String,
    scope: CoroutineScope,
) {
    enum class Source { User, BuiltIn, None }

    /** Kullanıcının girdiği anahtar (yoksa null). Açılışta bir kez eşzamanlı okunur ki ilk istekte hazır olsun. */
    val userKey: StateFlow<String?> = store.data
        .map { TmdbKeyRules.normalize(it[KEY]) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initialUserKey())

    private fun initialUserKey(): String? = try {
        runBlocking { TmdbKeyRules.normalize(store.data.first()[KEY]) }
    } catch (_: Exception) {
        null
    }

    /** İsteklerde kullanılacak anahtar: önce kullanıcınınki, yoksa derlemedeki (boş olabilir). */
    fun current(): String = userKey.value ?: builtIn

    fun source(user: String? = userKey.value): Source = when {
        user != null -> Source.User
        builtIn.isNotBlank() -> Source.BuiltIn
        else -> Source.None
    }

    /** Anahtarı kaydeder; [input] boşsa siler. Geçersiz anahtarda false döner ve hiçbir şey yazmaz. */
    suspend fun set(input: String?): Boolean {
        if (input.isNullOrBlank()) {
            store.edit { it.remove(KEY) }
            return true
        }
        val key = TmdbKeyRules.normalize(input) ?: return false
        store.edit { it[KEY] = key }
        return true
    }

    private companion object {
        val KEY = stringPreferencesKey("tmdb_user_api_key")
    }
}
