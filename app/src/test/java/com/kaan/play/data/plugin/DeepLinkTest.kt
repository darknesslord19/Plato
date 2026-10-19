package com.kaan.play.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    private val repoUrl = "https://raw.githubusercontent.com/o/r/refs/heads/main/repo.json"

    @Test
    fun `repo parametresi adresi dondurur`() {
        assertEquals(repoUrl, DeepLink.target("play", "add", repoUrl, null))
    }

    @Test
    fun `nuvio parametresi adresi dondurur`() {
        assertEquals("https://example.com/manifest.json", DeepLink.target("play", "add", null, "https://example.com/manifest.json"))
    }

    @Test
    fun `parametre yoksa ya da bos ise gecersiz`() {
        assertNull(DeepLink.target("play", "add", null, null))
        assertNull(DeepLink.target("play", "add", " ", ""))
    }

    @Test
    fun `http disi sema ve bozuk adres reddedilir`() {
        assertNull(DeepLink.target("play", "add", "javascript:alert(1)", null))
        assertNull(DeepLink.target("play", "add", "file:///sdcard/x.json", null))
        assertNull(DeepLink.target("play", "add", "ftp://example.com/x.json", null))
        assertNull(DeepLink.target("play", "add", "bozuk adres", null))
    }

    @Test
    fun `yanlis sema ya da sunucu reddedilir`() {
        assertNull(DeepLink.target("https", "add", repoUrl, null))
        assertNull(DeepLink.target("play", "other", repoUrl, null))
    }

    @Test
    fun `nuvio_plugin nuvio ile ayni sekilde calisir`() {
        val url = "https://example.com/manifest.json"
        assertEquals(url, DeepLink.target("play", "add", null, DeepLink.nuvioParam(null, url)))
        assertEquals(url, DeepLink.nuvioParam(url, "https://baska.example/x.json"))
    }

    @Test
    fun `bos nuvio parametresi nuvio_plugin degerine duser`() {
        val url = "https://example.com/manifest.json"
        assertEquals(url, DeepLink.nuvioParam("", url))
        assertEquals(url, DeepLink.nuvioParam("  ", url))
        assertNull(DeepLink.nuvioParam("", null))
    }

    @Test
    fun `parse fonksiyonu repo parametresini CLOUDSTREAM olarak ayristirir`() {
        val payload = DeepLink.parse("play", "add", repoUrl, null, null)
        assertEquals(repoUrl, payload?.url)
        assertEquals(SourceType.CLOUDSTREAM, payload?.expectedType)
    }

    @Test
    fun `parse fonksiyonu nuvio parametresini NUVIO_REPO olarak ayristirir`() {
        val nuvioUrl = "https://example.com/manifest.json"
        val payload = DeepLink.parse("play", "add", null, nuvioUrl, null)
        assertEquals(nuvioUrl, payload?.url)
        assertEquals(SourceType.NUVIO_REPO, payload?.expectedType)
    }

    @Test
    fun `parse fonksiyonu nuvio_plugin parametresini NUVIO_PLUGIN olarak ayristirir`() {
        val pluginUrl = "https://example.com/plugins/manifest.json"
        val payload = DeepLink.parse("play", "add", null, null, pluginUrl)
        assertEquals(pluginUrl, payload?.url)
        assertEquals(SourceType.NUVIO_PLUGIN, payload?.expectedType)
    }

    @Test
    fun `parse fonksiyonu gecersiz sema veya host durumunda null dondurur`() {
        assertNull(DeepLink.parse("https", "add", repoUrl, null, null))
        assertNull(DeepLink.parse("play", "invalid", repoUrl, null, null))
        assertNull(DeepLink.parse("play", "add", null, null, null))
        assertNull(DeepLink.parse("play", "add", "   ", "", " "))
        assertNull(DeepLink.parse("play", "add", "ftp://example.com/repo.json", null, null))
    }
}

