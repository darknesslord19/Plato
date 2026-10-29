package com.kaan.play.data.home

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutRulesTest {

    private val default = HomeCatalog.defaultLayout()

    @Test
    fun defaultMatchesPreviousHome() {
        assertEquals(
            listOf("hero", "continue", "providers", "trending_movies", "trending_series", "popular_movies", "popular_series"),
            default.map { it.id },
        )
        assertTrue(default.all { it.visible })
    }

    @Test
    fun brokenOrEmptyStorageFallsBackToDefault() {
        assertEquals(default, HomeLayoutRules.decode(null))
        assertEquals(default, HomeLayoutRules.decode("{bozuk"))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        var l = HomeLayoutRules.addCustom(default, "Marvel", "Marvel", MediaType.Movie, "abc")
        l = HomeLayoutRules.setVisible(l, "hero", false)
        assertEquals(l, HomeLayoutRules.decode(HomeLayoutRules.encode(l)))
    }

    @Test
    fun normalizeDropsUnknownDuplicatesAndRestoresSpecials() {
        val l = HomeLayoutRules.normalize(
            listOf(
                HomeSection("popular_movies"),
                HomeSection("popular_movies"),
                HomeSection("silinmis_kategori"),
                HomeSection("custom:x", name = "", query = "q"),
            ),
        )
        assertEquals(listOf("hero", "continue", "providers", "popular_movies"), l.map { it.id })
    }

    @Test
    fun moveUpAndDownStopsAtEdges() {
        val up = HomeLayoutRules.move(default, "popular_movies", -1)
        assertEquals(4, up.indexOfFirst { it.id == "popular_movies" })
        assertEquals(default, HomeLayoutRules.move(default, "hero", -1))
        assertEquals(default, HomeLayoutRules.move(default, "popular_series", +1))
        val down = HomeLayoutRules.move(default, "hero", +1)
        assertEquals(listOf("continue", "hero"), down.take(2).map { it.id })
    }

    @Test
    fun addPresetAppendsOrUnhides() {
        val added = HomeLayoutRules.addPreset(default, "turkish_series")
        assertEquals("turkish_series", added.last().id)
        val hidden = HomeLayoutRules.setVisible(default, "popular_movies", false)
        val shown = HomeLayoutRules.addPreset(hidden, "popular_movies")
        assertEquals(default.size, shown.size)
        assertTrue(shown.first { it.id == "popular_movies" }.visible)
        assertEquals(default, HomeLayoutRules.addPreset(default, "yok"))
    }

    @Test
    fun customCategoryBecomesSearchQuery() {
        val l = HomeLayoutRules.addCustom(default, "  Yeşilçam ", " Kemal Sunal ", MediaType.Movie, "id1")
        val s = l.last()
        assertEquals("custom:id1", s.id)
        assertEquals("Yeşilçam", s.name)
        val q = HomeCatalog.query(s)!!
        assertEquals("search/movie", q.path)
        assertEquals("Kemal Sunal", q.params["query"])
        assertEquals(MediaType.Movie, q.type)
        // Ad ya da sorgu boşsa eklenmez.
        assertEquals(default, HomeLayoutRules.addCustom(default, " ", "x", null, "id2"))
        // Tür seçilmemişse karışık arama.
        assertEquals("search/multi", HomeCatalog.query(HomeLayoutRules.addCustom(default, "a", "b", null, "id3").last())!!.path)
    }

    @Test
    fun editCustomChangesQuery() {
        val l = HomeLayoutRules.addCustom(default, "A", "B", null, "z")
        val e = HomeLayoutRules.editCustom(l, "custom:z", "Yeni", "Sorgu", MediaType.Tv)
        val s = e.last()
        assertEquals("Yeni", s.name)
        assertEquals("tv", s.type)
    }

    @Test
    fun specialsCannotBeRemovedOnlyHidden() {
        assertEquals(default, HomeLayoutRules.remove(default, "hero"))
        assertFalse(HomeLayoutRules.remove(default, "popular_movies").any { it.id == "popular_movies" })
        assertTrue(HomeLayoutRules.setVisible(default, "providers", false).none { it.id == "providers" && it.visible })
    }

    @Test
    fun presetsHaveQueriesAndUniqueIds() {
        assertEquals(HomeCatalog.PRESETS.size, HomeCatalog.PRESETS.map { it.id }.toSet().size)
        HomeCatalog.PRESETS.forEach { assertNotNull(it.id, HomeCatalog.query(HomeSection(it.id))) }
        HomeCatalog.SPECIALS.forEach { assertNull(HomeCatalog.query(HomeSection(it))) }
        assertEquals("discover/tv", HomeCatalog.preset("turkish_series")!!.query.path)
        assertEquals("tr", HomeCatalog.preset("turkish_series")!!.query.params["with_original_language"])
    }

    @Test
    fun addableListsMissingOrHiddenPresets() {
        val addable = HomeLayoutRules.addable(default).map { it.id }
        assertFalse("trending_movies" in addable)
        assertTrue("turkish_movies" in addable)
        val hidden = HomeLayoutRules.setVisible(default, "trending_movies", false)
        assertTrue("trending_movies" in HomeLayoutRules.addable(hidden).map { it.id })
    }

    @Test
    fun shelvesAreInsertedBeforeNewShelvesAnchorOnce() {
        val shelves = listOf(
            ShelfInfo(HomeCatalog.shelfId("addon:1", "movie:top"), "En iyiler", "Katalog"),
            ShelfInfo(HomeCatalog.shelfId("addon:1", "series:new"), "Yeni diziler", "Katalog"),
        )
        val merged = HomeLayoutRules.mergeShelves(default, shelves)
        assertEquals(
            listOf("hero", "continue", "shelf:addon:1|movie:top", "shelf:addon:1|series:new", "providers"),
            merged.take(5).map { it.id },
        )
        // İkinci kez: değişiklik yok (aynı liste).
        assertTrue(HomeLayoutRules.mergeShelves(merged, shelves) === merged)
        // Taşınmış raf yerinde kalır, yeni raf yine çapanın önüne gelir.
        val moved = HomeLayoutRules.move(merged, "shelf:addon:1|movie:top", -2)
        val more = HomeLayoutRules.mergeShelves(moved, shelves + ShelfInfo("shelf:addon:2|x", "X", "Diğer"))
        assertEquals("shelf:addon:1|movie:top", more.first().id)
        assertEquals("shelf:addon:2|x", more[more.indexOfFirst { it.id == "providers" } - 1].id)
        // Ad değişince güncellenir.
        val renamed = HomeLayoutRules.mergeShelves(merged, listOf(shelves[0].copy(title = "Yeni ad")))
        assertEquals("Yeni ad", renamed.first { it.id == shelves[0].key }.name)
    }

    @Test
    fun shelfRemoveOnlyHidesAndSurvivesStorage() {
        val merged = HomeLayoutRules.mergeShelves(default, listOf(ShelfInfo("shelf:p|r", "Raf", "Eklenti")))
        val removed = HomeLayoutRules.remove(merged, "shelf:p|r")
        assertEquals(merged.size, removed.size)
        assertFalse(removed.first { it.id == "shelf:p|r" }.visible)
        assertEquals(removed, HomeLayoutRules.decode(HomeLayoutRules.encode(removed)))
        assertEquals("p" to "r", HomeCatalog.parseShelf("shelf:p|r"))
        assertEquals("cs:12" to "a|b", HomeCatalog.parseShelf("shelf:cs:12|a|b"))
        assertEquals(null, HomeCatalog.parseShelf("shelf:|x"))
        assertEquals(null, HomeCatalog.query(merged.first { it.isShelf }))
    }
}
