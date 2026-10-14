package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Kullanıcının film/dizi başına **elle** seçtiği kaynak (eklenti, sunucu, tür). Sonraki açılışta ve dizinin sonraki bölümünde bu
 * kaynak kendi grubunda (dublajlı/altyazılı) önce denenir ([SourceRanking]). Ayarlarla aynı DataStore'da tek bir JSON alanı olarak
 * tutulur; en çok [MAX_ENTRIES] içerik, en eskisi düşer. Okuma/yazma hataları sessizce geçilir (oynatmayı etkilemez).
 */
class SourceMemory(private val store: DataStore<Preferences>) {

    private val serializer = MapSerializer(String.serializer(), SourcePref.serializer())

    suspend fun get(request: StreamRequest): SourcePref? = read()[keyOf(request)]

    suspend fun remember(request: StreamRequest, stream: PlayableStream) {
        val pref = SourcePref(
            addonId = stream.source.addonId,
            addonName = stream.source.addonName,
            host = stream.url.toHttpUrlOrNull()?.host,
            torrent = TorrentLinks.isMagnet(stream.url),
        )
        val key = keyOf(request)
        val next = LinkedHashMap(read()).also {
            it.remove(key) // en sona (en yeni) taşı
            it[key] = pref
            while (it.size > MAX_ENTRIES) it.remove(it.keys.first())
        }
        try {
            store.edit { it[KEY] = PlayJson.encodeToString(serializer, next) }
        } catch (_: java.io.IOException) {
        }
    }

    private suspend fun read(): Map<String, SourcePref> = try {
        val text = store.data.first()[KEY] ?: return emptyMap()
        PlayJson.decodeFromString(serializer, text)
    } catch (_: java.io.IOException) {
        emptyMap()
    } catch (_: IllegalArgumentException) { // bozuk JSON (SerializationException dahil)
        emptyMap()
    }

    private fun keyOf(request: StreamRequest) = "${request.type.name}:${request.tmdbId}"

    companion object {
        const val MAX_ENTRIES = 60
        private val KEY = stringPreferencesKey("source_memory")
    }
}
