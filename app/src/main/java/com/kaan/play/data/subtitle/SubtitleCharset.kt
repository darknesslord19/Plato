package com.kaan.play.data.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Çözülmüş altyazı metni ve hangi kodlamayla çözüldüğü (günlük/test için). */
data class DecodedSubtitle(val text: String, val charset: Charset)

/**
 * Altyazı dosyasının kodlamasını bulup metne çevirir. Türkçe altyazılar sık sık windows-1254 (ISO-8859-9) gelir; kodlama
 * HTTP başlığına güvenilmeden, bayt içeriğinden bulunur:
 *
 * 1. BOM (UTF-8, UTF-16 LE/BE, UTF-32 LE/BE); BOM'suz UTF-16 (çok sayıda sıfır bayt).
 * 2. Katı UTF-8: bozuk bayt dizisi yoksa UTF-8'dir (Türkçe gibi 8 bitlik dosyaların rastgele geçerli UTF-8 çıkma olasılığı önemsizdir).
 * 3. Eski kod sayfası: önce dil ipucu ([languageHint], addon'un verdiği etiket) ama Batı Avrupa ipucu / ipucu yoksa
 *    Türkçe'ye özgü baytlar (ı ş ğ İ Ş Ğ) belirgin biçimde baskınsa windows-1254; sonra yalnızca yüksek baytlardan oluşan
 *    (Kiril) metinse windows-1251; aksi halde windows-1252.
 *
 * Kütüphane (juniversalchardet) kullanılmadı: windows-1254'ü hiç tanımaz ve Türkçe için en sık yanlış kodlamaya düşer.
 */
object SubtitleCharset {

    private val UTF8 = StandardCharsets.UTF_8

    /** Dil (iki harfli) -> eski kod sayfası. Listede olmayan diller Batı Avrupa (windows-1252) sayılır. */
    private val LEGACY: Map<String, String> = buildMap {
        put("tr", "windows-1254")
        for (l in listOf("el")) put(l, "windows-1253")
        for (l in listOf("ru", "uk", "bg", "be", "sr", "mk", "kk")) put(l, "windows-1251")
        for (l in listOf("pl", "cs", "sk", "hu", "ro", "hr", "sl", "sq", "bs")) put(l, "windows-1250")
        for (l in listOf("he", "iw", "yi")) put(l, "windows-1255")
        for (l in listOf("ar", "fa", "ur", "ps")) put(l, "windows-1256")
        for (l in listOf("lt", "lv", "et")) put(l, "windows-1257")
        put("vi", "windows-1258")
        put("th", "windows-874")
        put("ja", "Shift_JIS")
        put("ko", "EUC-KR")
        put("zh", "GBK")
    }

    /** windows-1254'te ı ş ğ İ Ş Ğ: FD FE F0 DD DE D0. Windows-1252'de ý þ ð Ý Þ Ð; Batı Avrupa metninde neredeyse hiç geçmez. */
    private val TURKISH_BYTES = intArrayOf(0xFD, 0xFE, 0xF0, 0xDD, 0xDE, 0xD0)

    fun decode(bytes: ByteArray, languageHint: String? = null): DecodedSubtitle {
        bom(bytes)?.let { (charset, skip) -> return DecodedSubtitle(String(bytes, skip, bytes.size - skip, charset), charset) }
        utf16WithoutBom(bytes)?.let { return DecodedSubtitle(String(bytes, it), it) }
        strictUtf8(bytes)?.let { return DecodedSubtitle(it, UTF8) }
        val charset = legacyCharset(bytes, SubtitleLanguages.normalize(languageHint))
        return DecodedSubtitle(String(bytes, charset), charset)
    }

    private fun bom(b: ByteArray): Pair<Charset, Int>? {
        fun at(i: Int) = if (i < b.size) b[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> UTF8 to 3
            at(0) == 0xFF && at(1) == 0xFE && at(2) == 0 && at(3) == 0 -> Charset.forName("UTF-32LE") to 4
            at(0) == 0 && at(1) == 0 && at(2) == 0xFE && at(3) == 0xFF -> Charset.forName("UTF-32BE") to 4
            at(0) == 0xFF && at(1) == 0xFE -> StandardCharsets.UTF_16LE to 2
            at(0) == 0xFE && at(1) == 0xFF -> StandardCharsets.UTF_16BE to 2
            else -> null
        }
    }

    /** BOM'suz UTF-16: ASCII ağırlıklı metinde her ikinci bayt sıfırdır. */
    private fun utf16WithoutBom(b: ByteArray): Charset? {
        val n = minOf(b.size, 4096)
        if (n < 8) return null
        var evenZeros = 0
        var oddZeros = 0
        for (i in 0 until n) if (b[i].toInt() == 0) if (i % 2 == 0) evenZeros++ else oddZeros++
        val half = n / 2
        return when {
            oddZeros > half * 0.3 && evenZeros < half * 0.05 -> StandardCharsets.UTF_16LE
            evenZeros > half * 0.3 && oddZeros < half * 0.05 -> StandardCharsets.UTF_16BE
            else -> null
        }
    }

    private fun strictUtf8(b: ByteArray): String? = try {
        UTF8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun legacyCharset(b: ByteArray, language: String?): Charset {
        val hinted = language?.let(LEGACY::get)
        // Dil ipucu Batı Avrupa'yı gösteriyorsa ya da hiç yoksa: Türkçe baytlar baskınsa dosya aslında Türkçedir
        // (addon etiketi İngilizce ama içerik windows-1254 olabiliyor).
        if (hinted == null && looksTurkish(b)) return Charset.forName("windows-1254")
        if (hinted != null) return supported(hinted) ?: Charset.forName("windows-1252")
        if (language == null && looksNonLatin(b)) return Charset.forName("windows-1251")
        return Charset.forName("windows-1252")
    }

    private fun supported(name: String): Charset? = if (Charset.isSupported(name)) Charset.forName(name) else null

    /** Yüksek baytların en az dörtte biri Türkçe'ye özgü ve en az 3 tane. */
    private fun looksTurkish(b: ByteArray): Boolean {
        var high = 0
        var turkish = 0
        for (x in b) {
            val v = x.toInt() and 0xFF
            if (v >= 0x80) {
                high++
                if (v in TURKISH_BYTES) turkish++
            }
        }
        return turkish >= 3 && turkish * 4 >= high
    }

    /** Harf baytlarının çoğu yüksek aralıkta (Kiril gibi Latin olmayan alfabe); Batı Avrupa metninde yüksek baytlar seyrektir. */
    private fun looksNonLatin(b: ByteArray): Boolean {
        var high = 0
        var ascii = 0
        for (x in b) {
            val v = x.toInt() and 0xFF
            if (v >= 0xC0) high++ else if (v in 'A'.code..'z'.code && (v <= 'Z'.code || v >= 'a'.code)) ascii++
        }
        return high >= 20 && high > ascii
    }
}
