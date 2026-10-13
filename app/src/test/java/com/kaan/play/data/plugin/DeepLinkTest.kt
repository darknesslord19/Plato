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
}
