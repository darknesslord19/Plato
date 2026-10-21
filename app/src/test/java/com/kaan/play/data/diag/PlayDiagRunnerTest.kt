package com.kaan.play.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

class PlayDiagRunnerTest {

    @Test
    fun buildSmokeTestJson_producesValidJsonWithAllSections() {
        val cs = listOf(
            PlayDiagRunner.CsDiagResult(
                pluginName = "TestPlugin",
                catalogStatus = "OK (1 satır, 20 içerik)",
                loadStatus = "OK (Test Film)",
                episodeStatus = "OK (Film)",
                linkStatus = "OK (2 link, 1080p)",
                mediaStatus = "OK (M3U8 250KB)",
                subPluginStatus = "OK (TR 150 satır)",
                status = "OK",
                errorReason = "-",
            )
        )
        val addons = listOf(
            PlayDiagRunner.NuvioAddonDiagResult(
                name = "Torrentio",
                kind = "Video Addon",
                streamStatus = "OK (5 akış)",
                mediaStatus = "torrent, atlandı",
                subSourceStatus = "Yok (0)",
                status = "OK",
                errorReason = "-",
            )
        )
        val js = listOf(
            PlayDiagRunner.NuvioJsDiagResult(
                name = "Jasoelys",
                target = "Film (tt0137523)",
                streamStatus = "OK (3 akış)",
                mediaStatus = "OK (MP4 512KB)",
                subSourceStatus = "Yok (0)",
                status = "OK",
                errorReason = "-",
            )
        )
        val subs = listOf(
            PlayDiagRunner.SubtitleDiagResult(
                name = "AltyazıDB",
                target = "tt0137523",
                searchStatus = "OK (TR 2)",
                parseStatus = "OK (1140 satır)",
                status = "OK",
                errorReason = "-",
            )
        )
        val cont = PlayDiagRunner.ContinueDiagResult(
            name = "CsNextEpisodeFinder",
            target = "DiziBox S1B1",
            details = "OK (S1E2, dataUrl farklı)",
            status = "OK",
            errorReason = "-",
        )
        val updates = listOf(
            PlayDiagRunner.UpdateAnnounceDiagResult(
                name = "GitHub releases/latest",
                details = "OK (tag: v1.1.0)",
                status = "OK",
                errorReason = "-",
            ),
            PlayDiagRunner.UpdateAnnounceDiagResult(
                name = "announcements.json",
                details = "OK (1 duyuru)",
                status = "OK",
                errorReason = "-",
            )
        )

        val jsonStr = PlayDiagRunner.buildSmokeTestJson(
            versionName = "1.1.0",
            csResults = cs,
            addonResults = addons,
            jsResults = js,
            subResults = subs,
            continueResult = cont,
            updateResults = updates,
        )

        val parsed = Json.parseToJsonElement(jsonStr).jsonObject
        assertEquals("1.1.0", parsed["appVersion"]?.toString()?.replace("\"", ""))
        assertEquals(1, parsed["cloudstream"]?.jsonArray?.size)
        assertEquals(1, parsed["nuvio_addons"]?.jsonArray?.size)
        assertEquals(1, parsed["nuvio_js"]?.jsonArray?.size)
        assertEquals(1, parsed["subtitles"]?.jsonArray?.size)
        assertEquals(1, parsed["continue_watching"]?.jsonArray?.size)
        assertEquals(2, parsed["updates_and_announcements"]?.jsonArray?.size)

        // Güvenlik doğrulaması: JSON içinde token veya çerez kelimesi olmamalı
        assertFalse(jsonStr.contains("Bearer "))
        assertFalse(jsonStr.contains("cookie"))
    }

    @Test
    fun buildMarkdown_formatsAllSectionsAndSummary() {
        val cs = listOf(
            PlayDiagRunner.CsDiagResult(
                pluginName = "TestPlugin",
                catalogStatus = "OK",
                loadStatus = "OK",
                episodeStatus = "OK",
                linkStatus = "OK",
                mediaStatus = "OK",
                subPluginStatus = "OK",
                status = "OK",
                errorReason = "-",
            )
        )
        val md = PlayDiagRunner.buildMarkdown(
            versionName = "1.1.0",
            csResults = cs,
            durationStr = "1 dk 30 sn",
        )

        assertTrue(md.contains("# Play Otomatik Duman Testi (Smoke Test)"))
        assertTrue(md.contains("## Özet"))
        assertTrue(md.contains("1/1 oynatılabilir"))
        assertTrue(md.contains("## 1. CloudStream Eklentileri"))
        assertTrue(md.contains("## 2. Nuvio Addon'ları"))
        assertTrue(md.contains("## 3. Nuvio JS Eklentileri"))
        assertTrue(md.contains("## 4. Altyazı Sağlayıcıları"))
        assertTrue(md.contains("## 5. Devam Et (CsNextEpisodeFinder)"))
        assertTrue(md.contains("## 6. Güncelleme ve Duyuru Kontrolü"))
    }
}
