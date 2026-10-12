package com.kaan.play.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Altyazı görünümü ve "kaldığım yerden devam" ayarının saklanması (her testte dosyaya en çok bir yazma: Windows notu). */
class SubtitleSettingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { java.io.File(tmp.root, "settings.preferences_pb") }
    }

    private val repo by lazy { SettingsRepository(store) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsAreNormalWhiteTranslucentAndAlwaysResume() = runBlocking {
        assertEquals(SubtitleStyle(), repo.subtitleStyle.first())
        assertEquals(SubtitleSize.Normal, repo.subtitleStyle.first().size)
        assertEquals(SubtitleColor.White, repo.subtitleStyle.first().color)
        assertEquals(SubtitleBackground.Translucent, repo.subtitleStyle.first().background)
        assertFalse(repo.subtitleStyle.first().bold)
        assertFalse(repo.subtitleStyle.first().italic)
        assertTrue(repo.alwaysResume.first())
    }

    @Test
    fun allFiveSettingsAreStoredInOneWrite() = runBlocking {
        repo.updateSubtitleStyle {
            it.copy(
                size = SubtitleSize.ExtraLarge, bold = true, italic = true,
                color = SubtitleColor.Yellow, background = SubtitleBackground.Black,
            )
        }
        val stored = repo.subtitleStyle.first()
        assertEquals(SubtitleSize.ExtraLarge, stored.size)
        assertTrue(stored.bold)
        assertTrue(stored.italic)
        assertEquals(SubtitleColor.Yellow, stored.color)
        assertEquals(SubtitleBackground.Black, stored.background)
        assertEquals(ThemeMode.Dark, repo.themeMode.first()) // diğer ayarlar etkilenmez
    }

    @Test
    fun partialUpdateKeepsTheOtherFields() = runBlocking {
        repo.updateSubtitleStyle { it.copy(bold = true) }
        assertEquals(SubtitleStyle(bold = true), repo.subtitleStyle.first())
    }

    @Test
    fun corruptStoredValuesFallBackToDefaults() = runBlocking {
        store.edit {
            it[stringPreferencesKey("subtitle_size")] = "Gigantic"
            it[stringPreferencesKey("subtitle_color")] = "Neon"
            it[stringPreferencesKey("subtitle_background")] = "Glass"
            it[booleanPreferencesKey("subtitle_bold")] = true
        }
        assertEquals(SubtitleStyle(bold = true), repo.subtitleStyle.first())
    }

    @Test
    fun alwaysResumeCanBeTurnedOff() = runBlocking {
        repo.setAlwaysResume(false)
        assertFalse(repo.alwaysResume.first())
        assertEquals(SubtitleStyle(), repo.subtitleStyle.first())
    }

    @Test
    fun sizesGrowMonotonicallyAndNormalMatchesMedia3Default() {
        val fractions = SubtitleSize.entries.map { it.fraction }
        assertEquals(fractions.sorted(), fractions)
        assertEquals(0.0533f, SubtitleSize.Normal.fraction, 0.0001f)
    }
}
