package com.kaan.play.data.subtitle

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Altyazıyı yerel sahte sunucudan gerçekten indirir: kodlama tespiti, UTF-8'e çevirip önbelleğe yazma, SRT/VTT/ASS,
 * zaman aşımı ve boyut sınırı. Ağdan gelen baytlar gerçek altyazı dosyalarındaki gibi ham baytlardır.
 */
class SubtitleLoaderTest {

    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val hits = mutableListOf<String>()

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("subtitle-cache").toFile()
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(hits) { hits += request.url.encodedPath }
                    return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() {
        server.close()
        cacheDir.deleteRecursively()
    }

    private fun bytesResponse(bytes: ByteArray, type: String = "text/plain") =
        MockResponse.Builder().addHeader("Content-Type", type).body(Buffer().write(bytes)).build()

    private fun loader(maxBytes: Long = SubtitleLoader.MAX_BYTES, timeoutMs: Long = 5_000, clock: () -> Long = System::currentTimeMillis) =
        SubtitleLoader(OkHttpClient(), cacheDir, timeoutMs.milliseconds, maxBytes, clock)

    private fun track(path: String, lang: String?) =
        SubtitleTrack(server.url(path).toString(), SubtitleLanguages.normalize(lang), "Test")

    private val turkishSrt = "1\r\n00:00:01,000 --> 00:00:03,000\r\nİstanbul'da şöyle bir gün: ığdır, çağrı.\r\n\r\n" +
        "2\r\n00:00:04,000 --> 00:00:06,000\r\nÖĞRENCİ ŞEKER, Işık yok, ışık var.\r\n\r\n"

    private fun cached(url: String): String = File(cacheDir, SubtitleLoader.cacheName(url)).readText(Charsets.UTF_8)

    @Test
    fun windows1254TurkishSrtIsDecodedAndCachedAsUtf8() = runTest {
        routes["/tr.srt"] = { bytesResponse(turkishSrt.toByteArray(Charset.forName("windows-1254"))) }
        val t = track("/tr.srt", "tur")
        val loaded = loader().load(t) as SubtitleLoad.Loaded

        assertEquals("windows-1254", loaded.charset)
        assertFalse(loaded.fromCache)
        assertEquals("İstanbul'da şöyle bir gün: ığdır, çağrı.", loaded.cues[0].text)
        assertEquals("ÖĞRENCİ ŞEKER, Işık yok, ışık var.", loaded.cues[1].text)
        // Önbellekteki dosya gerçekten UTF-8 ve harfler bozulmamış.
        assertEquals(turkishSrt, cached(t.url))
    }

    @Test
    fun secondLoadComesFromCacheWithoutTouchingTheNetwork() = runTest {
        routes["/tr.srt"] = { bytesResponse(turkishSrt.toByteArray(Charset.forName("windows-1254"))) }
        val t = track("/tr.srt", "tur")
        val l = loader()
        l.load(t)
        server.close()
        val again = l.load(t) as SubtitleLoad.Loaded
        assertTrue(again.fromCache)
        assertEquals("İstanbul'da şöyle bir gün: ığdır, çağrı.", again.cues[0].text)
        assertEquals(1, hits.size)
    }

    @Test
    fun utf8SrtWithBomIsLoaded() = runTest {
        val body = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + turkishSrt.toByteArray(Charsets.UTF_8)
        routes["/utf8.srt"] = { bytesResponse(body) }
        val loaded = loader().load(track("/utf8.srt", "tur")) as SubtitleLoad.Loaded
        assertEquals("UTF-8", loaded.charset)
        assertEquals(2, loaded.cues.size)
        assertEquals("İstanbul'da şöyle bir gün: ığdır, çağrı.", loaded.cues[0].text)
    }

    @Test
    fun webVttAndAssAreLoadedFromExtensionlessUrls() = runTest {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nŞu çok güzel\n"
        val ass = "[Script Info]\nTitle: x\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Ğ, ş, ı\n"
        routes["/get/1"] = { bytesResponse(vtt.toByteArray(Charset.forName("windows-1254"))) }
        routes["/get/2"] = { bytesResponse(ass.toByteArray(Charset.forName("windows-1254"))) }
        val l = loader()
        assertEquals("Şu çok güzel", (l.load(track("/get/1", "tur")) as SubtitleLoad.Loaded).cues.single().text)
        assertEquals("Ğ, ş, ı", (l.load(track("/get/2", "tur")) as SubtitleLoad.Loaded).cues.single().text)
    }

    @Test
    fun httpErrorAndGarbageAreFailuresAndNothingIsCached() = runTest {
        routes["/html"] = { bytesResponse("<html><body>Not a subtitle</body></html>".toByteArray(), "text/html") }
        routes["/boom"] = { MockResponse.Builder().code(500).build() }
        val l = loader()
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Invalid), l.load(track("/html", "tur")))
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Http), l.load(track("/boom", "tur")))
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Http), l.load(track("/missing", "tur")))
        assertTrue(cacheDir.listFiles().isNullOrEmpty()) // bozuk dosya önbelleğe girmez
    }

    @Test
    fun oversizedSubtitleIsRejectedByDeclaredAndByActualSize() = runTest {
        val big = ("1\n00:00:01,000 --> 00:00:02,000\n" + "x".repeat(5_000) + "\n\n").toByteArray()
        routes["/big.srt"] = { bytesResponse(big) }
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.TooLarge), loader(maxBytes = 1_000).load(track("/big.srt", "eng")))
        // Content-Length vermeyen (parçalı) cevapta da gerçek boyut sınırlanır.
        routes["/chunked.srt"] = {
            MockResponse.Builder().chunkedBody(Buffer().write(big), 512).build()
        }
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.TooLarge), loader(maxBytes = 1_000).load(track("/chunked.srt", "eng")))
    }

    @Test
    fun slowServerTimesOut() = runTest {
        routes["/slow.srt"] = {
            MockResponse.Builder().body(Buffer().write(turkishSrt.toByteArray(Charsets.UTF_8))).headersDelay(2, TimeUnit.SECONDS).build()
        }
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Timeout), loader(timeoutMs = 300).load(track("/slow.srt", "tur")))
    }

    @Test
    fun unreachableServerIsANetworkFailureAndNonHttpUrlsAreRejected() = runTest {
        val t = track("/x.srt", "tur")
        server.close()
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Network), loader(timeoutMs = 2_000).load(t))
        assertEquals(
            SubtitleLoad.Failed(SubtitleLoad.Reason.Network),
            loader().load(SubtitleTrack("file:///etc/passwd", "tr", "Test")),
        )
    }

    @Test
    fun cacheIsPrunedByCount() = runTest {
        val total = SubtitleLoader.MAX_FILES + 5
        var now = 1_000_000L
        val l = loader(clock = { now++ })
        for (i in 1..total) {
            routes["/s$i.srt"] = { bytesResponse("1\n00:00:01,000 --> 00:00:02,000\nx$i\n".toByteArray()) }
            l.load(track("/s$i.srt", "eng"))
        }
        assertEquals(SubtitleLoader.MAX_FILES, cacheDir.listFiles { f -> f.name.endsWith(".txt") }!!.size)
    }
}
