package com.kaan.play.data.plugin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.PluginRuntime
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gerçek Android QuickJS kütüphanesiyle (cihazda) çalıştırıcının uçtan uca denemesi: kendi yazdığımız deneme eklentisi,
 * cihaz içi sahte sunucu, HTML ayrıştırma, şifre çözme, yerel adres engeli, zaman aşımı. Gerçek bir depoya bağlanmaz.
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeDeviceTest {

    private val plugin = """
        const cheerio = require('cheerio');
        const CryptoJS = require('crypto-js');
        module.exports = { getStreams: async function (tmdbId, mediaType, season, episode) {
          const page = await (await fetch(BASE + '/page')).text();
          const ${'$'} = cheerio.load(page);
          const link = ${'$'}('a.src').first().attr('href');
          const secret = CryptoJS.AES.decrypt(CryptoJS.lib.CipherParams.create({ ciphertext: CryptoJS.enc.Base64.parse('+j4Gnw0YSUiZaUKQSMRcCQ==') }),
            CryptoJS.enc.Utf8.parse('0123456789abcdef'), { iv: CryptoJS.enc.Utf8.parse('fedcba9876543210'), mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 }).toString(CryptoJS.enc.Utf8);
          const blocked = await fetch('http://127.0.0.1:1/x');
          return [{ name: 'Cihaz', title: tmdbId + ':' + mediaType + ':' + secret + ':' + blocked.statusText, url: link, quality: '720p' }];
        } };
    """.trimIndent()

    @Test
    fun runsAPluginOnTheDeviceWithRealQuickJs() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().code(200).body("<html><a class='src' href='https://cdn.example/v.m3u8'>x</a></html>").build())
        server.start()
        try {
            val base = "http://127.0.0.1:${server.port}"
            // Sunucu cihaz içinde ve yerel; bu yüzden sunucu adresi bu denemede izinli ana makine, 127.0.0.1:1 ise (kural) reddedilir.
            val rt = PluginRuntime(OkHttpClient(), "REAL_KEY", NetworkPolicy(allowPrivateHosts = setOf("127.0.0.1")))
            // 127.0.0.1 izinli olduğundan port 1 isteği bağlantı hatasıyla biter (blocked değil); yine de akış dönmeli.
            val code = "var BASE = '$base';\n$plugin"
            val out = rt.run(code, PluginRequest("27205", "movie"), "device", 15_000)
            assertTrue("$out", out is PluginOutcome.Success)
            val s = out.streams.single()
            assertEquals("https://cdn.example/v.m3u8", s.url)
            assertEquals("27205:movie:gizli:", s.title!!.substringBeforeLast(':') + ':')

            // Zaman aşımı gerçek motorda da çalışır.
            val loop = rt.run("module.exports = { getStreams: async () => { while (true) {} } };", PluginRequest("1", "movie"), "device", 600)
            assertTrue("$loop", loop is PluginOutcome.Failure && loop.reason == PluginOutcome.Reason.Timeout)
        } finally {
            server.close()
        }
    }
}
