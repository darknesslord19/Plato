package com.kaan.play.data.download

import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * İndirme sırasında saklanan altyazı. [url] özgün adrestir (oynatıcıda parçanın kimliği ve senkron kaydırmasının anahtarı
 * olarak aynen kalır, böylece çevrimiçi/çevrimdışı aynı kaydırma geçerli olur); [file] UTF-8'e çevrilmiş metnin tam yolu
 * ([DownloadFiles.subtitleFile] ile aynı yer); [language] iki harfli temel dil (bilinmiyorsa null); [origin] kaynak adı.
 */
@Serializable
data class DownloadSubtitle(val url: String, val language: String?, val origin: String, val file: String)

/** İndirme satırı için yardımcılar: JSON alanlarının çözülmesi ve oynatma hedefi. */
object DownloadCodec {
    private val headersSerializer = MapSerializer(String.serializer(), String.serializer())
    private val subtitlesSerializer = ListSerializer(DownloadSubtitle.serializer())

    fun encodeHeaders(headers: Map<String, String>): String = PlayJson.encodeToString(headersSerializer, headers)

    /** Bozuk kayıt boş sözlüğe düşer (indirme başlıksız denenir). */
    fun decodeHeaders(json: String): Map<String, String> =
        try {
            PlayJson.decodeFromString(headersSerializer, json)
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }

    fun encodeSubtitles(subtitles: List<DownloadSubtitle>): String = PlayJson.encodeToString(subtitlesSerializer, subtitles)

    fun decodeSubtitles(json: String): List<DownloadSubtitle> =
        try {
            PlayJson.decodeFromString(subtitlesSerializer, json)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
}

/** İndirilen satırın oynatma hedefi: geçmişe yazılan kimlik çevrimiçi oynatmayla birebir aynıdır (aynı `watch_history` satırı). */
fun DownloadEntity.toTarget(): PlayTarget {
    val isSeries = type == MediaType.Tv
    return PlayTarget(
        StreamRequest(tmdbId, imdbId, type, season.takeIf { isSeries }, episode.takeIf { isSeries }),
        title,
        posterPath,
    )
}

/** Kaynak listesinde bir satırın indirme simgesi durumu. */
enum class DownloadBadge {
    /** Hiç indirilmedi: dokununca indirir. */
    None,

    /** Sırada/iniyor/duraklatıldı: simge ilerleme halkası, dokunmak bir şey yapmaz (yönetim Kütüphane'de). */
    Active,

    /** İndirildi. */
    Done,

    /** Başarısız: dokununca yeniden dener. */
    Failed,
}
