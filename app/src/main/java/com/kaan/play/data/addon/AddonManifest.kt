package com.kaan.play.data.addon

import kotlinx.serialization.Serializable

/**
 * Doğrulanmış addon manifest'i (yalnızca Play'in kullandığı alanlar).
 * [AddonManifestParser] dışında doğrudan kurulmaz; Room'da bu modelin JSON'u saklanır.
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val types: List<String>,
    val resources: List<ManifestResource>,
    val idPrefixes: List<String>? = null,
) {
    /**
     * Bu addon'a `stream/{type}/{contentId}.json` isteği gönderilmeli mi?
     * Kaynak düzeyindeki `types`/`idPrefixes` varsa manifest düzeyindekini geçersiz kılar;
     * `idPrefixes` hiç yoksa her kimlik kabul edilir. [type]: `movie` / `series`.
     */
    fun supportsStream(type: String, contentId: String): Boolean = supports(STREAM, type, contentId)

    /** Aynı kural `subtitles` kaynağı için: `subtitles/{type}/{contentId}.json` istenmeli mi? */
    fun supportsSubtitles(type: String, contentId: String): Boolean = supports(SUBTITLES, type, contentId)

    /** Film/dizi akışı sunuyor mu? Yalnızca altyazı veren addon'lar kaynak listesinde ve "kaynak var mı" denetiminde sayılmaz. */
    val providesStreams: Boolean
        get() = servesPlayableType(STREAM)

    private fun supports(resource: String, type: String, contentId: String): Boolean =
        resources.filter { it.name == resource }.any { r ->
            val allowedTypes = r.types ?: types
            val prefixes = r.idPrefixes ?: idPrefixes
            type in allowedTypes && (prefixes.isNullOrEmpty() || prefixes.any { contentId.startsWith(it) })
        }

    /** [resource] kaynağı film ya da dizi için bildirilmiş mi (kaynak düzeyindeki `types` manifesttekini geçersiz kılar)? */
    internal fun servesPlayableType(resource: String): Boolean =
        resources.filter { it.name == resource }.any { r -> (r.types ?: types).any { it in PLAYABLE_TYPES } }

    companion object {
        const val STREAM = "stream"
        const val SUBTITLES = "subtitles"
        private val PLAYABLE_TYPES = setOf("movie", "series")
    }
}

/** Manifest'te kaynak ya düz isim (`"stream"`) ya da nesnedir; ikisi de bu modele çevrilir. */
@Serializable
data class ManifestResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)
