package com.kaan.play.data.torrent

import android.util.Log
import com.kaan.play.BuildConfig

/**
 * Torrent/oynatma teşhis logu (etiket `PlayTorrent`). **Yalnızca debug derlemede** yazar; release'te hiçbir şey yazmaz.
 * Tam adres, magnet ve yerel sunucu belirteci loglanmaz: yalnızca şema/host/uzantı, infoHash, HTTP kodu ve libtorrent alert'leri.
 */
object TorrentDiag {
    const val TAG = "PlayTorrent"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun e(message: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, error)
    }
}
