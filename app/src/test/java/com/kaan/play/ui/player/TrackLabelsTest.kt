package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TrackLabelsTest {

    @Test
    fun audioLabelJoinsLanguageChannelsAndCodec() {
        assertEquals("Türkçe • Stereo • AAC", TrackLabels.audio("Türkçe", null, 2, "audio/mp4a-latm", "Parça 1"))
        assertEquals("İngilizce • 5.1 • AC3", TrackLabels.audio("İngilizce", null, 6, "audio/ac3", "Parça 2"))
        assertEquals("Türkçe • E-AC3", TrackLabels.audio("Türkçe", null, 0, "audio/eac3", "Parça 1"))
    }

    @Test
    fun trackOwnLabelIsKeptWhenItAddsInformation() {
        assertEquals("Türkçe • Dublaj • Stereo", TrackLabels.audio("Türkçe", "Dublaj", 2, null, "x"))
        // Etiket dilin kendisiyse yinelenmez.
        assertEquals("Türkçe • Stereo", TrackLabels.audio("Türkçe", "türkçe", 2, null, "x"))
    }

    @Test
    fun noInformationFallsBackToTheTrackNumber() {
        assertEquals("Parça 3", TrackLabels.audio(null, null, 0, null, "Parça 3"))
        assertEquals("Parça 3", TrackLabels.audio("", " ", -1, "audio/unknown", "Parça 3"))
    }

    @Test
    fun codecAndChannelNames() {
        assertEquals("DTS", TrackLabels.codecName("audio/vnd.dts.hd"))
        assertEquals("Opus", TrackLabels.codecName("AUDIO/OPUS"))
        assertNull(TrackLabels.codecName("audio/x-weird"))
        assertNull(TrackLabels.codecName(null))
        assertEquals("Mono", TrackLabels.channelName(1))
        assertEquals("7.1", TrackLabels.channelName(8))
        assertEquals("4.0", TrackLabels.channelName(4))
        assertNull(TrackLabels.channelName(0))
    }

    @Test
    fun videoLabelUsesQualityAndBitrateWithLocaleSeparator() {
        assertEquals("1080p • 4,5 Mbps", TrackLabels.video(1080, 4_500_000, Locale("tr", "TR")))
        assertEquals("720p • 2.1 Mbps", TrackLabels.video(720, 2_100_000, Locale.US))
        assertEquals("720p", TrackLabels.video(720, 0, Locale.US))
        assertEquals("", TrackLabels.video(0, 0, Locale.US))
    }
}
