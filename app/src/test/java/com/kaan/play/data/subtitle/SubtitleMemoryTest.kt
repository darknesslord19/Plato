package com.kaan.play.data.subtitle

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMemoryTest {

    private val dao = FakeSubtitleDao()
    private var now = 1L
    private val memory = SubtitleMemory(dao) { now++ }

    private fun episode(season: Int, episode: Int, tmdbId: Int = 1396) =
        StreamRequest(tmdbId, "tt0903747", MediaType.Tv, season, episode)

    private val movie = StreamRequest(603, "tt0133093", MediaType.Movie)
    private val url = "https://subs.example/tr.srt"

    @Test
    fun contentKeysSeparateMoviesSeriesAndEpisodes() {
        assertEquals("movie:603", SubtitleMemory.languageKey(movie))
        assertEquals("tv:1396", SubtitleMemory.languageKey(episode(1, 3)))
        assertEquals("movie:603", SubtitleMemory.offsetKey(movie))
        assertEquals("tv:1396:1:3", SubtitleMemory.offsetKey(episode(1, 3)))
        // Aynı TMDB numarası film ve dizide ayrı içeriktir.
        assertEquals("movie:1396", SubtitleMemory.languageKey(movie.copy(tmdbId = 1396)))
    }

    @Test
    fun seriesLanguageIsSharedAcrossEpisodesAndSeasonsButNotAcrossShows() = runTest {
        assertNull(memory.language(episode(1, 1)))
        memory.rememberLanguage(episode(1, 1), "tr")
        assertEquals("tr", memory.language(episode(1, 2)))
        assertEquals("tr", memory.language(episode(3, 7)))
        assertNull(memory.language(episode(1, 1, tmdbId = 999)))
        assertNull(memory.language(movie))
    }

    @Test
    fun languageIsOverwrittenAndOffCanBeRemembered() = runTest {
        memory.rememberLanguage(movie, "tr")
        memory.rememberLanguage(movie, "en")
        assertEquals("en", memory.language(movie))
        memory.rememberLanguage(movie, SubtitleSelection.REMEMBERED_OFF)
        assertEquals(SubtitleSelection.REMEMBERED_OFF, memory.language(movie))
        // Dili bilinmeyen altyazı seçimi bir dil tercihi değildir: hafıza değişmez.
        memory.rememberLanguage(movie, null)
        assertEquals(SubtitleSelection.REMEMBERED_OFF, memory.language(movie))
    }

    @Test
    fun offsetIsStoredPerContentAndPerSubtitle() = runTest {
        assertEquals(0L, memory.offset(episode(1, 1), url))
        memory.saveOffset(episode(1, 1), url, 1_500)
        memory.saveOffset(episode(1, 1), "https://subs.example/en.srt", -300)
        memory.saveOffset(episode(1, 2), url, 200)

        assertEquals(1_500L, memory.offset(episode(1, 1), url))
        assertEquals(-300L, memory.offset(episode(1, 1), "https://subs.example/en.srt"))
        assertEquals(200L, memory.offset(episode(1, 2), url)) // aynı altyazı, başka bölüm: ayrı değer
        assertEquals(0L, memory.offset(episode(1, 3), url))
        assertEquals(0L, memory.offset(movie, url))
    }

    @Test
    fun savedOffsetSurvivesANewMemoryInstanceOverTheSameStorage() = runTest {
        memory.saveOffset(movie, url, -700)
        val reopened = SubtitleMemory(dao)
        assertEquals(-700L, reopened.offset(movie, url)) // "aynı içerik tekrar açılınca uygulanır"
    }

    @Test
    fun zeroOffsetDeletesTheRowInsteadOfStoringIt() = runTest {
        memory.saveOffset(movie, url, 400)
        assertEquals(1, dao.offsets.size)
        memory.saveOffset(movie, url, 0)
        assertEquals(0, dao.offsets.size)
        assertEquals(0L, memory.offset(movie, url))
    }

    @Test
    fun offsetTableKeepsOnlyTheNewestRows() = runTest {
        for (i in 1..SubtitleMemory.MAX_OFFSETS + 20) memory.saveOffset(movie, "https://s/$i.srt", 100L)
        assertEquals(SubtitleMemory.MAX_OFFSETS, dao.offsets.size)
        assertEquals(100L, memory.offset(movie, "https://s/${SubtitleMemory.MAX_OFFSETS + 20}.srt")) // en yeni durur
        assertEquals(0L, memory.offset(movie, "https://s/1.srt")) // en eski gitti
    }
}
