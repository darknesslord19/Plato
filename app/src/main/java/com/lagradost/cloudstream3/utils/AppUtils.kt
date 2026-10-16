package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mapper

object AppUtils {
    @JvmStatic
    fun toJson(obj: Any?): String = try {
        if (obj == null) "null" else mapper.writeValueAsString(obj)
    } catch (_: Exception) {
        obj.toString()
    }
}

/** Yardımcı uzantı işlevleri — eklentiler bu paketten fonksiyonlar çağırabilir. */

fun String.toRatingInt(): Int? {
    return this.replace(",", ".").toDoubleOrNull()?.times(10)?.toInt()
}

fun String?.toSafeInt(): Int? = this?.trim()?.toIntOrNull()

fun String.fixTitle(): String = this.trim()

fun String.fixUrl(baseUrl: String): String {
    return when {
        startsWith("http://") || startsWith("https://") -> this
        startsWith("//") -> "https:$this"
        startsWith("/") -> "${baseUrl.trimEnd('/')}$this"
        else -> "${baseUrl.trimEnd('/')}/$this"
    }
}

/**
 * Metin kalite etiketlerini sayısal çözünürlüğe çevirir.
 */
fun getQualityFromString(quality: String?): Int {
    return when {
        quality == null -> Qualities.Unknown.value
        quality.contains("4K", ignoreCase = true) || quality.contains("2160") -> Qualities.P2160.value
        quality.contains("1080") -> Qualities.P1080.value
        quality.contains("720") -> Qualities.P720.value
        quality.contains("480") -> Qualities.P480.value
        quality.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

/**
 * Bir `List<TvType>` değerini Play'in kullandığı türlere dönüştürür.
 */
fun Set<TvType>.toPlayTypes(): Set<String> = flatMapTo(mutableSetOf()) { type ->
    when (type) {
        TvType.Movie, TvType.AnimeMovie, TvType.DocumentaryMovie -> listOf("movie")
        TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.OVA,
        TvType.AsianDrama, TvType.Documentary -> listOf("tv")
        TvType.Live -> listOf("movie", "tv")
        else -> listOf("movie")
    }
}

