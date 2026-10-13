package com.kaan.play.ui.player

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import kotlin.math.roundToInt

/** Dikey kaydırmayla değiştirilen iki düzey: ekranın sağ yarısı ses, sol yarısı parlaklık. */
enum class LevelKind { Volume, Brightness }

/** Android'den bağımsız jest kuralları (birim testli). Düzeyler 0..1 aralığındadır. */
object LevelRules {
    /** Tam aralık (0 → 100%) için gereken kaydırma: ekran yüksekliğinin bu kadarı. Küçük hareket küçük değişim demektir. */
    const val RANGE_FRACTION = 0.75f

    /** Pencere parlaklığı 0'a inmez (bazı cihazlarda ekran tamamen kararır). */
    const val MIN_BRIGHTNESS = 0.01f

    /** Ekranın sağ yarısı ses, sol yarısı parlaklık. */
    fun kindAt(x: Float, width: Float): LevelKind = if (x >= width / 2f) LevelKind.Volume else LevelKind.Brightness

    /** [upwardPx] kadar yukarı kaydırmadan sonraki düzey; sınırda kalır ve geri dönüşte ölü bölge oluşturmaz. */
    fun move(level: Float, upwardPx: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return level
        return (level + upwardPx / (heightPx * RANGE_FRACTION)).coerceIn(0f, 1f)
    }

    /** Ses seviyesi tamsayı basamaktır (0..[max]). */
    fun volumeStep(level: Float, max: Int): Int = (level * max).roundToInt().coerceIn(0, max.coerceAtLeast(0))

    fun percent(level: Float): Int = (level * 100f).roundToInt().coerceIn(0, 100)
}

/**
 * Oynatıcı penceresinin ses ve parlaklık düzeyi. Ses `STREAM_MUSIC`'tir; parlaklık yalnızca bu pencerenin
 * `screenBrightness` değeridir (sistem ayarına dokunmaz, izin gerektirmez) ve çıkışta sistem değerine döner.
 */
class DeviceLevels(
    private val window: Window,
    private val audio: AudioManager,
    private val resolver: ContentResolver,
) {
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private var brightnessChanged = false

    /** Sabit ses seviyeli cihazlarda (ör. bazı TV kutuları) ses jesti kapalıdır. */
    val volumeAdjustable: Boolean = maxVolume > 0 && !audio.isVolumeFixed

    fun level(kind: LevelKind): Float = when (kind) {
        LevelKind.Volume -> volume()
        LevelKind.Brightness -> brightness()
    }

    fun set(kind: LevelKind, level: Float) {
        when (kind) {
            LevelKind.Volume -> setVolume(level)
            LevelKind.Brightness -> setBrightness(level)
        }
    }

    private fun volume(): Float =
        if (maxVolume > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume else 0f

    private fun setVolume(level: Float) {
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, LevelRules.volumeStep(level, maxVolume), 0) }
    }

    /** Pencere değeri varsa o, yoksa sistem parlaklığı (0..255), o da okunamazsa orta. */
    private fun brightness(): Float {
        val own = window.attributes.screenBrightness
        if (own >= 0f) return own.coerceIn(0f, 1f)
        val system = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
        return system?.let { (it / 255f).coerceIn(0f, 1f) } ?: 0.5f
    }

    private fun setBrightness(level: Float) {
        brightnessChanged = true
        window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(LevelRules.MIN_BRIGHTNESS, 1f) }
    }

    /** Pencere parlaklığını bırakır: sistem değeri geçerli olur. Dokunulmadıysa hiçbir şey yapmaz. */
    fun restoreBrightness() {
        if (!brightnessChanged) return
        brightnessChanged = false
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }

    companion object {
        /**
         * Jestler yalnızca dokunmatik ekranlı telefon/tablette açılır. TV ve kumanda cihazlarında (leanback ya da
         * televizyon kipi, dokunmatik ekransız) kapalıdır; kumanda davranışı olduğu gibi kalır.
         */
        fun supportsTouch(context: Context): Boolean {
            val pm = context.packageManager
            val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            return pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                uiMode?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION
        }
    }
}
