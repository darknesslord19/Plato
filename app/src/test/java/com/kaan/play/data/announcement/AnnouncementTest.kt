package com.kaan.play.data.announcement

import java.io.File
import kotlinx.coroutines.CoroutineScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.update.ReleaseAsset
import com.kaan.play.data.update.UpdateChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnnouncementRulesTest {

    private val sampleAnnouncement = Announcement(
        id = "2026-10-21-1",
        title = "Önemli Duyuru",
        message = "Play 1.1.0 sürümüne hoş geldiniz!",
        link = "https://example.com/detay",
        minVersion = "1.1.0",
        maxVersion = "1.2.0",
        until = "2026-12-31",
    )

    @Test
    fun `sure gecmis ise duyuru secilmez`() {
        val expired = sampleAnnouncement.copy(until = "2026-10-20")
        assertTrue(AnnouncementRules.isExpired("2026-10-20", today = "2026-10-21"))
        assertFalse(AnnouncementRules.isExpired("2026-10-21", today = "2026-10-21"))
        assertFalse(AnnouncementRules.isExpired("2026-10-22", today = "2026-10-21"))
        assertFalse(AnnouncementRules.isExpired(null, today = "2026-10-21"))

        val picked = AnnouncementRules.pick(
            announcements = listOf(expired),
            installedVersion = "1.1.0",
            seenIds = emptySet(),
            today = "2026-10-21",
        )
        assertNull(picked)
    }

    @Test
    fun `surum disi ise duyuru secilmez`() {
        // minVersion'dan küçük
        val minHigher = sampleAnnouncement.copy(minVersion = "1.2.0", maxVersion = null)
        assertNull(AnnouncementRules.pick(listOf(minHigher), installedVersion = "1.1.0", seenIds = emptySet()))

        // maxVersion'dan büyük
        val maxLower = sampleAnnouncement.copy(minVersion = null, maxVersion = "1.0.4")
        assertNull(AnnouncementRules.pick(listOf(maxLower), installedVersion = "1.1.0", seenIds = emptySet()))
    }

    @Test
    fun `daha once gorulmus duyuru secilmez`() {
        val seen = sampleAnnouncement.copy(id = "already-seen-id")
        val picked = AnnouncementRules.pick(
            announcements = listOf(seen),
            installedVersion = "1.1.0",
            seenIds = setOf("already-seen-id"),
        )
        assertNull(picked)
    }

    @Test
    fun `link https degilse guvensizdir ve secilmez`() {
        assertFalse(AnnouncementRules.isSafeLink("http://example.com"))
        assertFalse(AnnouncementRules.isSafeLink("javascript:alert(1)"))
        assertFalse(AnnouncementRules.isSafeLink("ftp://example.com"))
        assertFalse(AnnouncementRules.isSafeLink("not-a-url"))
        assertTrue(AnnouncementRules.isSafeLink("https://example.com/page"))
        assertTrue(AnnouncementRules.isSafeLink("https://raw.githubusercontent.com/x"))

        val unsafeHttp = sampleAnnouncement.copy(link = "http://example.com/insecure")
        assertNull(AnnouncementRules.pick(listOf(unsafeHttp), installedVersion = "1.1.0", seenIds = emptySet()))

        val safeHttps = sampleAnnouncement.copy(link = "https://example.com/secure")
        assertNotNull(AnnouncementRules.pick(listOf(safeHttps), installedVersion = "1.1.0", seenIds = emptySet()))
    }

    @Test
    fun `bos liste durumunda null doner`() {
        val picked = AnnouncementRules.pick(
            announcements = emptyList(),
            installedVersion = "1.1.0",
            seenIds = emptySet(),
        )
        assertNull(picked)
    }

    @Test
    fun `bozuk JSON durumunda parse null doner`() {
        assertNull(AnnouncementRules.parse("bu bir json degildir"))
        assertNull(AnnouncementRules.parse("{ \"announcements\": [ { \"id\": } ] }"))
        assertNull(AnnouncementRules.parse(""))
    }

    @Test
    fun `gecerli JSON basariyla ayristirilir`() {
        val json = """
            {
              "announcements": [
                {
                  "id": "2026-10-21-1",
                  "title": "Başlık",
                  "message": "İçerik",
                  "link": "https://example.com",
                  "minVersion": "1.1.0",
                  "until": "2026-12-31"
                }
              ]
            }
        """.trimIndent()
        val list = AnnouncementRules.parse(json)
        assertNotNull(list)
        assertEquals(1, list?.size)
        assertEquals("2026-10-21-1", list?.first()?.id)
        assertEquals("Başlık", list?.first()?.title)
        assertEquals("İçerik", list?.first()?.message)
        assertEquals("https://example.com", list?.first()?.link)
        assertEquals("1.1.0", list?.first()?.minVersion)
        assertEquals("2026-12-31", list?.first()?.until)
    }

    @Test
    fun `birden cok duyurudan kriterlere uyan ilk duyuru secilir`() {
        val list = listOf(
            sampleAnnouncement.copy(id = "1", until = "2026-01-01"), // süresi geçmiş
            sampleAnnouncement.copy(id = "2", minVersion = "2.0.0"), // sürüm dışı
            sampleAnnouncement.copy(id = "3", title = "Hedef 1"), // uygun ilk duyuru
            sampleAnnouncement.copy(id = "4", title = "Hedef 2"), // uygun ama sonraki
        )
        val picked = AnnouncementRules.pick(list, installedVersion = "1.1.0", seenIds = emptySet(), today = "2026-10-21")
        assertNotNull(picked)
        assertEquals("3", picked?.id)
        assertEquals("Hedef 1", picked?.title)
    }
}

class AnnouncementCheckerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    private fun newSettings(scope: CoroutineScope): SettingsRepository =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "test_settings_${System.nanoTime()}.preferences_pb")
            }
        )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sunucudan gecerli duyuru basariyla cekilir ve dismiss ile goruldu kaydedilir`() = runTest {
        val settings = newSettings(backgroundScope)
        val body = """
            {
              "announcements": [
                {
                  "id": "test-announcement-1",
                  "title": "Test Başlık",
                  "message": "Test Mesaj",
                  "link": "https://example.com",
                  "until": "2026-12-31"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(body).build())

        val checker = AnnouncementChecker(
            client = OkHttpClient(),
            json = PlayJson,
            installedVersion = "1.1.0",
            settings = settings,
            endpoint = server.url("/announcements.json").toString(),
            today = { "2026-10-21" },
        )

        val seenBefore = settings.seenAnnouncementIds.first()
        assertTrue(seenBefore.isEmpty())

        val announcement = checker.fetch(seenBefore)
        assertNotNull(announcement)
        assertEquals("test-announcement-1", announcement?.id)

        checker.dismiss(announcement)
        val seenAfter = settings.seenAnnouncementIds.first()
        assertTrue(seenAfter.contains("test-announcement-1"))

        // Artık görüldüğü için ikinci kez çekildiğinde null döner
        server.enqueue(MockResponse.Builder().body(body).build())
        val secondFetch = checker.fetch(seenAfter)
        assertNull(secondFetch)
    }

    @Test
    fun `sunucu 404 ya da bozuk yanit verirse sessizce null doner`() = runTest {
        val settings = newSettings(backgroundScope)
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        val checker = AnnouncementChecker(
            client = OkHttpClient(),
            json = PlayJson,
            installedVersion = "1.1.0",
            settings = settings,
            endpoint = server.url("/announcements.json").toString(),
        )
        val res = checker.fetch(emptySet())
        assertNull(res)
    }

    @Test
    fun `yanit 64 KB limitini asarsa okunmaz ve null doner`() = runTest {
        val settings = newSettings(backgroundScope)
        val hugeBody = "x".repeat(65 * 1024)
        server.enqueue(MockResponse.Builder().body(Buffer().writeUtf8(hugeBody)).build())
        val checker = AnnouncementChecker(
            client = OkHttpClient(),
            json = PlayJson,
            installedVersion = "1.1.0",
            settings = settings,
            endpoint = server.url("/announcements.json").toString(),
        )
        val res = checker.fetch(emptySet())
        assertNull(res)
    }

    @Test
    fun `guncelleme mevcutsa duyuru gosterilmez`() = runTest {
        val settings = newSettings(backgroundScope)
        val body = """{"announcements": [{"id": "ann-1", "title": "A", "message": "B"}]}"""
        server.enqueue(MockResponse.Builder().body(body).build())

        // Sahte update checker: güncelleme var
        val updateChecker = UpdateChecker(
            client = OkHttpClient(),
            json = PlayJson,
            installedVersion = "1.0.0",
            endpoint = server.url("/latest").toString(),
        )

        val announcementChecker = AnnouncementChecker(
            client = OkHttpClient(),
            json = PlayJson,
            installedVersion = "1.0.0",
            settings = settings,
            endpoint = server.url("/announcements.json").toString(),
        )

        // UpdateChecker tamamlandı ve güncelleme mevcut
        val releaseJson = """{"tag_name":"v1.1.0","html_url":"https://github.com/liberta09/Play/releases/tag/v1.1.0","draft":false,"prerelease":false}"""
        server.enqueue(MockResponse.Builder().body(releaseJson).build())

        updateChecker.checkOnce(this)
        announcementChecker.checkOnce(this, updateChecker)

        // AnnouncementChecker güncelleme çıktığı için available olmamalıdır
        assertNull(announcementChecker.available.value)
    }
}
