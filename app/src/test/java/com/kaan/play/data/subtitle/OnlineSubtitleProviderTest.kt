package com.kaan.play.data.subtitle

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/** Yerleşik çevrimiçi altyazı araması: istek adresi, IMDb çevirisi, dil süzgeci/sırası ve hatada boş liste (sahte sunucuyla). */
class OnlineSubtitleProviderTest {

    private lateinit var server: MockWebServer
    private val routes = mutableMapOf<String, () -> MockResponse>()
    private val paths = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(paths) { paths += request.url.encodedPath }
                    return routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build()
                }
            }
            it.start()
        }
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun provider(imdbFor: suspend (StreamRequest) -> String? = { null }, languages: List<String> = listOf("tr", "en")) =
        OnlineSubtitleProvider(
            client = OkHttpClient(),
            languages = { languages },
            imdbFor = imdbFor,
            timeout = 300.milliseconds,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )

    private val body = """
        {"subtitles":[
          {"id":"1","url":"https://s/ger.srt","lang":"ger"},
          {"id":"2","url":"https://s/en1.srt","lang":"eng","subtitleFileName":"Show.S01E02.WEB.srt"},
          {"id":"3","url":"https://s/tr1.srt","lang":"tur","subtitleFileName":"Show.S01E02.tr.srt"},
          {"id":"4","url":"https://s/tr1.srt","lang":"tur"},
          {"id":"5","url":"ftp://s/x.srt","lang":"tur"},
          {"id":"6","url":"https://s/tr2.srt","lang":"tr"}
        ]}
    """.trimIndent()

    @Test
    fun seriesQueryUsesImdbSeasonEpisodeAndKeepsPreferredThenEnglish() = runTest {
        routes["/subtitles/series/tt0903747:1:2.json"] = { json(body) }
        val tracks = provider().search(StreamRequest(1396, "tt0903747", MediaType.Tv, 1, 2))

        assertEquals(listOf("https://s/tr1.srt", "https://s/tr2.srt", "https://s/en1.srt"), tracks.map { it.url })
        assertEquals(listOf("tr", "tr", "en"), tracks.map { it.language })
        assertTrue(tracks.all { it.kind == SubtitleKind.Online && it.origin == OnlineSubtitleProvider.ORIGIN })
        assertEquals("Show.S01E02.tr.srt", tracks[0].label)
    }

    @Test
    fun missingImdbIsTranslatedFromTmdb() = runTest {
        routes["/subtitles/movie/tt0133093.json"] = { json(body) }
        var asked = 0
        val tracks = provider(imdbFor = { asked++; "tt0133093" }).search(StreamRequest(603, null, MediaType.Movie))
        assertEquals(1, asked)
        assertEquals(3, tracks.size)
        assertEquals(listOf("/subtitles/movie/tt0133093.json"), paths)
    }

    @Test
    fun networkErrorsAndBadAnswersGiveAnEmptyListNotAFailure() = runTest {
        routes["/subtitles/movie/tt1.json"] = { MockResponse.Builder().code(500).build() }
        routes["/subtitles/movie/tt2.json"] = { json("<html>") }
        routes["/subtitles/movie/tt3.json"] = { json(body).newBuilder().headersDelay(2, TimeUnit.SECONDS).build() }
        val p = provider()
        for (id in listOf("tt1", "tt2", "tt3", "tt4")) {
            val results = p.subtitles(StreamRequest(1, id, MediaType.Movie)).toList()
            assertEquals(listOf(SubtitleResult.Found(OnlineSubtitleProvider.SOURCE, emptyList())), results)
        }
        // IMDb bulunamazsa hiç istek gitmez.
        paths.clear()
        assertEquals(emptyList<SubtitleTrack>(), provider(imdbFor = { throw IllegalStateException("tmdb") }).search(StreamRequest(1, null, MediaType.Movie)))
        assertEquals(emptyList<String>(), paths)
    }

    @Test
    fun englishPreferenceCollapsesToOneLanguageAndCapsResults() {
        val many = (1..40).joinToString(",", prefix = """{"subtitles":[""", postfix = "]}") { """{"url":"https://s/$it.srt","lang":"eng"}""" }
        val tracks = OnlineSubtitleProvider.parse(many, listOf("en", "en"))
        assertEquals(OnlineSubtitleProvider.MAX_PER_LANGUAGE, tracks.size)
    }
}
