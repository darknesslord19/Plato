package com.kaan.play.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRulesTest {

    private val sampleItems = listOf(
        ProviderItem(id = "cs:1", name = "DiziBox", subtitle = "CloudStream · Turkish", kind = ProviderKind.CloudStream),
        ProviderItem(id = "cs:2", name = "HdFilmCehennemi", subtitle = "CloudStream · Turkish", kind = ProviderKind.CloudStream),
        ProviderItem(id = "nuvio_addon:10", name = "Torrentio", subtitle = "Nuvio · Torrentio", kind = ProviderKind.NuvioAddon),
        ProviderItem(id = "nuvio_plugin:20", name = "VidMoly", subtitle = "Nuvio · Scrapers", kind = ProviderKind.NuvioPlugin),
    )

    @Test
    fun filterByTabWorksCorrectly() {
        val all = ProviderRules.filterByTab(sampleItems, ProviderTab.All)
        assertEquals(4, all.size)

        val cs = ProviderRules.filterByTab(sampleItems, ProviderTab.CloudStream)
        assertEquals(2, cs.size)
        assertTrue(cs.all { it.kind == ProviderKind.CloudStream })

        val nuvio = ProviderRules.filterByTab(sampleItems, ProviderTab.Nuvio)
        assertEquals(2, nuvio.size)
        assertTrue(nuvio.all { it.kind == ProviderKind.NuvioAddon || it.kind == ProviderKind.NuvioPlugin })
    }

    @Test
    fun filterByQueryMatchesCaseInsensitive() {
        val matchDizi = ProviderRules.filterByQuery(sampleItems, "dizi")
        assertEquals(1, matchDizi.size)
        assertEquals("DiziBox", matchDizi.first().name)

        val matchAllWithEmpty = ProviderRules.filterByQuery(sampleItems, "   ")
        assertEquals(4, matchAllWithEmpty.size)

        val noMatch = ProviderRules.filterByQuery(sampleItems, "nonexistent")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun pinningBringsItemsToTopAndSortsAlphabetically() {
        // Pin "VidMoly" and "DiziBox"
        val pinned = setOf("nuvio_plugin:20", "cs:1")
        val sorted = ProviderRules.applyPinningAndSort(sampleItems, pinned)

        // Pinned items first: DiziBox (pinned), VidMoly (pinned)
        assertTrue(sorted[0].isPinned)
        assertEquals("DiziBox", sorted[0].name)

        assertTrue(sorted[1].isPinned)
        assertEquals("VidMoly", sorted[1].name)

        // Unpinned items next: HdFilmCehennemi, Torrentio
        assertFalse(sorted[2].isPinned)
        assertEquals("HdFilmCehennemi", sorted[2].name)

        assertFalse(sorted[3].isPinned)
        assertEquals("Torrentio", sorted[3].name)
    }

    @Test
    fun singleSelectBehavior() {
        assertEquals(setOf("cs:1"), ProviderRules.singleSelect("cs:1"))
        assertEquals(emptySet<String>(), ProviderRules.singleSelect(ProviderConstants.ALL_PROVIDERS_ID))
    }

    @Test
    fun multiSelectToggleBehavior() {
        var selected = emptySet<String>()

        // Toggle item 1
        selected = ProviderRules.toggleMultiSelect(selected, "cs:1")
        assertEquals(setOf("cs:1"), selected)

        // Toggle item 2
        selected = ProviderRules.toggleMultiSelect(selected, "nuvio_addon:10")
        assertEquals(setOf("cs:1", "nuvio_addon:10"), selected)

        // Toggle item 1 again (removes it)
        selected = ProviderRules.toggleMultiSelect(selected, "cs:1")
        assertEquals(setOf("nuvio_addon:10"), selected)

        // Selecting ALL_PROVIDERS_ID in multi-select clears/resets
        selected = ProviderRules.toggleMultiSelect(selected, ProviderConstants.ALL_PROVIDERS_ID)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun isAllProvidersCheck() {
        assertTrue(ProviderRules.isAllProviders(emptySet()))
        assertTrue(ProviderRules.isAllProviders(setOf(ProviderConstants.ALL_PROVIDERS_ID)))
        assertFalse(ProviderRules.isAllProviders(setOf("cs:1")))
        assertFalse(ProviderRules.isAllProviders(setOf("cs:1", "nuvio_addon:10")))
    }
}
