package com.kaan.play.data.subtitle

import java.util.Locale

/**
 * Altyazı dil etiketleri: addon'lar ISO 639-2 (`eng`, `tur`), bazıları 639-1 (`en`), bölgeli (`pt-BR`) ya da düz ad
 * (`English`) verir. Hepsi 639-1 iki harfli temel dile (`en`, `tr`) indirilir; gruplama ve varsayılan seçim bununla yapılır.
 * Tanınmayan etiket null döner (grup "bilinmeyen dil" olur).
 */
object SubtitleLanguages {

    private val iso1: Set<String> = Locale.getISOLanguages().toSet()

    /** 639-2/T (`tur`) ve 639-2/B (`fre`, `ger` ...) üç harfli kodlar -> iki harfli kod. */
    private val iso3: Map<String, String> = buildMap {
        for (code in iso1) put(Locale.forLanguageTag(code).isO3Language, code)
        // Bibliyografik (B) kodlar T kodlardan farklı olanlar + OpenSubtitles'ın eski/özel kodları.
        putAll(
            mapOf(
                "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs",
                "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
                "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk",
                "tib" to "bo", "wel" to "cy",
                "pob" to "pt", "pom" to "pt", "scc" to "sr", "scr" to "hr", "zht" to "zh", "zhs" to "zh", "zhc" to "zh",
            ),
        )
    }

    /** İngilizce dil adı (`turkish`) -> iki harfli kod. */
    private val byEnglishName: Map<String, String> =
        iso1.associateBy { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT) }

    /** `tur`, `tr`, `tr-TR`, `pt_BR`, `Turkish` -> `tr`; boş/tanınmayan -> null. */
    fun normalize(raw: String?): String? {
        val text = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (text.isEmpty() || text == "und" || text == "unknown") return null
        val base = text.replace('_', '-').substringBefore('-')
        return when {
            base.length == 2 && base in iso1 -> base
            base.length == 3 -> iso3[base]
            else -> byEnglishName[text] ?: byEnglishName[base]
        }
    }

    /** Arayüz dilinde ad ("tr" + arayüz `tr` -> "Türkçe", `en` -> "İngilizce"); ad bulunamazsa kodun kendisi büyük harfle. */
    fun displayName(language: String, uiLanguage: String): String {
        val ui = Locale.forLanguageTag(uiLanguage)
        val name = Locale.forLanguageTag(language).getDisplayLanguage(ui)
        if (name.isBlank() || name.equals(language, ignoreCase = true)) return language.uppercase(Locale.ROOT)
        return name.replaceFirstChar { it.titlecase(ui) }
    }
}
