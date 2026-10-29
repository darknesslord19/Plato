package com.kaan.play.data.tmdb

import com.kaan.play.data.model.MediaType

/**
 * TMDB'de sayfalı bir liste: yol (`trending/movie/week`, `discover/tv`, `search/multi` …), sonuçların türü
 * (karışık listede null; öğenin kendi `media_type`'ı kullanılır) ve ek sorgu parametreleri. `page` ve `language`
 * istekte eklenir. Android'den bağımsızdır (ana sayfa kategorileri birim testli).
 */
data class TmdbQuery(
    val path: String,
    val type: MediaType?,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        fun trending(type: MediaType?) =
            TmdbQuery(if (type == null) "trending/all/week" else "trending/${type.tmdbPath}/week", type)

        fun list(type: MediaType, name: String) = TmdbQuery("${type.tmdbPath}/$name", type)

        /** `discover`: popülerliğe göre, oy sayısı çok düşük olanlar elenir (boş/çöp sonuç gelmesin). */
        fun discover(type: MediaType, vararg params: Pair<String, String>) = TmdbQuery(
            "discover/${type.tmdbPath}",
            type,
            mapOf("sort_by" to "popularity.desc", "include_adult" to "false") + params,
        )

        /** Kullanıcının yazdığı arama: tür seçiliyse yalnızca film ya da dizi, değilse ikisi birden. */
        fun search(text: String, type: MediaType?) = TmdbQuery(
            if (type == null) "search/multi" else "search/${type.tmdbPath}",
            type,
            mapOf("query" to text.trim(), "include_adult" to "false"),
        )
    }
}
