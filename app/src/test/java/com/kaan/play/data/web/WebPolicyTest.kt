package com.kaan.play.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPolicyTest {

    private val policy = WebPolicy(listOf("doubleclick.net", "ads.example.org", "ExoClick.com."))

    @Test
    fun blocksTheDomainItself() {
        assertTrue(policy.isBlockedHost("doubleclick.net"))
        assertTrue(policy.isBlockedHost("DoubleClick.NET"))
        assertTrue(policy.isBlockedHost("doubleclick.net."))
    }

    @Test
    fun blocksSubdomainsAtAnyDepth() {
        assertTrue(policy.isBlockedHost("ad.doubleclick.net"))
        assertTrue(policy.isBlockedHost("a.b.c.doubleclick.net"))
        assertTrue(policy.isBlockedHost("static.exoclick.com")) // listedeki yazım büyük harf/nokta içerse de
    }

    @Test
    fun doesNotBlockLookalikesOrParents() {
        assertFalse(policy.isBlockedHost("notdoubleclick.net"))
        assertFalse(policy.isBlockedHost("doubleclick.net.example.com"))
        assertFalse(policy.isBlockedHost("net"))
        assertFalse(policy.isBlockedHost("example.org")) // "ads.example.org" yalnızca alt alan adını kapsar
        assertTrue(policy.isBlockedHost("x.ads.example.org"))
        assertFalse(policy.isBlockedHost("goodads.example.org"))
    }

    @Test
    fun emptyOrMissingHostIsNotBlocked() {
        assertFalse(policy.isBlockedHost(null))
        assertFalse(policy.isBlockedHost(""))
    }

    @Test
    fun urlsAreCheckedByTheirHost() {
        assertTrue(policy.isBlockedUrl("https://ad.doubleclick.net/pixel.gif?x=doubleclick.net"))
        assertFalse(policy.isBlockedUrl("https://example.com/?u=https://doubleclick.net/"))
        assertFalse(policy.isBlockedUrl("not a url"))
    }

    @Test
    fun navigationAllowsOnlyHttpAndHttps() {
        assertTrue(policy.allowsNavigation("https://example.com/watch"))
        assertTrue(policy.allowsNavigation("http://192.168.1.5:8080/"))
        listOf(
            "intent://scan/#Intent;scheme=zxing;end", "market://details?id=a.b", "android-app://com.a.b",
            "tel:123", "sms:123", "mailto:a@b.com", "javascript:alert(1)", "data:text/html,x", "file:///sdcard/a.html",
            "about:blank", "ftp://example.com", "", null,
        ).forEach { assertFalse("izin verilmemeliydi: $it", policy.allowsNavigation(it)) }
    }

    @Test
    fun navigationToABlockedDomainIsRefused() {
        assertFalse(policy.allowsNavigation("https://ad.doubleclick.net/click"))
    }

    @Test
    fun sameSiteMeansEqualHostOrSubdomain() {
        assertTrue(WebPolicy.sameSite("https://example.com/a", "https://example.com/b"))
        assertTrue(WebPolicy.sameSite("https://www.example.com/", "https://example.com/x"))
        assertTrue(WebPolicy.sameSite("https://example.com/", "https://cdn.example.com/x"))
        assertFalse(WebPolicy.sameSite("https://example.com/", "https://notexample.com/"))
        assertFalse(WebPolicy.sameSite("https://example.com/", "https://example.com.evil.net/"))
        assertFalse(WebPolicy.sameSite(null, "https://example.com/"))
        assertFalse(WebPolicy.sameSite("https://example.com/", "intent://x"))
    }

    @Test
    fun builtInListIsWellFormedAndBlocksCommonAdHosts() {
        val builtIn = WebPolicy()
        assertTrue(AdDomains.all.isNotEmpty())
        AdDomains.all.forEach { d ->
            assertEquals("küçük harf, boşluksuz, noktasız olmalı: $d", d, d.trim().lowercase().trimEnd('.'))
            assertTrue("en az iki etiket: $d", '.' in d)
            assertTrue(builtIn.isBlockedHost(d))
            assertTrue(builtIn.isBlockedHost("sub.$d"))
        }
        assertEquals("yinelenen kayıt var", AdDomains.all.size, AdDomains.all.toSet().size)
        assertTrue(builtIn.isBlockedHost("pagead2.googlesyndication.com"))
        assertFalse(builtIn.isBlockedHost("www.google.com"))
        assertFalse(builtIn.isBlockedHost("example.com"))
    }
}
