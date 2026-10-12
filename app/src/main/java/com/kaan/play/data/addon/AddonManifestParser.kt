package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * manifest.json'u ayrıştırır ve Faz 1 için doğrular. Alanları elle okur ki hata
 * "şu alan eksik/bozuk" diye somut olsun. Bilinmeyen alanlar yok sayılır.
 *
 * Zorunlu: `id`, `name` (boş olmayan metin), `types` (boş olmayan metin dizisi),
 * `resources` (boş olmayan dizi; her öğe metin ya da `name`'li nesne).
 * Ayrıca addon'un film/dizi için `stream` ya da `subtitles` kaynağı sunması gerekir.
 */
object AddonManifestParser {

    fun parse(raw: String): AddonManifest {
        val root = try {
            PlayJson.parseToJsonElement(raw)
        } catch (e: SerializationException) {
            throw AddonException(Kind.NotJson, cause = e)
        } catch (e: IllegalArgumentException) {
            throw AddonException(Kind.NotJson, cause = e)
        }
        if (root !is JsonObject) throw AddonException(Kind.NotJson)

        val manifest = AddonManifest(
            id = root.requiredString("id"),
            name = root.requiredString("name"),
            version = root.optionalString("version"),
            description = root.optionalString("description"),
            logo = root.optionalString("logo"),
            types = root.stringList("types", required = true)!!,
            resources = root.resources(),
            idPrefixes = root.stringList("idPrefixes", required = false),
        )

        // Akış ya da altyazı sunan addon kabul edilir (yalnızca altyazı verenler kaynak listesinde görünmez, altyazı seçicisinde kullanılır).
        val useful = listOf(AddonManifest.STREAM, AddonManifest.SUBTITLES)
        if (manifest.resources.none { it.name in useful }) throw AddonException(Kind.NoStreamResource)
        if (useful.none { manifest.servesPlayableType(it) }) throw AddonException(Kind.UnsupportedTypes)

        return manifest
    }

    private fun JsonObject.requiredString(field: String): String {
        val value = this[field]
        if (value == null || value is JsonNull) throw AddonException(Kind.MissingField, field)
        if (value !is JsonPrimitive || !value.isString) throw AddonException(Kind.InvalidField, field)
        return value.content.trim().ifEmpty { throw AddonException(Kind.MissingField, field) }
    }

    /** İsteğe bağlı bilgi alanları: yok ya da metin değilse sessizce null (addon'u reddetmeye değmez). */
    private fun JsonObject.optionalString(field: String): String? {
        val value = this[field] as? JsonPrimitive ?: return null
        return if (value.isString) value.content.trim().ifEmpty { null } else null
    }

    private fun JsonObject.stringList(field: String, required: Boolean): List<String>? {
        val value = this[field]
        if (value == null || value is JsonNull) {
            if (required) throw AddonException(Kind.MissingField, field) else return null
        }
        return stringsOf(value, field).also {
            if (required && it.isEmpty()) throw AddonException(Kind.MissingField, field)
        }
    }

    private fun stringsOf(value: JsonElement, field: String): List<String> {
        if (value !is JsonArray) throw AddonException(Kind.InvalidField, field)
        return value.map { item ->
            if (item !is JsonPrimitive || !item.isString) throw AddonException(Kind.InvalidField, field)
            item.content
        }
    }

    private fun JsonObject.resources(): List<ManifestResource> {
        val value = this["resources"]
        if (value == null || value is JsonNull) throw AddonException(Kind.MissingField, "resources")
        if (value !is JsonArray) throw AddonException(Kind.InvalidField, "resources")
        if (value.isEmpty()) throw AddonException(Kind.MissingField, "resources")

        return value.map { item ->
            when {
                item is JsonPrimitive && item.isString && item.content.isNotBlank() ->
                    ManifestResource(item.content)
                item is JsonObject -> ManifestResource(
                    name = item.requiredResourceName(),
                    types = item.nestedStringList("types"),
                    idPrefixes = item.nestedStringList("idPrefixes"),
                )
                else -> throw AddonException(Kind.InvalidField, "resources")
            }
        }
    }

    private fun JsonObject.requiredResourceName(): String {
        val v = this["name"]
        if (v !is JsonPrimitive || !v.isString || v.content.isBlank()) {
            throw AddonException(Kind.InvalidField, "resources")
        }
        return v.content
    }

    private fun JsonObject.nestedStringList(field: String): List<String>? {
        val v = this[field]
        if (v == null || v is JsonNull) return null
        return stringsOf(v, "resources")
    }
}
