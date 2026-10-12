package com.kaan.play.ui.player

import androidx.compose.ui.input.key.Key

/** Oynatıcıda bir tuşun yaptığı iş. [PassThrough]: olayı dokunma kontrollerine (odaklı düğme/çubuk) bırak. */
enum class PlayerKeyAction { PassThrough, TogglePlay, SeekBack, SeekForward, Reveal, OpenMenu }

/**
 * Oynatıcı kumanda eşlemesi (Android'den bağımsız, birim testli).
 *
 * Kontroller **gizliyken**: Tamam/Enter = oynat/duraklat; Sol/Sağ = geri/ileri sar; Yukarı = kontrolleri göster;
 * Aşağı (ya da Menü) = altyazı/kaynak menüsü; medya tuşları her zaman çalışır; başka herhangi bir tuş kontrolleri geri getirir.
 * Kontroller **açıkken** yön tuşları ve Tamam odaktaki düğmeye bırakılır (düğmeler arasında gezilir, Tamam basar);
 * oynat düğmesi başlangıç odağındadır, yani Tamam yine oynat/duraklatır.
 */
object PlayerKeys {

    fun actionFor(key: Key, controlsVisible: Boolean): PlayerKeyAction = when (key) {
        // Ses tuşlarını sistem işler; asla yutulmaz.
        Key.VolumeUp, Key.VolumeDown, Key.VolumeMute -> PlayerKeyAction.PassThrough
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> PlayerKeyAction.TogglePlay
        Key.MediaFastForward, Key.MediaStepForward -> PlayerKeyAction.SeekForward
        Key.MediaRewind, Key.MediaStepBackward -> PlayerKeyAction.SeekBack
        else -> if (controlsVisible) {
            PlayerKeyAction.PassThrough
        } else when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKeyAction.TogglePlay
            Key.DirectionLeft -> PlayerKeyAction.SeekBack
            Key.DirectionRight -> PlayerKeyAction.SeekForward
            Key.DirectionUp -> PlayerKeyAction.Reveal
            Key.DirectionDown, Key.Menu -> PlayerKeyAction.OpenMenu
            else -> PlayerKeyAction.Reveal
        }
    }
}

/**
 * Sol/Sağ tuşu basılı tutulunca sarma hızlanır. İlk basış 10 sn; tuş tekrarları (repeatCount 1, 2, …) önce kısa bir
 * bekleme (yanlışlıkla uzun basışı yutar), sonra 10 sn, ardından 30 sn, sonunda 60 sn'lik adımlarla ilerler.
 * Her tekrar olayı sarmaz: yaklaşık her 4. tekrar bir adım atar (çok sık sarıp oynatıcıyı boğmasın).
 */
object SeekAcceleration {
    const val BASE_STEP_MS = 10_000L

    /** [repeatCount]: Android `KeyEvent.repeatCount` (ilk basışta 0). 0 → sarılacak ms; atlanacaksa 0. */
    fun stepMs(repeatCount: Int): Long = when {
        repeatCount <= 0 -> BASE_STEP_MS
        repeatCount < 3 -> 0L // uzun basışın ilk gecikmesi
        repeatCount % 4 != 3 -> 0L
        repeatCount < 20 -> BASE_STEP_MS
        repeatCount < 48 -> 30_000L
        else -> 60_000L
    }
}
