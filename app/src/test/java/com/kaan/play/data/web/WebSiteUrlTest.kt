package com.kaan.play.data.web

import com.kaan.play.data.web.WebSiteException.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WebSiteUrlTest {

    private fun assertInvalid(input: String) {
        try {
            WebSiteUrl.normalize(input)
            fail("kabul edilmemeliydi: $input")
        } catch (e: WebSiteException) {
            assertEquals(Kind.InvalidUrl, e.kind)
        }
    }

    @Test
    fun addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://example.com/", WebSiteUrl.normalize("example.com"))
        assertEquals("https://example.com/izle?x=1", WebSiteUrl.normalize("  example.com/izle?x=1  "))
        assertEquals("https://192.168.1.5:8080/", WebSiteUrl.normalize("192.168.1.5:8080"))
        assertEquals("https://localhost:3000/", WebSiteUrl.normalize("localhost:3000"))
    }

    @Test
    fun keepsAnExplicitHttpOrHttpsScheme() {
        assertEquals("http://example.com/", WebSiteUrl.normalize("http://example.com"))
        assertEquals("https://example.com/", WebSiteUrl.normalize("HTTPS://Example.COM"))
    }

    @Test
    fun keepsFragmentForSinglePageSites() {
        assertEquals("https://example.com/#/home", WebSiteUrl.normalize("example.com/#/home"))
    }

    @Test
    fun rejectsOtherSchemes() {
        listOf(
            "ftp://example.com", "file:///sdcard/a.html", "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=a.b", "javascript:alert(1)", "data:text/html,<b>x</b>", "mailto:a@b.com", "tel:abc",
        ).forEach(::assertInvalid)
    }

    @Test
    fun schemelessHostColonNumberReadsAsHostAndPort() {
        // "localhost:3000" gibi girişler çalışsın diye "tel:123" de ana makine+port sayılır: https://tel:123/ ,
        // yani zararsız (yine https, hiçbir uygulama açmaz; sayfa yüklenemez).
        assertEquals("https://tel:123/", WebSiteUrl.normalize("tel:123"))
    }

    @Test
    fun rejectsEmptyWhitespaceAndGarbage() {
        listOf("", "   ", "exa mple.com", "https://", "http://:80", "://x").forEach(::assertInvalid)
    }

    @Test
    fun rejectsUserInfoThatDisguisesTheRealHost() {
        assertInvalid("https://google.com@evil.example")
        assertInvalid("mailto:x@y.com") // "https://mailto:x@y.com" olarak okunmamalı
    }

    @Test
    fun displayHostDropsWww() {
        assertEquals("example.com", WebSiteUrl.displayHost("https://www.example.com/a"))
        assertEquals("sub.example.com", WebSiteUrl.displayHost("https://sub.example.com/"))
    }
}
