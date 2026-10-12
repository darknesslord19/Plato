package com.kaan.play.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamParsingTest {

    private fun stream(res: Int?, size: Long?, id: Long = 1, url: String = "https://x/$res/$size") =
        PlayableStream(StreamSource(id, "A$id"), url, res, size, emptyMap(), emptyList())

    @Test
    fun resolutionFromFreeText() {
        assertEquals(2160, StreamLabels.resolution("Film 2160p HDR"))
        assertEquals(2160, StreamLabels.resolution("film.4K.remux"))
        assertEquals(1080, StreamLabels.resolution("Movie.2020.1080p.WEB-DL"))
        assertEquals(720, StreamLabels.resolution("720P"))
        assertEquals(480, StreamLabels.resolution("[480p]"))
        assertNull(StreamLabels.resolution("Film 2020 BluRay"))
        assertNull(StreamLabels.resolution("11080p")) // sayının parçası değil
    }

    @Test
    fun sizeFromFreeText() {
        assertEquals(2_450_000_000L, StreamLabels.sizeBytes("💾 2.45 GB 👤 12"))
        assertEquals(1_500_000_000L, StreamLabels.sizeBytes("1,5GB"))
        assertEquals(700_000_000L, StreamLabels.sizeBytes("700 MB"))
        assertEquals(1024L * 1024 * 1024, StreamLabels.sizeBytes("1 GiB"))
        assertNull(StreamLabels.sizeBytes("Film 1080p"))
        assertNull(StreamLabels.sizeBytes("0 GB"))
    }

    @Test
    fun proxyHeadersWithLineBreaksAreDropped() {
        val body = """{"streams":[{"url":"https://x/y.mp4","behaviorHints":{"proxyHeaders":{"request":
            {"Referer":"https://ok","Bad":"a\r\nInjected: 1","":"empty"}}}}]}"""
        val headers = StreamParser.parse(StreamSource(1, "A"), body).single().headers
        assertEquals(mapOf("Referer" to "https://ok"), headers)
    }

    @Test
    fun rankedPutsHighQualityFirstAndUnknownLast() {
        val list = listOf(stream(720, 1), stream(null, 9), stream(1080, 5), stream(1080, 7), stream(2160, 3))
        assertEquals(listOf(2160, 1080, 1080, 720, null), list.ranked().map { it.resolution })
        assertEquals(listOf<Long?>(3, 7, 5, 1, 9), list.ranked().map { it.sizeBytes })
    }

    @Test
    fun recommendedPrefersBestUpTo1080p() {
        val ranked = listOf(stream(2160, 3), stream(1080, 7), stream(720, 1)).ranked()
        assertEquals(1080, ranked.recommended()?.resolution)

        assertEquals(2160, listOf(stream(2160, 3), stream(null, 1)).ranked().recommended()?.resolution)
        assertEquals(480, listOf(stream(480, 1), stream(null, 1)).ranked().recommended()?.resolution)
        assertNull(emptyList<PlayableStream>().recommended())
    }

    @Test
    fun requestContentId() {
        val movie = StreamRequest(1, "tt1", com.kaan.play.data.model.MediaType.Movie)
        val episode = StreamRequest(2, "tt2", com.kaan.play.data.model.MediaType.Tv, 3, 4)
        assertEquals("tt1", movie.contentId)
        assertEquals("tt2:3:4", episode.contentId)
        assertNull(episode.copy(episode = null).contentId)
        assertNull(movie.copy(imdbId = null).contentId)
    }
}
