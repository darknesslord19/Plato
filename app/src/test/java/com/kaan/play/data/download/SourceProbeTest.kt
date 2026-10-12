package com.kaan.play.data.download

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Kaynak yoklaması yerel sahte sunucuyla: küçük bir mp4 (Range destekli), HLS, DASH, özel başlık zorunlu uç,
 * kısa ömürlü/404 veren uç ve medya olmayan yanıtlar. Gerçek bir siteye bağlanılmaz.
 */
class SourceProbeTest {

    private lateinit var server: MockWebServer
    private val received = mutableListOf<RecordedRequest>()
    private val routes = mutableMapOf<String, (RecordedRequest) -> MockResponse>()

    /** Gerçek bir mp4'ün ilk baytlarına benzer: `ftyp` kutusu + dolgu. */
    private val mp4 = ByteArray(10_000) { if (it < 12) "\u0000\u0000\u0000\u0018ftypmp42"[it].code.toByte() else (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(received) { received += request }
                    return routes[request.url.encodedPath]?.invoke(request) ?: MockResponse.Builder().code(404).build()
                }
            }
            it.start()
        }
        // Range destekli mp4: istenen aralığı 206 + Content-Range ile döner.
        routes["/movie.mp4"] = { req ->
            val range = req.headers["Range"]?.removePrefix("bytes=")?.split('-')
            if (range != null && range.size == 2) {
                val start = range[0].toInt()
                val end = minOf(range[1].toIntOrNull() ?: (mp4.size - 1), mp4.size - 1)
                MockResponse.Builder().code(206).addHeader("Content-Type", "video/mp4")
                    .addHeader("Content-Range", "bytes $start-$end/${mp4.size}")
                    .body(Buffer().write(mp4.copyOfRange(start, end + 1))).build()
            } else {
                MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(mp4)).build()
            }
        }
    }

    @After
    fun tearDown() = server.close()

    private val probe get() = SourceProbe(OkHttpClient(), timeoutMs = 3_000)
    private fun url(path: String) = server.url(path).toString()
    private fun text(body: String, type: String) =
        MockResponse.Builder().addHeader("Content-Type", type).body(Buffer().writeUtf8(body)).build()

    @Test
    fun rangeSupportingMp4ReportsItsTotalSize() = runTest {
        val result = probe.probe(url("/movie.mp4"), emptyMap()) as Probe.Ok
        assertEquals(SourceKind.Progressive, result.kind)
        assertEquals(mp4.size.toLong(), result.lengthBytes)
        // Yoklama yalnızca küçük bir aralık ister, dosyanın tamamını indirmez.
        assertEquals("bytes=0-4095", received.single().headers["Range"])
    }

    @Test
    fun serverIgnoringRangeStillWorksAndReportsContentLength() = runTest {
        routes["/norange.mp4"] = { MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(mp4)).build() }
        val result = probe.probe(url("/norange.mp4"), emptyMap()) as Probe.Ok
        assertEquals(SourceKind.Progressive, result.kind)
        assertEquals(mp4.size.toLong(), result.lengthBytes)
    }

    @Test
    fun octetStreamWithoutSizeIsAcceptedWithUnknownLength() = runTest {
        routes["/blob"] = {
            MockResponse.Builder().addHeader("Content-Type", "application/octet-stream").body(Buffer().write(mp4)).build()
        }
        val result = probe.probe(url("/blob"), emptyMap())
        assertTrue(result is Probe.Ok)
    }

    @Test
    fun hlsPlaylistIsRecognizedByItsContent() = runTest {
        routes["/hls/master.m3u8"] = {
            text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nv1/index.m3u8\n", "application/vnd.apple.mpegurl")
        }
        val result = probe.probe(url("/hls/master.m3u8"), emptyMap()) as Probe.Ok
        assertEquals(SourceKind.Hls, result.kind)
        assertNull(result.lengthBytes)
    }

    @Test
    fun hlsServedAsPlainTextIsStillHls() = runTest {
        routes["/p"] = { text("﻿#EXTM3U\n#EXTINF:4,\nseg0.ts\n#EXT-X-ENDLIST\n", "text/plain") }
        assertEquals(SourceKind.Hls, (probe.probe(url("/p"), emptyMap()) as Probe.Ok).kind)
    }

    @Test
    fun dashManifestIsRecognized() = runTest {
        routes["/m.mpd"] = { text("<?xml version=\"1.0\"?>\n<MPD type=\"static\" mediaPresentationDuration=\"PT10S\"></MPD>", "application/dash+xml") }
        assertEquals(SourceKind.Dash, (probe.probe(url("/m.mpd"), emptyMap()) as Probe.Ok).kind)
    }

    @Test
    fun protectedStreamsAreRejectedAsDrm() = runTest {
        routes["/fp.m3u8"] = {
            text("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXTINF:4,\ns.ts\n", "text/plain")
        }
        routes["/wv.mpd"] = {
            text("<MPD><ContentProtection schemeIdUri=\"urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED\"/></MPD>", "application/dash+xml")
        }
        assertEquals(Probe.Rejected(Probe.Reason.Drm), probe.probe(url("/fp.m3u8"), emptyMap()))
        assertEquals(Probe.Rejected(Probe.Reason.Drm), probe.probe(url("/wv.mpd"), emptyMap()))
    }

    @Test
    fun plainAes128HlsIsNotTreatedAsDrm() = runTest {
        routes["/aes.m3u8"] = { text("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:4,\ns.ts\n#EXT-X-ENDLIST\n", "text/plain") }
        assertTrue(probe.probe(url("/aes.m3u8"), emptyMap()) is Probe.Ok)
    }

    @Test
    fun requiredHeadersAreSentAndWithoutThemTheSourceIsRejected() = runTest {
        routes["/secret.mp4"] = { req ->
            if (req.headers["X-Token"] == "abc") routes.getValue("/movie.mp4")(req) else MockResponse.Builder().code(403).build()
        }
        assertEquals(Probe.Rejected(Probe.Reason.Http), probe.probe(url("/secret.mp4"), emptyMap()))
        assertTrue(probe.probe(url("/secret.mp4"), mapOf("X-Token" to "abc")) is Probe.Ok)
    }

    @Test
    fun expiredOrMissingSourcesAreRejected() = runTest {
        routes["/expired.mp4"] = { MockResponse.Builder().code(410).build() }
        assertEquals(Probe.Rejected(Probe.Reason.Http), probe.probe(url("/expired.mp4"), emptyMap()))
        assertEquals(Probe.Rejected(Probe.Reason.Http), probe.probe(url("/yok.mp4"), emptyMap())) // 404
        routes["/boom.mp4"] = { MockResponse.Builder().code(503).build() }
        assertEquals(Probe.Rejected(Probe.Reason.Http), probe.probe(url("/boom.mp4"), emptyMap()))
    }

    @Test
    fun notMediaResponsesAreRejected() = runTest {
        routes["/error.mp4"] = { text("<!DOCTYPE html><html><body>Not found</body></html>", "text/html; charset=utf-8") }
        routes["/api"] = { text("{\"error\":\"expired\"}", "application/json") }
        routes["/note.txt"] = { text("merhaba", "text/plain") }
        routes["/t.torrent"] = { MockResponse.Builder().addHeader("Content-Type", "application/x-bittorrent").body(Buffer().write(mp4)).build() }
        for (path in listOf("/error.mp4", "/api", "/note.txt", "/t.torrent")) {
            assertEquals(path, Probe.Rejected(Probe.Reason.NotMedia), probe.probe(url(path), emptyMap()))
        }
    }

    @Test
    fun unreachableServerIsANetworkProblem() = runTest {
        val gone = MockWebServer().also { it.start() }
        val dead = gone.url("/movie.mp4").toString()
        gone.close() // port artık dinlemiyor
        assertEquals(Probe.Rejected(Probe.Reason.Network), probe.probe(dead, emptyMap()))
    }

    @Test
    fun nonHttpSchemesAndGarbageAreRejectedWithoutARequest() = runTest {
        for (bad in listOf("ftp://x/y.mp4", "file:///sdcard/a.mp4", "magnet:?xt=urn:btih:abc", "not a url", "")) {
            assertEquals(bad, Probe.Rejected(Probe.Reason.NotMedia), probe.probe(bad, emptyMap()))
        }
        assertTrue(received.isEmpty())
    }

    @Test
    fun callersOwnRangeHeaderIsKept() = runTest {
        probe.probe(url("/movie.mp4"), mapOf("Range" to "bytes=0-99"))
        assertEquals("bytes=0-99", received.single().headers["Range"])
    }

    @Test
    fun lengthParsing() {
        assertEquals(12345L, SourceProbe.lengthOf(206, "bytes 0-4095/12345", "4096"))
        assertNull(SourceProbe.lengthOf(206, "bytes 0-4095/*", "4096"))
        assertNull(SourceProbe.lengthOf(206, null, null))
        assertEquals(777L, SourceProbe.lengthOf(200, null, "777"))
        assertNull(SourceProbe.lengthOf(200, null, "0"))
        assertNull(SourceProbe.lengthOf(200, null, "abc"))
    }
}
