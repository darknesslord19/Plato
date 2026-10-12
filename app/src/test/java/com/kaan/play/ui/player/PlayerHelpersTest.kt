package com.kaan.play.ui.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerHelpersTest {

    @Test
    fun formatTime() {
        assertEquals("0:00", formatTime(0))
        assertEquals("1:15", formatTime(75_000))
        assertEquals("59:59", formatTime(3_599_999))
        assertEquals("1:02:05", formatTime(3_725_000))
        assertEquals("0:00", formatTime(-5))
    }

    @Test
    fun streamMimeTypeIsInferredForHlsAndDashEvenWithQueryOrNoExtension() {
        assertEquals(MimeTypes.APPLICATION_M3U8, PlaybackController.streamMimeType("https://h/x/master.m3u8"))
        assertEquals(MimeTypes.APPLICATION_M3U8, PlaybackController.streamMimeType("https://h/play?file=a.M3U8&t=1"))
        assertEquals(MimeTypes.APPLICATION_MPD, PlaybackController.streamMimeType("https://h/a.mpd"))
        assertNull(PlaybackController.streamMimeType("https://h/movie.mp4"))
    }

    @Test
    fun subtitleMimeTypeFollowsExtensionAndDefaultsToSubRip() {
        assertEquals(MimeTypes.TEXT_VTT, PlaybackController.subtitleMimeType("https://s/a.vtt?x=1"))
        assertEquals(MimeTypes.TEXT_SSA, PlaybackController.subtitleMimeType("https://s/a.ASS"))
        assertEquals(MimeTypes.APPLICATION_TTML, PlaybackController.subtitleMimeType("https://s/a.ttml"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, PlaybackController.subtitleMimeType("https://s/a.srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, PlaybackController.subtitleMimeType("https://s/get/12345"))
    }

    @Test
    fun playbackErrorsMapToThreeUserFacingKinds() {
        fun kind(code: Int) = PlaybackController.errorOf(PlaybackException("x", null, code))
        assertEquals(PlaybackError.Network, kind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(PlaybackError.Network, kind(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(PlaybackError.Unavailable, kind(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(PlaybackError.Unavailable, kind(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlaybackError.Format, kind(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(PlaybackError.Format, kind(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(PlaybackError.Format, kind(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    @Test
    fun sessionSurvivesTheIntentRoundTripWithHeadersAndSubtitles() {
        val session = PlaySession(
            target = PlayTarget(StreamRequest(1399, "tt0944947", MediaType.Tv, 2, 5), "Dizi", "/p.jpg"),
            stream = PlayableStream(
                source = StreamSource(3, "Addon"),
                url = "https://cdn/x.m3u8?token=a b",
                resolution = 1080,
                sizeBytes = 2_000_000_000,
                headers = mapOf("Referer" to "https://r"),
                subtitles = listOf(StreamSubtitle("https://s/tr.vtt", "tur")),
            ),
            startPositionMs = 123_000,
        )
        val json = com.kaan.play.data.net.PlayJson.encodeToString(PlaySession.serializer(), session)
        val back = com.kaan.play.data.net.PlayJson.decodeFromString(PlaySession.serializer(), json)

        assertEquals(session, back)
        assertNotNull(back.stream.headers["Referer"])
    }
}
