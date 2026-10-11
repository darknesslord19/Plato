package com.kaan.play.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Arayüzün fiilen kullandığı dil. TMDB isteği ve altyazı varsayılanı buradan türetilir. */
enum class UiLanguage(val iso: String, val tmdbTag: String) {
    English("en", "en-US"),
    Turkish("tr", "tr-TR");

    companion object {
        /** Desteklenmeyen her dil İngilizceye düşer. */
        fun fromLocale(locale: Locale): UiLanguage =
            if (locale.language == Turkish.iso) Turkish else English
    }
}

/** Ayarlar'daki "Dil" seçimi. [tag] null ise sistem dili izlenir. */
enum class LanguageChoice(val tag: String?) {
    System(null),
    English("en"),
    Turkish("tr");

    companion object {
        fun fromTag(tag: String?): LanguageChoice = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/**
 * Uygulama başına dil, AppCompat'e devredilir (API 33+ sistem, altında AppCompat saklar).
 * Seçimin tek doğruluk kaynağı AppCompat'tir; ayrıca DataStore'da kopyası tutulmaz.
 */
object AppLanguage {

    fun choice(): LanguageChoice {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) LanguageChoice.System else LanguageChoice.fromTag(locales[0]?.language)
    }

    fun apply(choice: LanguageChoice) {
        val list = choice.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(list)
    }

    /** Seçime ya da (Sistem ise) cihaz diline göre geçerli arayüz dili. */
    fun effective(): UiLanguage {
        val app = AppCompatDelegate.getApplicationLocales()
        val locale = if (!app.isEmpty) app[0] else null
        return UiLanguage.fromLocale(locale ?: Locale.getDefault())
    }
}
