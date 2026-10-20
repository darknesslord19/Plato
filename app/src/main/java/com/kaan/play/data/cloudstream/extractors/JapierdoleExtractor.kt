package com.kaan.play.data.cloudstream.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Japierdole / Drakkar player extractor (japierdolevid.com).
 * DiziMag ve benzeri kaynaklarda iframe olarak sunulan korumalı oynatıcıyı çözer.
 */
class JapierdoleExtractor(
    override val name: String = "Japierdole",
    override val mainUrl: String = "https://japierdolevid.com",
    override val requiresReferer: Boolean = true,
) : ExtractorApi() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            val embedUrl = if (url.startsWith("http")) url else "$mainUrl$url"
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Referer" to (referer ?: "https://dizimag.onl/"),
            )

            val res = app.get(embedUrl, headers = headers)
            val html = res.text
            val cr = extractCr(html) ?: run {
                Log.w(TAG, "Japierdole: challenge response (cr) bulunamadı: $embedUrl")
                return
            }

            val ept = Regex("""_ept\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: return
            val ws = Regex("""_ws\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: return

            val pt = buildString {
                for (i in ept.indices) {
                    val eVal = ept[i].digitToIntOrNull(16) ?: 0
                    val crVal = cr[i % cr.length].digitToIntOrNull(16) ?: 0
                    append((eVal xor crVal).toString(16))
                }
            }

            val wcr = sha256Hex(ws + cr).take(16)
            val sig = randomHex(64)
            val postJson = JSONObject().apply {
                put("_sig", sig)
                put("_nonce", randomHex(32))
                put("_ts2", System.currentTimeMillis() - 200)
                put("_v", "2.5.12")
                put("_sid", randomHex(24))
                put("_ref", base64Encode(embedUrl.toByteArray(Charsets.UTF_8)).take(30))
                put("_enc", "ed25519")
                put("_mode", "cfb8")
                put("_iter", 3000)
                put("_salt", randomHex(16))
                put("_hmac", randomHex(40))
                put("_pad", "zero")
                put("_cmp", "none")
                put("_fmt", 2)
                put("_fl", 1)
                put("pt", pt)
                put("cr", cr)
                put("wc", wcr)
                put("bs", JSONObject().apply {
                    put("ts", System.currentTimeMillis())
                    put("sw", 1920)
                    put("sh", 1080)
                    put("plt", 120)
                    put("tz", -180)
                    put("lang", "tr-TR")
                    put("pl", "Win32")
                    put("ct", 8)
                    put("dm", 24)
                    put("td", 0)
                    put("cv", 1)
                    put("wg", 1)
                })
                put("_x", sig.take(8))
            }

            val streamUrl = embedUrl.trimEnd('/') + "/stream"
            // Sunucu timing denetimi: en az 1300 ms bekle
            delay(1350)

            val postRes = app.post(
                streamUrl,
                headers = mapOf(
                    "User-Agent" to headers["User-Agent"]!!,
                    "Referer" to embedUrl,
                    "Content-Type" to "application/json",
                ),
                data = mapOf(), // NiceHttp raw body desteği olmadığı durumda json string
                json = null,
                requestBody = postJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
            )

            val postBody = postRes.text
            val resObj = runCatching { JSONObject(postBody) }.getOrNull() ?: return
            val d = resObj.optString("d")
            val v = resObj.optString("v")
            val p1 = resObj.optString("p1")
            val p2 = resObj.optString("p2")
            val p3 = resObj.optString("p3")
            val p4 = resObj.optString("p4")
            val x = resObj.optString("x")

            if (d.isEmpty() || v.isEmpty()) return

            val seed = p1 + p2 + p3 + p4
            val decryptedText = runCatching {
                decryptOuterPayload(d, v, seed, cr, x)
            }.getOrNull() ?: return
            val outerData = JSONObject(decryptedText)

            val htmlContent = if (outerData.has("processed_template")) {
                outerData.getString("processed_template")
            } else if (outerData.has("encrypted_player_data")) {
                val epd = outerData.getJSONObject("encrypted_player_data")
                val innerCtRaw = epd.getString("ct")
                val innerKeyHex = epd.getString("k1") + epd.getString("k2") + epd.getString("k3") + epd.getString("k4")
                val innerIvHex = epd.getString("iv")
                runCatching {
                    decryptInnerPayload(innerCtRaw, innerKeyHex, innerIvHex)
                }.getOrNull()
            } else {
                null
            } ?: return

            // HLS veya video adresini çıkar
            val hlsUrl = extractVideoUrl(htmlContent) ?: return

            // Altyazıları çıkar
            val subsRegex = Regex("""subtitles\s*:\s*(\[[^\]]*\])""")
            val subsJson = subsRegex.find(htmlContent)?.groupValues?.get(1)
            if (!subsJson.isNullOrBlank()) {
                runCatching {
                    val arr = org.json.JSONArray(subsJson)
                    for (i in 0 until arr.length()) {
                        val subObj = arr.getJSONObject(i)
                        val subSrc = subObj.optString("src")
                        val subLang = subObj.optString("lang", subObj.optString("label", "tr"))
                        if (subSrc.isNotBlank()) {
                            subtitleCallback(SubtitleFile(subLang, subSrc))
                        }
                    }
                }
            }

            callback(
                ExtractorLink(
                    source = name,
                    name = "$name 1080p",
                    url = hlsUrl,
                    referer = embedUrl,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.M3U8,
                    headers = mapOf("Referer" to embedUrl)
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "JapierdoleExtractor hata: ${t.message}", t)
        }
    }

    companion object {
        private const val TAG = "JapierdoleExt"

        fun base64Decode(str: String): ByteArray {
            val clean = str.replace(Regex("[^A-Za-z0-9+/=]"), "")
            val b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = java.io.ByteArrayOutputStream()
            var buf = 0
            var bits = 0
            for (c in clean) {
                if (c == '=') break
                val v = b64.indexOf(c)
                if (v < 0) continue
                buf = (buf shl 6) or v
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buf shr bits) and 0xFF)
                }
            }
            return out.toByteArray()
        }

        fun base64Encode(bytes: ByteArray): String {
            val b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val sb = StringBuilder()
            var buf = 0
            var bits = 0
            for (b in bytes) {
                buf = (buf shl 8) or (b.toInt() and 0xFF)
                bits += 8
                while (bits >= 6) {
                    bits -= 6
                    sb.append(b64[(buf shr bits) and 0x3F])
                }
            }
            if (bits > 0) {
                buf = buf shl (6 - bits)
                sb.append(b64[buf and 0x3F])
                while (sb.length % 4 != 0) sb.append('=')
            }
            return sb.toString()
        }

        fun extractCr(html: String): String? {
            // Desen 1: var _[hex]a = atob('...'); var _[hex]b = atob('...');
            val atobRegex = Regex("""var\s+_[a-f0-9]+a\s*=\s*atob\(['"]([^'"]+)['"]\);\s*var\s+_[a-f0-9]+b\s*=\s*atob\(['"]([^'"]+)['"]\);""")
            atobRegex.find(html)?.let { m ->
                val a = String(base64Decode(m.groupValues[1]), Charsets.ISO_8859_1)
                val b = String(base64Decode(m.groupValues[2]), Charsets.ISO_8859_1)
                return a + b
            }

            // Desen 2: var _[hex]a = '...'; var _[hex]b = '...'; reverse
            val revRegex = Regex("""var\s+_[a-f0-9]+a\s*=\s*['"]([^'"]+)['"];\s*var\s+_[a-f0-9]+b\s*=\s*['"]([^'"]+)['"];\s*var\s+_[a-f0-9]+f\s*=\s*function\(s\)\{return\s+s\.split\(''\)\.reverse\(\)\.join\(''\)\};""")
            revRegex.find(html)?.let { m ->
                val a = m.groupValues[1].reversed()
                val b = m.groupValues[2].reversed()
                return a + b
            }

            // Desen 3: var _[hex]a = [...]; var _[hex]b = [...]; XOR döngüsü
            val xorRegex = Regex("""var\s+_[a-f0-9]+a\s*=\s*\[([0-9,]+)\];\s*var\s+_[a-f0-9]+b\s*=\s*\[([0-9,]+)\];""")
            xorRegex.find(html)?.let { m ->
                val aList = m.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() }
                val bList = m.groupValues[2].split(',').mapNotNull { it.trim().toIntOrNull() }
                if (aList.isNotEmpty() && bList.isNotEmpty()) {
                    return buildString {
                        for (i in aList.indices) {
                            val c = (aList[i] xor bList[i % bList.size]).toChar()
                            append(c)
                        }
                    }
                }
            }

            return null
        }

        fun decryptOuterPayload(d: String, v: String, seed: String, cr: String, x: String): String {
            val derivedKey = MessageDigest.getInstance("SHA-256").digest((seed + cr + x).toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(hexToBytes(v)))
            val decryptedBytes = cipher.doFinal(base64Decode(d))
            return String(decryptedBytes, Charsets.UTF_8)
        }

        fun decryptInnerPayload(ctRaw: String, kHex: String, ivHex: String): String {
            val innerCt = if (ctRaw.length % 2 == 0 && ctRaw.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                hexToBytes(ctRaw)
            } else {
                base64Decode(ctRaw)
            }
            val innerKey = hexToBytes(kHex)
            val innerIv = hexToBytes(ivHex)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(innerKey, "AES"), IvParameterSpec(innerIv))
            return String(cipher.doFinal(innerCt), Charsets.UTF_8)
        }

        fun extractVideoUrl(htmlContent: String): String? {
            val hlsRegex = Regex("""(?:videoHls|videoSrc)\s*:\s*['"]([^'"]+)['"]""")
            return hlsRegex.find(htmlContent)?.groupValues?.get(1)
        }

        fun sha256Hex(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun randomHex(len: Int): String {
            val chars = "0123456789abcdef"
            return (1..len).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        }

        fun hexToBytes(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in 0 until hex.length step 2) {
                result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            }
            return result
        }
    }
}
