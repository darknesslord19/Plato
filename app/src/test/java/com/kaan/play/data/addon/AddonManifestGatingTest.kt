package com.kaan.play.data.addon

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Addon'a yalnızca desteklediği isteği gönder" kuralı. */
class AddonManifestGatingTest {

    private fun m(
        types: List<String> = listOf("movie", "series"),
        idPrefixes: List<String>? = null,
        resources: List<ManifestResource> = listOf(ManifestResource("stream")),
    ) = AddonManifest("id", "name", types = types, resources = resources, idPrefixes = idPrefixes)

    @Test
    fun noPrefixesMeansAnyIdIsAccepted() {
        assertTrue(m().supportsStream("movie", "tt0111161"))
        assertTrue(m().supportsStream("series", "kitsu:1:2"))
    }

    @Test
    fun typeMustBeListed() {
        val movieOnly = m(types = listOf("movie"))
        assertTrue(movieOnly.supportsStream("movie", "tt1"))
        assertFalse(movieOnly.supportsStream("series", "tt1:1:1"))
    }

    @Test
    fun manifestLevelPrefixesFilterIds() {
        val tt = m(idPrefixes = listOf("tt"))
        assertTrue(tt.supportsStream("movie", "tt123"))
        assertTrue(tt.supportsStream("series", "tt123:2:5"))
        assertFalse(tt.supportsStream("movie", "tmdb:603"))
    }

    @Test
    fun emptyPrefixListIsTreatedAsNoRestriction() {
        assertTrue(m(idPrefixes = emptyList()).supportsStream("movie", "anything"))
    }

    @Test
    fun resourceLevelValuesOverrideManifestLevel() {
        val addon = m(
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt"),
            resources = listOf(
                ManifestResource("catalog"),
                ManifestResource("stream", types = listOf("series"), idPrefixes = listOf("kitsu:")),
            ),
        )
        assertTrue(addon.supportsStream("series", "kitsu:7:1"))
        assertFalse(addon.supportsStream("series", "tt123:1:1")) // kaynak düzeyi öneki geçerli
        assertFalse(addon.supportsStream("movie", "kitsu:7")) // kaynak düzeyi türü geçerli
    }

    @Test
    fun noStreamResourceMeansNothingIsSupported() {
        val addon = m(resources = listOf(ManifestResource("catalog"), ManifestResource("meta")))
        assertFalse(addon.supportsStream("movie", "tt1"))
    }

    @Test
    fun multipleStreamResourcesAreCombined() {
        val addon = m(
            resources = listOf(
                ManifestResource("stream", types = listOf("movie"), idPrefixes = listOf("tt")),
                ManifestResource("stream", types = listOf("series"), idPrefixes = listOf("kitsu:")),
            ),
        )
        assertTrue(addon.supportsStream("movie", "tt1"))
        assertTrue(addon.supportsStream("series", "kitsu:1:1"))
        assertFalse(addon.supportsStream("movie", "kitsu:1"))
        assertFalse(addon.supportsStream("series", "tt1:1:1"))
    }
}
