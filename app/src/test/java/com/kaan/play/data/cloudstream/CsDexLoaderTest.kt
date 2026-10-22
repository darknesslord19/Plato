package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `pluginClassName`, depo düzeyindeki `internalName`den farklı olabilir (gerçek depolarda genelde
 * farklıdır, ör. `DailymotionProvider` -> `recloudstream.DailymotionPlugin`); [resolveMainClassName]
 * bunu doğru çözmeli, yoksa/bozuksa [fallback]'e dönmelidir.
 */
class CsDexLoaderTest {

    @Test
    fun `manifestteki pluginClassName kullanilir`() {
        val manifest = """{"name":"DailymotionProvider","version":4,"pluginClassName":"recloudstream.DailymotionPlugin","requiresResources":false}"""
        assertEquals("recloudstream.DailymotionPlugin", resolveMainClassName(manifest, "DailymotionProvider"))
    }

    @Test
    fun `manifest yoksa internalName'e doner`() {
        assertEquals("DailymotionProvider", resolveMainClassName(null, "DailymotionProvider"))
        assertEquals("DailymotionProvider", resolveMainClassName("", "DailymotionProvider"))
    }

    @Test
    fun `pluginClassName alani eksikse internalName'e doner`() {
        val manifest = """{"name":"X","version":1}"""
        assertEquals("X", resolveMainClassName(manifest, "X"))
    }

    @Test
    fun `bozuk json'da internalName'e doner`() {
        assertEquals("X", resolveMainClassName("{not json", "X"))
    }

    @Test
    fun `bos pluginClassName internalName'e doner`() {
        val manifest = """{"pluginClassName":""}"""
        assertEquals("X", resolveMainClassName(manifest, "X"))
    }

    @Test
    fun `requiresResources manifestten okunur`() {
        org.junit.Assert.assertTrue(manifestRequiresResources("""{"pluginClassName":"a.B","requiresResources":true}"""))
        org.junit.Assert.assertFalse(manifestRequiresResources("""{"pluginClassName":"a.B","requiresResources":false}"""))
        org.junit.Assert.assertFalse(manifestRequiresResources("""{"pluginClassName":"a.B"}"""))
        org.junit.Assert.assertFalse(manifestRequiresResources(null))
        org.junit.Assert.assertFalse(manifestRequiresResources("{bozuk"))
    }
}
