package com.kaan.play.data.cloudstream

import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCredentials
import com.kaan.play.data.cloudstream.cloudflare.CloudflareDetector
import com.lagradost.cloudstream3.CloudflareKiller
import com.lagradost.cloudstream3.CloudflareRetryTag
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CloudflareTest {

    @Before
    fun setUp() {
        CloudflareCache.clear()
    }

    @After
    fun tearDown() {
        CloudflareCache.clear()
    }

    // 1. Challenge tespiti testleri
    @Test
    fun testChallengeDetectionWithCfMitigatedHeader() {
        val headers = Headers.Builder()
            .add("cf-mitigated", "challenge")
            .build()
        assertTrue(CloudflareDetector.isChallenge(403, headers, null))
    }

    @Test
    fun testChallengeDetectionWithCloudflareServerAndBody() {
        val headers = Headers.Builder()
            .add("server", "cloudflare")
            .build()
        val body = "<html><head><title>Just a moment...</title></head><body>challenges.cloudflare.com</body></html>"
        assertTrue(CloudflareDetector.isChallenge(503, headers, body))
    }

    @Test
    fun testChallengeDetectionWithCfRayAndTurnstile() {
        val headers = Headers.Builder()
            .add("cf-ray", "8f123456789abc")
            .build()
        val body = "<html><body><div class=\"cf-turnstile\"></div></body></html>"
        assertTrue(CloudflareDetector.isChallenge(429, headers, body))
    }

    @Test
    fun testNonChallengeResponses() {
        // 200 OK hiçbir zaman challenge değildir
        val okHeaders = Headers.Builder().add("server", "cloudflare").build()
        assertFalse(CloudflareDetector.isChallenge(200, okHeaders, "challenges.cloudflare.com"))

        // 404 Cloudflare başlığı olmadan challenge değildir
        val notFoundHeaders = Headers.Builder().build()
        assertFalse(CloudflareDetector.isChallenge(404, notFoundHeaders, "Not found"))

        // Standart 403 (CF başlığı ve gövdesi yok) challenge değildir
        val forbiddenHeaders = Headers.Builder().add("server", "nginx").build()
        assertFalse(CloudflareDetector.isChallenge(403, forbiddenHeaders, "Forbidden"))
    }

    @Test
    fun testInteractiveChallengeDetection() {
        val interactiveHtml = "<div><div class=\"cf-turnstile\" data-sitekey=\"xxx\"></div></div>"
        assertTrue(CloudflareDetector.isInteractiveChallenge(interactiveHtml))

        val normalHtml = "<html><head><title>Site</title></head><body><h1>Hoş geldiniz</h1></body></html>"
        assertFalse(CloudflareDetector.isInteractiveChallenge(normalHtml))
    }

    // 2. Çerez ve alan adı izolasyonu testleri
    @Test
    fun testDomainIsolation() {
        val credsA = CloudflareCredentials(
            cookies = mapOf("cf_clearance" to "token_site_a"),
            userAgent = "UA_A",
        )
        CloudflareCache.put("https://www.hdfilmcehennemi.nl/film/1", credsA)

        // Aynı alan adının farklı URL veya alt adımlarında çerez döner
        val fetchedSame = CloudflareCache.get("hdfilmcehennemi.nl")
        assertNotNull(fetchedSame)
        assertEquals("token_site_a", fetchedSame?.cookies?.get("cf_clearance"))
        assertEquals("cf_clearance=token_site_a", fetchedSame?.cookieHeader)

        // Başka alan adına kesinlikle çerez sızmaz
        assertNull(CloudflareCache.get("dizibox.tv"))
        assertNull(CloudflareCache.get("https://selcukflix.com"))
    }

    @Test
    fun testHostNormalization() {
        assertEquals("hdfilmcehennemi.nl", CloudflareCache.normalizeHost("https://www.hdfilmcehennemi.nl/kategori/film"))
        assertEquals("dizibox.tv", CloudflareCache.normalizeHost("http://dizibox.tv/"))
        assertEquals("example.com", CloudflareCache.normalizeHost("WWW.EXAMPLE.COM"))
    }

    @Test
    fun testCredentialsExpiration() {
        // Süresi geçmiş kimlik bilgisi
        val expiredCreds = CloudflareCredentials(
            cookies = mapOf("cf_clearance" to "old_token"),
            userAgent = "UA_OLD",
            expiresAt = System.currentTimeMillis() - 1000L,
        )
        assertTrue(expiredCreds.isExpired)

        CloudflareCache.put("expired-site.com", expiredCreds)
        assertNull("Süresi dolan çerez cache'den null dönmelidir", CloudflareCache.get("expired-site.com"))
    }

    // 3. Tekrar deneme sınırı (sonsuz döngü engeli) testi
    @Test
    fun testRetryLimitPreventsInfiniteLoop() {
        val killer = CloudflareKiller()
        val requestCount = AtomicInteger(0)

        // Her zaman 403 challenge dönen sahte bir ağ istemcisi
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(killer)
            .addInterceptor(Interceptor { chain ->
                requestCount.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .header("cf-mitigated", "challenge")
                    .body("challenges.cloudflare.com".toResponseBody())
                    .build()
            })
            .build()

        // İsteğe zaten CloudflareRetryTag eklenmişse Interceptor tekrar çözücü çalıştırmaz
        val reqWithRetryTag = Request.Builder()
            .url("https://blocked-site.com/api")
            .tag(CloudflareRetryTag::class.java, CloudflareRetryTag)
            .build()

        val resp = mockClient.newCall(reqWithRetryTag).execute()
        // Retry tag olduğu için interceptor tekrar çözmeye çalışmadan isteği tek seferde geçirmelidir
        assertEquals(1, requestCount.get())
        assertEquals(403, resp.code)
    }

    @Test
    fun testPosterArticleDomCompatibility() {
        val mockResponse = Response.Builder()
            .request(Request.Builder().url("https://www.dizibox.live/dizi-arsivi/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """
                <html>
                <body>
                  <article class="poster-article big-poster">
                    <a href="https://www.dizibox.live/diziler/test-dizi/" class="figure-link">
                      <figure class="figure big-cover">
                        <figcaption class="poster-caption">
                          <span class="archive" title="Test Dizi">Test Dizi</span>
                        </figcaption>
                        <img class="afis" data-src="https://www.dizibox.live/wp-content/uploads/afisler/test.jpg" />
                      </figure>
                    </a>
                  </article>
                </body>
                </html>
                """.trimIndent().toResponseBody()
            )
            .build()

        val niceResp = com.lagradost.nicehttp.NiceResponse(mockResponse)
        val doc = niceResp.document
        val articles = doc.select("article.article-series-poster")
        assertEquals(1, articles.size)
        val detailedArticles = doc.select("article.detailed-article")
        assertEquals(1, detailedArticles.size)
        val art = articles.first()!!
        val a = art.selectFirst("a[href]")
        assertNotNull(a)
        assertEquals("Test Dizi", a!!.attr("title"))
        assertEquals("https://www.dizibox.live/diziler/test-dizi/", a.attr("href"))
        val img = art.selectFirst("img")
        assertNotNull(img)
        assertEquals("https://www.dizibox.live/wp-content/uploads/afisler/test.jpg", img!!.attr("data-src"))
    }
}
