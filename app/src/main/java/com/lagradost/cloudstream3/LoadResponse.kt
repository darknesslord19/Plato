package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink

/** CloudStream yük yanıtı temel arayüzü (detay sayfası içeriği). */
interface LoadResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
    val plot: String?
    val rating: Int?     // 0-10000 arası (TMDB 8.0 → 8000)
    val tags: List<String>?
    val duration: Int?   // dakika
    val trailers: List<TrailerData>?
}

data class TrailerData(val extractorUrl: String?, val raw: List<ExtractorLink>? = null)

data class Episode(
    val data: String,       // loadLinks için anahtar
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val description: String? = null,
    val date: String? = null,
    val runTime: Int? = null,
)

data class SeasonData(
    val season: Int,
    val name: String? = null,
    val displaySeason: Int? = null,
)

data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.Movie,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: Int? = null,
    override val trailers: List<TrailerData>? = null,
    /** Film için `loadLinks` verisi. */
    val dataUrl: String = url,
    val actors: List<ActorData>? = null,
    val recommendations: List<SearchResponse>? = null,
    val backgroundPosterUrl: String? = null,
    val comingSoon: Boolean = false,
) : LoadResponse

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.TvSeries,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: Int? = null,
    override val trailers: List<TrailerData>? = null,
    val episodes: List<Episode> = emptyList(),
    val seasonNames: List<SeasonData>? = null,
    val actors: List<ActorData>? = null,
    val recommendations: List<SearchResponse>? = null,
    val backgroundPosterUrl: String? = null,
    val comingSoon: Boolean = false,
) : LoadResponse

data class AnimeLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.Anime,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: Int? = null,
    override val trailers: List<TrailerData>? = null,
    val episodes: Map<String, List<Episode>> = emptyMap(),
    val japName: String? = null,
    val engName: String? = null,
) : LoadResponse

data class LiveStreamLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.Live,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    override val duration: Int? = null,
    override val trailers: List<TrailerData>? = null,
    val dataUrl: String = url,
) : LoadResponse

data class ActorData(
    val actor: Actor,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)

data class Actor(val name: String, val image: String? = null)

// Eklentilerde kullanılan kısayol uzantı işlevleri
fun List<Episode>.getEpisodesForSeason(season: Int): List<Episode> = filter { it.season == season }

/** `LoadResponse` için `newEpisode` yardımcısı. */
fun newEpisode(data: String, block: Episode.() -> Unit = {}): Episode = Episode(data).apply(block)
