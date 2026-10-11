package com.kaan.play.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageTest {

    @Test
    fun turkishLocaleMapsToTurkish() {
        val lang = UiLanguage.fromLocale(Locale.forLanguageTag("tr-TR"))
        assertEquals(UiLanguage.Turkish, lang)
        assertEquals("tr-TR", lang.tmdbTag)
    }

    @Test
    fun unsupportedLocaleFallsBackToEnglish() {
        val lang = UiLanguage.fromLocale(Locale.GERMANY)
        assertEquals(UiLanguage.English, lang)
        assertEquals("en-US", lang.tmdbTag)
    }

    @Test
    fun choiceTagRoundTrips() {
        LanguageChoice.entries.forEach { assertEquals(it, LanguageChoice.fromTag(it.tag)) }
        assertEquals(LanguageChoice.System, LanguageChoice.fromTag("de"))
    }
}
