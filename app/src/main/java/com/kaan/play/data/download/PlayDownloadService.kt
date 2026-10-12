package com.kaan.play.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.kaan.play.MainActivity
import com.kaan.play.PlayApp
import com.kaan.play.R
import com.kaan.play.data.model.MediaType

/**
 * İndirmeleri ön plan servisi olarak sürdürür (bildirimde ilerleme). Android 14+ için manifestte tür `dataSync`.
 * Ağ koşulu yüzünden bekleyen indirmeler için [PlatformScheduler] koşul sağlanınca servisi yeniden başlatır.
 */
@OptIn(UnstableApi::class)
class PlayDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0,
) {

    private val center: DownloadCenter get() = (application as PlayApp).container.downloads

    override fun getDownloadManager(): DownloadManager = center.manager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
            this,
            R.drawable.ic_stat_download,
            open,
            currentTitle(downloads),
            downloads,
            notMetRequirements,
        )
    }

    /** İnen (yoksa ilk) indirmenin adı: "Dizi · S1 B3" ya da film adı. Bilinmiyorsa null (Media3'ün genel metni kalır). */
    private fun currentTitle(downloads: List<Download>): String? {
        val active = downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: downloads.firstOrNull() ?: return null
        val row = center.latestRows.firstOrNull { it.id.toString() == active.request.id } ?: return null
        return if (row.type == MediaType.Tv) getString(R.string.player_title_episode, row.title, row.season, row.episode) else row.title
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
        const val JOB_ID = 1001
    }
}
