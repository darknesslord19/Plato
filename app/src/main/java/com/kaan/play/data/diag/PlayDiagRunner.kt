package com.kaan.play.data.diag

import android.content.Context
import android.util.Log
import com.kaan.play.AppContainer
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamSource
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PlayDiag"

object PlayDiagRunner {

    data class DiagResult(
        val pluginName: String,
        val catalogStatus: String,
        val loadStatus: String,
        val episodeStatus: String,
        val linkStatus: String,
        val mediaStatus: String,
        val errorReason: String,
    )

    suspend fun runDiagnostics(
        context: Context,
        container: AppContainer,
        filterNames: List<String>? = null,
    ): File = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== CloudStream Oynatma Tanısı Başlatıldı ===")
        val defaultRepos = listOf(
            "https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json",
            "https://raw.githubusercontent.com/Wiojelt/WioSinema/builds/repo.json",
            "https://raw.githubusercontent.com/csprofesor/gsrepo/builds/repo.json",
        )
        for (repoUrl in defaultRepos) {
            try {
                if (container.database.addonDao().countBySourceUrl(repoUrl) == 0) {
                    Log.i(TAG, "Depo kuruluyor: $repoUrl")
                    container.sourceInstaller.add(repoUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Depo kurulumu atlandı: $repoUrl (${e.message})")
            }
        }
        runCatching {
            container.csRepos.setAllEnabled(true)
        }

        val allInstalled = runCatching {
            container.database.pluginDao().observeOfType(com.kaan.play.data.db.AddonType.CLOUDSTREAM_REPO).first().map {
                InstalledCsPlugin(
                    id = it.id,
                    repoId = it.repoId,
                    pluginId = it.pluginId,
                    name = it.name,
                    version = it.version,
                    description = it.description,
                    language = it.contentLanguage,
                    tvTypes = it.supportedTypes.split(',').filter { s -> s.isNotBlank() },
                    enabled = it.enabled,
                )
            }
        }.getOrDefault(emptyList())

        val allPlugins = if (!filterNames.isNullOrEmpty()) {
            allInstalled
        } else {
            val enabled = container.csRepos.enabledPlugins()
            if (enabled.isNotEmpty()) enabled else allInstalled
        }

        val repoMap = runCatching {
            container.database.addonDao().enabledOfType(com.kaan.play.data.db.AddonType.CLOUDSTREAM_REPO).associate { it.id to it.name }
        }.getOrDefault(emptyMap())
        Log.i(TAG, "CS Depoları: $repoMap")

        val preferredRepos = mapOf(
            "AnimeciX" to "gsrepo",
            "Anizm" to "gsrepo",
            "BelgeselX" to "gsrepo",
            "DiziGom" to "gsrepo",
            "DiziKorea" to "gsrepo",
            "FullHDFilm" to "gsrepo",
            "SetFilmIzle" to "gsrepo",
            "SinemaCX" to "gsrepo",
            "TavsiyeFilmIzle" to "gsrepo",
            "SinemaTV" to "WioSinema",
            "Watch2Movies" to "gsrepo",
        )

        val targetPlugins = if (!filterNames.isNullOrEmpty()) {
            filterNames.mapNotNull { f ->
                val candidates = allPlugins.filter { p -> p.name.contains(f, ignoreCase = true) || p.pluginId.contains(f, ignoreCase = true) }
                if (candidates.isEmpty()) null
                else {
                    val prefKey = preferredRepos.keys.firstOrNull { it.contains(f, ignoreCase = true) || f.contains(it, ignoreCase = true) }
                    val targetRepo = if (prefKey != null) preferredRepos[prefKey] else "TurkSinema"
                    candidates.firstOrNull { repoMap[it.repoId]?.contains(targetRepo ?: "", ignoreCase = true) == true }
                        ?: candidates.firstOrNull { repoMap[it.repoId]?.contains("TurkSinema", ignoreCase = true) == true }
                        ?: candidates.first()
                }
            }
        } else {

            val standardNames = listOf(
                "AnimeciX", "Anizm", "BelgeselX", "DDizi", "DiziBox",
                "DiziGom", "DiziKorea", "Dizilla", "DiziMag", "DiziMom",
                "DiziPal", "FilmMakinesi", "FilmModu", "FullHDFilm", "HDFilmCehennemi",
                "HDFilmIzle", "HintFilmIzle", "JetFilmizle", "KultFilmler", "SelcukFlix",
                "SetFilmIzle", "SezonlukDizi", "SinemaCX", "SinemaTV", "SineWix",
                "TavsiyeFilmIzle", "Watch2Movies", "WebteIzle", "YabanciDizi"
            )
            standardNames.mapNotNull { stdName ->
                val candidates = allPlugins.filter { it.name.equals(stdName, ignoreCase = true) }
                if (candidates.isEmpty()) null
                else {
                    val targetRepo = preferredRepos[stdName] ?: "TurkSinema"
                    candidates.firstOrNull { repoMap[it.repoId]?.contains(targetRepo, ignoreCase = true) == true }
                        ?: candidates.firstOrNull { repoMap[it.repoId]?.contains("TurkSinema", ignoreCase = true) == true }
                        ?: candidates.first()
                }
            }
        }

        Log.i(TAG, "Test edilecek eklenti sayısı: ${targetPlugins.size}")

        val results = targetPlugins.map { plugin ->
            val res = testPlugin(container, plugin)
            kotlinx.coroutines.delay(500)
            res
        }

        val outDir = File(context.filesDir, "diag")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "oynatma-tanisi.md")

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val md = buildString {
            appendLine("# CloudStream Oynatma Tanısı")
            appendLine()
            appendLine("- **Tarih:** $timestamp")
            appendLine("- **Test Edilen Eklenti Sayısı:** ${results.size}")
            appendLine()
            appendLine("| Eklenti | Katalog | Load | Bölüm | Link | Çalışan Medya | Hata/Neden |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            for (r in results) {
                appendLine("| ${r.pluginName} | ${r.catalogStatus} | ${r.loadStatus} | ${r.episodeStatus} | ${r.linkStatus} | ${r.mediaStatus} | ${r.errorReason} |")
            }
        }

        outFile.writeText(md)
        Log.i(TAG, "=== CloudStream Oynatma Tanısı Tamamlandı ===")
        Log.i(TAG, "Dosya: ${outFile.absolutePath}")
        Log.i(TAG, "DIAGNOSTIC_COMPLETED: ${outFile.absolutePath}")
        outFile
    }

    private suspend fun testPlugin(
        container: AppContainer,
        plugin: InstalledCsPlugin,
    ): DiagResult {
        Log.i(TAG, "[${plugin.name}] Test başlıyor...")
        val apis = withTimeoutOrNull(12_000L) {
            runCatching { container.csRepos.loadApis(plugin.id) }.getOrNull()
        }

        if (apis.isNullOrEmpty()) {
            Log.w(TAG, "[${plugin.name}] DEX veya API yüklenemedi")
            return DiagResult(
                pluginName = plugin.name,
                catalogStatus = "-",
                loadStatus = "-",
                episodeStatus = "-",
                linkStatus = "-",
                mediaStatus = "-",
                errorReason = "DEX/API yüklenemedi",
            )
        }

        val api = apis.first()

        // 1. Katalog (mainPage)
        val sections = runCatching {
            container.csRuntime.mainPage(api, 1, timeoutMs = 6_000L)
        }.getOrNull() ?: emptyList()

        val allItems = sections.flatMap { it.items }.distinctBy { it.url.ifBlank { it.name } }
        if (allItems.isEmpty()) {
            val cfBlocked = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.wasBlocked(api.mainUrl)
            val reason = if (cfBlocked) "Cloudflare engeli / Turnstile" else "Katalog içeriği boş"
            Log.w(TAG, "[${plugin.name}] Katalog boş: $reason")
            return DiagResult(
                pluginName = plugin.name,
                catalogStatus = "X (0 içerik)",
                loadStatus = "-",
                episodeStatus = "-",
                linkStatus = "-",
                mediaStatus = "-",
                errorReason = reason,
            )
        }

        val catalogStatus = "OK (${sections.size} satır, ${allItems.size} içerik)"
        Log.i(TAG, "[${plugin.name}] Katalog başarılı: $catalogStatus")

        // 2. İlk 3 içeriği test et
        val testItems = allItems.take(3)
        var lastResult: DiagResult? = null

        for (item in testItems) {
            val itemTitle = item.name
            Log.i(TAG, "[${plugin.name}] İçerik deneniyor: $itemTitle (${item.url})")

            var loadErr: Throwable? = null
            val loadResp = withTimeoutOrNull(15_000L) {
                try {
                    api.load(item.url)
                } catch (t: Throwable) {
                    loadErr = t
                    null
                }
            }

            if (loadResp == null) {
                Log.w(TAG, "[${plugin.name}] load($itemTitle) başarısız -> ${loadErr?.javaClass?.name}: ${loadErr?.message}", loadErr)
                lastResult = DiagResult(
                    pluginName = plugin.name,
                    catalogStatus = catalogStatus,
                    loadStatus = "X (${itemTitle.take(20)})",
                    episodeStatus = "-",
                    linkStatus = "-",
                    mediaStatus = "-",
                    errorReason = "load() hata: ${loadErr?.javaClass?.simpleName ?: "boş/zaman aşımı"}",
                )
                continue
            }

            val loadStatus = "OK (${loadResp.name.take(20)})"

            // Bölüm / dataUrl çöz
            val (dataUrl, episodeLabel) = resolveDataUrl(loadResp)
            if (dataUrl.isNullOrBlank()) {
                Log.w(TAG, "[${plugin.name}] dataUrl çözülemedi: $episodeLabel")
                lastResult = DiagResult(
                    pluginName = plugin.name,
                    catalogStatus = catalogStatus,
                    loadStatus = loadStatus,
                    episodeStatus = episodeLabel,
                    linkStatus = "-",
                    mediaStatus = "-",
                    errorReason = "Bölüm/dataUrl bulunamadı",
                )
                continue
            }

            val episodeStatus = "OK ($episodeLabel)"

            // 3. Link çıkarımı (loadLinks)
            val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
            val streams = withTimeoutOrNull(20_000L) {
                runCatching { container.csRuntime.loadLinksDirect(api, dataUrl, source, 18_000L) }.getOrNull()
            } ?: emptyList()

            if (streams.isEmpty()) {
                Log.w(TAG, "[${plugin.name}] loadLinks link bulamadı")
                if (lastResult == null || lastResult.linkStatus.startsWith("X")) {
                    lastResult = DiagResult(
                        pluginName = plugin.name,
                        catalogStatus = catalogStatus,
                        loadStatus = loadStatus,
                        episodeStatus = episodeStatus,
                        linkStatus = "X (0 link)",
                        mediaStatus = "-",
                        errorReason = "Extractor link çıkaramadı",
                    )
                }
                continue
            }

            val firstStream = streams.first()
            val qualityStr = firstStream.resolution?.let { "${it}p" } ?: "?"
            val linkStatus = "OK (${streams.size} link, $qualityStr)"

            // 4. Doğrudan link medya testi (HEAD / 64KB GET) - streams içindeki linklerden çalışan var mı dene
            var mediaOk = false
            var probeMsg = ""
            for (st in streams.take(3)) {
                val (ok, msg) = probeMedia(container.httpClient, st)
                probeMsg = msg
                if (ok) {
                    mediaOk = true
                    break
                }
            }
            val mediaStatus = if (mediaOk) "OK ($probeMsg)" else "X ($probeMsg)"
            val errorReason = if (mediaOk) "-" else "Medya doğrulanamadı: $probeMsg"

            Log.i(TAG, "[${plugin.name}] Medya sonucu: $mediaStatus ($errorReason)")

            val result = DiagResult(
                pluginName = plugin.name,
                catalogStatus = catalogStatus,
                loadStatus = loadStatus,
                episodeStatus = episodeStatus,
                linkStatus = linkStatus,
                mediaStatus = mediaStatus,
                errorReason = errorReason,
            )

            if (mediaOk) {
                return result
            } else {
                if (lastResult == null || lastResult.linkStatus.startsWith("X")) {
                    lastResult = result
                }
            }
        }

        return lastResult ?: DiagResult(
            pluginName = plugin.name,
            catalogStatus = catalogStatus,
            loadStatus = "X",
            episodeStatus = "-",
            linkStatus = "-",
            mediaStatus = "-",
            errorReason = "İçerik testleri başarısız",
        )
    }

    private fun resolveDataUrl(loadResp: com.lagradost.cloudstream3.LoadResponse): Pair<String?, String> {
        return when (loadResp) {
            is MovieLoadResponse -> loadResp.dataUrl to "Film"
            is LiveStreamLoadResponse -> loadResp.dataUrl to "Canlı Yayın"
            is TvSeriesLoadResponse -> {
                val ep = loadResp.episodes.firstOrNull()
                val label = if (ep != null) "S${ep.season ?: 1}E${ep.episode ?: 1}" else "Bölüm yok"
                ep?.data to label
            }
            is AnimeLoadResponse -> {
                val ep = loadResp.episodes.values.flatten().firstOrNull()
                val label = if (ep != null) "Bölüm ${ep.episode ?: 1}" else "Bölüm yok"
                ep?.data to label
            }
            else -> null to "Bilinmeyen tür"
        }
    }

    private suspend fun probeMedia(
        httpClient: OkHttpClient,
        stream: PlayableStream,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // İlk ~512 KB indir (524288 bayt) ve medya imzasını kontrol et
        val maxBytes = 512 * 1024
        try {
            val isM3u8 = stream.url.contains(".m3u8", ignoreCase = true)
            fun buildReq(withRange: Boolean): Request {
                val b = Request.Builder().url(stream.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                stream.headers.forEach { (k, v) -> b.header(k, v) }
                if (withRange) b.header("Range", "bytes=0-${maxBytes - 1}")
                return b.get().build()
            }
            var getResp = httpClient.newCall(buildReq(withRange = !isM3u8)).execute()
            if (getResp.code in listOf(400, 416) && !isM3u8) {
                getResp.close()
                getResp = httpClient.newCall(buildReq(withRange = false)).execute()
            }
            val code = getResp.code
            val body = getResp.body
            var bytesRead = 0
            val buffer = ByteArray(maxBytes)
            if (body != null) {
                body.byteStream().use { input ->
                    while (bytesRead < maxBytes) {
                        val r = input.read(buffer, bytesRead, maxBytes - bytesRead)
                        if (r == -1) break
                        bytesRead += r
                    }
                }
            }
            getResp.close()

            Log.d(TAG, "probeMedia [${stream.source.addonName}]: url=${stream.url} code=$code bytes=$bytesRead headers=${stream.headers}")
            if (code !in 200..299 && code != 206) {
                return@withContext false to "HTTP $code"
            }

            val signature = detectMediaSignature(buffer, bytesRead)
            if (signature != null) {
                val kb = (bytesRead + 1023) / 1024
                return@withContext true to "$signature ${kb}KB"
            } else {
                return@withContext false to "Geçersiz imza (HTTP $code, ${bytesRead / 1024}KB)"
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "probeMedia [${stream.source.addonName}] GET istisna: $msg (url=${stream.url})")
            return@withContext false to msg.take(40)
        }
    }

    private fun detectMediaSignature(bytes: ByteArray, length: Int): String? {
        if (length < 4) return null

        // 1. M3U8 / HLS playlist
        val headText = String(bytes, 0, minOf(length, 1024), Charsets.ISO_8859_1)
        if (headText.contains("#EXTM3U") || headText.contains("#EXT-X-STREAM-INF") || headText.contains("#EXTINF")) {
            return "M3U8"
        }

        // 2. MKV / WebM (EBML: 0x1A 0x45 0xDF 0xA3)
        if (length >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            return "MKV"
        }

        // 3. MP4 (box header: 4 bayt boyut + "ftyp")
        if (length >= 8) {
            val scanLimit = minOf(length - 4, 64)
            for (i in 0..scanLimit) {
                if (bytes[i] == 'f'.code.toByte() &&
                    bytes[i + 1] == 't'.code.toByte() &&
                    bytes[i + 2] == 'y'.code.toByte() &&
                    bytes[i + 3] == 'p'.code.toByte()
                ) {
                    return "MP4"
                }
            }
        }

        // 4. MPEG-TS (senkronizasyon baytı 0x47, 188 bayt aralıklarla)
        if (length >= 188 && bytes[0] == 0x47.toByte()) {
            if (length < 376 || bytes[188] == 0x47.toByte()) {
                return "TS"
            }
        }

        return null
    }
}
