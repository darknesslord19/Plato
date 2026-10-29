package com.kaan.play.data.watchlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** "Sonra izleyeceğim" listesindeki içerik. [type]: `movie` / `tv`. */
@Serializable
data class WatchlistItem(
    val tmdbId: Int,
    val type: String,
    val title: String,
    val posterPath: String? = null,
    val addedAt: Long = 0L,
) {
    val mediaType: MediaType get() = MediaType.fromTmdb(type) ?: MediaType.Movie
}

/** Liste kuralları (Android'den bağımsız, birim testli). En yeni eklenen en üstte; aynı içerik tek kez. */
object WatchlistRules {
    const val MAX_ITEMS = 500
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(WatchlistItem.serializer())

    fun decode(raw: String?): List<WatchlistItem> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun encode(items: List<WatchlistItem>): String = json.encodeToString(serializer, items)

    fun contains(items: List<WatchlistItem>, type: MediaType, id: Int): Boolean =
        items.any { it.tmdbId == id && it.type == type.tmdbPath }

    fun add(items: List<WatchlistItem>, item: WatchlistItem): List<WatchlistItem> =
        (listOf(item) + items.filterNot { it.tmdbId == item.tmdbId && it.type == item.type }).take(MAX_ITEMS)

    fun remove(items: List<WatchlistItem>, type: MediaType, id: Int): List<WatchlistItem> =
        items.filterNot { it.tmdbId == id && it.type == type.tmdbPath }
}

/** İzleme listesi; profil başına ayrı tutulur (DataStore `watchlist_<profil>`). */
class WatchlistStore(
    private val store: DataStore<Preferences>,
    private val profileId: Flow<String>,
) {
    private fun key(profile: String) = stringPreferencesKey("watchlist_" + profile.ifEmpty { "legacy" })

    val items: Flow<List<WatchlistItem>> =
        combine(store.data, profileId) { prefs, p -> WatchlistRules.decode(prefs[key(p)]) }.distinctUntilChanged()

    fun contains(type: MediaType, id: Int): Flow<Boolean> =
        items.map { WatchlistRules.contains(it, type, id) }.distinctUntilChanged()

    suspend fun set(type: MediaType, id: Int, title: String, posterPath: String?, inList: Boolean) {
        val k = key(profileId.first())
        store.edit { prefs ->
            val current = WatchlistRules.decode(prefs[k])
            val next = if (inList) {
                WatchlistRules.add(current, WatchlistItem(id, type.tmdbPath, title, posterPath, System.currentTimeMillis()))
            } else {
                WatchlistRules.remove(current, type, id)
            }
            prefs[k] = WatchlistRules.encode(next)
        }
    }
}
