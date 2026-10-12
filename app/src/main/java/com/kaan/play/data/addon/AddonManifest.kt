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
    fun supportsStream(type: String, contentId: String): Boolean =
        resources.filter { it.name == STREAM }.any { r ->
            val allowedTypes = r.types ?: types
            val prefixes = r.idPrefixes ?: idPrefixes
            type in allowedTypes && (prefixes.isNullOrEmpty() || prefixes.any { contentId.startsWith(it) })
        }

    companion object {
        const val STREAM = "stream"
    }
}

/** Manifest'te kaynak ya düz isim (`"stream"`) ya da nesnedir; ikisi de bu modele çevrilir. */
@Serializable
data class ManifestResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)
