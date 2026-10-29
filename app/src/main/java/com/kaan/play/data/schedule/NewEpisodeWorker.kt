package com.kaan.play.data.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kaan.play.MainActivity
import com.kaan.play.PlayApp
import com.kaan.play.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Günde bir: takip edilen dizilerin (favoriler + izleme listesi) son bölümü son 2 günde yayınlandıysa ve daha önce
 * bildirilmediyse bildirim. Ayar kapalıysa, bildirim izni yoksa ya da ağ hatasında sessizce biter.
 */
class NewEpisodeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PlayApp ?: return Result.success()
        val container = runCatching { app.container }.getOrNull() ?: return Result.success()
        if (!container.settings.episodeAlerts.first()) return Result.success()
        if (!canNotify(applicationContext)) return Result.success()

        val shows = runCatching { container.episodeSchedule.load(force = true) }.getOrNull() ?: return Result.retry()
        val store = container.settings.store
        val notified = store.data.first()[NOTIFIED]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val alerts = ScheduleRules.newAlerts(shows, notified.toSet(), Days.today())
        if (alerts.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        alerts.forEach { (show, ep) -> post(applicationContext, show, ep) }
        store.edit { prefs ->
            prefs[NOTIFIED] = ScheduleRules.trimNotified(notified + alerts.map { (s, e) -> e.keyFor(s.tvId) }).joinToString(",")
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "new_episode_check"
        const val CHANNEL_ID = "new_episodes"
        private val NOTIFIED = stringPreferencesKey("episode_alerts_notified")

        /** Uygulama açılışında çağrılır; zaten kuruluysa dokunulmaz. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodeWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.episode_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }

        @android.annotation.SuppressLint("MissingPermission")
        private fun post(context: Context, show: ShowAirInfo, ep: AirEpisode) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TV_ID, show.tvId)
            }
            val pending = PendingIntent.getActivity(
                context, show.tvId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = if (ep.name.isBlank()) {
                context.getString(R.string.episode_notification_text, ep.season, ep.episode)
            } else {
                context.getString(R.string.episode_notification_text_named, ep.season, ep.episode, ep.name)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(context.getString(R.string.episode_notification_title, show.title))
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify("ep-${show.tvId}", 1, notification) }
        }
    }
}
