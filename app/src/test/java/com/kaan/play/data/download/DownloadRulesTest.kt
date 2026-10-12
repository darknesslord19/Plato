package com.kaan.play.data.download

import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.download.DownloadRules.Fix
import com.kaan.play.data.download.DownloadRules.Managed
import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** İndirmenin durum geçişleri, ağ koşulu, alan denetimi ve açılış uzlaştırması (Android'siz). */
class DownloadRulesTest {

    private fun row(
        id: Long,
        status: DownloadStatus = DownloadStatus.Queued,
        downloaded: Long = 0,
        total: Long = 0,
        url: String = "https://x/$id.mp4",
    ) = DownloadEntity(
        id = id, tmdbId = 100 + id.toInt(), imdbId = "tt$id", type = MediaType.Movie, title = "Film $id", posterPath = null,
        sourceName = "Eklenti", sourceUrl = url, status = status, downloadedBytes = downloaded, totalBytes = total, createdAt = id,
    )

    // ---- durum geçişleri ----

    @Test
    fun media3StatesMapToOurStatuses() {
        assertEquals(DownloadStatus.Queued, DownloadRules.statusOf(Download.STATE_QUEUED))
        assertEquals(DownloadStatus.Downloading, DownloadRules.statusOf(Download.STATE_DOWNLOADING))
        assertEquals(DownloadStatus.Paused, DownloadRules.statusOf(Download.STATE_STOPPED))
        assertEquals(DownloadStatus.Completed, DownloadRules.statusOf(Download.STATE_COMPLETED))
        assertEquals(DownloadStatus.Failed, DownloadRules.statusOf(Download.STATE_FAILED))
        // Silinirken/yeniden başlarken arayüzde "sırada" görünür.
        assertEquals(DownloadStatus.Queued, DownloadRules.statusOf(Download.STATE_REMOVING))
        assertEquals(DownloadStatus.Queued, DownloadRules.statusOf(Download.STATE_RESTARTING))
    }

    @Test
    fun onlyQueuedAndDownloadingAreActive() {
        assertTrue(DownloadRules.isActive(DownloadStatus.Queued))
        assertTrue(DownloadRules.isActive(DownloadStatus.Downloading))
        assertFalse(DownloadRules.isActive(DownloadStatus.Paused))
        assertFalse(DownloadRules.isActive(DownloadStatus.Completed))
        assertFalse(DownloadRules.isActive(DownloadStatus.Failed))
    }

    @Test
    fun rowActionIsTheNaturalNextStep() {
        assertEquals(DownloadRules.RowAction.Play, DownloadRules.rowAction(DownloadStatus.Completed))
        assertEquals(DownloadRules.RowAction.Pause, DownloadRules.rowAction(DownloadStatus.Downloading))
        assertEquals(DownloadRules.RowAction.Pause, DownloadRules.rowAction(DownloadStatus.Queued))
        assertEquals(DownloadRules.RowAction.Resume, DownloadRules.rowAction(DownloadStatus.Paused))
        // Başarısızda "sürdür" yeniden dener.
        assertEquals(DownloadRules.RowAction.Resume, DownloadRules.rowAction(DownloadStatus.Failed))
    }

    // ---- Wi-Fi koşulu ----

    @Test
    fun wifiOnlyRequiresAnUnmeteredNetworkOtherwiseAnyNetwork() {
        assertEquals(Requirements.NETWORK_UNMETERED, DownloadRules.requirementFlags(wifiOnly = true))
        assertEquals(Requirements.NETWORK, DownloadRules.requirementFlags(wifiOnly = false))
        assertTrue(Requirements(DownloadRules.requirementFlags(true)).isUnmeteredNetworkRequired)
        assertFalse(Requirements(DownloadRules.requirementFlags(false)).isUnmeteredNetworkRequired)
        assertTrue(Requirements(DownloadRules.requirementFlags(false)).isNetworkRequired)
    }

    @Test
    fun waitingForNetworkLooksOnlyAtNetworkFlags() {
        assertFalse(DownloadRules.waitingForNetwork(0))
        assertTrue(DownloadRules.waitingForNetwork(Requirements.NETWORK))
        assertTrue(DownloadRules.waitingForNetwork(Requirements.NETWORK_UNMETERED))
        assertTrue(DownloadRules.waitingForNetwork(Requirements.NETWORK or Requirements.DEVICE_CHARGING))
        assertFalse(DownloadRules.waitingForNetwork(Requirements.DEVICE_CHARGING))
    }

    // ---- boyut / ilerleme ----

    @Test
    fun totalPrefersContentLengthThenPercentEstimateThenHint() {
        // Bilinen uzunluk.
        assertEquals(1000L, DownloadRules.totalBytes(DownloadStatus.Downloading, 100, 1000, 10f, 0))
        // Bölünmüş akış: uzunluk yok, yüzde var -> tahmin (100 bayt = %10 -> 1000).
        assertEquals(1000L, DownloadRules.totalBytes(DownloadStatus.Downloading, 100, -1, 10f, 0))
        // İlk anlar: ne uzunluk ne yüzde; kaynağın bildirdiği boyut.
        assertEquals(5000L, DownloadRules.totalBytes(DownloadStatus.Queued, 0, -1, -1f, 5000))
        // Hiçbiri yok: bilinmiyor.
        assertEquals(0L, DownloadRules.totalBytes(DownloadStatus.Queued, 0, -1, -1f, 0))
        // İndirilen toplamı aşmaz (kaynak yanlış boyut bildirse bile ilerleme %100'ü geçmez).
        assertEquals(1200L, DownloadRules.totalBytes(DownloadStatus.Downloading, 1200, 1000, 100f, 0))
    }

    @Test
    fun completedTotalIsTheRealDownloadedSize() {
        assertEquals(777L, DownloadRules.totalBytes(DownloadStatus.Completed, 777, 1000, 100f, 5000))
    }

    @Test
    fun fractionIsClampedAndUnknownTotalGivesNull() {
        assertEquals(0.25f, DownloadRules.fraction(250, 1000)!!, 0.0001f)
        assertEquals(1f, DownloadRules.fraction(2000, 1000)!!, 0.0001f)
        assertNull(DownloadRules.fraction(250, 0))
    }

    // ---- alan denetimi ----

    @Test
    fun enoughSpaceKeepsAReserve() {
        val reserve = DownloadRules.RESERVE_BYTES
        assertEquals(DownloadRules.Space.Enough, DownloadRules.checkSpace(1_000_000_000L, 1_000_000_000L + reserve))
        val short = DownloadRules.checkSpace(1_000_000_000L, 1_000_000_000L + reserve - 1) as DownloadRules.Space.Short
        assertEquals(1_000_000_000L, short.neededBytes)
        assertFalse(short.estimated)
    }

    @Test
    fun unknownSizeAssumesAConservativeAmount() {
        val tight = DownloadRules.checkSpace(null, 300L * 1024 * 1024) as DownloadRules.Space.Short
        assertTrue(tight.estimated)
        assertEquals(DownloadRules.UNKNOWN_SIZE_ASSUMED_BYTES, tight.neededBytes)
        assertEquals(DownloadRules.Space.Enough, DownloadRules.checkSpace(null, 5L * 1024 * 1024 * 1024))
        // 0 ya da negatif boyut "bilinmiyor" sayılır.
        assertTrue((DownloadRules.checkSpace(0, 300L * 1024 * 1024) as DownloadRules.Space.Short).estimated)
    }

    @Test
    fun noFreeSpaceAtAllIsAlwaysShort() {
        assertTrue(DownloadRules.checkSpace(1, 0) is DownloadRules.Space.Short)
    }

    // ---- açılış uzlaştırması ----

    @Test
    fun reconcileLeavesMatchingRowsAlone() {
        val rows = listOf(row(1, DownloadStatus.Downloading, 100, 1000))
        val managed = listOf(Managed("1", DownloadStatus.Downloading, 100, 1000))
        assertTrue(DownloadRules.reconcile(rows, managed).isEmpty())
    }

    @Test
    fun reconcileTakesStatusAndBytesFromMedia3() {
        // Uygulama öldürüldü: satır "iniyor" kalmış ama Media3 dizininde duraklatılmış ve daha ileride.
        val rows = listOf(row(1, DownloadStatus.Downloading, 100, 1000))
        val managed = listOf(Managed("1", DownloadStatus.Paused, 400, 1000))
        assertEquals(listOf<Fix>(Fix.UpdateRow(1, DownloadStatus.Paused, 400, 1000)), DownloadRules.reconcile(rows, managed))
    }

    @Test
    fun reconcileKeepsKnownTotalWhenMedia3DoesNotKnowIt() {
        val rows = listOf(row(1, DownloadStatus.Queued, 0, 5000))
        val managed = listOf(Managed("1", DownloadStatus.Downloading, 10, 0))
        assertEquals(listOf<Fix>(Fix.UpdateRow(1, DownloadStatus.Downloading, 10, 5000)), DownloadRules.reconcile(rows, managed))
    }

    @Test
    fun reconcileDeletesRowsWithoutData() {
        // Media3 dizininde karşılığı olmayan satır (veri silindi): satır anlamsız, kalkar.
        val rows = listOf(row(1, DownloadStatus.Completed, 1000, 1000), row(2, DownloadStatus.Downloading, 5, 10))
        val managed = listOf(Managed("2", DownloadStatus.Downloading, 5, 10))
        assertEquals(listOf<Fix>(Fix.DeleteRow(1)), DownloadRules.reconcile(rows, managed))
    }

    @Test
    fun reconcileRemovesManagedDownloadsNobodyCanSee() {
        val rows = listOf(row(1, DownloadStatus.Completed, 10, 10))
        val managed = listOf(Managed("1", DownloadStatus.Completed, 10, 10), Managed("9", DownloadStatus.Completed, 50, 50), Managed("pending", DownloadStatus.Queued, 0, 0))
        assertEquals(listOf<Fix>(Fix.RemoveManaged("9"), Fix.RemoveManaged("pending")), DownloadRules.reconcile(rows, managed))
    }

    // ---- kaynak listesindeki simge ----

    @Test
    fun badgeShowsStateOnlyForTheSameSourceUrl() {
        val completed = row(1, DownloadStatus.Completed, url = "https://x/a.mp4")
        assertEquals(DownloadBadge.None, DownloadRules.badge(null, "https://x/a.mp4", starting = false))
        assertEquals(DownloadBadge.Done, DownloadRules.badge(completed, "https://x/a.mp4", starting = false))
        // Aynı içeriğin başka kaynağı indirilmiş: bu satırda simge normal.
        assertEquals(DownloadBadge.None, DownloadRules.badge(completed, "https://x/other.mp4", starting = false))
        for (active in listOf(DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Paused)) {
            assertEquals(DownloadBadge.Active, DownloadRules.badge(row(1, active, url = "https://x/a.mp4"), "https://x/a.mp4", false))
        }
        assertEquals(DownloadBadge.Failed, DownloadRules.badge(row(1, DownloadStatus.Failed, url = "https://x/a.mp4"), "https://x/a.mp4", false))
        // Dokunuldu, kaynak yoklanıyor.
        assertEquals(DownloadBadge.Active, DownloadRules.badge(null, "https://x/a.mp4", starting = true))
    }
}
