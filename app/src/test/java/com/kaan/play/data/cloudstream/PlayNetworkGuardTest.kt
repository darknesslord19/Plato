package com.kaan.play.data.cloudstream

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PlayNetworkGuardTest {

    @Before
    fun setUp() {
        PlayNetworkGuard.clearAllowedLoopbackPorts()
    }

    @After
    fun tearDown() {
        PlayNetworkGuard.clearAllowedLoopbackPorts()
    }

    @Test
    fun `allowed loopback port passes for 127_0_0_1 and localhost`() {
        PlayNetworkGuard.allowLoopbackPort(8888)

        // 127.0.0.1 on allowed port
        PlayNetworkGuard.checkUrl("http://127.0.0.1:8888/stream.m3u8")

        // localhost on allowed port
        PlayNetworkGuard.checkUrl("http://localhost:8888/stream.m3u8")

        // Other 127.x.x.x on allowed port
        PlayNetworkGuard.checkUrl("http://127.0.0.2:8888/playlist")
    }

    @Test
    fun `disallowed loopback port is rejected`() {
        PlayNetworkGuard.allowLoopbackPort(8888)

        try {
            PlayNetworkGuard.checkUrl("http://127.0.0.1:9999/stream.m3u8")
            fail("Expected SecurityException for port 9999")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }

        try {
            PlayNetworkGuard.checkUrl("http://localhost:9999/stream.m3u8")
            fail("Expected SecurityException for localhost port 9999")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }
    }

    @Test
    fun `localhost without allowed port is rejected`() {
        try {
            PlayNetworkGuard.checkUrl("http://localhost/stream")
            fail("Expected SecurityException for localhost without allowed port")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }

        try {
            PlayNetworkGuard.checkUrl("http://127.0.0.1/stream")
            fail("Expected SecurityException for 127.0.0.1 without allowed port")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }
    }

    @Test
    fun `ipv6 loopback and 0_0_0_0 are unconditionally blocked even on allowed port`() {
        PlayNetworkGuard.allowLoopbackPort(8888)

        // ::1 with brackets
        try {
            PlayNetworkGuard.checkUrl("http://[::1]:8888/stream")
            fail("Expected SecurityException for ::1")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }

        // 0.0.0.0
        try {
            PlayNetworkGuard.checkUrl("http://0.0.0.0:8888/stream")
            fail("Expected SecurityException for 0.0.0.0")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }
    }

    @Test
    fun `domains starting with fc or fd pass safely`() {
        // Domains like fcbarcelona.com or fda.gov must not be confused with IPv6 fc00::/7
        PlayNetworkGuard.checkUrl("https://fcbarcelona.com/news")
        PlayNetworkGuard.checkUrl("https://fda.gov/safety")
        PlayNetworkGuard.checkUrl("http://fc-koln.de/home")
        assertFalse(PlayNetworkGuard.isBlockedHost("fcbarcelona.com"))
        assertFalse(PlayNetworkGuard.isBlockedHost("fda.gov"))
    }

    @Test
    fun `ipv6 ULA addresses starting with fc or fd are blocked`() {
        try {
            PlayNetworkGuard.checkUrl("http://[fc00::1]:8080/stream")
            fail("Expected SecurityException for IPv6 fc00::1")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }

        try {
            PlayNetworkGuard.checkUrl("http://[fd12:3456:789a::1]:8080/stream")
            fail("Expected SecurityException for IPv6 fd12:...")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("private address blocked") == true)
        }
    }

    @Test
    fun `private IPv4 ranges are blocked`() {
        listOf(
            "http://10.0.0.1/video",
            "http://192.168.1.1/video",
            "http://172.16.0.1/video",
            "http://172.31.255.255/video",
            "http://169.254.1.1/video",
            "http://100.64.0.1/video",
        ).forEach { url ->
            try {
                PlayNetworkGuard.checkUrl(url)
                fail("Expected SecurityException for private IPv4: $url")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("private address blocked") == true)
            }
        }
    }

    @Test
    fun `non http protocols are rejected`() {
        try {
            PlayNetworkGuard.checkUrl("ftp://example.com/file")
            fail("Expected SecurityException for ftp")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("only http(s) allowed") == true)
        }
    }
}
