package com.lagradost.cloudstream3

/** CloudStream arama yanıtı temel arayüzü. */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType?
    val posterUrl: String?
    val year: Int?
    val id: Int?
    val score: SearchQuality?
}

enum class SearchQuality {
    CamRip, HdCam, CamMicFix, HardcodedSubs, BlurredSubs, Cam, Telecine, Telesync, HD, HQ, DVD,
    Good, True, HDTV, Unknown, SD, FourK, UHD, BluRay, WebRip,
}

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Movie,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val id: Int? = null,
    override val score: SearchQuality? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.TvSeries,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val id: Int? = null,
    override val score: SearchQuality? = null,
    val episodes: Int? = null,
) : SearchResponse

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Anime,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val id: Int? = null,
    override val score: SearchQuality? = null,
    val dubStatus: Set<String>? = null,
) : SearchResponse

data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Live,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val id: Int? = null,
    override val score: SearchQuality? = null,
) : SearchResponse
