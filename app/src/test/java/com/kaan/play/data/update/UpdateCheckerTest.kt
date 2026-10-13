package com.kaan.play.data.update

import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateRulesTest {

    @Test
    fun `daha yuksek surum yeni sayilir`() {
        assertTrue(UpdateRules.isNewer("v1.0.1", "1.0.0"))
        assertTrue(UpdateRules.isNewer("v1.1.0", "1.0.9"))
        assertTrue(UpdateRules.isNewer("v2.0.0", "1.9.9"))
        assertTrue(UpdateRules.isNewer("1.0.1", "1.0.0")) // "v" olmasa da
        assertTrue(UpdateRules.isNewer("V1.0.1", "1.0.0")) // büyük V
    }

    @Test
    fun `sayilar bilesen bilesen karsilastirilir metin gibi degil`() {
        assertTrue(UpdateRules.isNewer("v1.10.0", "1.9.0")) // metinde "1.10" < "1.9" olurdu
        assertFalse(UpdateRules.isNewer("v1.9.0", "1.10.0"))
        assertTrue(UpdateRules.isNewer("v10.0.0", "9.0.0"))
    }

    @Test
    fun `esit ya da eski surum yeni sayilmaz`() {
        assertFalse(UpdateRules.isNewer("v1.0.0", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v0.9.9", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v1.0", "1.0.0")) // eksik bileşen 0 sayılır
        assertFalse(UpdateRules.isNewer("v1.0.0.0", "1.0.0"))
        assertTrue(UpdateRules.isNewer("v1.0.0.1", "1.0.0"))
    }

    @Test
    fun `on surum etiketi yuklu kararli surumu eskitmez`() {
        // Yüklü 1.0.0, GitHub'daki son etiket v1.0.0-beta: bu bir güncelleme değil.
        assertFalse(UpdateRules.isNewer("v1.0.0-beta", "1.0.0"))
        // Ama daha yüksek numaralı ön sürüm haber verilir.
        assertTrue(UpdateRules.isNewer("v1.0.1-beta", "1.0.0"))
    }

    @Test
    fun `yuklu on surum icin ayni numarali kararli surum yenidir`() {
        assertTrue(UpdateRules.isNewer("v1.0.0", "1.0.0-beta"))
        assertFalse(UpdateRules.isNewer("v1.0.0-beta", "1.0.0-beta"))
        assertFalse(UpdateRules.isNewer("v1.0.0-rc1", "1.0.0-beta")) // ikisi de ön sürüm: sayılar eşit, haber verme
    }

    @Test
    fun `build metadata yok sayilir`() {
        assertFalse(UpdateRules.isNewer("v1.0.0+7", "1.0.0"))
        assertTrue(UpdateRules.isNewer("v1.0.1+7", "1.0.0"))
    }

    @Test
    fun `ayristirilamayan etiket ya da surum adi false doner`() {
        assertFalse(UpdateRules.isNewer("", "1.0.0"))
        assertFalse(UpdateRules.isNewer("latest", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v1..2", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v-1.0.0", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v1.0.x", "1.0.0"))
        assertFalse(UpdateRules.isNewer("v99999999999.0.0", "1.0.0")) // Int taşması
        assertFalse(UpdateRules.isNewer("v2.0.0", "dev"))
        assertFalse(UpdateRules.isNewer("v2.0.0", ""))
    }

    @Test
    fun `yalnizca github dot com https sayfasi acilir`() {
        assertTrue(UpdateRules.isSafePage("https://github.com/liberta09/Play/releases/tag/v1.0.1"))
        assertFalse(UpdateRules.isSafePage("http://github.com/liberta09/Play/releases/tag/v1.0.1"))
        assertFalse(UpdateRules.isSafePage("https://evil.example/releases"))
        assertFalse(UpdateRules.isSafePage("https://github.com.evil.example/releases"))
        assertFalse(UpdateRules.isSafePage("https://evilgithub.com/releases"))
        assertFalse(UpdateRules.isSafePage("https://user.github.com/x"))
        assertFalse(UpdateRules.isSafePage("intent://github.com/#Intent;scheme=https;end"))
        assertFalse(UpdateRules.isSafePage("javascript:alert(1)"))
        assertFalse(UpdateRules.isSafePage("file:///sdcard/a.apk"))
        assertFalse(UpdateRules.isSafePage(""))
    }
}

/** Yerel sahte sunucuyla; gerçek GitHub'a dokunmaz. */
class UpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun checker(installed: String = "1.0.0", timeoutSeconds: Long = 8) = UpdateChecker(
        client = OkHttpClient(),
        json = PlayJson,
        installedVersion = installed,
        endpoint = server.url("/repos/liberta09/Play/releases/latest").toString(),
        timeoutSeconds = timeoutSeconds,
    )

    private fun reply(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    private fun release(
        tag: String = "v1.0.1",
        url: String = "https://github.com/liberta09/Play/releases/tag/v1.0.1",
        extra: String = "",
    ) = """{"tag_name":"$tag","html_url":"$url","name":"Play $tag","assets":[{"name":"a.apk"}]$extra}"""

    @Test
    fun `yeni surum varsa etiket ve sayfa adresi gelir`() = runTest {
        reply(release())
        val info = checker().fetch()
        assertEquals(UpdateInfo("v1.0.1", "https://github.com/liberta09/Play/releases/tag/v1.0.1"), info)
    }

    @Test
    fun `istek dogru yola Accept ve User-Agent basligiyla gider`() = runTest {
        reply(release())
        checker().fetch()
        val request = server.takeRequest()
        assertEquals("/repos/liberta09/Play/releases/latest", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertTrue(request.headers["User-Agent"]!!.isNotBlank())
    }

    @Test
    fun `ayni ya da eski surumde null`() = runTest {
        reply(release(tag = "v1.0.0"))
        assertNull(checker(installed = "1.0.0").fetch())
        reply(release(tag = "v0.9.0"))
        assertNull(checker(installed = "1.0.0").fetch())
    }

    @Test
    fun `gercek durum yuklu 1_0_0 son etiket v1_0_0-beta icin pencere yok`() = runTest {
        reply(release(tag = "v1.0.0-beta", url = "https://github.com/liberta09/Play/releases/tag/v1.0.0-beta"))
        assertNull(checker(installed = "1.0.0").fetch())
    }

    @Test
    fun `taslak ve on surum release gosterilmez`() = runTest {
        reply(release(extra = ""","draft":true"""))
        assertNull(checker().fetch())
        reply(release(extra = ""","prerelease":true"""))
        assertNull(checker().fetch())
    }

    @Test
    fun `HTTP hatalari ve oran siniri sessizce null`() = runTest {
        for (code in listOf(404, 403, 429, 500, 503)) {
            reply("""{"message":"x"}""", code)
            assertNull("kod $code", checker().fetch())
        }
    }

    @Test
    fun `bozuk ya da beklenmeyen govde hata atar ama checkOnce yutar`() = runTest {
        reply("<html>not json</html>")
        val c = checker()
        val scope = CoroutineScope(Dispatchers.IO)
        c.checkOnce(scope)
        awaitRequests(1)
        withContext(Dispatchers.Default) { Thread.sleep(300) } // yanıt işlenip hata yutulsun
        assertNull(c.available.value)
        // Aynı gövde fetch'te gerçekten hata fırlatır; checkOnce'ın onu yuttuğunu kanıtlar.
        reply("<html>not json</html>")
        val failed = runCatching { checker().fetch() }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `eksik alanlar ve guvensiz sayfa adresi null`() = runTest {
        reply("""{"assets":[]}""")
        assertNull(checker().fetch())
        reply("""{"tag_name":"v1.0.1"}""")
        assertNull(checker().fetch())
        reply(release(url = "https://evil.example/download.apk"))
        assertNull(checker().fetch())
        reply(release(url = "http://github.com/liberta09/Play/releases/tag/v1.0.1"))
        assertNull(checker().fetch())
    }

    @Test
    fun `cok buyuk govde reddedilir`() = runTest {
        reply(release(extra = ""","padding":"${"x".repeat(600 * 1024)}""""))
        assertNull(checker().fetch())
    }

    @Test
    fun `yavas sunucu zaman asimina ugrar ve checkOnce sessiz kalir`() = runTest {
        server.enqueue(MockResponse.Builder().body(release()).headersDelay(3, TimeUnit.SECONDS).build())
        val c = checker(timeoutSeconds = 1)
        val started = System.nanoTime()
        c.checkOnce(CoroutineScope(Dispatchers.IO))
        withContext(Dispatchers.Default) { awaitRequests(1) }
        // Zaman aşımı dolana kadar sonuç yok; dolunca da hata yutulur, pencere çıkmaz.
        withContext(Dispatchers.Default) { Thread.sleep(1500) }
        assertNull(c.available.value)
        assertTrue("zaman aşımı beklenenden uzun sürdü", (System.nanoTime() - started) / 1_000_000 < 4500)
    }

    @Test
    fun `ulasilamayan sunucu sessizce null`() = runTest {
        val dead = checker().also { server.close() }
        dead.checkOnce(CoroutineScope(Dispatchers.IO))
        withContext(Dispatchers.Default) { Thread.sleep(500) }
        assertNull(dead.available.value)
    }

    @Test
    fun `checkOnce oturumda yalnizca bir kez agi kullanir`() = runTest {
        reply(release())
        val c = checker()
        val scope = CoroutineScope(Dispatchers.IO)
        c.checkOnce(scope)
        val first = withContext(Dispatchers.Default) { withTimeout(5_000) { c.available.first { it != null } } }
        assertNotNull(first)
        c.checkOnce(scope) // ekran döndürme / etkinlik yeniden oluşması
        c.checkOnce(scope)
        withContext(Dispatchers.Default) { Thread.sleep(300) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `dismiss pencereyi kapatir ve ayni oturumda geri gelmez`() = runTest {
        reply(release())
        val c = checker()
        val scope = CoroutineScope(Dispatchers.IO)
        c.checkOnce(scope)
        withContext(Dispatchers.Default) { withTimeout(5_000) { c.available.first { it != null } } }
        c.dismiss()
        assertNull(c.available.value)
        reply(release())
        c.checkOnce(scope)
        withContext(Dispatchers.Default) { Thread.sleep(300) }
        assertNull(c.available.value)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `uzun etiket kesilir`() = runTest {
        reply(release(tag = "v1.0.1-" + "a".repeat(200)))
        val info = checker().fetch()
        assertNotNull(info)
        assertTrue(info!!.tag.length <= 24)
    }

    private suspend fun awaitRequests(count: Int) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (server.requestCount < count) Thread.sleep(20) }
    }
}
