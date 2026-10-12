package com.kaan.play.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TMDB yanıt şemaları. Her alan bilerek nullable/varsayılanlı: eksik alan tüm yanıtı düşürmesin.

@Serializable
internal data class ListDto(
    val page: Int = 1,
    val results: List<ItemDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
internal data class ItemDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
internal data class DetailDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val seasons: List<SeasonDto> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
)

@Serializable
internal data class GenreDto(val name: String = "")

@Serializable
internal data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
internal data class SeasonDto(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
internal data class SeasonDetailDto(
    @SerialName("season_number") val seasonNumber: Int,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
internal data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)
