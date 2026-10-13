package com.kaan.play.data.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamLogTest {
    @Test
    fun `magnet yalnizca sema olarak ozetlenir`() {
        assertEquals("magnet:", StreamLog.describe("magnet:?xt=urn:btih:ABC&dn=film"))
    }

    @Test
    fun `http adresinde sorgu ve token loga girmez`() {
        assertEquals("https://cdn.example.com/.m3u8", StreamLog.describe("https://user:pw@cdn.example.com/a/b/index.m3u8?token=SECRET"))
        assertEquals("https://x.io/.torrent", StreamLog.describe("https://x.io/f/film.torrent"))
        assertEquals("http://x.io/", StreamLog.describe("http://x.io"))
    }

    @Test
    fun `adres olmayan metin`() {
        assertEquals("(adres değil)", StreamLog.describe("[object Object]"))
    }
}
