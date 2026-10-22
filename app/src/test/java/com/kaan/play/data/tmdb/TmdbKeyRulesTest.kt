package com.kaan.play.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbKeyRulesTest {

    private val key = "0123456789abcdef0123456789abcdef"

    @Test
    fun `v3 anahtari kabul edilir`() {
        assertEquals(key, TmdbKeyRules.normalize(key))
        assertEquals(key, TmdbKeyRules.normalize("  ${key.uppercase()}  "))
    }

    @Test
    fun `adresten anahtar cikarilir`() {
        assertEquals(key, TmdbKeyRules.normalize("https://api.themoviedb.org/3/movie/550?api_key=$key&language=tr"))
    }

    @Test
    fun `gecersizler reddedilir`() {
        listOf(null, "", "   ", "abc", key + "0", "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMjMifQ.abc", key.replace('a', 'z'))
            .forEach { assertNull(it, TmdbKeyRules.normalize(it)) }
    }

    @Test
    fun `ekranda maskelenir`() {
        assertEquals("0123…cdef", TmdbKeyRules.mask(key))
    }
}
