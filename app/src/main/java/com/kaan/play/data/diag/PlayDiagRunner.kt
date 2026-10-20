package com.kaan.play.data.diag

import android.content.Context
import android.util.Log
import com.kaan.play.AppContainer
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.toPlayable
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.MediaIdentityResolver
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.TitleCleaner
import com.kaan.play.data.subtitle.AddonSubtitleProvider
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleTrack
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val TAG = "PlayDiag"

object PlayDiagRunner {

    data class DiagResult(
        val pluginName: String,
        val catalogStatus: String,
        val loadStatus: String,
        val episodeStatus: String,
        val linkStatus: String,
        val mediaStatus: String,
        val subPluginStatus: String,
        val subOnlineStatus: String,
        val errorReason: String,
    )

    data class NuvioDiagResult(
        val name: String,
        val kind: String,
        val streamStatus: String,
        val mediaStatus: String,
        val subSourceStatus: String,
        val subAddonStatus: String,
        val subOnlineStatus: String,
        val errorReason: String,
    )

    suspend fun runDiagnostics(
        context: Context,
        container: AppContainer,
        filterNames: List<String>? = null,
    ): File = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== CloudStream ve Nuvio Oynatma & Altyazı Tanısı Başlatıldı ===")

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
        runCatching { container.csRepos.setAllEnabled(true) }

        // Nuvio eklenti ve altyazı addon'larını da aktif yap
        runCatching {
            container.database.addonDao().setAllEnabledOfType(AddonType.NUVIO_ADDON, true)
            container.database.addonDao().setAllEnabledOfType(AddonType.NUVIO_PLUGIN_REPO, true)
            container.database.pluginDao().setAllEnabled(true)
        }

        val allInstalled = runCatching {
            container.database.pluginDao().observeOfType(AddonType.CLOUDSTREAM_REPO).first().map {
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
            container.database.addonDao().enabledOfType(AddonType.CLOUDSTREAM_REPO).associate { it.id to it.name }
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

        Log.i(TAG, "Test edilecek CloudStream eklenti sayısı: ${targetPlugins.size}")

        val csResults = targetPlugins.map { plugin ->
            val res = testPlugin(container, plugin)
            kotlinx.coroutines.delay(500)
            res
        }

        // Nuvio tanı çalıştırması (filtre boşsa veya nuvio içeriyorsa)
        val runNuvio = filterNames == null || filterNames.any { it.contains("nuvio", ignoreCase = true) || it.contains("jasoelys", ignoreCase = true) }
        val nuvioResults = if (runNuvio) testNuvioSources(container) else emptyList()

        val outDir = File(context.filesDir, "diag")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "oynatma-tanisi.md")

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val md = buildString {
            appendLine("# CloudStream Oynatma ve Altyazı Tanısı")
            appendLine()
            appendLine("- **Tarih:** $timestamp")
            appendLine("- **Test Edilen Eklenti Sayısı:** ${csResults.size}")
            appendLine()
            appendLine("| Eklenti | Katalog | Load | Bölüm | Link | Çalışan Medya | Altyazı (eklenti) | Altyazı (internet) | Hata/Neden |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            for (r in csResults) {
                appendLine("| ${r.pluginName} | ${r.catalogStatus} | ${r.loadStatus} | ${r.episodeStatus} | ${r.linkStatus} | ${r.mediaStatus} | ${r.subPluginStatus} | ${r.subOnlineStatus} | ${r.errorReason} |")
            }

            if (nuvioResults.isNotEmpty()) {
                appendLine()
                appendLine("# Nuvio Oynatma ve Altyazı Tanısı")
                appendLine()
                appendLine("- **Tarih:** $timestamp")
                appendLine("- **Test Edilen Nuvio Kaynak Sayısı:** ${nuvioResults.size}")
                appendLine()
                appendLine("| Kaynak | Tür | Akış | Çalışan Medya | Altyazı (kaynak) | Altyazı Addon (TR) | İnternet Arama (TR) | Hata/Neden |")
                appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
                for (r in nuvioResults) {
                    appendLine("| ${r.name} | ${r.kind} | ${r.streamStatus} | ${r.mediaStatus} | ${r.subSourceStatus} | ${r.subAddonStatus} | ${r.subOnlineStatus} | ${r.errorReason} |")
                }
            }
        }

        outFile.writeText(md, Charsets.UTF_8)
        Log.i(TAG, "=== Oynatma Tanısı Tamamlandı ===")
        Log.i(TAG, "Dosya: ${outFile.absolutePath}")
        Log.i(TAG, "DIAGNOSTIC_COMPLETED: ${outFile.absolutePath}")
        outFile
    }

    private suspend fun testPlugin(
        container: AppContainer,
        plugin: InstalledCsPlugin,
    ): DiagResult {
        Log.i(TAG, "[${plugin.name}] Test başlıyor...")
        val apis = runCatching { container.csRepos.loadApis(plugin.id) }.getOrNull()
        if (apis.isNullOrEmpty()) {
            Log.w(TAG, "[${plugin.name}] Dex yüklenemedi / API bulunamadı")
            return DiagResult(
                pluginName = plugin.name,
                catalogStatus = "X (Yüklenemedi)",
                loadStatus = "-",
                episodeStatus = "-",
                linkStatus = "-",
                mediaStatus = "-",
                subPluginStatus = "-",
                subOnlineStatus = "-",
                errorReason = "Dex yükleme / API sınıfı bulunamadı",
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
                subPluginStatus = "-",
                subOnlineStatus = "-",
                errorReason = reason,
            )
        }

        val catalogStatus = "OK (${sections.size} satır, ${allItems.size} içerik)"
        Log.i(TAG, "[${plugin.name}] Katalog başarılı: $catalogStatus")

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
                    subPluginStatus = "-",
                    subOnlineStatus = "-",
                    errorReason = "load() hata: ${loadErr?.javaClass?.simpleName ?: "boş/zaman aşımı"}",
                )
                continue
            }

            val loadStatus = "OK (${loadResp.name.take(20)})"

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
                    subPluginStatus = "-",
                    subOnlineStatus = "-",
                    errorReason = "Bölüm/dataUrl bulunamadı",
                )
                continue
            }

            val episodeStatus = "OK ($episodeLabel)"

            // 3. Link ve Altyazı çıkarımı (loadLinks)
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
                        subPluginStatus = "-",
                        subOnlineStatus = "-",
                        errorReason = "Extractor link çıkaramadı",
                    )
                }
                continue
            }

            val firstStream = streams.first()
            val qualityStr = firstStream.resolution?.let { "${it}p" } ?: "?"
            val linkStatus = "OK (${streams.size} link, $qualityStr)"

            // 4. Doğrudan link medya testi (HEAD / ~512KB GET)
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

            // 5. Altyazı testi (eklenti altyazısı)
            val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.url }
            var subPluginStatus = "Yok (0)"
            if (allSubs.isNotEmpty()) {
                val trSub = allSubs.firstOrNull { it.language?.contains("tr", ignoreCase = true) == true || it.language?.contains("tur", ignoreCase = true) == true }
                val candidate = trSub ?: allSubs.first()
                val track = SubtitleTrack(
                    url = candidate.url,
                    language = candidate.language,
                    origin = plugin.name,
                    headers = candidate.headers,
                )
                val loadRes = container.subtitleLoader.load(track)
                subPluginStatus = when (loadRes) {
                    is SubtitleLoad.Loaded -> {
                        val langTag = (candidate.language ?: "TR").uppercase()
                        "OK (${allSubs.size} sub, $langTag ${loadRes.cues.size} satır)"
                    }
                    is SubtitleLoad.Failed -> "X (${allSubs.size} sub, ${loadRes.reason})"
                }
            }

            // 6. İnternetten altyazı arama testi
            var subOnlineStatus = "Yok (0)"
            try {
                val (cleanTitle, parsedYear) = TitleCleaner.cleanTitleAndYear(loadResp.name)
                val year = loadResp.year ?: parsedYear
                val isSeries = loadResp is TvSeriesLoadResponse || loadResp is AnimeLoadResponse
                val identity = MediaIdentityResolver.resolveIdentity(
                    rawTitle = loadResp.name,
                    rawYear = year,
                    syncData = runCatching { loadResp.syncData }.getOrNull(),
                    type = if (isSeries) MediaType.Tv else MediaType.Movie,
                    tmdb = container.tmdb,
                )
                val req = StreamRequest(
                    tmdbId = identity.tmdbId ?: 0,
                    imdbId = identity.imdbId,
                    type = if (isSeries) MediaType.Tv else MediaType.Movie,
                    season = 1,
                    episode = 1,
                    title = identity.cleanTitle,
                    year = identity.year,
                )
                val onlineTracks = withTimeoutOrNull(6_000L) {
                    container.subtitleProvider.subtitles(req)
                        .filterIsInstance<SubtitleResult.Found>()
                        .flatMapConcat { it.tracks.asFlow() }
                        .toList()
                } ?: emptyList()
                val trCount = onlineTracks.count { it.language?.equals("tr", ignoreCase = true) == true }
                subOnlineStatus = if (trCount > 0) {
                    "OK (TR $trCount)"
                } else if (onlineTracks.isNotEmpty()) {
                    "OK (${onlineTracks.size})"
                } else {
                    "Yok (0)"
                }
            } catch (t: Throwable) {
                subOnlineStatus = "X (${t.javaClass.simpleName})"
            }

            val result = DiagResult(
                pluginName = plugin.name,
                catalogStatus = catalogStatus,
                loadStatus = loadStatus,
                episodeStatus = episodeStatus,
                linkStatus = linkStatus,
                mediaStatus = mediaStatus,
                subPluginStatus = subPluginStatus,
                subOnlineStatus = subOnlineStatus,
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
            subPluginStatus = "-",
            subOnlineStatus = "-",
            errorReason = "İçerik testleri başarısız",
        )
    }

    private suspend fun testNuvioSources(container: AppContainer): List<NuvioDiagResult> {
        val results = mutableListOf<NuvioDiagResult>()
        val testMovie = StreamRequest(
            tmdbId = 550,
            imdbId = "tt0137523",
            type = MediaType.Movie,
            title = "Fight Club",
            year = 1999,
        )
        val testSeries = StreamRequest(
            tmdbId = 1396,
            imdbId = "tt0903747",
            type = MediaType.Tv,
            season = 1,
            episode = 1,
            title = "Breaking Bad",
            year = 2008,
        )

        // 1. Altyazı Addon'ları testi (ALTYAZI, nexus)
        val addonSubProvider = AddonSubtitleProvider(
            addons = container.addons::enabledAddons,
            client = container.httpClient,
            timeout = { 6.seconds },
        )
        val addonSubTracks = withTimeoutOrNull(6_000L) {
            addonSubProvider.subtitles(testMovie)
                .filterIsInstance<SubtitleResult.Found>()
                .flatMapConcat { it.tracks.asFlow() }
                .toList()
        } ?: emptyList()
        val addonTrCount = addonSubTracks.count { it.language?.equals("tr", ignoreCase = true) == true }
        val subAddonStr = if (addonTrCount > 0) "OK (TR $addonTrCount)" else if (addonSubTracks.isNotEmpty()) "OK (${addonSubTracks.size})" else "Yok (0)"

        // İnternet arama testi
        val onlineTracks = withTimeoutOrNull(6_000L) {
            container.subtitleProvider.subtitles(testMovie)
                .filterIsInstance<SubtitleResult.Found>()
                .flatMapConcat { it.tracks.asFlow() }
                .toList()
        } ?: emptyList()
        val onlineTrCount = onlineTracks.count { it.language?.equals("tr", ignoreCase = true) == true }
        val subOnlineStr = if (onlineTrCount > 0) "OK (TR $onlineTrCount)" else if (onlineTracks.isNotEmpty()) "OK (${onlineTracks.size})" else "Yok (0)"

        // 2. Nuvio Video Addon'ları
        val allAddons = runCatching { container.addons.enabledAddons() }.getOrDefault(emptyList())
        val videoAddons = allAddons.filter { addon ->
            addon.manifest.resources.any { it.name.equals("stream", ignoreCase = true) }
        }

        for (addon in videoAddons) {
            Log.i(TAG, "[Nuvio Addon: ${addon.manifest.name}] Test başlıyor...")
            val req = if (addon.manifest.types.contains("series")) testSeries else testMovie
            val streamProvider = AddonStreamProvider(
                addons = { listOf(addon) },
                client = container.httpClient,
                timeout = { 10.seconds },
            )
            val streams = withTimeoutOrNull(12_000L) {
                streamProvider.streams(req).filterIsInstance<StreamResult.Found>()
                    .flatMapConcat { it.streams.asFlow() }
                    .toList()
            } ?: emptyList()

            var mediaOk = false
            var probeMsg = ""
            for (st in streams.take(2)) {
                val (ok, msg) = probeMedia(container.httpClient, st)
                probeMsg = msg
                if (ok) {
                    mediaOk = true
                    break
                }
            }

            val streamStatus = if (streams.isNotEmpty()) "OK (${streams.size} akış)" else "X (0 akış)"
            val mediaStatus = if (mediaOk) "OK ($probeMsg)" else if (streams.isNotEmpty()) "X ($probeMsg)" else "-"

            val subs = streams.flatMap { it.subtitles }
            val subSourceStatus = if (subs.isNotEmpty()) {
                val candidate = subs.firstOrNull { it.language?.contains("tr", ignoreCase = true) == true } ?: subs.first()
                val loadRes = container.subtitleLoader.load(SubtitleTrack(candidate.url, candidate.language, addon.manifest.name, headers = candidate.headers))
                if (loadRes is SubtitleLoad.Loaded) "OK (${subs.size} sub, TR ${loadRes.cues.size} satır)" else "X (${subs.size} sub)"
            } else "Yok (0)"

            results += NuvioDiagResult(
                name = addon.manifest.name,
                kind = "Video Addon",
                streamStatus = streamStatus,
                mediaStatus = mediaStatus,
                subSourceStatus = subSourceStatus,
                subAddonStatus = subAddonStr,
                subOnlineStatus = subOnlineStr,
                errorReason = if (mediaOk) "-" else if (streams.isEmpty()) "Akış bulunamadı" else probeMsg,
            )
        }

        // 3. Nuvio JS Plugins (Scrapers)
        val plugins = runCatching { container.plugins.enabledPlugins() }.getOrDefault(emptyList())

        for (plugin in plugins) {
            Log.i(TAG, "[Nuvio Scraper: ${plugin.name}] Test başlıyor...")
            val req = if (plugin.pluginId.contains("anime", ignoreCase = true) || plugin.pluginId.contains("aniz", ignoreCase = true)) {
                StreamRequest(
                    tmdbId = 1399,
                    imdbId = "tt0877057",
                    type = MediaType.Tv,
                    season = 1,
                    episode = 1,
                    title = "Death Note",
                    year = 2006,
                )
            } else if (plugin.supportedTypes.contains("tv") || plugin.supportedTypes.contains("series")) {
                testSeries
            } else {
                testMovie
            }

            val code = runCatching { container.plugins.loadCode(plugin.id) }.getOrNull()
            if (code.isNullOrBlank()) {
                results += NuvioDiagResult(
                    name = plugin.name,
                    kind = "JS Scraper",
                    streamStatus = "X (Kod yok)",
                    mediaStatus = "-",
                    subSourceStatus = "-",
                    subAddonStatus = subAddonStr,
                    subOnlineStatus = subOnlineStr,
                    errorReason = "Betik dosyası okunamadı",
                )
                continue
            }

            val savedSettings = container.pluginSettings.get(plugin.id)
            val pluginRequest = PluginRequest(
                tmdbId = req.tmdbId.toString(),
                mediaType = if (req.type == MediaType.Movie) "movie" else "tv",
                season = req.season,
                episode = req.episode,
            )
            val outcome = withTimeoutOrNull(15_000L) {
                container.pluginRuntime.run(code, pluginRequest, plugin.pluginId, timeoutMs = 12_000L, savedSettings)
            }

            if (outcome == null || outcome !is PluginOutcome.Success) {
                val err = (outcome as? PluginOutcome.Failure)?.message ?: "Zaman aşımı / hata"
                results += NuvioDiagResult(
                    name = plugin.name,
                    kind = "JS Scraper",
                    streamStatus = "X (Hata)",
                    mediaStatus = "-",
                    subSourceStatus = "-",
                    subAddonStatus = subAddonStr,
                    subOnlineStatus = subOnlineStr,
                    errorReason = err.take(40),
                )
                continue
            }

            val source = StreamSource(-plugin.id, plugin.name)
            val playableStreams = outcome.streams.map { it.toPlayable(source) }
            val streamStatus = if (playableStreams.isNotEmpty()) "OK (${playableStreams.size} akış)" else "X (0 akış)"

            var mediaOk = false
            var probeMsg = ""
            for (st in playableStreams.take(2)) {
                val (ok, msg) = probeMedia(container.httpClient, st)
                probeMsg = msg
                if (ok) {
                    mediaOk = true
                    break
                }
            }
            val mediaStatus = if (mediaOk) "OK ($probeMsg)" else if (playableStreams.isNotEmpty()) "X ($probeMsg)" else "-"

            val subs = playableStreams.flatMap { it.subtitles }
            val subSourceStatus = if (subs.isNotEmpty()) {
                val candidate = subs.firstOrNull { it.language?.contains("tr", ignoreCase = true) == true } ?: subs.first()
                val loadRes = container.subtitleLoader.load(SubtitleTrack(candidate.url, candidate.language, plugin.name, headers = candidate.headers))
                if (loadRes is SubtitleLoad.Loaded) "OK (${subs.size} sub, TR ${loadRes.cues.size} satır)" else "X (${subs.size} sub)"
            } else "Yok (0)"

            results += NuvioDiagResult(
                name = plugin.name,
                kind = "JS Scraper",
                streamStatus = streamStatus,
                mediaStatus = mediaStatus,
                subSourceStatus = subSourceStatus,
                subAddonStatus = subAddonStr,
                subOnlineStatus = subOnlineStr,
                errorReason = if (mediaOk) "-" else if (playableStreams.isEmpty()) "Akış bulunamadı" else probeMsg,
            )
        }

        return results
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

        val headText = String(bytes, 0, minOf(length, 1024), Charsets.ISO_8859_1)
        if (headText.contains("#EXTM3U") || headText.contains("#EXT-X-STREAM-INF") || headText.contains("#EXTINF")) {
            return "M3U8"
        }

        if (length >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            return "MKV"
        }

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

        if (length >= 188 && bytes[0] == 0x47.toByte()) {
            if (length < 376 || bytes[188] == 0x47.toByte()) {
                return "TS"
            }
        }

        return null
    }
}
