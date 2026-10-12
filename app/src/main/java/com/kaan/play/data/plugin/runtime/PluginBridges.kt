package com.kaan.play.data.plugin.runtime

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.IdentityHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Eklentinin cheerio benzeri kabuğunun yerel tarafı: HTML'i jsoup ile ayrıştırır. Düğümler tamsayı kimliğiyle verilir
 * (`-1` belgenin kendisi); belge/düğüm listeleri **çağrı başına** tutulur ve çağrı bitince atılır. Tam cheerio değildir:
 * belgelenen kullanım (docs §3.2) kadarını sağlar. Bozuk seçici hata fırlatmaz, boş sonuç döner.
 */
internal class DomBridge(private val limits: PluginLimits) {

    private val docs = ArrayList<Document>()
    private val nodes = ArrayList<Element>()
    private val ids = IdentityHashMap<Element, Int>()

    fun load(html: String): Int {
        require(html.length <= limits.maxHtmlChars) { "html too large" }
        require(docs.size < 64) { "too many documents" }
        val doc = Jsoup.parse(html)
        doc.outputSettings().prettyPrint(false)
        docs += doc
        return docs.lastIndex
    }

    fun select(doc: Int, node: Int, selector: String): String = guarded("[]") {
        ref(doc, node).select(normalize(selector)).joinToString(prefix = "[", postfix = "]", separator = ",") { idOf(it).toString() }
    }

    fun text(doc: Int, node: Int): String = guarded("") { ref(doc, node).wholeText() }

    fun html(doc: Int, node: Int, outer: Boolean): String = guarded("") {
        val e = ref(doc, node)
        if (outer) e.outerHtml() else e.html()
    }

    /** Öznitelik yoksa null (cheerio'da `undefined`). */
    fun attr(doc: Int, node: Int, name: String): String? = guarded(null) {
        val e = ref(doc, node)
        if (e.hasAttr(name)) e.attr(name) else null
    }

    fun matches(doc: Int, node: Int, selector: String): Boolean = guarded(false) { ref(doc, node).`is`(normalize(selector)) }

    /** `kind`: children, parent, closest, next, prev, nextAll, prevAll, siblings. [selector] boş değilse sonuçlar süzülür. */
    fun relatives(doc: Int, node: Int, kind: String, selector: String): String = guarded("[]") {
        val e = ref(doc, node)
        val found: List<Element> = when (kind) {
            "children" -> e.children()
            "parent" -> listOfNotNull(e.parent() as? Element).filter { it !is Document }
            "closest" -> listOfNotNull(if (selector.isBlank()) null else e.closest(normalize(selector)))
            "next" -> listOfNotNull(e.nextElementSibling())
            "prev" -> listOfNotNull(e.previousElementSibling())
            "nextAll" -> generateSequence(e.nextElementSibling()) { it.nextElementSibling() }.toList()
            "prevAll" -> generateSequence(e.previousElementSibling()) { it.previousElementSibling() }.toList()
            "siblings" -> e.siblingElements()
            else -> emptyList()
        }
        val filtered = if (selector.isBlank() || kind == "closest") found else found.filter { it.`is`(normalize(selector)) }
        filtered.joinToString(prefix = "[", postfix = "]", separator = ",") { idOf(it).toString() }
    }

    private fun ref(doc: Int, node: Int): Element = if (node < 0) docs[doc] else nodes[node]

    private fun idOf(e: Element): Int = ids.getOrPut(e) {
        require(nodes.size < limits.maxDomNodes) { "too many nodes" }
        nodes.add(e)
        nodes.lastIndex
    }

    /** `:contains("x")` / `:contains('x')` -> jsoup'un `:contains(x)` biçimi (tırnaksız). */
    private fun normalize(selector: String): String = CONTAINS_QUOTED.replace(selector) { ":contains(${it.groupValues[2]})" }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        if (e.message?.contains("too") == true) throw e // sınır aşımı çağırana (eklentiye) hata olarak döner
        fallback
    } catch (_: RuntimeException) {
        fallback
    }

    private companion object {
        val CONTAINS_QUOTED = Regex(""":contains\(\s*(["'])(.*?)\1\s*\)""")
    }
}

/**
 * Eklentinin CryptoJS benzeri kabuğunun yerel tarafı: yalnızca kullanılan alt küme (özet, HMAC, AES/3DES). Veriler
 * onaltılık metinle taşınır. Hata durumunda `ERR:` + kısa neden döner (anahtar/veri içeriği yazılmaz).
 */
internal object CryptoBridge {

    private val DIGESTS = setOf("MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512")
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val random = SecureRandom()

    fun digest(algorithm: String, dataHex: String): String {
        if (algorithm !in DIGESTS) return "ERR:unsupported digest"
        return try {
            hex(MessageDigest.getInstance(algorithm).digest(bytes(dataHex)))
        } catch (_: Exception) {
            "ERR:digest failed"
        }
    }

    fun hmac(algorithm: String, keyHex: String, dataHex: String): String {
        if (algorithm !in DIGESTS) return "ERR:unsupported digest"
        return try {
            val name = "Hmac" + algorithm.replace("-", "")
            val key = bytes(keyHex).let { if (it.isEmpty()) ByteArray(1) else it } // boş anahtar = sıfır doldurma
            val mac = Mac.getInstance(name)
            mac.init(SecretKeySpec(key, name))
            hex(mac.doFinal(bytes(dataHex)))
        } catch (_: Exception) {
            "ERR:hmac failed"
        }
    }

    /** @param name `AES` ya da `DESede`; @param mode `CBC`/`ECB`/`CTR`; @param padding `PKCS7` ya da `NO`. */
    fun cipher(name: String, mode: String, padding: String, encrypt: Boolean, keyHex: String, ivHex: String, dataHex: String): String {
        if (name != "AES" && name != "DESede") return "ERR:unsupported cipher"
        if (mode != "CBC" && mode != "ECB" && mode != "CTR") return "ERR:unsupported mode"
        if (dataHex.length > MAX_BYTES * 2) return "ERR:data too large"
        return try {
            var key = bytes(keyHex)
            if (name == "DESede" && key.size == 16) key += key.copyOfRange(0, 8) // K1,K2,K1
            val pad = if (padding == "PKCS7" && mode != "CTR") "PKCS5Padding" else "NoPadding"
            val c = Cipher.getInstance("$name/$mode/$pad")
            val spec = SecretKeySpec(key, name)
            val op = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
            if (mode == "ECB") c.init(op, spec) else c.init(op, spec, IvParameterSpec(bytes(ivHex)))
            hex(c.doFinal(bytes(dataHex)))
        } catch (_: Exception) {
            "ERR:cipher failed"
        }
    }

    fun randomHex(count: Int): String = hex(ByteArray(count.coerceIn(0, 64)).also { random.nextBytes(it) })

    private fun hex(b: ByteArray): String {
        val out = StringBuilder(b.size * 2)
        for (x in b) {
            out.append("0123456789abcdef"[(x.toInt() shr 4) and 15]).append("0123456789abcdef"[x.toInt() and 15])
        }
        return out.toString()
    }

    private fun bytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { ((Character.digit(hex[it * 2], 16) shl 4) or Character.digit(hex[it * 2 + 1], 16)).toByte() }
    }
}

/** `new URL(u, base)` için: OkHttp'nin ayrıştırıcısıyla bileşenler (JSON); geçersizse boş metin. Yalnızca http(s) çözülür. */
internal object UrlBridge {
    fun parse(url: String, base: String): String {
        val resolved: HttpUrl? = if (base.isBlank()) url.toHttpUrlOrNull() else base.toHttpUrlOrNull()?.resolve(url)
        resolved ?: return ""
        val port = if (resolved.port == HttpUrl.defaultPort(resolved.scheme)) "" else resolved.port.toString()
        val host = if (port.isEmpty()) resolved.host else "${resolved.host}:$port"
        val query = resolved.encodedQuery?.let { "?$it" }.orEmpty()
        val hash = resolved.encodedFragment?.let { "#$it" }.orEmpty()
        return kotlinx.serialization.json.buildJsonObject {
            put("href", kotlinx.serialization.json.JsonPrimitive(resolved.toString()))
            put("protocol", kotlinx.serialization.json.JsonPrimitive("${resolved.scheme}:"))
            put("host", kotlinx.serialization.json.JsonPrimitive(host))
            put("hostname", kotlinx.serialization.json.JsonPrimitive(resolved.host))
            put("port", kotlinx.serialization.json.JsonPrimitive(port))
            put("pathname", kotlinx.serialization.json.JsonPrimitive(resolved.encodedPath))
            put("search", kotlinx.serialization.json.JsonPrimitive(query))
            put("hash", kotlinx.serialization.json.JsonPrimitive(hash))
            put("origin", kotlinx.serialization.json.JsonPrimitive("${resolved.scheme}://$host"))
            put("username", kotlinx.serialization.json.JsonPrimitive(resolved.username))
            put("password", kotlinx.serialization.json.JsonPrimitive(resolved.password))
        }.toString()
    }
}
