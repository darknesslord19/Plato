package com.kaan.play.data.update

import com.kaan.play.data.net.PlayJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val repo = "darknesslord19/Plato"
    private val sha = "a".repeat(64)

    private fun manifest(
        version: String = "1.1.5",
        apk: String = "https://github.com/darknesslord19/Plato/releases/download/v1.1.5/Play-1.1.5.apk",
        sha256: String = sha,
        extra: String = "",
    ) = """{"version":"$version","apkUrl":"$apk","sha256":"$sha256"$extra}"""

    private fun eval(text: String, installed: String = "1.1.4.7") =
        UpdateManifestRules.evaluate(text, installed, repo, PlayJson)

    @Test
    fun `gecerli bildirim yeni surumu verir`() {
        val r = eval(manifest(extra = ""","size":47518540,"notes":"CloudStream düzeltmeleri""""))
        assertTrue(r is UpdateManifestRules.Result.Valid)
        val info = (r as UpdateManifestRules.Result.Valid).info!!
        assertEquals("v1.1.5", info.tag)
        assertEquals("Play-1.1.5.apk", info.apk!!.name)
        assertEquals(sha, info.apk!!.sha256)
        assertEquals(47518540L, info.apk!!.size)
        assertEquals("CloudStream düzeltmeleri", info.notes)
        assertEquals("https://github.com/darknesslord19/Plato/releases/tag/v1.1.5", info.pageUrl)
    }

    @Test
    fun `yuklu surum ayni ya da yeniyse guncelleme yok`() {
        assertEquals(UpdateManifestRules.Result.Valid(null), eval(manifest(), installed = "1.1.5"))
        assertEquals(UpdateManifestRules.Result.Valid(null), eval(manifest(), installed = "1.2.0"))
    }

    @Test
    fun `baska yerden apk kabul edilmez`() {
        listOf(
            "https://evil.example/Play-1.1.5.apk",
            "http://github.com/darknesslord19/Plato/releases/download/v1.1.5/Play-1.1.5.apk",
            "https://github.com/saldirgan/Plato/releases/download/v1.1.5/Play-1.1.5.apk",
            "https://github.com/darknesslord19/Plato/raw/main/Play-1.1.5.apk",
            "https://github.com/darknesslord19/Plato/releases/download/v1.1.5/baska.apk",
        ).forEach { url ->
            assertTrue(url, eval(manifest(apk = url)) is UpdateManifestRules.Result.Invalid)
        }
    }

    @Test
    fun `bozuk ya da eksik bildirim github'a geri duser`() {
        listOf(
            "\"A\"", "null", "{bozuk", "[]",
            manifest(sha256 = "kisa"),
            manifest(version = ""),
            manifest(version = "yeni"),
            """{"apkUrl":"https://github.com/darknesslord19/Plato/releases/download/v1/Play-1.apk","sha256":"$sha"}""",
        ).forEach { t -> assertTrue(t, eval(t) is UpdateManifestRules.Result.Invalid) }
    }

    @Test
    fun `sha256 buyuk harf ve v onekli surum kabul edilir`() {
        val info = (eval(manifest(version = "v1.1.5", sha256 = "A".repeat(64))) as UpdateManifestRules.Result.Valid).info
        assertEquals("v1.1.5", info!!.tag)
        assertEquals(sha, info.apk!!.sha256)
    }

    @Test
    fun `bilinmeyen alanlar yok sayilir`() {
        val r = eval(manifest(extra = ""","mandatory":true,"channel":"beta""""))
        assertTrue(r is UpdateManifestRules.Result.Valid)
        assertNull(((r as UpdateManifestRules.Result.Valid).info)?.notes)
    }
}
