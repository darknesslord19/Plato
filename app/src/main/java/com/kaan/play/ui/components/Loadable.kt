package com.kaan.play.ui.components

import com.kaan.play.data.tmdb.TmdbException

/** Ağdan gelen bir verinin ekran durumu. Hata yutulmaz: [Failed] türü ve kodu taşır. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: TmdbException) : Loadable<Nothing>
}

/** [block]'u çalıştırır; yalnızca TMDB hatasını [Loadable.Failed]'a çevirir (iptal ve gerçek hatalar geçer). */
suspend inline fun <T> loadable(block: () -> T): Loadable<T> =
    try {
        Loadable.Ready(block())
    } catch (e: TmdbException) {
        Loadable.Failed(e)
    }
