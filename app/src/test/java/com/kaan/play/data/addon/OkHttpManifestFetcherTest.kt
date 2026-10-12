package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpManifestFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun fetcher(maxBytes: Long = 1024, timeout: Long = 5) =
        OkHttpManifestFetcher(OkHttpClient(), timeout, maxBytes)

    private suspend fun expect(kind: Kind, detail: String? = null, block: suspend () -> Unit) {
        try {
            block()
            fail("AddonException($kind) bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(kind, e.kind)
            if (detail != null) assertEquals(detail, e.detail)
        }
    }

    @Test
    fun returnsBodyOn200() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"id":"a"}""").build())
        assertEquals("""{"id":"a"}""", fetcher().fetch(server.url("/manifest.json").toString()))
        assertEquals("application/json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun followsRedirects() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/real.json").toString()).build())
        server.enqueue(MockResponse.Builder().body("ok").build())
        assertEquals("ok", fetcher().fetch(server.url("/manifest.json").toString()))
    }

    @Test
    fun mapsHttpErrorsWithCode() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        expect(Kind.Http, "404") { fetcher().fetch(server.url("/manifest.json").toString()) }
    }

    @Test
    fun rejectsOversizedBodies() = runTest {
        server.enqueue(MockResponse.Builder().body("x".repeat(2000)).build())
        expect(Kind.TooLarge) { fetcher(maxBytes = 1000).fetch(server.url("/manifest.json").toString()) }
    }

    @Test
    fun acceptsBodyExactlyAtLimit() = runTest {
        server.enqueue(MockResponse.Builder().body("x".repeat(1000)).build())
        assertEquals(1000, fetcher(maxBytes = 1000).fetch(server.url("/manifest.json").toString()).length)
    }

    @Test
    fun unreachableHostIsNetworkError() = runTest {
        val url = server.url("/manifest.json").toString()
        server.close()
        expect(Kind.Network) { fetcher().fetch(url) }
    }
}
