package com.kaan.play.data.stream

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Cevap `{"streams":[...]}` biçiminde değil (ya da JSON değil). */
class InvalidStreamResponse : Exception()

/**
 * Addon'un `stream/...json` cevabını [PlayableStream] listesine çevirir.
 * Yalnızca `url` alanı http(s) olan akışlar alınır (`infoHash`, `ytId`, `externalUrl` yok sayılır).
 * Tek bir bozuk akış kaydı yalnızca kendini düşürür, cevabın geri kalanı kullanılır.
 */
object StreamParser {

    fun parse(source: StreamSource, body: String): List<PlayableStream> {
        val root = try {
            PlayJson.parseToJsonElement(body)
        } catch (_: IllegalArgumentException) { // SerializationException de buradan geçer
            throw InvalidStreamResponse()
        }
        val array = (root as? JsonObject)?.get("streams") as? JsonArray ?: throw InvalidStreamResponse()

        return array.mapNotNull { element ->
            val dto = try {
                PlayJson.decodeFromJsonElement(StreamDto.serializer(), element)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            dto.toPlayable(source)
        }
    }

    private fun StreamDto.toPlayable(source: StreamSource): PlayableStream? {
        val link = url?.trim()?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            ?: return null
        val text = listOfNotNull(name, title, description, behaviorHints?.filename).joinToString(" ")
        return PlayableStream(
            source = source,
            url = link,
            resolution = StreamLabels.resolution(text),
            sizeBytes = behaviorHints?.videoSize?.takeIf { it > 0 }?.toLong() ?: StreamLabels.sizeBytes(text),
            headers = behaviorHints?.proxyHeaders?.request.orEmpty().filter { (k, v) -> isSafeHeader(k, v) },
            subtitles = subtitles.orEmpty().mapNotNull { s ->
                val subUrl = s.url?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    ?: return@mapNotNull null
                StreamSubtitle(subUrl, s.lang?.takeIf { it.isNotBlank() })
            },
        )
    }

    /** Üstbilgi adı/değeri satır sonu içeremez (başlık enjeksiyonu). */
    private fun isSafeHeader(name: String, value: String): Boolean =
        name.isNotBlank() && listOf(name, value).none { s -> s.any { it == '\r' || it == '\n' || it == '\u0000' } }
}

/** Akış adı/başlığı/açıklamasındaki serbest metinden kalite ve boyut çıkarır. */
object StreamLabels {

    private val RESOLUTION = Regex("""\b(2160p|4k|uhd|1440p|1080p|1080i|720p|576p|480p|360p)\b""", RegexOption.IGNORE_CASE)
    private val SIZE = Regex("""\b(\d+(?:[.,]\d+)?)\s?(TB|GB|MB|TiB|GiB|MiB)\b""", RegexOption.IGNORE_CASE)

    fun resolution(text: String): Int? {
        val token = RESOLUTION.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        return when (token) {
            "2160p", "4k", "uhd" -> 2160
            "1440p" -> 1440
            "1080p", "1080i" -> 1080
            "720p" -> 720
            "576p", "480p" -> 480
            else -> 360
        }
    }

    fun sizeBytes(text: String): Long? {
        val match = SIZE.find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit: Double = when (match.groupValues[2].lowercase()) {
            "tb" -> 1e12
            "gb" -> 1e9
            "mb" -> 1e6
            "tib" -> 1024.0 * 1024 * 1024 * 1024
            "gib" -> 1024.0 * 1024 * 1024
            else -> 1024.0 * 1024
        }
        return Math.round(number * unit).takeIf { it > 0 }
    }
}

@Serializable
private data class StreamDto(
    val url: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val subtitles: List<SubtitleDto>? = null,
    val behaviorHints: HintsDto? = null,
)

@Serializable
private data class SubtitleDto(val id: String? = null, val url: String? = null, val lang: String? = null)

@Serializable
private data class HintsDto(
    val videoSize: Double? = null,
    val filename: String? = null,
    val proxyHeaders: ProxyHeadersDto? = null,
)

@Serializable
private data class ProxyHeadersDto(val request: Map<String, String>? = null)
