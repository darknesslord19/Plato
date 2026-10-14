package com.kaan.play.ui.player

import java.util.Locale

/** Parça etiketleri (Android'den bağımsız, birim testli): ses "Türkçe • Stereo • AAC", video "1080p • 4,5 Mbps". */
object TrackLabels {

    /** Ses MIME türü → kısa codec adı; bilinmiyorsa null. */
    fun codecName(mime: String?): String? = when (mime?.lowercase(Locale.ROOT)) {
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/ac3" -> "AC3"
        "audio/eac3", "audio/eac3-joc" -> "E-AC3"
        "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd" -> "DTS"
        "audio/true-hd" -> "TrueHD"
        "audio/opus" -> "Opus"
        "audio/mpeg", "audio/mpeg-l2" -> "MP3"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        else -> null
    }

    fun channelName(channels: Int): String? = when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        in 3..Int.MAX_VALUE -> "$channels.0"
        else -> null
    }

    /** Boş olmayan parçaları " • " ile birleştirir; hiçbiri yoksa [fallback]. */
    fun audio(language: String?, label: String?, channels: Int, mime: String?, fallback: String): String {
        val parts = listOfNotNull(
            language?.takeIf { it.isNotBlank() },
            label?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) },
            channelName(channels),
            codecName(mime),
        ).distinct()
        return if (parts.isEmpty()) fallback else parts.joinToString(" • ")
    }

    fun video(height: Int, bitrate: Int, locale: Locale): String {
        val quality = if (height > 0) "${height}p" else null
        val rate = if (bitrate > 0) String.format(locale, "%.1f Mbps", bitrate / 1_000_000.0) else null
        return listOfNotNull(quality, rate).joinToString(" • ")
    }
}
