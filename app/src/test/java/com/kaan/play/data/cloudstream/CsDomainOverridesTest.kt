package com.kaan.play.data.cloudstream

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CsDomainRulesTest {

    @Test
    fun `adres kanonik bicime cevrilir`() {
        assertEquals("https://yeni.site", CsDomainRules.normalize("yeni.site"))
        assertEquals("https://yeni.site", CsDomainRules.normalize("  https://yeni.site/  "))
        assertEquals("https://www.yeni.site", CsDomainRules.normalize("https://www.yeni.site/film/abc?x=1#y"))
        assertEquals("http://yeni.site", CsDomainRules.normalize("http://yeni.site"))
        assertEquals("https://yeni.site:8443", CsDomainRules.normalize("yeni.site:8443"))
        assertEquals("https://yeni.site", CsDomainRules.normalize("https://yeni.site:443"))
        assertEquals("https://yeni.site", CsDomainRules.normalize("HTTPS://YENI.SITE"))
    }

    @Test
    fun `gecersiz adresler reddedilir`() {
        listOf(
            "",
            "   ",
            "yeni site.com",
            "ftp://yeni.site",
            "javascript:alert(1)",
            "file:///etc/hosts",
            "intent://x",
            "https://kullanici:parola@yeni.site",
            "yenisite",
            "https://",
        ).forEach { assertNull("kabul edilmemeli: '$it'", CsDomainRules.normalize(it)) }
    }

    @Test
    fun `yerel ve ozel ag adresleri reddedilir`() {
        listOf(
            "localhost",
            "http://localhost:8080",
            "test.localhost",
            "127.0.0.1",
            "http://127.0.0.1:5555",
            "10.0.0.5",
            "192.168.1.1",
            "172.16.0.1",
            "0.0.0.0",
            "http://[::1]",
            "http://[fd00::1]",
            "http://[fe80::1]",
        ).forEach { assertNull("kabul edilmemeli: '$it'", CsDomainRules.normalize(it)) }
    }

    @Test
    fun `herkese acik ip kabul edilir`() {
        assertEquals("https://93.184.216.34", CsDomainRules.normalize("93.184.216.34"))
    }

    @Test
    fun `ekranda yalnizca sunucu adi gorunur`() {
        assertEquals("yeni.site", CsDomainRules.displayHost("https://yeni.site"))
        assertEquals("www.yeni.site", CsDomainRules.displayHost("https://www.yeni.site:8443"))
    }
}

class CsDomainOverridesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun overrides(name: String = "cs_domains") = CsDomainOverrides(
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "$name.preferences_pb") },
    )

    @Test
    fun `adres kaydedilir ve eklenti adina gore okunur`() = runBlocking {
        val store = overrides()
        assertTrue(store.set("HDFilm", "yeni-adres.com/"))
        assertEquals("https://yeni-adres.com", store.get("HDFilm"))
        assertNull(store.get("BaskaEklenti"))
        assertEquals(mapOf("HDFilm" to "https://yeni-adres.com"), store.all.first())
    }

    @Test
    fun `gecersiz adres hicbir sey yazmaz`() = runBlocking {
        val store = overrides()
        assertFalse(store.set("HDFilm", "192.168.1.10"))
        assertNull(store.get("HDFilm"))
        assertEquals(emptyMap<String, String>(), store.all.first())
    }

    @Test
    fun `bos adres gecersiz kilmayi siler`() = runBlocking {
        val store = overrides("cs_domains_clear")
        store.set("A", "a.site")
        assertTrue(store.set("A", null))
        assertNull(store.get("A"))
        assertTrue(store.set("A", "  "))
        assertNull(store.get("A"))
    }
}
