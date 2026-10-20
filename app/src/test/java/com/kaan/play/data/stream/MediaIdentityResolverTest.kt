package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.i18n.UiLanguage
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MediaIdentityResolverTest {

    private lateinit var server: MockWebServer
    private val fixtures = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath.removePrefix("/3/")
                val body = fixtures[path] ?: fixtures["$path|${request.url.queryParameter("language")}"]
                    ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo() = TmdbRepository(
        client = OkHttpClient(),
        json = PlayJson,
        apiKey = "fake",
        language = { UiLanguage.Turkish },
        baseUrl = server.url("/3/"),
    )

    @Test
    fun syncDataWithImdbIdResolvesDirectlyWithoutSearch() = runTest {
        val identity = MediaIdentityResolver.resolveIdentity(
            rawTitle = "By Any Means izle (2026)",
            rawYear = null,
            syncData = mapOf("imdb" to "tt1234567"),
            type = MediaType.Movie,
            tmdb = repo(),
        )
        assertEquals("By Any Means", identity.cleanTitle)
        assertEquals(2026, identity.year)
        assertEquals("tt1234567", identity.imdbId)
    }

    @Test
    fun syncDataWithTmdbIdFetchesImdbIdFromDetail() = runTest {
        fixtures["movie/550"] = """
            {"id":550,"title":"Dövüş Kulübü","overview":"...","imdb_id":"tt0137523","external_ids":{"imdb_id":"tt0137523"}}
        """.trimIndent()

        val identity = MediaIdentityResolver.resolveIdentity(
            rawTitle = "Dövüş Kulübü",
            rawYear = 1999,
            syncData = mapOf("tmdb" to "550"),
            type = MediaType.Movie,
            tmdb = repo(),
        )
        assertEquals("Dövüş Kulübü", identity.cleanTitle)
        assertEquals(1999, identity.year)
        assertEquals(550, identity.tmdbId)
        assertEquals("tt0137523", identity.imdbId)
    }

    @Test
    fun noSyncDataPerformsTmdbSearchWithCleanTitleAndYear() = runTest {
        fixtures["search/multi"] = """
            {"page":1,"total_pages":1,"results":[
              {"id":789,"media_type":"movie","title":"By Any Means","release_date":"2026-03-01"}
            ]}
        """.trimIndent()
        fixtures["movie/789"] = """
            {"id":789,"title":"By Any Means","imdb_id":"tt9876543","external_ids":{"imdb_id":"tt9876543"}}
        """.trimIndent()

        val identity = MediaIdentityResolver.resolveIdentity(
            rawTitle = "By Any Means izle (2026)",
            rawYear = null,
            syncData = null,
            type = MediaType.Movie,
            tmdb = repo(),
        )
        assertEquals("By Any Means", identity.cleanTitle)
        assertEquals(2026, identity.year)
        assertEquals(789, identity.tmdbId)
        assertEquals("tt9876543", identity.imdbId)
    }

    @Test
    fun noSyncDataAndNoTmdbMatchRetainsCleanTitleAndYearWithNullIds() = runTest {
        fixtures["search/multi"] = """{"page":1,"total_pages":1,"results":[]}"""

        val identity = MediaIdentityResolver.resolveIdentity(
            rawTitle = "Bilinmeyen Film izle (2025)",
            rawYear = null,
            syncData = null,
            type = MediaType.Movie,
            tmdb = repo(),
        )
        assertEquals("Bilinmeyen Film", identity.cleanTitle)
        assertEquals(2025, identity.year)
        assertNull(identity.tmdbId)
        assertNull(identity.imdbId)
    }
}
