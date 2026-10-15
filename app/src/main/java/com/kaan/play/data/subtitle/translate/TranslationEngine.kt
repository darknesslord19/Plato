package com.kaan.play.data.subtitle.translate

import com.kaan.play.data.subtitle.SubtitleCue

enum class ModelPrep { Ready, NeedsWifi, Failed }

/**
 * Oynatıcının çeviri katmanından beklediği şey: ayar, model durumu ve çeviri. Gerçeği cihaz üstü ML Kit'tir
 * ([MlKitTranslationEngine]); denetleyici testlerinde sahtesi kullanılır. Metin hiçbir zaman sunucuya gitmez.
 */
interface TranslationEngine {
    /** Ayarlar > Oynatıcı > "Türkçe yoksa İngilizceyi otomatik çevir". */
    suspend fun enabled(): Boolean

    /** İngilizce→Türkçe modeli cihazda var mı (yoksa ilk kullanımda iner)? */
    suspend fun isModelReady(): Boolean

    /** Modeli indirir (ayar "yalnızca Wi-Fi" ise ve ölçümsüz ağ yoksa [ModelPrep.NeedsWifi] döner, indirmeyi denemez). */
    suspend fun prepareModel(): ModelPrep

    /** Satırları Türkçeye çevirir; zamanlar korunur. [onProgress]: 0..100. Başarısızlıkta [TranslationException]. */
    suspend fun translate(cues: List<SubtitleCue>, onProgress: (Int) -> Unit): List<SubtitleCue>
}
