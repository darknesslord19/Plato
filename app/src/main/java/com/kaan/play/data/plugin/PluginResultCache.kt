package com.kaan.play.data.plugin

import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.PluginStream

/**
 * Kısa ömürlü (varsayılan 5 dk) bellek içi sonuç önbelleği: aynı (eklenti, sürüm, içerik, sezon/bölüm, ayarlar) için
 * kaynak listesine geri dönüşte eklenti yeniden çalıştırılmasın. Diske yazılmaz; uygulama kapanınca gider. En eski girdi
 * [maxEntries] aşılınca atılır. Zaman kaynağı testler için enjekte edilir.
 */
class PluginResultCache(
    private val ttlMs: Long = 5 * 60 * 1000L,
    private val maxEntries: Int = 200,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** [settingsHash]: ayar değişince eski sonuç kullanılmasın. */
    data class Key(val pluginRowId: Long, val version: String?, val request: PluginRequest, val settingsHash: Int)

    private class Entry(val at: Long, val streams: List<PluginStream>)

    private val map = LinkedHashMap<Key, Entry>()

    @Synchronized
    fun get(key: Key): List<PluginStream>? {
        val e = map[key] ?: return null
        if (now() - e.at >= ttlMs) {
            map.remove(key)
            return null
        }
        return e.streams
    }

    @Synchronized
    fun put(key: Key, streams: List<PluginStream>) {
        map.remove(key)
        map[key] = Entry(now(), streams)
        while (map.size > maxEntries) map.remove(map.keys.first())
    }

    @Synchronized
    fun clear() = map.clear()
}
