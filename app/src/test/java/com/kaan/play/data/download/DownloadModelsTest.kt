package com.kaan.play.data.download

import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.ui.player.PlaySession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** İndirme satırının modeli: JSON alanları, oynatma hedefi, çevrimdışı oturum ve yerel altyazı yükleyicisi. */
class DownloadModelsTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dl-models").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun movie() = DownloadEntity(
        id = 5, tmdbId = 603, imdbId = "tt0133093", type = MediaType.Movie, title = "The Matrix", posterPath = "/m.jpg",
        sourceName = "Eklenti A", sourceUrl = "https://cdn/x.mp4?t=1", headersJson = DownloadCodec.encodeHeaders(mapOf("Referer" to "https://a")),
        status = DownloadStatus.Completed, downloadedBytes = 900, totalBytes = 900, createdAt = 1,
    )

    private fun episode() = movie().copy(
        id = 6, tmdbId = 1396, imdbId = "tt0903747", type = MediaType.Tv, season = 1, episode = 3, title = "Breaking Bad",
    )

    @Test
    fun headersRoundTripAndCorruptJsonFallsBackToNoHeaders() {
        val headers = mapOf("Referer" to "https://a.example", "X-Token" to "çok gizli")
        assertEquals(headers, DownloadCodec.decodeHeaders(DownloadCodec.encodeHeaders(headers)))
        assertEquals(emptyMap<String, String>(), DownloadCodec.decodeHeaders(DownloadCodec.encodeHeaders(emptyMap())))
        assertEquals(emptyMap<String, String>(), DownloadCodec.decodeHeaders("bozuk{"))
        assertEquals(emptyMap<String, String>(), DownloadCodec.decodeHeaders(""))
    }

    @Test
    fun subtitlesRoundTripAndCorruptJsonFallsBackToNone() {
        val subs = listOf(
            DownloadSubtitle("https://s/tr.srt", "tr", "Altyazı Addon", "/x/1-0.txt"),
            DownloadSubtitle("https://s/x.srt", null, "Eklenti", "/x/1-1.txt"),
        )
        assertEquals(subs, DownloadCodec.decodeSubtitles(DownloadCodec.encodeSubtitles(subs)))
        assertTrue(DownloadCodec.decodeSubtitles("[]").isEmpty())
        assertTrue(DownloadCodec.decodeSubtitles("bozuk").isEmpty())
    }

    @Test
    fun movieTargetHasNoSeasonOrEpisode() {
        val target = movie().toTarget()
        assertEquals(603, target.request.tmdbId)
        assertEquals("tt0133093", target.request.imdbId)
        assertEquals(MediaType.Movie, target.request.type)
        assertNull(target.request.season)
        assertNull(target.request.episode)
        assertEquals("The Matrix", target.title)
        assertEquals("/m.jpg", target.posterPath)
    }

    @Test
    fun episodeTargetKeepsSeasonAndEpisodeSoHistoryIsShared() {
        val target = episode().toTarget()
        assertEquals(MediaType.Tv, target.request.type)
        assertEquals(1, target.request.season)
        assertEquals(3, target.request.episode)
        // Geçmiş ve altyazı hafızası aynı anahtarı kullanır (çevrimiçi oynatmayla birebir).
        assertEquals("tt0903747:1:3", target.request.contentId)
    }

    @Test
    fun offlineSessionCarriesDownloadedSubtitlesAndSurvivesTheIntentJson() {
        val subs = listOf(DownloadSubtitle("https://s/tr.srt", "tr", "Altyazı Addon", "/x/5-0.txt"))
        val download = movie().copy(subtitlesJson = DownloadCodec.encodeSubtitles(subs))

        val session = PlaySession.offline(download, startPositionMs = 61_000, autoResumed = true)

        assertEquals(5L, session.offline!!.downloadId)
        assertEquals(mapOf("https://s/tr.srt" to "/x/5-0.txt"), session.offline!!.subtitleFiles)
        assertEquals(61_000L, session.startPositionMs)
        assertTrue(session.autoResumed)
        assertEquals("Eklenti A", session.stream.source.addonName)
        // Oynatıcı yalnızca indirilen altyazıları görür; internetten başlık/altyazı gitmez.
        assertEquals(listOf("https://s/tr.srt"), session.stream.subtitles.map { it.url })
        assertTrue(session.stream.headers.isEmpty())

        val json = PlayJson.encodeToString(PlaySession.serializer(), session)
        assertEquals(session, PlayJson.decodeFromString(PlaySession.serializer(), json))
    }

    @Test
    fun onlineSessionsStayOnline() {
        val json = """{"target":{"request":{"tmdbId":1,"imdbId":"tt1","type":"Movie"},"title":"x","posterPath":null},
            "stream":{"source":{"addonId":1,"addonName":"A"},"url":"https://v/x.mp4","resolution":1080,"sizeBytes":null,"headers":{},"subtitles":[]},
            "startPositionMs":0}""".trimIndent()
        assertNull(PlayJson.decodeFromString(PlaySession.serializer(), json).offline)
    }

    // ---- yerel altyazı yükleyicisi ----

    private val srt = "1\n00:00:01,000 --> 00:00:03,000\nİstanbul'da şöyle bir gün.\n\n"

    @Test
    fun downloadedSubtitleLoaderReadsTheLocalUtf8File() = runTest {
        val file = File(dir, "5-0.txt").also { it.writeText(srt, Charsets.UTF_8) }
        val loader = DownloadedSubtitleLoader(mapOf("https://s/tr.srt" to file.path))

        val loaded = loader.load(com.kaan.play.data.subtitle.SubtitleTrack("https://s/tr.srt", "tr", "x"))

        val cues = (loaded as com.kaan.play.data.subtitle.SubtitleLoad.Loaded).cues
        assertEquals("İstanbul'da şöyle bir gün.", cues.single().text)
    }

    @Test
    fun downloadedSubtitleLoaderFailsWithoutTouchingTheNetwork() = runTest {
        val empty = File(dir, "empty.txt").also { it.writeText("hiçbir altyazı satırı yok") }
        val loader = DownloadedSubtitleLoader(mapOf("https://s/empty.srt" to empty.path, "https://s/gone.srt" to File(dir, "yok.txt").path))
        for (url in listOf("https://s/empty.srt", "https://s/gone.srt", "https://baska/adres.srt")) {
            val result = loader.load(com.kaan.play.data.subtitle.SubtitleTrack(url, "tr", "x"))
            assertTrue(url, result is com.kaan.play.data.subtitle.SubtitleLoad.Failed)
        }
    }

    // ---- dosya yerleşimi ----

    @Test
    fun subtitleFilesAreWrittenAtomically() {
        val files = DownloadFiles(File(dir, "downloads"))
        val file = files.writeSubtitle(3, 0, "merhaba şöyle")
        assertEquals("merhaba şöyle", file.readText(Charsets.UTF_8))
        assertEquals(listOf("3-0.txt"), file.parentFile!!.list()!!.toList())
        assertEquals(File(dir, "downloads/cache"), files.cacheDir)
    }

    @Test
    fun freeSpaceWorksBeforeTheDirectoryExists() {
        val files = DownloadFiles(File(dir, "yeni/downloads"))
        assertTrue(files.freeBytes() > 0)
    }
}
