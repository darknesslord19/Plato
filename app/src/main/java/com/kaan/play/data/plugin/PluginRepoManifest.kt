package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nuvio eklenti deposunun (JS kazıyıcı deposu) kendi modelimizle kodlanmış manifest'i. Biçim `docs/nuvio-plugin-format.md`
 * §1'de belgelidir; yalnızca Play'in kullandığı alanlar tutulur, bilinmeyenler yok sayılır.
 */
@Serializable
data class PluginRepoManifest(
    val name: String,
    val version: String,
    val description: String? = null,
    val scrapers: List<PluginScraperEntry>,
)

@Serializable
data class PluginScraperEntry(
    val id: String,
    val name: String,
    val version: String,
    /** Manifest'teki ham değer (göreli ya da mutlak). */
    val filename: String,
    /** İndirme adresi: `filename` mutlaksa kendisi, değilse manifest adresine göre çözülmüş hali. */
    val scriptUrl: String,
    val description: String? = null,
    /** Ham değerler (`movie`, `tv`, `series`...); eşleme için [normalizedTypes]. */
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val contentLanguage: List<String> = emptyList(),
    val hasSettings: Boolean = false,
) {
    /** `series/show/other` -> `tv` (Nuvio ile aynı normalleştirme). */
    val normalizedTypes: Set<String>
        get() = supportedTypes.map { normalizeType(it) }.toSet()

    companion object {
        fun normalizeType(value: String): String = when (val v = value.trim().lowercase()) {
            "series", "show", "other" -> "tv"
            else -> v
        }
    }
}

/** İçeriğine göre manifest türü. Etikete ya da adrese değil, yalnızca alanlara bakılır. */
enum class ManifestKind {
    /** Stremio protokollü addon: `resources` var. */
    Addon,

    /** JS kazıyıcı deposu: `scrapers` dizisi var, `resources` yok. */
    PluginRepo,

    /** CloudStream tarzı harici depo (`pluginLists`): Faz 3. */
    External,

    /** Hiçbirine uymuyor; addon ayrıştırıcısı kesin hatayı verir. */
    Unknown;

    companion object {
        /** @throws AddonException [Kind.NotJson] */
        fun classify(raw: String): ManifestKind {
            val root = try {
                PlayJson.parseToJsonElement(raw)
            } catch (e: SerializationException) {
                throw AddonException(Kind.NotJson, cause = e)
            } catch (e: IllegalArgumentException) {
                throw AddonException(Kind.NotJson, cause = e)
            }
            if (root !is JsonObject) throw AddonException(Kind.NotJson)
            return when {
                root["resources"] is JsonArray -> Addon
                root["scrapers"] is JsonArray -> PluginRepo
                root["pluginLists"] is JsonArray -> External
                else -> Unknown
            }
        }
    }
}

object PluginRepoManifestParser {

    /** Bir depodan alınacak en çok kazıyıcı sayısı (gerçek depolar 6–43). */
    const val MAX_SCRAPERS = 100

    /**
     * Depo manifest'ini doğrular. Eksik/bozuk girişler (kimlik ya da dosya adı yok, adres çözülemiyor, http(s) değil,
     * yinelenen kimlik) atlanır; hiç kullanılabilir giriş kalmazsa [Kind.NoPlugins].
     * @param manifestUrl göreli `filename` değerlerinin çözüleceği adres.
     */
    fun parse(raw: String, manifestUrl: String): PluginRepoManifest {
        val root = try {
            PlayJson.parseToJsonElement(raw)
        } catch (e: SerializationException) {
            throw AddonException(Kind.NotJson, cause = e)
        } catch (e: IllegalArgumentException) {
            throw AddonException(Kind.NotJson, cause = e)
        }
        if (root !is JsonObject) throw AddonException(Kind.NotJson)

        val name = root.string("name") ?: throw AddonException(Kind.MissingField, "name")
        val array = root["scrapers"] as? JsonArray ?: throw AddonException(Kind.MissingField, "scrapers")
        val base = manifestUrl.toHttpUrlOrNull()

        val seen = HashSet<String>()
        val entries = array.asSequence()
            .mapNotNull { (it as? JsonObject)?.let { obj -> entry(obj, base) } }
            .filter { seen.add(it.id) }
            .take(MAX_SCRAPERS)
            .toList()
        if (entries.isEmpty()) throw AddonException(Kind.NoPlugins)

        return PluginRepoManifest(
            name = name,
            version = root.string("version") ?: "0",
            description = root.string("description"),
            scrapers = entries,
        )
    }

    private fun entry(obj: JsonObject, base: HttpUrl?): PluginScraperEntry? {
        val id = obj.string("id") ?: return null
        val filename = obj.string("filename") ?: return null
        val url = resolve(filename, base) ?: return null
        return PluginScraperEntry(
            id = id,
            name = obj.string("name") ?: id,
            version = obj.string("version") ?: "0",
            filename = filename,
            scriptUrl = url,
            description = obj.string("description"),
            supportedTypes = obj.strings("supportedTypes").ifEmpty { listOf("movie", "tv") },
            contentLanguage = obj.strings("contentLanguage"),
            hasSettings = (obj["hasSettings"] as? JsonPrimitive)?.content == "true",
        )
    }

    /** Mutlak http(s) adresi olduğu gibi, göreli olan manifest adresine göre çözülür; başka şema reddedilir. */
    internal fun resolve(filename: String, base: HttpUrl?): String? {
        val absolute = filename.toHttpUrlOrNull()
        if (absolute != null) return absolute.toString()
        // `ftp://`, `file:`, `javascript:` gibi başka şemalar resolve() içinde null döner.
        return base?.resolve(filename)?.toString()
    }

    private fun JsonObject.string(field: String): String? {
        val v = this[field] as? JsonPrimitive ?: return null
        if (!v.isString) return v.content.takeIf { it != "null" && it.isNotBlank() }?.trim()
        return v.content.trim().ifEmpty { null }
    }

    private fun JsonObject.strings(field: String): List<String> {
        val v: JsonElement = this[field] ?: return emptyList()
        if (v is JsonNull || v !is JsonArray) return emptyList()
        return v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.ifEmpty { null } }
    }
}

/** Sürüm karşılaştırması: nokta ayrımlı sayılar sayısal, başka her şey eşitlik testi. */
object VersionCompare {
    /** @return `>0` ise [remote] daha yeni; `0` aynı; `<0` yerel daha yeni. Sayısal olmayanlarda farklıysa `1`. */
    fun compare(local: String, remote: String): Int {
        if (local == remote) return 0
        val a = parts(local)
        val b = parts(remote)
        if (a == null || b == null) return 1
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y.compareTo(x)
        }
        return 0
    }

    private fun parts(v: String): List<Int>? {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val pieces = cleaned.split('.')
        val numbers = pieces.map { it.toIntOrNull() ?: return null }
        return numbers.takeIf { it.isNotEmpty() }
    }
}
