package com.kaan.play.data.subtitle

import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Altyazı **arama sonuçlarının** film/bölüm bazında önbelleği: aynı film tekrar açılınca (ya da aynı oturumda panel yeniden
 * açılınca) sağlayıcıya yeniden sorulmaz (SubDL ücretsiz anahtarı günde 2000 istekle sınırlı). İndirilen altyazı metinleri ayrıca
 * [SubtitleLoader]'ın adres bazlı önbelleğindedir.
 *
 * Dosyalar `cacheDir` altında (sistem boşaltabilir). Dolu sonuç [TTL_MS] (7 gün), boş sonuç [EMPTY_TTL_MS] (10 dk) geçerlidir;
 * hata (ağ yok, anahtar geçersiz …) önbelleğe **yazılmaz**. Dizin [MAX_FILES] dosyayla sınırlıdır.
 */
class SubtitleSearchCache(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private class Entry(val at: Long, val tracks: List<SubtitleTrack>)

    private val serializer = Entry.serializer()

    /**
     * [key] için taze kayıt varsa onu döner; yoksa [fetch] çağrılır. [fetch] null dönerse (hata) null döner ve bir şey yazılmaz
     * (çağıran "yanıt vermedi" gösterebilir); sonuç (boş olsa da) yazılır.
     */
    suspend fun getOrFetch(key: String, fetch: suspend () -> List<SubtitleTrack>?): List<SubtitleTrack>? {
        read(key)?.let { return it }
        val fresh = fetch() ?: return null
        write(key, fresh)
        return fresh
    }

    private suspend fun read(key: String): List<SubtitleTrack>? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        try {
            val text = file.readText(Charsets.UTF_8)
            val entry = PlayJson.decodeFromString(serializer, text)
            val tracks = entry.tracks.map { it.copy(url = stripApiKey(it.url)) }
            // Eski sürümlerden kalan, anahtar içeren dosyayı temizlenmiş hâliyle yeniden yaz.
            if (API_KEY_PARAM in text) write(key, tracks, entry.at)
            val ttl = if (tracks.isEmpty()) EMPTY_TTL_MS else TTL_MS
            if (clock() - entry.at in 0..ttl) tracks else null
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) { // bozuk dosya
            null
        }
    }

    private suspend fun write(key: String, tracks: List<SubtitleTrack>, at: Long = clock()) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val file = fileFor(key)
            val temp = File(dir, file.name + ".tmp")
            val clean = tracks.map { it.copy(url = stripApiKey(it.url)) } // anahtar asla diske yazılmaz
            temp.writeText(PlayJson.encodeToString(serializer, Entry(at, clean)), Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                // Hedef varken rename bazı dosya sistemlerinde (Windows) başarısız olur: eskiyi silip bir kez daha dene.
                file.delete()
                if (!temp.renameTo(file)) temp.delete()
            }
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_FILES)
                ?.forEach { it.delete() }
        } catch (_: IOException) {
            // Yazılamazsa arama çalışır; yalnızca bir sonraki açılışta yeniden sorulur.
        }
    }

    private fun fileFor(key: String) = File(dir, MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) } + ".json")

    companion object {
        const val TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val EMPTY_TTL_MS = 10L * 60 * 1000
        const val MAX_FILES = 300
        const val API_KEY_PARAM = "api_key"
    }
}

/** Adresteki `api_key` sorgu parametresini siler (SubDL indirme adreslerine anahtarı kendisi ekler); adres çözümlenemezse olduğu gibi döner. */
internal fun stripApiKey(url: String): String {
    if (!url.contains(SubtitleSearchCache.API_KEY_PARAM)) return url
    return url.toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters(SubtitleSearchCache.API_KEY_PARAM)?.build()?.toString() ?: url
}

/** Arama anahtarı: sağlayıcı + içerik (IMDb, tür, sezon/bölüm) + istenen diller. */
internal fun searchKey(provider: String, type: String, imdb: String, season: Int?, episode: Int?, languages: List<String>) =
    listOf(provider, type, imdb, season ?: 0, episode ?: 0, languages.joinToString(",")).joinToString("|")
