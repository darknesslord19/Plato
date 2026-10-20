package com.kaan.play.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCleanerTest {

    @Test
    fun cleansFilmSiteSuffixesAndExtractsYear() {
        val (t1, y1) = TitleCleaner.cleanTitleAndYear("By Any Means izle (2026)")
        assertEquals("By Any Means", t1)
        assertEquals(2026, y1)

        val (t2, y2) = TitleCleaner.cleanTitleAndYear("Bad Apples izle (2026)")
        assertEquals("Bad Apples", t2)
        assertEquals(2026, y2)

        val (t3, y3) = TitleCleaner.cleanTitleAndYear("Ağzımdan Kaçtı Türkçe Dublaj izle")
        assertEquals("Ağzımdan Kaçtı", t3)
        assertNull(y3)

        val (t4, y4) = TitleCleaner.cleanTitleAndYear("Deadpool & Wolverine (2024) Full HD izle")
        assertEquals("Deadpool & Wolverine", t4)
        assertEquals(2024, y4)

        val (t5, y5) = TitleCleaner.cleanTitleAndYear("Inception (2010)")
        assertEquals("Inception", t5)
        assertEquals(2010, y5)

        val (t6, y6) = TitleCleaner.cleanTitleAndYear("Apex 2026 izle")
        assertEquals("Apex", t6)
        assertEquals(2026, y6)

        val (t7, y7) = TitleCleaner.cleanTitleAndYear("Yalı Çapkını 12. Bölüm izle")
        assertEquals("Yalı Çapkını", t7)
        assertNull(y7)
    }

    @Test
    fun cleansEpisodeAndSeasonPrefixesAndSuffixes() {
        assertEquals("LINK CLICK", TitleCleaner.clean("S4B11 - LINK CLICK"))
        assertEquals("SOLO LEVELING", TitleCleaner.clean("S1B12 - SOLO LEVELING"))
        assertEquals("Breaking Bad", TitleCleaner.clean("S01E05 - Breaking Bad"))
        assertEquals("Stranger Things", TitleCleaner.clean("1. Sezon 5. Bölüm - Stranger Things"))
        assertEquals("Stranger Things", TitleCleaner.clean("Stranger Things - 1. Sezon 5. Bölüm"))
        assertEquals("Bleach", TitleCleaner.clean("Episode 24 - Bleach"))
        assertEquals("Dizi Adı", TitleCleaner.clean("Dizi Adı - Sezon Finali"))
    }

    @Test
    fun cleansQualityAndDubTags() {
        assertEquals("Inception", TitleCleaner.clean("Inception (Türkçe Dublaj)"))
        assertEquals("Interstellar", TitleCleaner.clean("Interstellar [Altyazılı]"))
        assertEquals("Avatar", TitleCleaner.clean("Avatar (1080p)"))
        assertEquals("The Matrix", TitleCleaner.clean("The Matrix [Dual]"))
        assertEquals("Film Adı", TitleCleaner.clean("Film Adı Tek Parça izle"))
    }
}
