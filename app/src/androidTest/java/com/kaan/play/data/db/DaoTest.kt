package com.kaan.play.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** GerÃ§ek Room/SQLite Ã¼zerinde DAO sorgularÄ± (bellek iÃ§i veritabanÄ±). */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: PlayDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlayDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun history(
        tmdbId: Int, type: MediaType = MediaType.Movie, season: Int = 0, episode: Int = 0,
        position: Long, duration: Long = 100_000, at: Long, title: String = "T$tmdbId",
    ) = WatchHistoryEntity(
        tmdbId = tmdbId, type = type, season = season, episode = episode,
        positionMs = position, durationMs = duration, lastWatchedAt = at, title = title, posterPath = null,
    )

    // ---- devam et ----

    @Test
    fun latestPerContentIsNewestFirstAndSkipsUnknownDuration() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(1, position = 10_000, at = 100))
        dao.upsert(history(2, position = 96_000, at = 200)) // bitmiÅŸ de olsa listelenir: izlendi sÃ¼zmesi WatchRules'ta
        dao.upsert(history(3, position = 50_000, at = 300))
        dao.upsert(history(4, position = 5_000, duration = 0, at = 400)) // sÃ¼re bilinmiyor, girmez

        assertEquals(listOf(3, 2, 1), dao.observeLatestPerContent().first().map { it.tmdbId })
    }

    @Test
    fun latestPerContentKeepsOnlyTheLatestEpisodeOfEachSeries() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(10, MediaType.Tv, 1, 1, position = 40_000, at = 100))
        dao.upsert(history(10, MediaType.Tv, 1, 2, position = 30_000, at = 200)) // en yeni bÃ¶lÃ¼m
        dao.upsert(history(11, MediaType.Tv, 1, 1, position = 20_000, at = 150))

        val list = dao.observeLatestPerContent().first()
        assertEquals(listOf(10 to 2, 11 to 1), list.map { it.tmdbId to it.episode })
    }

    @Test
    fun finishedLatestEpisodeIsStillReturnedSoTheNextOneCanBeSuggested() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(10, MediaType.Tv, 1, 1, position = 40_000, at = 100))
        dao.upsert(history(10, MediaType.Tv, 1, 2, position = 99_000, at = 200)) // en yeni bÃ¶lÃ¼m bitti

        val latest = dao.observeLatestPerContent().first().single()
        assertEquals(2, latest.episode)
        assertTrue(com.kaan.play.data.library.WatchRules.isWatched(latest.positionMs, latest.durationMs))
    }

    @Test
    fun sameMovieAndSeriesIdAreDistinguishedByType() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(5, MediaType.Movie, position = 10_000, at = 100))
        dao.upsert(history(5, MediaType.Tv, 1, 1, position = 10_000, at = 200))

        assertEquals(2, dao.observeLatestPerContent().first().size)
    }

    @Test
    fun upsertReplacesTheSameEpisodeInsteadOfDuplicatingIt() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(10, MediaType.Tv, 2, 3, position = 10_000, at = 100))
        dao.upsert(history(10, MediaType.Tv, 2, 3, position = 60_000, at = 300))

        val list = dao.observeLatestPerContent().first()
        assertEquals(1, list.size)
        assertEquals(60_000L, list.single().positionMs)
        assertEquals(60_000L, dao.find(10, MediaType.Tv, 2, 3)!!.positionMs)
        assertNull(dao.find(10, MediaType.Tv, 2, 4))
    }

    @Test
    fun historyListsEveryRecordNewestFirstThenDeleteAndClear() = runBlocking {
        val dao = db.watchHistoryDao()
        dao.upsert(history(1, position = 10_000, at = 100))
        dao.upsert(history(10, MediaType.Tv, 1, 1, position = 99_000, at = 200)) // izlendi de listelenir
        dao.upsert(history(10, MediaType.Tv, 1, 2, position = 30_000, at = 300)) // aynı dizinin ayrı bölümü
        dao.upsert(history(2, position = 5_000, duration = 0, at = 400)) // süre bilinmese de geçmiştedir

        val all = dao.observeHistory().first()
        assertEquals(listOf(2, 10, 10, 1), all.map { it.tmdbId })
        assertEquals(listOf(0, 2, 1, 0), all.map { it.episode })

        dao.delete(all.first { it.tmdbId == 1 }.id)
        assertEquals(3, dao.observeHistory().first().size)

        dao.clear()
        assertTrue(dao.observeHistory().first().isEmpty())
    }

    @Test
    fun clearingHistoryDoesNotTouchFavorites() = runBlocking {
        db.favoriteDao().add(FavoriteEntity(1, MediaType.Movie, "A", null, addedAt = 100))
        db.watchHistoryDao().upsert(history(1, position = 10_000, at = 100))

        db.watchHistoryDao().clear()

        assertEquals(1, db.favoriteDao().observeAll().first().size)
    }

    // ---- favoriler ----

    @Test
    fun favoritesToggleAndListNewestFirst() = runBlocking {
        val dao = db.favoriteDao()
        assertFalse(dao.observeIsFavorite(1, MediaType.Movie).first())

        dao.add(FavoriteEntity(1, MediaType.Movie, "Bir", null, addedAt = 100))
        dao.add(FavoriteEntity(1, MediaType.Tv, "AynÄ± kimlik, dizi", null, addedAt = 200))
        assertTrue(dao.observeIsFavorite(1, MediaType.Movie).first())
        assertTrue(dao.observeIsFavorite(1, MediaType.Tv).first())
        assertEquals(listOf(MediaType.Tv, MediaType.Movie), dao.observeAll().first().map { it.type })

        dao.remove(1, MediaType.Movie)
        assertFalse(dao.observeIsFavorite(1, MediaType.Movie).first())
        assertTrue(dao.observeIsFavorite(1, MediaType.Tv).first())
    }

    @Test
    fun addingTheSameFavoriteTwiceKeepsOneRow() = runBlocking {
        val dao = db.favoriteDao()
        dao.add(FavoriteEntity(1, MediaType.Movie, "A", null, addedAt = 100))
        dao.add(FavoriteEntity(1, MediaType.Movie, "A (gÃ¼ncel)", null, addedAt = 200))
        assertEquals(1, dao.observeAll().first().size)
    }

    // ---- addon'lar ----

    private fun addon(url: String, type: AddonType = AddonType.NUVIO_ADDON, enabled: Boolean = true, at: Long = 1) =
        AddonEntity(
            type = type, sourceUrl = url, addonId = "id", name = "n", version = null, description = null,
            logoUrl = null, enabled = enabled, manifestJson = "{}", addedAt = at,
        )

    @Test
    fun duplicateAddonUrlIsIgnoredAndReturnsMinusOne() = runBlocking {
        val dao = db.addonDao()
        assertTrue(dao.insert(addon("https://a/manifest.json")) > 0)
        assertEquals(-1L, dao.insert(addon("https://a/manifest.json")))
        assertEquals(1, dao.countBySourceUrl("https://a/manifest.json"))
    }

    @Test
    fun addonQueriesFilterByTypeAndEnabledState() = runBlocking {
        val dao = db.addonDao()
        dao.insert(addon("https://a/manifest.json", at = 2))
        val b = dao.insert(addon("https://b/manifest.json", at = 1))
        dao.insert(addon("https://repo/manifest.json", type = AddonType.NUVIO_PLUGIN_REPO))

        assertEquals(listOf("https://b/manifest.json", "https://a/manifest.json"), dao.observeOfType(AddonType.NUVIO_ADDON).first().map { it.sourceUrl })

        dao.setEnabled(b, false)
        assertEquals(listOf("https://a/manifest.json"), dao.enabledOfType(AddonType.NUVIO_ADDON).map { it.sourceUrl })

        dao.delete(b)
        assertEquals(1, dao.observeOfType(AddonType.NUVIO_ADDON).first().size)
    }

    @Test
    fun webSiteRowsAreCountedAndListedSeparatelyFromAddons() = runBlocking {
        val dao = db.addonDao()
        dao.insert(addon("https://a/manifest.json"))
        val site = dao.insert(addon("https://site.example/", type = AddonType.WEB_SITE))
        dao.insert(addon("https://site2.example/", type = AddonType.WEB_SITE))

        assertEquals(2, dao.countOfType(AddonType.WEB_SITE))
        assertEquals(1, dao.countOfType(AddonType.NUVIO_ADDON))
        assertEquals(1, dao.enabledOfType(AddonType.NUVIO_ADDON).size) // web siteleri kaynak toplamaya girmez
        assertEquals(2, dao.observeOfType(AddonType.WEB_SITE).first().size)

        dao.delete(site)
        assertEquals(1, dao.countOfType(AddonType.WEB_SITE))
    }
}
