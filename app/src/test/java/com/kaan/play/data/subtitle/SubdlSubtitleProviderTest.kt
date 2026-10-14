package com.kaan.play.data.subtitle

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** SubDL: istek biçimi, sonuç ayrıştırma/sıralama, anahtar yokken sessiz atlama, hata bildirimi ve film bazında önbellek (sahte sunucuyla). */
class SubdlSubtitleProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<HttpUrl>()
    private var responder: () -> MockResponse = { MockResponse.Builder().code(404).build() }

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requests) { requests += request.url }
                    return responder()
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun provider(
        key: String = "SECRET-KEY-123",
        languages: List<String> = listOf("tr", "en"),
        cache: SubtitleSearchCache? = null,
        imdbFor: suspend (StreamRequest) -> String? = { null },
    ) = SubdlSubtitleProvider(
        client = OkHttpClient(),
        apiKey = key,
        languages = { languages },
        imdbFor = imdbFor,
        cache = cache,
        endpoints = { SubdlEndpoints(api = server.url("/").toString().trimEnd('/'), dl = "https://dl.subdl.example") },
    )

    private val body = """
        {"status":true,
         "results":[{"imdb_id":"tt0903747","type":"tv","name":"Breaking Bad"}],
         "subtitles":[
          {"release_name":"BB.S01E02.EN.WEB","name":"en1.zip","lang":"EN","url":"/subtitle/100-1.zip","season":1,"episode":2},
          {"release_name":"BB.S01E02.TR.BluRay","name":"tr1.zip","lang":"TR","url":"/subtitle/200-1.zip","season":1,"episode":2},
          {"release_name":"BB.S01E02.TR.WEB","name":"tr2.zip","language":"TR","url":"/subtitle/200-2.zip","season":1,"episode":2},
          {"release_name":"BB.S01.Pack","name":"pack.zip","lang":"TR","url":"/subtitle/300-1.zip","full_season":true},
          {"release_name":"BB.S01E02.DE","name":"de.zip","lang":"DE","url":"/subtitle/400-1.zip"},
          {"release_name":"dup","name":"dup.zip","lang":"TR","url":"/subtitle/200-1.zip"},
          {"release_name":"no url","lang":"TR"}
        ]}
    """.trimIndent()

    @Test
    fun seriesRequestCarriesImdbSeasonEpisodeLanguagesAndKey() = runTest {
        responder = { json(body) }
        val tracks = provider().search(StreamRequest(1396, "tt0903747", MediaType.Tv, 1, 2))

        val url = requests.single()
        assertEquals("/api/v1/subtitles", url.encodedPath)
        assertEquals("SECRET-KEY-123", url.queryParameter("api_key"))
        assertEquals("tt0903747", url.queryParameter("imdb_id"))
        assertEquals("tv", url.queryParameter("type"))
        assertEquals("1", url.queryParameter("season_number"))
        assertEquals("2", url.queryParameter("episode_number"))
        assertEquals("TR", url.queryParameter("languages")) // önce yalnız Türkçe
        assertEquals("30", url.queryParameter("subs_per_page"))

        // Türkçe sonuç var: ikinci (İngilizce) istek atılmaz; tam sezon paketi, yinelenen ve adressiz atılır.
        assertEquals(
            listOf("https://dl.subdl.example/subtitle/200-1.zip", "https://dl.subdl.example/subtitle/200-2.zip"),
            tracks.map { it.url },
        )
        assertEquals(listOf("tr", "tr"), tracks.map { it.language })
        assertEquals(listOf("BB.S01E02.TR.BluRay", "BB.S01E02.TR.WEB"), tracks.map { it.label })
        assertTrue(tracks.all { it.kind == SubtitleKind.Online && it.origin == SubdlSubtitleProvider.ORIGIN })
    }

    @Test
    fun englishIsRequestedOnlyWhenTurkishHasNoResults() = runTest {
        responder = {
            if (requests.last().queryParameter("languages") == "EN") json(body) else json("""{"status":true,"subtitles":[]}""")
        }
        val tracks = provider().search(StreamRequest(1396, "tt0903747", MediaType.Tv, 1, 2))
        assertEquals(listOf("TR", "EN"), requests.map { it.queryParameter("languages") })
        assertEquals(listOf("en"), tracks.map { it.language })

        // Türkçe aramada hata: İngilizceye geçilmez, "yanıt vermedi".
        requests.clear()
        responder = { MockResponse.Builder().code(500).build() }
        assertTrue(provider().subtitles(StreamRequest(1, "tt1", MediaType.Movie)).toList().single() is SubtitleResult.Failed)
        assertEquals(1, requests.size)
    }

    @Test
    fun apiKeyInResultAddressesIsStrippedAndNeverCached() = runTest {
        val dir = tmp.newFolder("keyed")
        val keyed = """{"status":true,"subtitles":[
            {"release_name":"a","lang":"TR","url":"/subtitle/1-1.zip?api_key=SECRET-KEY-123"},
            {"release_name":"b","lang":"TR","url":"https://dl.subdl.example/subtitle/2-2.zip?x=1&api_key=SECRET-KEY-123"}]}"""
        responder = { json(keyed) }
        val tracks = provider(cache = SubtitleSearchCache(dir)).search(StreamRequest(603, "tt0133093", MediaType.Movie))
        assertEquals(
            listOf("https://dl.subdl.example/subtitle/1-1.zip", "https://dl.subdl.example/subtitle/2-2.zip?x=1"),
            tracks.map { it.url },
        )
        assertTrue(dir.listFiles()!!.isNotEmpty())
        assertTrue(dir.listFiles()!!.none { "api_key" in it.readText() || "SECRET" in it.readText() })
    }

    @Test
    fun oldCacheFilesWithAKeyAreCleanedWhenRead() = runTest {
        val dir = tmp.newFolder("old")
        val cache = SubtitleSearchCache(dir, clock = { 1_000L })
        cache.getOrFetch("k") { listOf(SubtitleTrack("https://x/1.zip", "tr", "SubDL", SubtitleKind.Online, "r")) }
        val file = dir.listFiles { f -> f.name.endsWith(".json") }!!.single()
        file.writeText(file.readText().replace("https://x/1.zip", "https://x/1.zip?api_key=SECRET-KEY-123"))
        assertTrue("api_key" in file.readText())

        val read = cache.getOrFetch("k") { error("önbellekten okunmalı") }
        assertEquals("https://x/1.zip", read!!.single().url)
        assertFalse("api_key" in file.readText())
    }

    @Test
    fun movieRequestHasNoSeasonAndTheKeyNeverLeaksIntoResults() = runTest {
        responder = { json(body) }
        val tracks = provider().search(StreamRequest(603, "tt0133093", MediaType.Movie))
        val url = requests.single()
        assertEquals("movie", url.queryParameter("type"))
        assertNull(url.queryParameter("season_number"))
        assertNull(url.queryParameter("episode_number"))
        assertTrue(tracks.none { "SECRET" in it.url || "SECRET" in (it.label ?: "") || "api_key" in it.url })
    }

    @Test
    fun withoutAKeyNothingIsRequestedAndTheFlowIsSilent() = runTest {
        responder = { json(body) }
        val p = provider(key = "")
        assertEquals(emptyList<SubtitleResult>(), p.subtitles(StreamRequest(1, "tt1", MediaType.Movie)).toList())
        assertEquals(emptyList<SubtitleTrack>(), p.search(StreamRequest(1, "tt1", MediaType.Movie)))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun missingImdbIsTranslatedFromTmdbAndNoImdbMeansNoRequest() = runTest {
        responder = { json(body) }
        var asked = 0
        provider(imdbFor = { asked++; "tt0133093" }).search(StreamRequest(603, null, MediaType.Movie))
        assertEquals(1, asked)
        assertEquals("tt0133093", requests.single().queryParameter("imdb_id"))

        requests.clear()
        assertEquals(emptyList<SubtitleTrack>(), provider(imdbFor = { null }).search(StreamRequest(603, null, MediaType.Movie)))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun serverErrorIsReportedAsFailedAndStatusFalseAsEmpty() = runTest {
        responder = { MockResponse.Builder().code(500).build() }
        val failed = provider().subtitles(StreamRequest(1, "tt1", MediaType.Movie)).toList()
        assertEquals(listOf(SubtitleResult.Failed(SubdlSubtitleProvider.SOURCE, FailureReason.Network)), failed)

        responder = { json("""{"status":false,"error":"Invalid API key"}""") }
        val empty = provider().subtitles(StreamRequest(1, "tt1", MediaType.Movie)).toList()
        assertEquals(listOf(SubtitleResult.Found(SubdlSubtitleProvider.SOURCE, emptyList())), empty)

        responder = { json("<html>") }
        assertEquals(
            listOf(SubtitleResult.Found(SubdlSubtitleProvider.SOURCE, emptyList())),
            provider().subtitles(StreamRequest(1, "tt1", MediaType.Movie)).toList(),
        )
    }

    @Test
    fun resultsAreCachedPerContentButErrorsAreNot() = runTest {
        val cache = SubtitleSearchCache(tmp.newFolder("search"))
        val p = provider(cache = cache)
        val request = StreamRequest(1396, "tt0903747", MediaType.Tv, 1, 2)

        responder = { MockResponse.Builder().code(503).build() }
        assertTrue(p.subtitles(request).toList().single() is SubtitleResult.Failed)
        assertEquals(1, requests.size)

        responder = { json(body) }
        assertEquals(2, p.search(request).size) // hata önbelleğe yazılmadığı için tekrar sorulur
        assertEquals(2, requests.size)

        assertEquals(2, p.search(request).size) // artık önbellekten: ağa gidilmez
        assertEquals(2, requests.size)

        // Başka bölüm ayrı sorgudur.
        p.search(request.copy(episode = 3))
        assertEquals(3, requests.size)
    }

    @Test
    fun relativeAndAbsoluteDownloadAddressesBothWork() {
        val tracks = SubdlSubtitleProvider.parse(
            """{"status":true,"subtitles":[
                {"release_name":"a","lang":"TR","url":"/subtitle/1.zip"},
                {"release_name":"b","lang":"TR","url":"subtitle/2.zip"},
                {"release_name":"c","lang":"TR","url":"https://other.example/3.zip"}]}""",
            listOf("tr"),
            "https://dl.subdl.com/",
        )
        assertEquals(
            listOf("https://dl.subdl.com/subtitle/1.zip", "https://dl.subdl.com/subtitle/2.zip", "https://other.example/3.zip"),
            tracks.map { it.url },
        )
    }

    @Test
    fun brazilianPortugueseAndNamesMapToBaseLanguages() {
        assertEquals("pt", SubdlSubtitleProvider.language("BR_PT"))
        assertEquals("tr", SubdlSubtitleProvider.language("TR"))
        assertEquals("tr", SubdlSubtitleProvider.language("Turkish"))
        assertNull(SubdlSubtitleProvider.language(""))
        assertFalse(SubdlSubtitleProvider.language("xx-yy") != null)
    }
}

class SubtitleZipTest {

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nMerhaba\n".toByteArray()

    @Test
    fun recognizesZipByMagic() {
        assertTrue(SubtitleZip.isZip(zip("a.srt" to srt)))
        assertFalse(SubtitleZip.isZip(srt))
        assertFalse(SubtitleZip.isZip(ByteArray(2)))
    }

    @Test
    fun extractsTheSubtitleAndPrefersSrt() {
        val bytes = zip("readme.txt" to "x".toByteArray(), "movie.ass" to "ass".toByteArray(), "movie.srt" to srt, "movie.idx" to ByteArray(3))
        assertEquals(String(srt), String(SubtitleZip.extract(bytes, 1_000_000)!!))
        assertEquals("ass", String(SubtitleZip.extract(zip("a.ass" to "ass".toByteArray()), 1_000_000)!!))
    }

    @Test
    fun skipsMacMetadataAndDirectoriesAndTooLargeEntries() {
        val bytes = zip("__MACOSX/._movie.srt" to "junk".toByteArray(), "sub/movie.srt" to srt)
        assertEquals(String(srt), String(SubtitleZip.extract(bytes, 1_000_000)!!))
        assertNull(SubtitleZip.extract(zip("big.srt" to ByteArray(5_000)), 1_000))
    }

    @Test
    fun noSubtitleInsideOrCorruptArchiveGivesNull() {
        assertNull(SubtitleZip.extract(zip("a.txt" to "x".toByteArray(), "b.sub" to ByteArray(4)), 1_000))
        assertNull(SubtitleZip.extract("PK\u0003\u0004garbage".toByteArray(), 1_000))
    }
}

class SubtitleSearchCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val track = SubtitleTrack("https://x/1.zip", "tr", "SubDL", SubtitleKind.Online, "rel")

    @Test
    fun freshEntriesAreReusedAndExpireByTtl() = runTest {
        var now = 1_000L
        val cache = SubtitleSearchCache(tmp.newFolder("c"), clock = { now })
        var calls = 0
        val fetch: suspend () -> List<SubtitleTrack>? = { calls++; listOf(track) }

        assertEquals(listOf(track), cache.getOrFetch("k", fetch))
        assertEquals(listOf(track), cache.getOrFetch("k", fetch))
        assertEquals(1, calls)

        now += SubtitleSearchCache.TTL_MS + 1
        cache.getOrFetch("k", fetch)
        assertEquals(2, calls)
    }

    @Test
    fun emptyResultsExpireSoonerAndErrorsAreNeverStored() = runTest {
        var now = 0L
        val cache = SubtitleSearchCache(tmp.newFolder("c2"), clock = { now })
        var calls = 0
        assertNull(cache.getOrFetch("e") { calls++; null })
        assertNull(cache.getOrFetch("e") { calls++; null })
        assertEquals(2, calls)

        assertEquals(emptyList<SubtitleTrack>(), cache.getOrFetch("empty") { calls++; emptyList() })
        cache.getOrFetch("empty") { calls++; emptyList() }
        assertEquals(3, calls)
        now += SubtitleSearchCache.EMPTY_TTL_MS + 1
        cache.getOrFetch("empty") { calls++; emptyList() }
        assertEquals(4, calls)
    }

    @Test
    fun differentKeysAreSeparateAndCorruptFilesAreIgnored() = runTest {
        val dir = tmp.newFolder("c3")
        val cache = SubtitleSearchCache(dir)
        cache.getOrFetch("a") { listOf(track) }
        cache.getOrFetch("b") { emptyList() }
        assertEquals(2, dir.listFiles { f -> f.name.endsWith(".json") }!!.size)
        dir.listFiles { f -> f.name.endsWith(".json") }!!.forEach { it.writeText("{bozuk") }
        var calls = 0
        assertEquals(listOf(track), cache.getOrFetch("a") { calls++; listOf(track) })
        assertEquals(1, calls)
    }
}

class SubtitleLoaderZipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.close()

    private fun zipOf(name: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(name))
            z.write(data)
            z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun zipDownloadIsUnpackedDecodedAndCached() = runTest {
        // Windows-1254 kodlu Türkçe altyazı zip içinde.
        val text = "1\r\n00:00:01,000 --> 00:00:03,000\r\nÇok güzel bir şey, değil mi? Işık ve ölüm.\r\n"
        val zip = zipOf("Film.TR.srt", text.toByteArray(charset("windows-1254")))
        server.enqueue(MockResponse.Builder().body(Buffer().write(zip)).addHeader("Content-Type", "application/zip").build())
        val loader = SubtitleLoader(OkHttpClient(), tmp.newFolder("subs"))
        val track = SubtitleTrack(server.url("/subtitle/1.zip").toString(), "tr", "SubDL", SubtitleKind.Online, "Film.TR")

        val first = loader.load(track) as SubtitleLoad.Loaded
        assertEquals(1, first.cues.size)
        assertTrue(first.cues.single().text.contains("Çok güzel bir şey"))
        assertTrue(first.cues.single().text.contains("Işık"))
        assertEquals(false, first.fromCache)

        val second = loader.load(track) as SubtitleLoad.Loaded
        assertEquals(true, second.fromCache) // aynı altyazı tekrar ağa gitmez
        assertEquals(1, server.requestCount)
    }

    @Test
    fun zipWithoutASubtitleFileIsInvalid() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf("readme.txt", "merhaba".toByteArray()))).build())
        val loader = SubtitleLoader(OkHttpClient(), tmp.newFolder("subs2"))
        val result = loader.load(SubtitleTrack(server.url("/x.zip").toString(), "tr", "SubDL", SubtitleKind.Online))
        assertEquals(SubtitleLoad.Failed(SubtitleLoad.Reason.Invalid), result)
    }
}
