package com.kaan.play.data.watchlist

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistRulesTest {
    private fun item(id: Int, type: String = "tv") = WatchlistItem(id, type, "T$id", null, id.toLong())

    @Test
    fun addPutsNewestFirstWithoutDuplicates() {
        var l = WatchlistRules.add(emptyList(), item(1))
        l = WatchlistRules.add(l, item(2))
        l = WatchlistRules.add(l, item(1))
        assertEquals(listOf(1, 2), l.map { it.tmdbId })
        // Aynı kimlik farklı türde ayrı içeriktir.
        l = WatchlistRules.add(l, item(1, "movie"))
        assertEquals(3, l.size)
    }

    @Test
    fun containsAndRemoveUseType() {
        val l = listOf(item(1), item(1, "movie"))
        assertTrue(WatchlistRules.contains(l, MediaType.Tv, 1))
        val r = WatchlistRules.remove(l, MediaType.Tv, 1)
        assertFalse(WatchlistRules.contains(r, MediaType.Tv, 1))
        assertTrue(WatchlistRules.contains(r, MediaType.Movie, 1))
    }

    @Test
    fun encodeDecodeAndBrokenData() {
        val l = listOf(item(5), item(6, "movie"))
        assertEquals(l, WatchlistRules.decode(WatchlistRules.encode(l)))
        assertTrue(WatchlistRules.decode("{bozuk").isEmpty())
        assertTrue(WatchlistRules.decode(null).isEmpty())
    }

    @Test
    fun listIsCapped() {
        var l = emptyList<WatchlistItem>()
        repeat(WatchlistRules.MAX_ITEMS + 10) { l = WatchlistRules.add(l, item(it)) }
        assertEquals(WatchlistRules.MAX_ITEMS, l.size)
    }
}
