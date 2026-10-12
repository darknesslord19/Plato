package com.kaan.play.ui.player

import android.content.Context
import android.content.Intent
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import kotlinx.serialization.Serializable

/** Oynatıcı etkinliğine giden her şey: ne oynanıyor, hangi kaynakla, nereden başlanacak. */
@Serializable
data class PlaySession(
    val target: PlayTarget,
    val stream: PlayableStream,
    val startPositionMs: Long,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).putExtra(EXTRA, PlayJson.encodeToString(serializer(), this))

    companion object {
        private const val EXTRA = "session"

        /** Bozuk/eksik veri null döner (etkinlik kendini kapatır). */
        fun from(intent: Intent): PlaySession? {
            val text = intent.getStringExtra(EXTRA) ?: return null
            return try {
                PlayJson.decodeFromString(serializer(), text)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
