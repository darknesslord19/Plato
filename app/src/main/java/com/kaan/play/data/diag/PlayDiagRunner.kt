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
        val allPlugins = container.csRepos.enabledPlugins().let { enabled ->
            if (enabled.isNotEmpty()) enabled else {
                // Eğer açık eklenti yoksa, kurulu tüm CS eklentilerini dene
                container.csRepos.plugins
                container.database.pluginDao().enabledOfType(com.kaan.play.data.db.AddonType.CLOUDSTREAM_REPO).map {
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
            }
        }

        val targetPlugins = if (!filterNames.isNullOrEmpty()) {
            allPlugins.filter { p -> filterNames.any { f -> p.name.contains(f, ignoreCase = true) || p.pluginId.contains(f, ignoreCase = true) } }
        } else {
            allPlugins
        }

        Log.i(TAG, "Test edilecek eklenti sayısı: ${targetPlugins.size}")

        val gate = Semaphore(3)
        val results = coroutineScope {
            targetPlugins.map { plugin ->
                async {
                    gate.withPermit {
                        testPlugin(container, plugin)
                    }
                }
            }.awaitAll()
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
            val linkStatus = "OK (${streams.size} link, ${firstStream.resolution ?: "auto"}p)"

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
        val requestBuilder = Request.Builder()
            .url(stream.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        stream.headers.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        // 1. Önce HEAD dene
        try {
            val headReq = requestBuilder.head().build()
            val headResp = httpClient.newCall(headReq).execute()
            val code = headResp.code
            headResp.close()
            if (code in 200..299) {
                return@withContext true to "HEAD $code"
            }
        } catch (_: Exception) {
            // HEAD reddedilirse GET ile dene
        }

        // 2. 64KB GET dene
        try {
            val getReq = requestBuilder.get()
                .header("Range", "bytes=0-65535")
                .build()
            val getResp = httpClient.newCall(getReq).execute()
            val code = getResp.code
            val body = getResp.body
            var bytesRead = 0
            if (body != null) {
                val buf = ByteArray(8192)
                body.byteStream().use { input ->
                    while (bytesRead < 65536) {
                        val r = input.read(buf, 0, (65536 - bytesRead).coerceAtMost(buf.size))
                        if (r == -1) break
                        bytesRead += r
                    }
                }
            }
            getResp.close()
            Log.d(TAG, "probeMedia [${stream.source.addonName}]: url=${stream.url} code=$code bytes=$bytesRead headers=${stream.headers}")
            if (code in 200..299 || code == 206) {
                val kb = bytesRead / 1024
                return@withContext true to "GET $code (${kb}KB)"
            } else {
                return@withContext false to "HTTP $code"
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "probeMedia [${stream.source.addonName}] GET istisna: $msg (url=${stream.url})")
            return@withContext false to msg.take(40)
        }
    }
}
