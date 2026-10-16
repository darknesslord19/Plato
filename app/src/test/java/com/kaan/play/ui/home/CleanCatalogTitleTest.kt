package com.kaan.play.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanCatalogTitleTest {

    @Test
    fun cleansAnimecixPrefix() {
        assertEquals("LINK CLICK", cleanCatalogTitle("S4B11 - LINK CLICK"))
        assertEquals("SOLO LEVELING", cleanCatalogTitle("S1B12 - SOLO LEVELING"))
    }

    @Test
    fun cleansSeasonEpisodePrefixes() {
        assertEquals("Breaking Bad", cleanCatalogTitle("S01E05 - Breaking Bad"))
        assertEquals("Stranger Things", cleanCatalogTitle("1. Sezon 5. Bölüm - Stranger Things"))
        assertEquals("Yalı Çapkını", cleanCatalogTitle("12. Bölüm - Yalı Çapkını"))
        assertEquals("Bleach", cleanCatalogTitle("Episode 24 - Bleach"))
    }

    @Test
    fun cleansSeasonEpisodeSuffixes() {
        assertEquals("Link Click", cleanCatalogTitle("Link Click - S4B11"))
        assertEquals("Stranger Things", cleanCatalogTitle("Stranger Things - 1. Sezon 5. Bölüm"))
        assertEquals("Yalı Çapkını", cleanCatalogTitle("Yalı Çapkını 12. Bölüm"))
        assertEquals("Dizi Adı", cleanCatalogTitle("Dizi Adı - Sezon Finali"))
    }

    @Test
    fun cleansDubAndSubTags() {
        assertEquals("Inception", cleanCatalogTitle("Inception (Türkçe Dublaj)"))
        assertEquals("Interstellar", cleanCatalogTitle("Interstellar [Altyazılı]"))
        assertEquals("Avatar", cleanCatalogTitle("Avatar (1080p)"))
    }

    @Test
    fun preservesStandardTitles() {
        assertEquals("The Dark Knight", cleanCatalogTitle("The Dark Knight"))
        assertEquals("Iron Man 2", cleanCatalogTitle("Iron Man 2"))
        assertEquals("Mob Psycho 100", cleanCatalogTitle("Mob Psycho 100"))
    }
}
