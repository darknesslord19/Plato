package com.kaan.play.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kaan.play.R
import com.kaan.play.data.home.HomeCatalog
import com.kaan.play.data.home.HomePreset
import com.kaan.play.data.home.HomeSection

/** Bölümün arayüz dilindeki başlığı: hazır kategoride çeviri, kullanıcının kategorisinde yazdığı ad. */
@Composable
internal fun HomeSection.title(): String =
    if (isCustom || isShelf) name.orEmpty() else stringResource(homeSectionTitleRes(id))

@StringRes
internal fun homeSectionTitleRes(id: String): Int = when (id) {
    HomeCatalog.HERO -> R.string.home_cat_hero
    HomeCatalog.CONTINUE -> R.string.home_row_continue
    HomeCatalog.PROVIDERS -> R.string.home_cat_providers
    "trending_movies" -> R.string.home_row_trending_movies
    "trending_series" -> R.string.home_row_trending_series
    "popular_movies" -> R.string.home_row_popular_movies
    "popular_series" -> R.string.home_row_popular_series
    "trending_all" -> R.string.home_cat_trending_all
    "top_rated_movies" -> R.string.home_cat_top_rated_movies
    "top_rated_series" -> R.string.home_cat_top_rated_series
    "now_playing_movies" -> R.string.home_cat_now_playing_movies
    "upcoming_movies" -> R.string.home_cat_upcoming_movies
    "airing_today_series" -> R.string.home_cat_airing_today_series
    "on_air_series" -> R.string.home_cat_on_air_series
    "movie_28" -> R.string.home_cat_movie_action
    "movie_12" -> R.string.home_cat_movie_adventure
    "movie_16" -> R.string.home_cat_movie_animation
    "movie_35" -> R.string.home_cat_movie_comedy
    "movie_80" -> R.string.home_cat_movie_crime
    "movie_99" -> R.string.home_cat_movie_documentary
    "movie_18" -> R.string.home_cat_movie_drama
    "movie_10751" -> R.string.home_cat_movie_family
    "movie_14" -> R.string.home_cat_movie_fantasy
    "movie_36" -> R.string.home_cat_movie_history
    "movie_27" -> R.string.home_cat_movie_horror
    "movie_10402" -> R.string.home_cat_movie_music
    "movie_9648" -> R.string.home_cat_movie_mystery
    "movie_10749" -> R.string.home_cat_movie_romance
    "movie_878" -> R.string.home_cat_movie_scifi
    "movie_53" -> R.string.home_cat_movie_thriller
    "movie_10752" -> R.string.home_cat_movie_war
    "movie_37" -> R.string.home_cat_movie_western
    "tv_10759" -> R.string.home_cat_tv_action
    "tv_16" -> R.string.home_cat_tv_animation
    "tv_35" -> R.string.home_cat_tv_comedy
    "tv_80" -> R.string.home_cat_tv_crime
    "tv_99" -> R.string.home_cat_tv_documentary
    "tv_18" -> R.string.home_cat_tv_drama
    "tv_10751" -> R.string.home_cat_tv_family
    "tv_10762" -> R.string.home_cat_tv_kids
    "tv_9648" -> R.string.home_cat_tv_mystery
    "tv_10764" -> R.string.home_cat_tv_reality
    "tv_10765" -> R.string.home_cat_tv_scifi
    "tv_10768" -> R.string.home_cat_tv_war
    "turkish_movies" -> R.string.home_cat_turkish_movies
    "turkish_series" -> R.string.home_cat_turkish_series
    "korean_series" -> R.string.home_cat_korean_series
    "korean_movies" -> R.string.home_cat_korean_movies
    "anime_series" -> R.string.home_cat_anime_series
    "anime_movies" -> R.string.home_cat_anime_movies
    "indian_movies" -> R.string.home_cat_indian_movies
    "spanish_series" -> R.string.home_cat_spanish_series
    "netflix_series" -> R.string.home_cat_netflix_series
    "disney_series" -> R.string.home_cat_disney_series
    "hbo_series" -> R.string.home_cat_hbo_series
    "prime_series" -> R.string.home_cat_prime_series
    "apple_series" -> R.string.home_cat_apple_series
    "hulu_series" -> R.string.home_cat_hulu_series
    else -> R.string.home_cat_unknown
}

@StringRes
internal fun HomePreset.Group.titleRes(): Int = when (this) {
    HomePreset.Group.Lists -> R.string.home_group_lists
    HomePreset.Group.MovieGenres -> R.string.home_group_movie_genres
    HomePreset.Group.SeriesGenres -> R.string.home_group_series_genres
    HomePreset.Group.Regional -> R.string.home_group_regional
    HomePreset.Group.Platforms -> R.string.home_group_platforms
}
