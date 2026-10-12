package com.kaan.play.data.model

/** [addonType]: addon protokolündeki tür adı (`movie` / `series`). */
enum class MediaType(val tmdbPath: String, val addonType: String) {
    Movie("movie", "movie"),
    Tv("tv", "series");

    companion object {
        fun fromTmdb(value: String?): MediaType? = entries.firstOrNull { it.tmdbPath == value }
    }
}

/** Liste/kart için yeterli özet. Birincil kimlik TMDB id'dir. */
data class MediaSummary(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val rating: Double?,
)

data class MediaDetail(
    val summary: MediaSummary,
    /** Addon'lar IMDb "tt..." kimliği ister; TMDB external_ids'ten türetilir. Yoksa addon sorgulanamaz. */
    val imdbId: String?,
    val tagline: String,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    /** Yalnızca diziler için; "Özel bölümler" (sezon 0) hariç. */
    val seasons: List<SeasonInfo>,
)

data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val posterPath: String?,
)

data class SeasonDetail(
    val number: Int,
    val episodes: List<Episode>,
)

data class Episode(
    val number: Int,
    val name: String,
    val overview: String,
    val stillPath: String?,
    val runtimeMinutes: Int?,
    val airDate: String?,
)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}
