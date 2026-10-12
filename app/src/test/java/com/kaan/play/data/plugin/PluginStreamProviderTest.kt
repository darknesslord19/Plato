package com.kaan.play.data.plugin

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.PluginStream
import com.kaan.play.data.stream.CompositeStreamProvider
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/** Eklenti kaynak sağlayıcısı ve birleşik sağlayıcı: gerçek QuickJS yok, sahte çalıştırıcıyla (sanal zamanla). */
class PluginStreamProviderTest {

    private fun plugin(
        id: Long,
        name: String = "Eklenti $id",
        enabled: Boolean = true,
        types: List<String> = listOf("movie", "tv"),
        version: String? = "1.0.0",
    ) = InstalledPlugin(
        id = id, repoId = 1, pluginId = "p$id", name = name, version = version, description = null,
        supportedTypes = types.map { if (it == "series") "tv" else it }.toSet(), rawTypes = types,
        contentLanguage = emptyList(), hasSettings = false, enabled = enabled,
    )

    private fun stream(url: String = "https://cdn.example/v.mp4", quality: String? = "1080p", title: String? = null) =
        PluginStream(url, null, title, quality, null, null, emptyMap(), emptyList())

    private class Call(val code: String, val request: PluginRequest, val scraperId: String, val timeoutMs: Long, val settings: Map<String, JsonElement>)

    private val calls = ArrayList<Call>()
    private var plugins = listOf(plugin(1), plugin(2, enabled = false), plugin(3, types = listOf("movie")), plugin(4, types = listOf("series")))
    private var behavior: suspend (Call) -> PluginOutcome = { PluginOutcome.Success(listOf(stream())) }
    private var saved: Map<Long, Map<String, JsonElement>> = emptyMap()
    private var timeoutSeconds = 15
    private var now = 0L
    private val cache = PluginResultCache(ttlMs = 5 * 60_000L, now = { now })

    private val movie = StreamRequest(27205, "tt1375666", MediaType.Movie)
    private val episode = StreamRequest(1399, "tt0944947", MediaType.Tv, 2, 5)

    private fun provider(maxParallel: Int = PluginStreamProvider.MAX_PARALLEL) = PluginStreamProvider(
        plugins = { plugins },
        code = { id -> "code-$id" },
        runner = { code, req, sid, t, s -> Call(code, req, sid, t, s).also { synchronized(calls) { calls += it } }.let { behavior(it) } },
        settings = { saved[it].orEmpty() },
        timeout = { timeoutSeconds.seconds },
        cache = cache,
        maxParallel = maxParallel,
    )

    @Test
    fun onlyEnabledPluginsOfTheRequestedTypeAreAsked() = runTest {
        val p = provider()
        assertEquals(listOf(-1L, -3L), p.sources(movie).map { it.addonId }) // kapalı (2) ve yalnızca diziler (4) yok
        assertEquals(listOf(-1L, -4L), p.sources(episode).map { it.addonId }) // yalnızca film olan (3) yok
        assertEquals(listOf("Eklenti 1", "Eklenti 3"), p.sources(movie).map { it.addonName })

        val results = p.streams(movie).toList()
        assertEquals(setOf(-1L, -3L), results.map { it.source.addonId }.toSet())
        assertTrue(results.all { it is StreamResult.Found })
        assertEquals(2, calls.size)

        plugins = emptyList()
        assertTrue(p.streams(movie).toList().isEmpty())
        assertTrue(p.sources(movie).isEmpty())
    }

    @Test
    fun documentedArgumentsComeFromTheRequestAndSeriesTypeIsHonoured() = runTest {
        val p = provider()
        p.streams(movie).toList()
        val m = calls.first { it.scraperId == "p1" }
        assertEquals(PluginRequest("27205", "movie", null, null), m.request) // tmdbId metin, film: sezon/bölüm yok
        assertEquals("code-1", m.code)

        calls.clear()
        p.streams(episode).toList()
        assertEquals(PluginRequest("1399", "tv", 2, 5), calls.first { it.scraperId == "p1" }.request)
        // Manifest "series" bildirdiyse ve sezon+bölüm varsa tür "series".
        assertEquals(PluginRequest("1399", "series", 2, 5), calls.first { it.scraperId == "p4" }.request)
        assertEquals("tv", PluginStreamProvider.pluginMediaType(MediaType.Tv, listOf("series"), null, null)) // bölüm yoksa tv
        assertEquals("movie", PluginStreamProvider.pluginMediaType(MediaType.Movie, listOf("movie", "series"), 1, 1))
    }

    @Test
    fun settingsAndSharedTimeoutArePassedAndEachRunIsIndependent() = runTest {
        saved = mapOf(1L to mapOf("enable4K" to JsonPrimitive(false), "max" to JsonPrimitive("2")))
        timeoutSeconds = 30
        provider().streams(movie).toList()
        val c1 = calls.first { it.scraperId == "p1" }
        assertEquals(30_000L, c1.timeoutMs) // ortak kaynak zaman aşımı ayarı
        assertEquals(mapOf("enable4K" to JsonPrimitive(false), "max" to JsonPrimitive("2")), c1.settings)
        assertTrue(calls.first { it.scraperId == "p3" }.settings.isEmpty()) // ayarı olmayana boş nesne
    }

    @Test
    fun resultsStreamAsTheyArriveAndASlowPluginDoesNotBlockOthers() = runTest {
        plugins = listOf(plugin(1), plugin(2), plugin(3))
        behavior = { call ->
            when (call.scraperId) {
                "p1" -> { delay(10_000); PluginOutcome.Success(listOf(stream("https://a/1"))) } // yavaş
                "p2" -> { delay(100); PluginOutcome.Success(listOf(stream("https://a/2"))) }
                else -> PluginOutcome.Failure(PluginOutcome.Reason.Timeout) // hemen "yanıt vermedi"
            }
        }
        val arrivals = ArrayList<Pair<Long, Long>>() // (kaynak, ms)
        val flow = provider().streams(movie)
        flow.collect { arrivals += it.source.addonId to currentTime }

        assertEquals(listOf(-3L, -2L, -1L), arrivals.map { it.first }) // tamamlanma sırasıyla
        assertEquals(listOf(0L, 100L, 10_000L), arrivals.map { it.second })
        val failed = flow.toList().first { it.source.addonId == -3L } as StreamResult.Failed
        assertEquals(FailureReason.Timeout, failed.reason)
    }

    @Test
    fun atMostSixPluginsRunAtTheSameTime() = runTest {
        plugins = (1L..14L).map { plugin(it) }
        val running = AtomicInteger()
        val peak = AtomicInteger()
        behavior = {
            val now = running.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            delay(1_000)
            running.decrementAndGet()
            PluginOutcome.Success(emptyList())
        }
        val results = provider().streams(movie).toList()
        assertEquals(14, results.size)
        assertEquals(6, peak.get()) // sınıra ulaşıldı ama aşılmadı
        assertEquals(3_000L, currentTime) // 14 eklenti / 6 = 3 dalga
        // Daha düşük sınır.
        peak.set(0)
        provider(maxParallel = 2).streams(movie).toList()
        assertEquals(2, peak.get())
    }

    @Test
    fun failuresMapToFailedResults() = runTest {
        plugins = listOf(plugin(1), plugin(2))
        behavior = { call ->
            if (call.scraperId == "p1") PluginOutcome.Failure(PluginOutcome.Reason.Script, "boom") else PluginOutcome.Success(emptyList())
        }
        val results = provider().streams(movie).toList().associateBy { it.source.addonId }
        assertEquals(FailureReason.InvalidResponse, (results.getValue(-1L) as StreamResult.Failed).reason)
        assertTrue((results.getValue(-2L) as StreamResult.Found).streams.isEmpty())

        // Kodu bulunamayan eklenti de "yanıt vermedi" olur, çalıştırılmaz.
        calls.clear()
        val noCode = PluginStreamProvider({ plugins }, { null }, { _, _, _, _, _ -> error("çalışmamalı") }, { emptyMap() }, { 5.seconds })
        assertTrue(noCode.streams(movie).toList().all { it is StreamResult.Failed })
        assertTrue(calls.isEmpty())
    }

    @Test
    fun resultsAreMappedToPlayableStreamsWithCleanedFields() = runTest {
        plugins = listOf(plugin(1, name = "Kaynak"))
        behavior = {
            PluginOutcome.Success(listOf(
                PluginStream("https://cdn.example/a.m3u8", "n", "Film 2.5 GB [TR]", "1080P", null, null, mapOf("Referer" to "https://x"), emptyList()),
                PluginStream("https://cdn.example/b.mp4", null, null, "Auto", "700 MB", "English", emptyMap(), emptyList()),
            ))
        }
        val found = provider().streams(movie).toList().single() as StreamResult.Found
        assertEquals(StreamSource(-1, "Kaynak"), found.source)
        val (a, b) = found.streams
        assertEquals(1080, a.resolution)
        assertEquals(2_500_000_000L, a.sizeBytes)
        assertEquals("TR", a.language)
        assertEquals(mapOf("Referer" to "https://x"), a.headers) // oynatıcıya giden başlıklar (proxyHeaders ile aynı alan)
        assertEquals(null, b.resolution) // "Auto": bilinmiyor
        assertEquals(700_000_000L, b.sizeBytes)
        assertEquals("EN", b.language)
    }

    // ---- önbellek ----

    @Test
    fun cacheAvoidsRerunUntilItExpiresOrRefreshIsRequested() = runTest {
        plugins = listOf(plugin(1))
        val p = provider()
        p.streams(movie).toList()
        assertEquals(1, calls.size)

        now += 4 * 60_000L // 4 dk: önbellekte
        val again = p.streams(movie).toList().single() as StreamResult.Found
        assertEquals(1, calls.size)
        assertEquals(1, again.streams.size)

        p.streams(movie.copy(refresh = true)).toList() // Yenile: yeniden çalışır
        assertEquals(2, calls.size)

        now += 5 * 60_000L // yenileme önbelleği tazeledi; üstüne 5 dk: süresi doldu
        p.streams(movie).toList()
        assertEquals(3, calls.size)
    }

    @Test
    fun cacheKeysSeparateEpisodesSettingsAndVersions() = runTest {
        plugins = listOf(plugin(1))
        val p = provider()
        p.streams(episode).toList()
        p.streams(episode.copy(episode = 6)).toList() // başka bölüm
        p.streams(episode).toList() // önbellekte
        assertEquals(2, calls.size)

        saved = mapOf(1L to mapOf("x" to JsonPrimitive(true))) // ayar değişti
        p.streams(episode).toList()
        assertEquals(3, calls.size)

        plugins = listOf(plugin(1, version = "1.1.0")) // eklenti güncellendi
        p.streams(episode).toList()
        assertEquals(4, calls.size)
    }

    @Test
    fun emptyAndFailedResultsAreNotCached() = runTest {
        plugins = listOf(plugin(1))
        behavior = { PluginOutcome.Success(emptyList()) }
        val p = provider()
        p.streams(movie).toList(); p.streams(movie).toList()
        assertEquals(2, calls.size) // boş sonuç saklanmaz: geçici site sorunu yeniden denenebilir

        behavior = { PluginOutcome.Failure(PluginOutcome.Reason.Timeout) }
        p.streams(movie).toList(); p.streams(movie).toList()
        assertEquals(4, calls.size)
    }

    // ---- birleşik sağlayıcı ----

    private class FakeProvider(val delayMs: Long, val items: List<StreamResult>, val asked: List<StreamSource>) : StreamProvider {
        override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
            launch { delay(delayMs); items.forEach { send(it) } }
        }
        override suspend fun sources(request: StreamRequest) = asked
    }

    @Test
    fun compositeMergesFlowsAndSourcesAndFinishesWhenAllDo() = runTest {
        val addon = StreamSource(7, "Addon")
        val plug = StreamSource(-1, "Eklenti")
        val a = FakeProvider(500, listOf(StreamResult.Found(addon, emptyList())), listOf(addon))
        val b = FakeProvider(100, listOf(StreamResult.Failed(plug, FailureReason.Timeout)), listOf(plug))
        val composite = CompositeStreamProvider(a, b)

        assertEquals(listOf(addon, plug), composite.sources(movie)) // aynı kaynak listesi: addon'lar + eklentiler
        val arrivals = ArrayList<Long>()
        composite.streams(movie).collect { arrivals += it.source.addonId }
        assertEquals(listOf(-1L, 7L), arrivals) // hazır olan önce akar
        assertEquals(500L, currentTime) // akış hepsi bitince biter

        assertTrue(CompositeStreamProvider(emptyList()).streams(movie).toList().isEmpty())
    }
}
