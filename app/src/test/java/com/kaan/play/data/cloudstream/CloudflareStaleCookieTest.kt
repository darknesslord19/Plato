package com.kaan.play.data.cloudstream

import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engel sayfasında kalan alan adının "Doğrula" için doğru bulunması:
 * eklenti mainUrl'den farklı bir alan adına gitse bile engel kaydı oradan okunur.
 */
class CloudflareStaleCookieTest {

    @After
    fun tearDown() {
        CloudflareSolver.unblock("dizibox.example")
        CloudflareSolver.unblock("yeni-alan.example")
    }

    @Test
    fun `engel kaydi baslangictan sonraysa bulunur, oncesindeyse bulunmaz`() {
        CloudflareSolver.noteUnresolved("https://www.yeni-alan.example/dizi", "https://www.yeni-alan.example/dizi")
        val after = System.currentTimeMillis() + 60_000
        assertNull(CloudflareSolver.unresolvedSince(after))
        val found = CloudflareSolver.unresolvedSince(0)
        assertEquals("yeni-alan.example", found?.first)
        assertEquals("https://www.yeni-alan.example/dizi", found?.second)
    }

    @Test
    fun `markBlocked dogrula adresini saklar, unblock temizler`() {
        CloudflareSolver.markBlocked("https://dizibox.example/x", "https://dizibox.example/x")
        assertTrue(CloudflareSolver.wasBlocked("dizibox.example"))
        assertEquals("https://dizibox.example/x", CloudflareSolver.getBlockedUrl("dizibox.example"))
        CloudflareSolver.noteUnresolved("dizibox.example", "https://dizibox.example/x")
        CloudflareSolver.unblock("dizibox.example")
        assertFalse(CloudflareSolver.wasBlocked("dizibox.example"))
        assertNull(CloudflareSolver.unresolvedSince(0)?.takeIf { it.first == "dizibox.example" })
    }

    @Test
    fun `invalidate WebView olmadan cokmez`() {
        // Birim testte CookieManager yok; sessizce geçmeli.
        CloudflareSolver.invalidate("dizibox.example", "https://dizibox.example/")
    }
}
