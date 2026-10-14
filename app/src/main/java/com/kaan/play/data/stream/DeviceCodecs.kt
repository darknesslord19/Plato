package com.kaan.play.data.stream

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** Cihazın video kod çözücülerinden (MediaCodecList) çözebildiği en yüksek video yüksekliğini bulur. */
object DeviceCodecs {

    private val VIDEO_TYPES = setOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01")

    /** Bulunamazsa 1080. 4K'ya kadar (2160) sınırlanır. Donanım çözücü varsa yalnızca onlara bakılır. */
    fun maxDecodableHeight(): Int = try {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) decoders.filter { it.isHardwareAccelerated } else emptyList()
        (hardware.ifEmpty { decoders }).maxOfOrNull(::maxHeightOf)?.takeIf { it > 0 }?.coerceAtMost(2160) ?: DEFAULT_HEIGHT
    } catch (_: Exception) {
        DEFAULT_HEIGHT
    }

    private fun maxHeightOf(info: MediaCodecInfo): Int = info.supportedTypes
        .filter { it.lowercase() in VIDEO_TYPES }
        .maxOfOrNull { type ->
            try {
                info.getCapabilitiesForType(type).videoCapabilities?.supportedHeights?.upper ?: 0
            } catch (_: Exception) {
                0
            }
        } ?: 0

    private const val DEFAULT_HEIGHT = 1080
}
