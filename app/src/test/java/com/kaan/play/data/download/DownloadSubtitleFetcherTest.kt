package com.kaan.play.data.download

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import com.kaan.play.data.subtitle.FakeSubtitleDao
import com.kaan.play.data.subtitle.SubtitleLoader
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTrack
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Duration.Companion.seconds

/** İndirme sırasında hangi altyazıların saklandığı: uygulama dili + İngilizce + kullanıcının seçtiği; UTF-8 olarak. */
class DownloadSubtitleFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var root: File
    private lateinit var cacheDir: File
    private lateinit var files: DownloadFiles
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val hits = mutableListOf<String>()
    private val dao = FakeSubtitleDao()
    private val request = StreamRequest(tmdbId = 42, imdbId = "tt42", type = MediaType.Movie)

    private val turkishSrt = "1\r\n00:00:01,000 --> 00:00:03,000\r\nİstanbul'da şöyle bir gün: ığdır, çağrı.\r\n\r\n"
    private val englishSrt = "1\n00:00:01,000 --> 00:00:03,000\nHello there.\n\n"
    private val germanSrt = "1\n00:00:01,000 --> 00:00:03,000\nGuten Tag, schöne Grüße.\n\n"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("dl-root").toFile()
        cacheDir = Files.createTempDirectory("dl-subcache").toFile()
        files = DownloadFiles(root)
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(hits) { hits += request.url.encodedPath }
                    return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
                }
            }
            it.start()
        }
        routes["/tr.srt"] = { bytes(turkishSrt.toByteArray(Charset.forName("windows-1254"))) }
        routes["/en.srt"] = { bytes(englishSrt.toByteArray()) }
        routes["/de.srt"] = { bytes(germanSrt.toByteArray()) }
    }

    @After
    fun tearDown() {
        server.close()
        root.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    private fun bytes(b: ByteArray) = MockResponse.Builder().addHeader("Content-Type", "text/plain").body(Buffer().write(b)).build()

    private fun url(path: String) = server.url(path).toString()

    private fun stream(vararg subs: Pair<String, String?>) = PlayableStream(
        StreamSource(1, "Eklenti"), "https://video/x.mp4", 1080, null, emptyMap(),
        subs.map { (path, lang) -> StreamSubtitle(url(path), lang) },
    )

    private fun fetcher(app: String, provider: SubtitleProvider = SubtitleProvider { emptyFlow() }) = DownloadSubtitleFetcher(
        loader = SubtitleLoader(OkHttpClient(), cacheDir, 5.seconds),
        provider = provider,
        memory = SubtitleMemory(dao),
        appLanguage = { app },
        files = files,
    )

    private fun addonProvider(vararg tracks: SubtitleTrack, onCall: () -> Unit = {}) = SubtitleProvider {
        onCall()
        flowOf(SubtitleResult.Found(StreamSource(2, "Altyazı Addon"), tracks.toList()))
    }

    private fun track(path: String, lang: String) = SubtitleTrack(url(path), lang, "Altyazı Addon")

    @Test
    fun languagesFollowTheDefaultRules() {
        assertEquals(listOf("tr", "en"), DownloadSubtitleFetcher.languages(null, "tr"))
        assertEquals(listOf("en"), DownloadSubtitleFetcher.languages(null, "en"))
        // Kullanıcı o içerikte bir dil seçtiyse o da eklenir (önce o).
        assertEquals(listOf("de", "tr", "en"), DownloadSubtitleFetcher.languages("de", "tr"))
        assertEquals(listOf("tr", "en"), DownloadSubtitleFetcher.languages("tr", "tr"))
        // Altyazıyı kapatmış olmak ek dil getirmez; uygulama dili + İngilizce yine alınır.
        assertEquals(listOf("tr", "en"), DownloadSubtitleFetcher.languages(SubtitleSelection.REMEMBERED_OFF, "tr"))
    }

    @Test
    fun streamSubtitlesCoveringTheLanguagesAreSavedAsUtf8WithoutAskingAddons() = runTest {
        var asked = false
        val f = fetcher("tr", addonProvider(onCall = { asked = true }))

        val saved = f.fetch(7, request, stream("/tr.srt" to "tur", "/en.srt" to "eng"))

        assertFalse("Akış zaten tr ve en veriyor: addon'a sorulmamalı", asked)
        assertEquals(listOf("tr", "en"), saved.map { it.language })
        assertEquals(listOf(url("/tr.srt"), url("/en.srt")), saved.map { it.url })
        assertEquals("Eklenti", saved[0].origin)
        // Dosya UTF-8'e çevrilmiş metindir; Türkçe harfler bozulmamıştır.
        assertEquals(turkishSrt, File(saved[0].file).readText(Charsets.UTF_8))
        assertEquals(englishSrt, File(saved[1].file).readText(Charsets.UTF_8))
        assertEquals(files.subtitleFile(7, 0).path, saved[0].file)
        assertEquals(files.subtitleFile(7, 1).path, saved[1].file)
    }

    @Test
    fun missingLanguagesComeFromSubtitleAddons() = runTest {
        var asked = 0
        val f = fetcher("tr", addonProvider(track("/tr.srt", "tr"), track("/de.srt", "de"), onCall = { asked++ }))

        val saved = f.fetch(1, request, stream("/en.srt" to "en"))

        assertEquals(1, asked)
        // Yalnızca istenen diller: tr (addon) ve en (akış); almanca istenmedi.
        assertEquals(listOf("tr", "en"), saved.map { it.language })
        assertEquals(listOf("Altyazı Addon", "Eklenti"), saved.map { it.origin })
        assertFalse(hits.contains("/de.srt"))
    }

    @Test
    fun theLanguageTheUserPickedForThisTitleIsAlsoSaved() = runTest {
        dao.languages["movie:42"] = com.kaan.play.data.db.SubtitleLanguageEntity("movie:42", "de", 1)
        val f = fetcher("tr", addonProvider(track("/de.srt", "de"), track("/tr.srt", "tr"), track("/en.srt", "en")))

        val saved = f.fetch(1, request, stream())

        assertEquals(listOf("de", "tr", "en"), saved.map { it.language })
        assertEquals(germanSrt, File(saved[0].file).readText(Charsets.UTF_8))
    }

    @Test
    fun firstLoadableCandidateOfALanguageIsUsed() = runTest {
        routes["/broken.srt"] = { MockResponse.Builder().code(500).build() }
        val f = fetcher("en", addonProvider(track("/yok.srt", "en"), track("/broken.srt", "en"), track("/en.srt", "en")))

        val saved = f.fetch(1, request, stream())

        assertEquals(1, saved.size)
        assertEquals(url("/en.srt"), saved.single().url)
    }

    @Test
    fun onlyOneTrackPerLanguageIsKept() = runTest {
        val f = fetcher("en", addonProvider(track("/en.srt", "en"), track("/tr.srt", "en")))
        assertEquals(1, f.fetch(1, request, stream()).size)
    }

    @Test
    fun noSubtitlesAnywhereIsFine() = runTest {
        assertTrue(fetcher("tr").fetch(1, request, stream()).isEmpty())
        // Dili bilinmeyen parçalar eşleşmez, atlanır.
        assertTrue(fetcher("tr").fetch(1, request, stream("/tr.srt" to null)).isEmpty())
    }

    @Test
    fun failedAddonsAreIgnored() = runTest {
        val provider = SubtitleProvider { flowOf(SubtitleResult.Failed(StreamSource(2, "Ölü Addon"), FailureReason.Timeout)) }
        val saved = fetcher("en", provider).fetch(1, request, stream("/en.srt" to "en"))
        assertEquals(listOf("en"), saved.map { it.language })
    }

    @Test
    fun savedFilesBelongToTheirDownloadAndAreDeletedWithIt() = runTest {
        val f = fetcher("en")
        f.fetch(1, request, stream("/en.srt" to "en"))
        f.fetch(10, request, stream("/en.srt" to "en"))
        assertTrue(files.subtitleFile(1, 0).exists())
        assertTrue(files.subtitleFile(10, 0).exists())

        files.deleteSubtitles(1)

        assertFalse(files.subtitleFile(1, 0).exists())
        // "1-" öneki "10-"ı eşleştirmemeli.
        assertTrue(files.subtitleFile(10, 0).exists())
        files.deleteAllSubtitles()
        assertFalse(files.subtitleFile(10, 0).exists())
    }
}
