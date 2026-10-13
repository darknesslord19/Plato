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
        assertNull(PlaybackController.streamMimeType("https://h/movie"))
        assertNull(PlaybackController.streamMimeType("https://pixeldrain.dev/api/file/abc?download"))
    }

    @Test
    fun rejectedRequestWithSourceHeadersIsRetriedOnceWithoutThem() {
        // CDN tarayıcı Referer/User-Agent'ını 429/428 ile reddediyor; başlıksız istek 206 dönüyor.
        assertEquals(true, PlaybackController.retryWithoutHeaders(429, headersSent = true))
        assertEquals(true, PlaybackController.retryWithoutHeaders(403, headersSent = true))
        assertEquals(false, PlaybackController.retryWithoutHeaders(429, headersSent = false)) // zaten başlıksız
        assertEquals(false, PlaybackController.retryWithoutHeaders(503, headersSent = true))
        assertEquals(false, PlaybackController.retryWithoutHeaders(null, headersSent = true)) // http hatası değil
    }

    @Test
    fun streamMimeTypeIsInferredForPlainFilesFromPathExtension() {
        assertEquals(MimeTypes.VIDEO_MATROSKA, PlaybackController.streamMimeType("https://h/a/Movie.MKV"))
        assertEquals(MimeTypes.VIDEO_MATROSKA, PlaybackController.streamMimeType("https://h/a.mkv?token=x.mp4#t"))
        assertEquals(MimeTypes.VIDEO_MP4, PlaybackController.streamMimeType("https://h/movie.mp4"))
        assertEquals(MimeTypes.VIDEO_AVI, PlaybackController.streamMimeType("https://h/movie.avi"))
        assertEquals(MimeTypes.VIDEO_WEBM, PlaybackController.streamMimeType("https://h/movie.webm"))
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
        assertEquals(false, back.autoResumed) // varsayılan: bildirim yok
    }

    @Test
    fun autoResumedFlagSurvivesTheIntentRoundTripAndOldSessionsDefaultToFalse() {
        val json = com.kaan.play.data.net.PlayJson
        val session = PlaySession(
            target = PlayTarget(StreamRequest(1, "tt1", MediaType.Movie, null, null), "Film", null),
            stream = PlayableStream(StreamSource(1, "A"), "https://x/v.mp4", null, null, emptyMap(), emptyList()),
            startPositionMs = 40_000,
            autoResumed = true,
        )
        val text = json.encodeToString(PlaySession.serializer(), session)
        assertEquals(true, json.decodeFromString(PlaySession.serializer(), text).autoResumed)

        // alan hiç yazılmamışsa (varsayılan) false okunur
        val withoutField = text.replace(Regex(",?\"autoResumed\":true"), "")
        assertEquals(false, json.decodeFromString(PlaySession.serializer(), withoutField).autoResumed)
    }
}
