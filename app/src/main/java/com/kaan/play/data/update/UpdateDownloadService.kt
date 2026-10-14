package com.kaan.play.data.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kaan.play.MainActivity
import com.kaan.play.PlayApp
import com.kaan.play.R
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Güncelleme bildirimleri: kimlikler, kanal ve bildirimleri kuran küçük yardımcılar. */
object UpdateNotifications {
    const val ACTION_INSTALL = "com.kaan.play.action.INSTALL_UPDATE"
    const val EXTRA_INFO = "update_info"

    const val CHANNEL_ID = "update"
    const val PROGRESS_ID = 2001
    const val RESULT_ID = 2002

    fun encode(info: UpdateInfo): String = PlayJson.encodeToString(UpdateInfo.serializer(), info)

    /** Bozuk/yabancı girdi null döner. */
    fun decode(text: String?): UpdateInfo? =
        text?.let { runCatching { PlayJson.decodeFromString(UpdateInfo.serializer(), it) }.getOrNull() }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun openApp(context: Context, info: UpdateInfo?, install: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (install && info != null) intent.setAction(ACTION_INSTALL).putExtra(EXTRA_INFO, encode(info))
        return PendingIntent.getActivity(
            context,
            if (install) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** [percent] < 0: toplam bilinmiyor (belirsiz çubuk). */
    fun progress(context: Context, info: UpdateInfo, percent: Int): Notification {
        val cancel = PendingIntent.getService(
            context,
            2,
            Intent(context, UpdateDownloadService::class.java).setAction(UpdateDownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.update_notification_downloading, info.tag))
            .setContentText(if (percent >= 0) context.getString(R.string.update_progress, percent) else null)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, info, install = false))
            .addAction(0, context.getString(R.string.update_cancel), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** İndirme bitti, doğrulandı: dokununca kurulum ekranı açılır. */
    fun ready(context: Context, info: UpdateInfo): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.update_notification_ready))
            .setContentText(context.getString(R.string.update_notification_ready_text, info.tag))
            .setContentIntent(openApp(context, info, install = true))
            .setAutoCancel(true)
            .build()

    fun failed(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.update_failed_title))
            .setContentText(context.getString(R.string.update_notification_failed_text))
            .setContentIntent(openApp(context, null, install = false))
            .setAutoCancel(true)
            .build()

    /** İzin yoksa (Android 13+) ya da bildirimler kapalıysa sessizce geçer: indirme/kurulum bildirimsiz de çalışır. */
    fun notify(context: Context, id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelResult(context: Context) {
        NotificationManagerCompat.from(context).cancel(RESULT_ID)
    }
}

/**
 * Güncelleme indirmesini ön planda tutar: bildirimde ilerleme (İptal düğmesiyle), uygulamadan çıkılsa da kesilmez. İndirmenin
 * kendisi [UpdateFlow] içinde uygulama kapsamında yürür; bu servis süreci ön plan önceliğinde tutar ve durumu bildirime yansıtır.
 * Bitince "Güncelleme indirildi, yüklemek için dokun" bildirimi çıkar (dokununca kurulum ekranı). Süreç ölürse sistem isteği
 * yeniden iletir ([START_REDELIVER_INTENT]) ve indirme `Range` ile yarım dosyadan sürer.
 *
 * Bildirim izni (Android 13+) reddedilmiş olsa da servis çalışır, yalnızca bildirim görünmez; ön plana alınamazsa (sistem
 * kısıtı) indirme yine uygulama kapsamında sürer, çökme olmaz.
 */
class UpdateDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastPercent = Int.MIN_VALUE
    private var lastNotifyAt = 0L
    private var foreground = false

    private val container get() = (application as PlayApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            container.updateFlow.dismiss() // durum Idle olunca gözlemci bildirimi kaldırıp servisi durdurur
            if (observer == null) stopSelf()
            return START_NOT_STICKY
        }
        val info = UpdateNotifications.decode(intent?.getStringExtra(UpdateNotifications.EXTRA_INFO))
        if (info?.apk == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        UpdateNotifications.ensureChannel(this)
        UpdateNotifications.cancelResult(this) // eski "indirildi/olmadı" bildirimi kalmasın
        promoteToForeground(UpdateNotifications.progress(this, info, percent = -1))

        val flow = container.updateFlow
        // Önce indirme (durum Downloading olur), sonra gözlemci: ilk gördüğü durum Idle olup servisi erken durdurmasın.
        flow.launchDownload(info, container.appScope)
        observer?.cancel()
        lastPercent = Int.MIN_VALUE
        observer = serviceScope.launch {
            flow.state.collect { state ->
                when (state) {
                    is UpdateState.Downloading -> {
                        val percent = if (state.total > 0) (state.done * 100 / state.total).toInt() else -1
                        // Android bildirim güncellemelerini paket başına saniyede ~5 ile sınırlar; aşılırsa bildirimleri (kaldırma
                        // dahil) atar ve ilerleme bildirimi takılı kalır. Hızlı ağda yüzde değişimi bundan sık olur: saniyede en çok bir.
                        val now = SystemClock.elapsedRealtime()
                        if (percent != lastPercent && now - lastNotifyAt >= PROGRESS_MIN_INTERVAL_MS) {
                            lastPercent = percent
                            lastNotifyAt = now
                            UpdateNotifications.notify(this@UpdateDownloadService, UpdateNotifications.PROGRESS_ID, UpdateNotifications.progress(this@UpdateDownloadService, info, percent))
                        }
                    }
                    // Pencere ön plandaysa kurulum ekranını zaten açıp durumu kapatmış olabilir: bildirime gerek kalmaz.
                    is UpdateState.Ready ->
                        finish(if (flow.state.value is UpdateState.Ready) UpdateNotifications.ready(this@UpdateDownloadService, info) else null)
                    is UpdateState.Failed -> finish(UpdateNotifications.failed(this@UpdateDownloadService))
                    else -> finish(null) // iptal edildi / başka bir duruma geçti
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun promoteToForeground(notification: Notification) {
        foreground = try {
            ServiceCompat.startForeground(
                this,
                UpdateNotifications.PROGRESS_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
            true
        } catch (_: Exception) {
            // Sistem ön plana almaya izin vermedi (ör. Android 12+ arka plan kısıtı): indirme uygulama kapsamında yine sürer.
            false
        }
    }

    private fun finish(result: Notification?) {
        observer?.cancel()
        observer = null
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Kaldırma kesinleşsin: ön plan bildirimi (varsa) ve yarım kalmış ilerleme bildirimi her durumda silinir.
        NotificationManagerCompat.from(this).cancel(UpdateNotifications.PROGRESS_ID)
        foreground = false
        if (result != null) UpdateNotifications.notify(this, UpdateNotifications.RESULT_ID, result)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.kaan.play.action.CANCEL_UPDATE_DOWNLOAD"

        /** İlerleme bildirimi en sık bu aralıkla güncellenir (sistem hız sınırının çok altında). */
        private const val PROGRESS_MIN_INTERVAL_MS = 1_000L

        /** Servisi başlatır; sistem engellerse false (çağıran indirmeyi doğrudan sürdürür). */
        fun start(context: Context, info: UpdateInfo): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UpdateDownloadService::class.java).putExtra(UpdateNotifications.EXTRA_INFO, UpdateNotifications.encode(info)),
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
