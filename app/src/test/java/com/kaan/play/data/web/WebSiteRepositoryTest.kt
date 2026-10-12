package com.kaan.play.data.web

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Room'un unique/IGNORE davranışını taklit eden bellek içi DAO. */
private class FakeDao : AddonDao {
    val rows = MutableStateFlow<List<AddonEntity>>(emptyList())
    private var nextId = 1L

    override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = rows.map { l -> l.filter { it.type == type } }
    override suspend fun enabledOfType(type: AddonType) = rows.value.filter { it.enabled && it.type == type }
    override suspend fun countOfType(type: AddonType) = rows.value.count { it.type == type }
    override suspend fun countBySourceUrl(url: String) = rows.value.count { it.sourceUrl == url }
    override suspend fun insert(entity: AddonEntity): Long {
        if (rows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
        val id = nextId++
        rows.value += entity.copy(id = id)
        return id
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class WebSiteRepositoryTest {

    private val dao = FakeDao()
    private val cleared = mutableListOf<Pair<String, Boolean>>()
    private val repo = WebSiteRepository(dao, { url, anyLeft -> cleared += url to anyLeft }, clock = { 5L })

    @Test
    fun addNormalizesAndStoresAsWebSiteRow() = runTest {
        val site = repo.add("  Benim Sitem ", "example.com/izle")
        assertEquals("Benim Sitem", site.name)
        assertEquals("https://example.com/izle", site.url)

        val row = dao.rows.value.single()
        assertEquals(AddonType.WEB_SITE, row.type)
        assertEquals("example.com", row.addonId)
        assertEquals(site, repo.sites.first().single())
    }

    @Test
    fun blankNameFallsBackToHostWithoutWww() = runTest {
        assertEquals("example.com", repo.add("", "https://www.example.com").name)
        assertEquals("b.example.org", repo.add("   ", "b.example.org").name)
    }

    @Test
    fun invalidAddressIsRejectedAndNothingStored() = runTest {
        listOf("ftp://x.com", "intent://x", "").forEach { bad ->
            try {
                repo.add("x", bad)
                fail()
            } catch (e: WebSiteException) {
                assertEquals(WebSiteException.Kind.InvalidUrl, e.kind)
            }
        }
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun sameAddressInAnotherSpellingIsADuplicate() = runTest {
        repo.add("", "https://example.com")
        try {
            repo.add("", "Example.com/")
            fail()
        } catch (e: WebSiteException) {
            assertEquals(WebSiteException.Kind.AlreadyAdded, e.kind)
        }
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun webSitesNeverLeakIntoAddonListsOrStreamCollection() = runTest {
        repo.add("", "example.com")
        assertTrue(dao.enabledOfType(AddonType.NUVIO_ADDON).isEmpty())
        assertTrue(dao.observeOfType(AddonType.NUVIO_ADDON).first().isEmpty())
    }

    @Test
    fun removeDeletesAndClearsWebData() = runTest {
        val a = repo.add("", "a.example.com")
        val b = repo.add("", "b.example.com")

        repo.remove(a)
        assertEquals(listOf(b), repo.sites.first())
        assertEquals(listOf("https://a.example.com/" to true), cleared) // başka site kaldı: yalnızca o site temizlenir

        repo.remove(b)
        assertTrue(repo.sites.first().isEmpty())
        assertEquals("https://b.example.com/" to false, cleared.last()) // son site: hepsi temizlenir
    }
}
