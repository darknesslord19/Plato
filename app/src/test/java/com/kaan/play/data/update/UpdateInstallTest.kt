package com.kaan.play.data.update

import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
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
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private fun sha256Hex(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class UpdateApkRulesTest {

    private fun assets(vararg abis: String, base: String = "https://github.com/liberta09/Play/releases/download/v1.0.3") =
        abis.map { ReleaseAsset("Play-1.0.3-$it.apk", "$base/Play-1.0.3-$it.apk", 13_000_000) } +
            ReleaseAsset("Play-1.0.3-SHA256.txt", "$base/Play-1.0.3-SHA256.txt", 272)

    private val all = assets("arm64-v8a", "armeabi-v7a", "x86_64")

    @Test
    fun `cihazin en tercih ettigi islemci turu secilir`() {
        assertEquals("Play-1.0.3-arm64-v8a.apk", UpdateApkRules.pick("v1.0.3", all, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))?.name)
        assertEquals("Play-1.0.3-armeabi-v7a.apk", UpdateApkRules.pick("v1.0.3", all, listOf("armeabi-v7a", "armeabi"))?.name)
        assertEquals("Play-1.0.3-x86_64.apk", UpdateApkRules.pick("v1.0.3", all, listOf("x86_64", "x86", "arm64-v8a"))?.name)
    }

    @Test
    fun `tercih sirasi cihazdan gelir release sirasindan degil`() {
        // x86 emülatörde x86_64 önce gelir; yalnızca arm64 varsa o seçilir.
        val onlyArm = assets("arm64-v8a")
        assertEquals("Play-1.0.3-arm64-v8a.apk", UpdateApkRules.pick("v1.0.3", onlyArm, listOf("x86_64", "arm64-v8a"))?.name)
    }

    @Test
    fun `eslesme yoksa null ve tarayici yedegine dusulur`() {
        assertNull(UpdateApkRules.pick("v1.0.3", all, listOf("x86", "mips")))
        assertNull(UpdateApkRules.pick("v1.0.3", all, emptyList()))
        assertNull(UpdateApkRules.pick("v1.0.3", emptyList(), listOf("arm64-v8a")))
    }

    @Test
    fun `ozet dosyasi yoksa null`() {
        assertNull(UpdateApkRules.pick("v1.0.3", all.filter { !it.name.endsWith("SHA256.txt") }, listOf("arm64-v8a")))
    }

    @Test
    fun `sonuc apk adresi boyutu ve ozet adresini tasir`() {
        val apk = UpdateApkRules.pick("v1.0.3", all, listOf("arm64-v8a"))!!
        assertEquals("https://github.com/liberta09/Play/releases/download/v1.0.3/Play-1.0.3-arm64-v8a.apk", apk.url)
        assertEquals("https://github.com/liberta09/Play/releases/download/v1.0.3/Play-1.0.3-SHA256.txt", apk.sha256Url)
        assertEquals(13_000_000, apk.size)
    }

    @Test
    fun `baska surume ait ya da yanlis adli dosyalar secilmez`() {
        val old = all.map { it.copy(name = it.name.replace("1.0.3", "1.0.2")) }
        assertNull(UpdateApkRules.pick("v1.0.3", old, listOf("arm64-v8a")))
        val renamed = all.map { it.copy(name = it.name.replace("Play-", "Other-")) }
        assertNull(UpdateApkRules.pick("v1.0.3", renamed, listOf("arm64-v8a")))
        assertNull(UpdateApkRules.pick("", all, listOf("arm64-v8a")))
    }

    @Test
    fun `sifir ya da asiri boyutlu apk atlanir sonraki islemci turune bakilir`() {
        val list = listOf(
            ReleaseAsset("Play-1.0.3-arm64-v8a.apk", "https://github.com/a/b/c.apk", 0),
            ReleaseAsset("Play-1.0.3-armeabi-v7a.apk", "https://github.com/a/b/d.apk", 12_000_000),
            ReleaseAsset("Play-1.0.3-SHA256.txt", "https://github.com/a/b/h.txt", 100),
        )
        assertEquals("Play-1.0.3-armeabi-v7a.apk", UpdateApkRules.pick("v1.0.3", list, listOf("arm64-v8a", "armeabi-v7a"))?.name)
        val huge = list.map { it.copy(size = UpdateApkRules.MAX_APK_BYTES + 1) }
        assertNull(UpdateApkRules.pick("v1.0.3", huge, listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test
    fun `guvenli olmayan adresler reddedilir`() {
        assertNull(UpdateApkRules.pick("v1.0.3", assets("arm64-v8a", base = "http://github.com/x"), listOf("arm64-v8a")))
        assertNull(UpdateApkRules.pick("v1.0.3", assets("arm64-v8a", base = "https://evil.example/x"), listOf("arm64-v8a")))
        assertNull(UpdateApkRules.pick("v1.0.3", assets("arm64-v8a", base = "https://github.com.evil.example/x"), listOf("arm64-v8a")))
    }

    @Test
    fun `sha256sum bicimleri okunur`() {
        val h = "a".repeat(64)
        val k = "B".repeat(64)
        val text = "$h *Play-1.0.3-arm64-v8a.apk\r\n$k  Play-1.0.3-x86_64.apk\n"
        assertEquals(h, UpdateApkRules.parseSha256(text, "Play-1.0.3-arm64-v8a.apk"))
        assertEquals(k.lowercase(), UpdateApkRules.parseSha256(text, "Play-1.0.3-x86_64.apk")) // boşluk biçimi, büyük harf
        assertNull(UpdateApkRules.parseSha256(text, "Play-1.0.3-armeabi-v7a.apk"))
    }

    @Test
    fun `bozuk ozet satiri reddedilir`() {
        assertNull(UpdateApkRules.parseSha256("abc *x.apk", "x.apk"))
        assertNull(UpdateApkRules.parseSha256("${"g".repeat(64)} *x.apk", "x.apk"))
        assertNull(UpdateApkRules.parseSha256("${"a".repeat(63)} *x.apk", "x.apk"))
        assertNull(UpdateApkRules.parseSha256("", "x.apk"))
        assertNull(UpdateApkRules.parseSha256("${"a".repeat(64)}", "x.apk")) // dosya adı yok
    }

    @Test
    fun `indirme adresleri yalnizca https github`() {
        assertTrue(UpdateApkRules.isSafeDownload("https://github.com/liberta09/Play/releases/download/v1.0.3/a.apk"))
        assertTrue(UpdateApkRules.isSafeDownload("https://release-assets.githubusercontent.com/x?y=1"))
        assertTrue(UpdateApkRules.isSafeDownload("https://objects.githubusercontent.com/x"))
        assertFalse(UpdateApkRules.isSafeDownload("http://github.com/a.apk"))
        assertFalse(UpdateApkRules.isSafeDownload("https://githubusercontent.com.evil.example/a.apk"))
        assertFalse(UpdateApkRules.isSafeDownload("https://evilgithubusercontent.com/a.apk"))
        assertFalse(UpdateApkRules.isSafeDownload("file:///sdcard/a.apk"))
        assertFalse(UpdateApkRules.isSafeDownload("content://x/a.apk"))
        assertFalse(UpdateApkRules.isSafeDownload(""))
        // Sahte sunucu istisnası yalnızca verilen ana bilgisayar için.
        assertTrue(UpdateApkRules.isSafeDownload("http://127.0.0.1:8080/a.apk", debugHost = "127.0.0.1"))
        assertFalse(UpdateApkRules.isSafeDownload("http://127.0.0.2:8080/a.apk", debugHost = "127.0.0.1"))
    }
}

/** Sahte release sunucusu: API yanıtı, APK ve SHA-256 dosyası tek sunucuda. Gerçek GitHub'a dokunmaz. */
class UpdateFlowTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dir: File

    private val apkBytes = ByteArray(200 * 1024) { (it * 31).toByte() }
    private val apkName = "Play-9.9.9-x86_64.apk"

    private var hashLine = ""
    private var apkResponse: () -> MockResponse = { MockResponse.Builder().body(Buffer().write(apkBytes)).build() }
    private var hashResponse: (() -> MockResponse)? = null
    private val paths = mutableListOf<String>()

    @Before
    fun setUp() {
        dir = tmp.newFolder("updates")
        hashLine = "${sha256Hex(apkBytes)} *$apkName\n"
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    paths += path
                    return when (path) {
                        "/api/latest" -> MockResponse.Builder().body(releaseJson()).build()
                        "/dl/$apkName" -> apkResponse()
                        "/dl/Play-9.9.9-SHA256.txt" -> hashResponse?.invoke() ?: MockResponse.Builder().body(hashLine).build()
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            start()
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun releaseJson(): String {
        val base = server.url("/dl").toString()
        return """{"tag_name":"v9.9.9","html_url":"https://github.com/liberta09/Play/releases/tag/v9.9.9","assets":[
            {"name":"$apkName","browser_download_url":"$base/$apkName","size":${apkBytes.size}},
            {"name":"Play-9.9.9-SHA256.txt","browser_download_url":"$base/Play-9.9.9-SHA256.txt","size":100}]}"""
    }

    private class FakePlatform : InstallPlatform {
        @Volatile var canInstall = true
        @Volatile var ours = true
        @Volatile var installerOpens = true
        @Volatile var settingsOpens = true
        val installed = mutableListOf<File>()
        var settingsOpened = 0
        override fun canInstall() = canInstall
        override fun openInstaller(file: File): Boolean {
            if (installerOpens) installed += file
            return installerOpens
        }
        override fun openInstallPermissionSettings(): Boolean {
            settingsOpened++
            return settingsOpens
        }
        override fun isOurPackage(file: File) = ours
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun newFlow(platform: FakePlatform, abis: List<String> = listOf("x86_64")): Pair<UpdateFlow, UpdateChecker> {
        val client = OkHttpClient()
        val checker = UpdateChecker(
            client, PlayJson, installedVersion = "1.0.0",
            endpoint = server.url("/api/latest").toString(), abis = abis,
        )
        val flow = UpdateFlow(checker, UpdateDownloader(client) { checker.debugHost }, platform, dir, scope)
        checker.checkOnce(scope, server.url("/api/latest").toString())
        return flow to checker
    }

    private suspend fun UpdateFlow.await(predicate: (UpdateState) -> Boolean): UpdateState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { state.first(predicate) } }

    @Test
    fun `surum bulunur indirilir dogrulanir ve kurulum ekrani acilir`() = runTest {
        val platform = FakePlatform()
        val (flow, _) = newFlow(platform)
        val offer = flow.await { it is UpdateState.Offer } as UpdateState.Offer
        assertEquals("v9.9.9", offer.info.tag)
        assertEquals(apkName, offer.info.apk?.name)

        flow.start()
        val ready = flow.await { it is UpdateState.Ready } as UpdateState.Ready
        assertEquals(sha256Hex(apkBytes), sha256Hex(ready.file.readBytes()))
        assertEquals(dir, ready.file.parentFile)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })

        flow.installIfPossible()
        assertEquals(listOf(ready.file), platform.installed)
        assertEquals(UpdateState.Idle, flow.state.value)
    }

    @Test
    fun `ilerleme artarak bildirilir`() = runTest {
        val (flow, _) = newFlow(FakePlatform())
        flow.await { it is UpdateState.Offer }
        val seen = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val collector = CoroutineScope(Dispatchers.Default).launch {
            flow.state.collect { if (it is UpdateState.Downloading) seen += it.done }
        }
        flow.start()
        flow.await { it is UpdateState.Ready }
        withContext(Dispatchers.Default) { Thread.sleep(100) }
        collector.cancel()
        val copy = seen.toList()
        assertTrue(copy.isNotEmpty())
        assertEquals(copy.sorted(), copy)
        assertTrue(copy.all { it <= apkBytes.size })
    }

    @Test
    fun `ozet uyusmazsa kurulmaz dosya kalmaz ve tarayici yedegi sunulur`() = runTest {
        hashLine = "${"0".repeat(64)} *$apkName\n"
        val platform = FakePlatform()
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        val failed = flow.await { it is UpdateState.Failed } as UpdateState.Failed
        assertEquals(UpdateState.Reason.CHECKSUM, failed.reason)
        assertEquals("https://github.com/liberta09/Play/releases/tag/v9.9.9", failed.info.pageUrl)
        assertTrue(platform.installed.isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
        flow.installIfPossible() // Failed'dan kurulum açılamaz
        assertTrue(platform.installed.isEmpty())
    }

    @Test
    fun `bir bayt bozuk indirme dogrulamada reddedilir`() = runTest {
        val corrupted = apkBytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        apkResponse = { MockResponse.Builder().body(Buffer().write(corrupted)).build() }
        val platform = FakePlatform()
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        val failed = flow.await { it is UpdateState.Failed } as UpdateState.Failed
        assertEquals(UpdateState.Reason.CHECKSUM, failed.reason)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `ozet dosyasinda satir yoksa dogrulanamaz`() = runTest {
        hashLine = "${sha256Hex(apkBytes)} *baska.apk\n"
        val (flow, _) = newFlow(FakePlatform())
        flow.await { it is UpdateState.Offer }
        flow.start()
        assertEquals(UpdateState.Reason.CHECKSUM, (flow.await { it is UpdateState.Failed } as UpdateState.Failed).reason)
    }

    @Test
    fun `ag kesilirse cokme yok ve yarim dosya kalmaz`() = runTest {
        apkResponse = {
            MockResponse.Builder().body(Buffer().write(apkBytes)).onResponseBody(SocketEffect.CloseSocket()).build()
        }
        val platform = FakePlatform()
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        val failed = flow.await { it is UpdateState.Failed } as UpdateState.Failed
        assertEquals(UpdateState.Reason.DOWNLOAD, failed.reason)
        assertTrue(platform.installed.isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `sunucu hatasi indirme hatasidir`() = runTest {
        apkResponse = { MockResponse.Builder().code(500).build() }
        val (flow, _) = newFlow(FakePlatform())
        flow.await { it is UpdateState.Offer }
        flow.start()
        assertEquals(UpdateState.Reason.DOWNLOAD, (flow.await { it is UpdateState.Failed } as UpdateState.Failed).reason)
    }

    @Test
    fun `ozet dosyasi indirilemezse indirme hatasi`() = runTest {
        hashResponse = { MockResponse.Builder().code(404).build() }
        val (flow, _) = newFlow(FakePlatform())
        flow.await { it is UpdateState.Offer }
        flow.start()
        assertEquals(UpdateState.Reason.DOWNLOAD, (flow.await { it is UpdateState.Failed } as UpdateState.Failed).reason)
        assertFalse(paths.contains("/dl/$apkName")) // özet alınamadan APK indirilmez
    }

    @Test
    fun `izin yoksa ayara yonlendirir donunce kaldigi yerden kurar tekrar indirmez`() = runTest {
        val platform = FakePlatform().also { it.canInstall = false }
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        flow.await { it is UpdateState.Ready }
        flow.installIfPossible()
        val needs = flow.state.value as UpdateState.NeedsPermission
        assertTrue(platform.installed.isEmpty())

        flow.openPermissionSettings()
        assertEquals(1, platform.settingsOpened)
        flow.installIfPossible() // izin hâlâ yok: aynı durumda kal
        assertTrue(flow.state.value is UpdateState.NeedsPermission)

        platform.canInstall = true // kullanıcı ayarı açıp geri döndü
        flow.installIfPossible()
        assertEquals(listOf(needs.file), platform.installed)
        assertEquals(UpdateState.Idle, flow.state.value)
        assertEquals(1, paths.count { it == "/dl/$apkName" })
    }

    @Test
    fun `ayar sayfasi acilamazsa tarayici yedegi`() = runTest {
        val platform = FakePlatform().also { it.canInstall = false; it.settingsOpens = false }
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        flow.await { it is UpdateState.Ready }
        flow.installIfPossible()
        flow.openPermissionSettings()
        assertEquals(UpdateState.Reason.INSTALL, (flow.state.value as UpdateState.Failed).reason)
    }

    @Test
    fun `kurulum ekrani acilamazsa tarayici yedegi`() = runTest {
        val platform = FakePlatform().also { it.installerOpens = false }
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        flow.await { it is UpdateState.Ready }
        flow.installIfPossible()
        assertEquals(UpdateState.Reason.INSTALL, (flow.state.value as UpdateState.Failed).reason)
    }

    @Test
    fun `baska pakete ait dosya kurulmaz`() = runTest {
        val platform = FakePlatform().also { it.ours = false }
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        assertEquals(UpdateState.Reason.CHECKSUM, (flow.await { it is UpdateState.Failed } as UpdateState.Failed).reason)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `indirme sirasinda kapatmak iptal eder ve yarim dosya birakmaz`() = runTest {
        apkResponse = {
            MockResponse.Builder().body(Buffer().write(apkBytes)).throttleBody(8 * 1024, 100, TimeUnit.MILLISECONDS).build()
        }
        val platform = FakePlatform()
        val (flow, checker) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        flow.await { it is UpdateState.Downloading && it.done > 0 }
        flow.dismiss()
        assertEquals(UpdateState.Idle, flow.state.value)
        assertNull(checker.available.value)
        withContext(Dispatchers.Default) { Thread.sleep(600) }
        assertEquals(UpdateState.Idle, flow.state.value)
        assertTrue(platform.installed.isEmpty())
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun `daha once indirilmis ve ozeti tutan dosya yeniden indirilmez`() = runTest {
        val platform = FakePlatform()
        val (flow, _) = newFlow(platform)
        flow.await { it is UpdateState.Offer }
        flow.start()
        flow.await { it is UpdateState.Ready }
        flow.dismiss()
        assertEquals(1, paths.count { it == "/dl/$apkName" })

        val second = UpdateDownloader(OkHttpClient()) { server.hostName }
        val apk = UpdateApkRules.pick(
            "v9.9.9",
            listOf(
                ReleaseAsset(apkName, server.url("/dl/$apkName").toString(), apkBytes.size.toLong()),
                ReleaseAsset("Play-9.9.9-SHA256.txt", server.url("/dl/Play-9.9.9-SHA256.txt").toString(), 100),
            ),
            listOf("x86_64"),
            server.hostName,
        )!!
        val file = second.download(apk, dir)
        assertEquals(sha256Hex(apkBytes), sha256Hex(file.readBytes()))
        assertEquals(1, paths.count { it == "/dl/$apkName" })
    }

    @Test
    fun `uygun islemci turu yoksa apk bilgisi bos ve eski yontem kalir`() = runTest {
        val (flow, _) = newFlow(FakePlatform(), abis = listOf("arm64-v8a"))
        val offer = flow.await { it is UpdateState.Offer } as UpdateState.Offer
        assertNull(offer.info.apk)
        flow.start() // APK yok: hiçbir şey indirilmez
        assertTrue(flow.state.value is UpdateState.Offer)
        assertFalse(paths.contains("/dl/$apkName"))
    }

    @Test
    fun `yonlendirme guvenli olmayan adrese giderse engellenir`() = runTest {
        apkResponse = {
            MockResponse.Builder().code(302).addHeader("Location", "http://127.0.0.2:1/evil.apk").build()
        }
        val (flow, _) = newFlow(FakePlatform())
        flow.await { it is UpdateState.Offer }
        flow.start()
        assertEquals(UpdateState.Reason.DOWNLOAD, (flow.await { it is UpdateState.Failed } as UpdateState.Failed).reason)
        assertNotNull(flow.state.value)
    }
}
