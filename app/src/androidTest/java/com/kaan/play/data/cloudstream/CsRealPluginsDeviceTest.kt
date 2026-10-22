package com.kaan.play.data.cloudstream

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaan.play.PlayApp
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Gerçek CloudStream eklentilerini cihazda (CI emülatöründe) Play'in kendi yükleyicisiyle çalıştırır ve rapor yazar:
 * her eklenti yüklendi mi, ana sayfanın ilk bölümü ve bir arama sonuç döndü mü, hata hangi sınıftan geldi.
 *
 * Yalnızca `cs_repos` cihaz testi argümanı verilince çalışır (ağa çıkar); normal cihaz testlerinde atlanır.
 * Rapor: `files/cs-device-report.json` (CI `run-as` ile okur). Eklentilerin başarısız olması testi
 * düşürmez: amaç ölçmek (site kapalı/bölge engeli ile "Play yükleyemedi" ayrımı rapordadır).
 */
@RunWith(AndroidJUnit4::class)
class CsRealPluginsDeviceTest {

    private val args = InstrumentationRegistry.getArguments()
    private val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(url: String): ByteArray = http.newCall(
        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 Play-CS-Device-Test").build(),
    ).execute().use { r ->
        check(r.isSuccessful) { "HTTP ${r.code}" }
        r.body.bytes()
    }

    private class Entry(val repo: String, val name: String, val url: String)

    private fun entries(repoUrl: String, limit: Int): List<Entry> {
        val repo = json.parseToJsonElement(get(repoUrl).decodeToString()).jsonObject
        val repoName = repo["name"]?.jsonPrimitive?.content ?: repoUrl
        val lists = repo["pluginLists"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        return lists.flatMap { list ->
            val listUrl = java.net.URI(repoUrl).resolve(list).toString()
            runCatching { json.parseToJsonElement(get(listUrl).decodeToString()).jsonArray }.getOrDefault(JsonArray(emptyList()))
                .map { it.jsonObject }
                .filter { (it["status"]?.jsonPrimitive?.content ?: "1") != "0" }
                .take(limit)
                .map { p ->
                    Entry(
                        repoName,
                        p["name"]?.jsonPrimitive?.content ?: "?",
                        java.net.URI(listUrl).resolve(p["url"]!!.jsonPrimitive.content).toString(),
                    )
                }
        }
    }

    private fun manifestOf(bytes: ByteArray): String? {
        ZipInputStream(bytes.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "manifest.json") return z.readBytes().decodeToString()
                e = z.nextEntry
            }
        }
        return null
    }

    private fun err(t: Throwable?): String? = t?.let { "${it.javaClass.name}: ${it.message?.lineSequence()?.firstOrNull()?.take(160)}" }

    @Test
    fun loadsRealPluginsAndReports() = runBlocking {
        val repos = args.getString("cs_repos")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        assumeTrue("cs_repos verilmedi; gerçek eklenti denemesi atlandı", repos.isNotEmpty())
        val limit = args.getString("cs_limit")?.toIntOrNull() ?: 15
        val query = args.getString("cs_query") ?: "a"

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (ctx.applicationContext as PlayApp).container
        val loader = container.csDexLoader
        val runtime = container.csRuntime
        val dir = File(ctx.filesDir, "cs-device-test").apply { deleteRecursively(); mkdirs() }

        val all = repos.flatMap { r -> runCatching { entries(r, limit) }.getOrElse { Log.w(TAG, "depo okunamadı $r: ${err(it)}"); emptyList() } }
        Log.i(TAG, "${all.size} eklenti denenecek")
        val gate = Semaphore(4)
        val results = all.mapIndexed { i, e ->
            async(Dispatchers.IO) {
                gate.withPermit { tryPlugin(i, e, dir, loader, runtime, query) }
                    .also { Log.i(TAG, it.toString()) }
            }
        }.awaitAll()

        val report = buildJsonObject {
            put("query", query)
            put("plugins", buildJsonArray { results.forEach { add(it) } })
        }
        val out = File(ctx.filesDir, "cs-device-report.json")
        out.writeText(report.toString())
        val counts = results.groupingBy { (it as JsonObject)["stage"]?.jsonPrimitive?.content }.eachCount()
        Log.i(TAG, "rapor: ${out.absolutePath} $counts")
    }

    private suspend fun tryPlugin(
        i: Int,
        e: Entry,
        dir: File,
        loader: CsDexLoader,
        runtime: CsRuntime,
        query: String,
    ): JsonObject {
        val r = linkedMapOf<String, Any?>("repo" to e.repo, "name" to e.name)
        fun done(stage: String): JsonObject {
            r["stage"] = stage
            return buildJsonObject {
                r.forEach { (k, v) ->
                    when (v) {
                        null -> Unit
                        is Number -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
            }
        }
        val bytes = runCatching { withContext(Dispatchers.IO) { get(e.url) } }
        if (bytes.isFailure) {
            r["error"] = err(bytes.exceptionOrNull())
            return done("download")
        }
        val file = File(dir, "$i.cs3").apply { writeBytes(bytes.getOrThrow()); setReadOnly() }
        val manifest = manifestOf(bytes.getOrThrow())
        val started = System.currentTimeMillis()
        val apis: List<MainAPI> = loader.loadPlugin(file, resolveMainClassName(manifest, e.name), manifest)
        r["loadMs"] = System.currentTimeMillis() - started
        if (apis.isEmpty()) {
            r["error"] = loader.lastErrors[file.absolutePath]
            return done("load")
        }
        r["apis"] = apis.size
        val api = apis.first()
        r["mainUrl"] = api.mainUrl
        val home = withTimeoutOrNull(25_000) { runCatching { runtime.mainPage(api, 1, 12_000) } }
        val homeItems = home?.getOrNull()?.sumOf { it.items.size } ?: 0
        r["homeItems"] = homeItems
        home?.exceptionOrNull()?.let { r["homeError"] = err(it) }
        val search = withTimeoutOrNull(15_000) { runCatching { withContext(Dispatchers.IO) { api.search(query) } } }
        val searchCount = search?.getOrNull()?.size ?: 0
        r["searchItems"] = searchCount
        r["searchError"] = if (search == null) "zaman aşımı" else err(search.exceptionOrNull())
        return done(if (homeItems > 0 || searchCount > 0) "ok" else "empty")
    }

    private companion object {
        const val TAG = "PlayCsDevice"
    }
}
