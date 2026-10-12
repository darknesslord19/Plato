package com.kaan.play.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/** Kodlama tespiti: Türkçe altyazılar sık sık windows-1254 gelir; harfler (ş ğ ı İ ...) bozulmadan çıkmalı. */
class SubtitleCharsetTest {

    private val turkish = """
        1
        00:00:01,000 --> 00:00:03,000
        İstanbul'da şöyle bir gün: ığdır, çağrı, ÖĞRENCİ ŞEKER.

        2
        00:00:04,000 --> 00:00:06,000
        Çok güzel değil mi? Işık yok, ışık var.
    """.trimIndent()

    private fun bytes(text: String, charset: String) = text.toByteArray(Charset.forName(charset))

    @Test
    fun windows1254TurkishSurvivesWithLanguageHint() {
        val decoded = SubtitleCharset.decode(bytes(turkish, "windows-1254"), "tur")
        assertEquals(turkish, decoded.text)
        assertEquals("windows-1254", decoded.charset.name())
    }

    @Test
    fun windows1254TurkishSurvivesWithoutAnyHint() {
        assertEquals(turkish, SubtitleCharset.decode(bytes(turkish, "windows-1254"), null).text)
    }

    @Test
    fun windows1254TurkishSurvivesEvenWhenAddonMislabelsItEnglish() {
        // Etiket "eng" ama dosya Türkçe baytlar taşıyor: Batı Avrupa kod sayfasına düşüp ý/þ/ð çıkmamalı.
        assertEquals(turkish, SubtitleCharset.decode(bytes(turkish, "windows-1254"), "eng").text)
    }

    @Test
    fun iso88599IsDecodedLikeWindows1254ForTurkishLetters() {
        assertEquals(turkish, SubtitleCharset.decode(bytes(turkish, "ISO-8859-9"), "tr").text)
    }

    @Test
    fun specificTurkishLettersMapCorrectly() {
        val text = "ş ğ ı İ Ş Ğ ç ö ü"
        val decoded = SubtitleCharset.decode(bytes(text, "windows-1254"), "tur").text
        assertEquals(text, decoded)
        assertTrue(decoded.contains('ş') && decoded.contains('ğ') && decoded.contains('ı') && decoded.contains('İ'))
    }

    @Test
    fun utf8IsRecognizedWithAndWithoutBomRegardlessOfHint() {
        assertEquals(turkish, SubtitleCharset.decode(turkish.toByteArray(Charsets.UTF_8), "tur").text)
        assertEquals(turkish, SubtitleCharset.decode(turkish.toByteArray(Charsets.UTF_8), "eng").text)
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + turkish.toByteArray(Charsets.UTF_8)
        val decoded = SubtitleCharset.decode(withBom, null)
        assertEquals(turkish, decoded.text) // BOM metne karışmaz
        assertEquals("UTF-8", decoded.charset.name())
    }

    @Test
    fun utf16WithBomAndWithoutIsRecognized() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + turkish.toByteArray(Charsets.UTF_16LE)
        assertEquals(turkish, SubtitleCharset.decode(le, null).text)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + turkish.toByteArray(Charsets.UTF_16BE)
        assertEquals(turkish, SubtitleCharset.decode(be, null).text)
        val asciiLe = "1\n00:00:01,000 --> 00:00:02,000\nHello world, this is plain ASCII.\n"
        assertEquals(asciiLe, SubtitleCharset.decode(asciiLe.toByteArray(Charsets.UTF_16LE), null).text)
    }

    @Test
    fun westernEuropeanAndPlainAsciiAreNotMistakenForTurkish() {
        val french = "Où est le café? C'est déjà très tard, à bientôt, noël, être, ça va, über, señor."
        assertEquals(french, SubtitleCharset.decode(bytes(french, "windows-1252"), null).text)
        assertEquals(french, SubtitleCharset.decode(bytes(french, "windows-1252"), "fre").text)
        val ascii = "Just ASCII text."
        assertEquals(ascii, SubtitleCharset.decode(ascii.toByteArray(), "tur").text)
    }

    @Test
    fun cyrillicAndOtherLegacyPagesFollowTheLanguageHint() {
        val russian = "Привет, как дела? Это длинный русский текст для проверки кодировки, привет еще раз и еще раз."
        assertEquals(russian, SubtitleCharset.decode(bytes(russian, "windows-1251"), "rus").text)
        assertEquals(russian, SubtitleCharset.decode(bytes(russian, "windows-1251"), "ru").text) // 639-1 de olur
        assertEquals(russian, SubtitleCharset.decode(bytes(russian, "windows-1251"), null).text) // ipucu yoksa sezgi
        val greek = "Καλημέρα κόσμε"
        assertEquals(greek, SubtitleCharset.decode(bytes(greek, "windows-1253"), "ell").text)
        val polish = "Zażółć gęślą jaźń"
        assertEquals(polish, SubtitleCharset.decode(bytes(polish, "windows-1250"), "pol").text)
    }

    @Test
    fun emptyAndTinyInputsDoNotCrash() {
        assertEquals("", SubtitleCharset.decode(ByteArray(0), null).text)
        assertEquals("a", SubtitleCharset.decode("a".toByteArray(), "tur").text)
    }
}
