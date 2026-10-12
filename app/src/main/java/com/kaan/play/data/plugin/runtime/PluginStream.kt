package com.kaan.play.data.plugin.runtime

import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamLabels
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Eklentinin döndürdüğü tek akış (docs/nuvio-plugin-format.md §2.2). Alanların çoğu isteğe bağlı ve serbest metindir. */
data class PluginStream(
    val url: String,
    val name: String?,
    val title: String?,
    /** Serbest metin: `Auto`, `1080p`, `1080P`, `1080p • HLS`... */
    val quality: String?,
    val size: String?,
    val language: String?,
    val headers: Map<String, String>,
    val subtitles: List<PluginSubtitle>,
) {
    companion object {
        private const val MAX_TEXT = 500
        private const val MAX_SUBTITLES = 40

        /**
         * `url` zorunlu (metin ya da `{url}` nesnesi) ve http(s) olmalı. Metin alanlarındaki **görünmez sıralama karakterleri**
         * (`U+FEFF`, `U+200B..200F`, `U+2060`; eklentiler adı bunlarla sıralatıyor) temizlenir, başlık değerlerinde satır sonu
         * barındıranlar atılır. Bozuk tek kayıt null döner ve yalnızca kendini düşürür.
         */
        fun fromJson(obj: JsonObject): PluginStream? {
            val rawUrl = when (val u = obj["url"]) {
                is JsonPrimitive -> u.contentOrNull
                is JsonObject -> (u["url"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }?.trim() ?: return null
            if (rawUrl.contains("[object") || !isHttp(rawUrl)) return null

            return PluginStream(
                url = rawUrl,
                name = obj.text("name"),
                title = obj.text("title"),
                quality = obj.text("quality"),
                size = obj.text("size"),
                language = obj.text("language"),
                headers = (obj["headers"] as? JsonObject).headerMap(),
                subtitles = (obj["subtitles"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .asSequence().mapNotNull { (it as? JsonObject)?.let(PluginSubtitle::fromJson) }
                    .take(MAX_SUBTITLES).toList(),
            )
        }

        internal fun isHttp(u: String) = u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)

        internal fun JsonObject.text(key: String): String? {
            val v = (this[key] as? JsonPrimitive)?.contentOrNull ?: return null
            return clean(v)
        }

        internal fun clean(value: String): String? =
            value.filterNot { it in INVISIBLE }.trim().take(MAX_TEXT).takeIf { it.isNotEmpty() && !it.contains("[object") }

        internal fun JsonObject?.headerMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for ((k, v) in this) {
                val value = (v as? JsonPrimitive)?.contentOrNull ?: continue
                if (k.isBlank() || listOf(k, value).any { s -> s.any { it == '\r' || it == '\n' || it == '\u0000' } }) continue
                out[k] = value
            }
            return out
        }

        private val INVISIBLE = ('​'..'‏').toSet() + '﻿' + '⁠' + ('‪'..'‮').toSet()
    }
}

data class PluginSubtitle(val url: String, val language: String?, val name: String?, val headers: Map<String, String>) {
    companion object {
        fun fromJson(obj: JsonObject): PluginSubtitle? {
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            if (!PluginStream.isHttp(url)) return null
            fun s(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.let(PluginStream::clean)
            return PluginSubtitle(
                url = url,
                language = s("language") ?: s("lang"),
                name = s("name"),
                headers = with(PluginStream) { (obj["headers"] as? JsonObject).headerMap() },
            )
        }
    }
}

// ---- Bizim modele çeviri ----

/** Eklenti akışını Play'in oynatılabilir akışına çevirir: kalite/boyut/dil serbest metinden çıkarılır (mevcut kurallarla). */
fun PluginStream.toPlayable(source: StreamSource): PlayableStream {
    val text = listOfNotNull(quality, title, name).joinToString(" ")
    return PlayableStream(
        source = source,
        url = url,
        resolution = StreamLabels.resolution(text),
        sizeBytes = size?.let(StreamLabels::sizeBytes) ?: StreamLabels.sizeBytes(text),
        headers = headers,
        subtitles = subtitles.map { StreamSubtitle(it.url, it.language) },
        language = language?.let { StreamLabels.language(it) } ?: StreamLabels.language(text),
    )
}

/** Çalıştırma sonucunu kaynak toplama modeline çevirir: başarı (boş olabilir) ya da "yanıt vermedi". */
fun PluginOutcome.toStreamResult(source: StreamSource): StreamResult = when (this) {
    is PluginOutcome.Success -> StreamResult.Found(source, streams.map { it.toPlayable(source) })
    is PluginOutcome.Failure -> StreamResult.Failed(
        source,
        if (reason == PluginOutcome.Reason.Timeout) FailureReason.Timeout else FailureReason.InvalidResponse,
    )
}
