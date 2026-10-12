package com.kaan.play.data.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaan.play.PlayApp
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gerçek Media3 indirme hattı (Faz 4/B), cihazda ve yerel sahte sunucuyla: düz mp4 (Range destekli), başlık zorunlu uç,
 * 404/HTML veren uçlar, başlıklı HLS ve silme. Uygulamanın kendi [DownloadCenter]'ı ve veritabanı kullanılır: **test başında ve
 * sonunda tüm indirilenleri siler** (yalnızca test cihazında çalıştırın).
 */
@RunWith(AndroidJUnit4::class)
class DownloadPipelineDeviceTest {

    private val app = ApplicationProvider.getApplicationContext<PlayApp>()
    private val center get() = app.container.downloads
    private val dao get() = app.container.database.downloadDao()

    private lateinit var server: MockWebServer
    private val seen = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val mp4 = ByteArray(300_000) { (it % 251).toByte() }
    private val segment = ByteArray(80_000) { (it % 239).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request
                    val path = request.url.encodedPath
                    val authorized = request.headers["X-Token"] == "abc"
                    return when {
                        path == "/movie.mp4" -> ranged(request)
                        path == "/secret.mp4" -> if (authorized) ranged(request) else MockResponse.Builder().code(403).build()
                        path == "/page.mp4" -> text("<html>hata</html>", "text/html")
                        path.startsWith("/hls/") && !authorized -> MockResponse.Builder().code(403).build()
                        path == "/hls/master.m3u8" -> text(
                            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720,CODECS=\"avc1.64001f,mp4a.40.2\"\nv/index.m3u8\n",
                            "application/vnd.apple.mpegurl",
                        )
                        path == "/hls/v/index.m3u8" -> text(
                            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:0\n" +
                                (0..3).joinToString("") { "#EXTINF:4.0,\nseg$it.ts\n" } + "#EXT-X-ENDLIST\n",
                            "application/vnd.apple.mpegurl",
                        )
                        path.startsWith("/hls/v/seg") -> MockResponse.Builder().body(Buffer().write(segment)).build()
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            it.start()
        }
        clean()
    }

    @After
    fun tearDown() {
        clean()
        server.close()
    }

    private fun clean() = runBlocking {
        onMain { center.removeAll() }
        waitFor("indirilenler silinsin") { dao.all().isEmpty() }
    }

    /** Media3 yöneticisi uygulama (ana) iş parçacığından kullanılır. */
    private fun onMain(block: () -> Unit) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun ranged(req: RecordedRequest): MockResponse {
        val range = req.headers["Range"]?.removePrefix("bytes=")?.split('-')
        if (range == null || range.size != 2) {
            return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(mp4)).build()
        }
        val start = range[0].toInt()
        val end = minOf(range[1].toIntOrNull() ?: (mp4.size - 1), mp4.size - 1)
        return MockResponse.Builder().code(206).addHeader("Content-Type", "video/mp4")
            .addHeader("Content-Range", "bytes $start-$end/${mp4.size}")
            .body(Buffer().write(mp4.copyOfRange(start, end + 1))).build()
    }

    private fun text(body: String, type: String) = MockResponse.Builder().addHeader("Content-Type", type).body(Buffer().writeUtf8(body)).build()

    private suspend fun waitFor(what: String, timeoutMs: Long = 30_000, cond: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(150) }
    }

    private fun target(id: Int, season: Int? = null, episode: Int? = null) = PlayTarget(
        StreamRequest(tmdbId = id, imdbId = "tt$id", type = if (season == null) MediaType.Movie else MediaType.Tv, season = season, episode = episode),
        title = "Deneme $id",
        posterPath = null,
    )

    private fun stream(path: String, headers: Map<String, String> = emptyMap()) = PlayableStream(
        StreamSource(1, "Sahte"), server.url(path).toString(), 1080, null, headers, emptyList(),
    )

    private suspend fun row(id: Long): DownloadEntity? = dao.find(id)

    @Test
    fun rangedMp4DownloadsCompletesAndIsPlayableFromCache() = runBlocking {
        val result = center.start(target(900001), stream("/movie.mp4")) as StartResult.Started
        waitFor("tamamlansın") { row(result.id)?.status == DownloadStatus.Completed }

        val done = row(result.id)!!
        assertEquals(mp4.size.toLong(), done.downloadedBytes)
        assertEquals(mp4.size.toLong(), done.totalBytes)
        assertNotNull(center.completedFor(target(900001).request))
        // Oynatıcı için öğe indirme dizininden kurulur.
        assertNotNull(center.mediaItem(result.id))
        // Aynı film ikinci kez indirilemez.
        assertTrue(center.start(target(900001), stream("/movie.mp4")) is StartResult.AlreadyDownloaded)
    }

    @Test
    fun episodeIsStoredPerSeasonAndEpisode() = runBlocking {
        val a = center.start(target(900002, 1, 1), stream("/movie.mp4")) as StartResult.Started
        waitFor("b1") { row(a.id)?.status == DownloadStatus.Completed }
        val b = center.start(target(900002, 1, 2), stream("/movie.mp4")) as StartResult.Started // aynı dizi, başka bölüm
        waitFor("b2") { row(b.id)?.status == DownloadStatus.Completed }
        assertEquals(setOf(1 to 1, 1 to 2), dao.all().map { it.season to it.episode }.toSet())
    }

    @Test
    fun headerRequiredSourceNeedsItsHeadersOnBothProbeAndDownload() = runBlocking {
        assertEquals(StartResult.NotDownloadable(Probe.Reason.Http), center.start(target(900003), stream("/secret.mp4")))
        assertTrue(dao.all().isEmpty())

        val started = center.start(target(900003), stream("/secret.mp4", mapOf("X-Token" to "abc"))) as StartResult.Started
        waitFor("tamamlansın") { row(started.id)?.status == DownloadStatus.Completed }

        val secretRequests = seen.filter { it.url.encodedPath == "/secret.mp4" && it.headers["X-Token"] == "abc" }
        assertTrue("yoklama + indirme en az iki istek", secretRequests.size >= 2)
    }

    @Test
    fun brokenSourcesAreRejectedBeforeAnythingIsStored() = runBlocking {
        assertEquals(StartResult.NotDownloadable(Probe.Reason.Http), center.start(target(900004), stream("/yok.mp4")))
        assertEquals(StartResult.NotDownloadable(Probe.Reason.NotMedia), center.start(target(900004), stream("/page.mp4")))
        assertTrue(dao.all().isEmpty())
        assertNull(center.completedFor(target(900004).request))
    }

    @Test
    fun hlsDownloadsOnlyTheSelectedVariantWithTheHeadersOnEveryRequest() = runBlocking {
        val started = center.start(target(900005), stream("/hls/master.m3u8", mapOf("X-Token" to "abc"))) as StartResult.Started
        waitFor("tamamlansın") { row(started.id)?.status == DownloadStatus.Completed }

        val hls = seen.filter { it.url.encodedPath.startsWith("/hls/") }
        assertTrue(hls.any { it.url.encodedPath == "/hls/v/seg3.ts" })
        assertTrue("her HLS isteği özel başlığı taşımalı", hls.all { it.headers["X-Token"] == "abc" })
        assertTrue(row(started.id)!!.downloadedBytes >= 4 * segment.size)
        assertNotNull(center.mediaItem(started.id))
    }

    @Test
    fun removeDeletesTheRowAndTheData() = runBlocking {
        val started = center.start(target(900006), stream("/movie.mp4")) as StartResult.Started
        waitFor("tamamlansın") { row(started.id)?.status == DownloadStatus.Completed }

        onMain { center.remove(started.id) }
        waitFor("satır silinsin") { row(started.id) == null }
        waitFor("veri silinsin") { center.mediaItem(started.id) == null }
        assertNull(center.completedFor(target(900006).request))
    }
}
