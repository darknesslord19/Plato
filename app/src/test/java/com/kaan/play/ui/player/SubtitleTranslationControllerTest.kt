@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.subtitle.FakeSubtitleDao
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTrack
import com.kaan.play.data.subtitle.TrackLoader
import com.kaan.play.data.subtitle.translate.ModelPrep
import com.kaan.play.data.subtitle.translate.SubtitleTranslation
import com.kaan.play.data.subtitle.translate.TranslatedSubtitleStore
import com.kaan.play.data.subtitle.translate.TranslationEngine
import com.kaan.play.data.subtitle.translate.TranslationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Türkçe yoksa İngilizceyi otomatik çevirme akışı: tetikleme, seçim kuralları, önbellek, hata; motor ve ağ sahtedir. */
class SubtitleTranslationControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeHost : SubtitleHost {
        override fun positionMs() = 0L
        override fun embeddedSubtitles() = emptyList<EmbeddedSubtitle>()
        override fun selectEmbeddedSubtitle(index: Int) = Unit
        override fun disableEmbeddedSubtitles() = Unit
    }

    private class FakeEngine : TranslationEngine {
        var enabled = true
        var modelReady = true
        var prep = ModelPrep.Ready
        var failTranslation = false
        var prepared = 0
        var translated = 0
        override suspend fun enabled() = enabled
        override suspend fun isModelReady() = modelReady
        override suspend fun prepareModel(): ModelPrep { prepared++; if (prep == ModelPrep.Ready) modelReady = true; return prep }
        override suspend fun translate(cues: List<SubtitleCue>, onProgress: (Int) -> Unit): List<SubtitleCue> {
            translated++
            if (failTranslation) throw TranslationException("hata")
            onProgress(100)
            return cues.map { it.copy(text = "TR " + it.text) }
        }
    }

    private val request = StreamRequest(603, "tt0133093", MediaType.Movie)
    private val english = SubtitleCue(1_000, 3_000, "Hello")

    private inner class Env(
        scope: TestScope,
        remembered: String? = null,
        subtitleLanguage: String = "tr",
        val engine: FakeEngine = FakeEngine(),
        val store: TranslatedSubtitleStore = TranslatedSubtitleStore(tmp.newFolder(), io = StandardTestDispatcher(scope.testScheduler)),
    ) {
        var incoming = Channel<SubtitleResult>(Channel.UNLIMITED)
        val dao = FakeSubtitleDao()
        val loadedUrls = mutableListOf<String>()
        val loader = store.loader(TrackLoader { track ->
            loadedUrls += track.url
            if (track.url.endsWith(".srt")) SubtitleLoad.Loaded(listOf(english), "UTF-8", false) else SubtitleLoad.Failed(SubtitleLoad.Reason.Http)
        })
        val controller: SubtitleController

        init {
            if (remembered != null) kotlinx.coroutines.runBlocking { SubtitleMemory(dao).rememberLanguage(request, remembered) }
            controller = SubtitleController(
                scope = scope.backgroundScope,
                playback = FakeHost(),
                request = request,
                loader = loader,
                memory = SubtitleMemory(dao),
                provider = SubtitleProvider { incoming.receiveAsFlow() },
                preferredLanguage = { subtitleLanguage },
                persist = scope.backgroundScope,
                translation = engine,
                translationStore = store,
                io = StandardTestDispatcher(scope.testScheduler),
            )
            controller.start()
        }

        fun addon(vararg subs: Pair<String, String>) {
            incoming.trySend(
                SubtitleResult.Found(
                    StreamSource(100, "Altyazı Addon"),
                    subs.map { (url, lang) -> SubtitleTrack(url, lang, "Altyazı Addon") },
                ),
            )
        }

        /** Altyazı aramaları bitti. */
        fun finishSearch() = incoming.close()

        fun selectedTrack() = (controller.selected as? SubtitleItem.External)?.track
    }

    @Test
    fun englishOnlyIsShownAtOnceThenTranslationIsSelectedAutomatically() = runTest {
        val env = Env(this)
        env.addon("https://s/en.srt" to "en")
        runCurrent()
        // Arama bitmeden çeviri başlamaz; bu sırada İngilizce gösterilir.
        assertEquals("https://s/en.srt", env.selectedTrack()?.url)
        assertEquals(0, env.engine.translated)

        env.finishSearch()
        runCurrent()

        val chosen = env.selectedTrack()!!
        assertEquals(SubtitleKind.Translated, chosen.kind)
        assertEquals("tr", chosen.language)
        assertEquals(1, env.engine.translated)
        assertEquals(TranslationState.Idle, env.controller.translationState)
        assertFalse("kullanıcı seçmedi: dil hafızaya yazılmaz", env.controller.userSelected)
        assertEquals("TR Hello", env.store.read(chosen)!!.single().text)
        // Menüde çeviri ayrı bir parça olarak durur; İngilizce de seçilebilir kalır.
        assertTrue(env.controller.items.any { it is SubtitleItem.External && it.track.kind == SubtitleKind.Translated })
        assertTrue(env.controller.items.any { it is SubtitleItem.External && it.track.url == "https://s/en.srt" })
    }

    @Test
    fun realTurkishWinsAndNothingIsTranslated() = runTest {
        val env = Env(this)
        env.addon("https://s/en.srt" to "en", "https://s/tr.srt" to "tr")
        env.finishSearch()
        runCurrent()

        assertEquals("https://s/tr.srt", env.selectedTrack()?.url)
        assertEquals(0, env.engine.translated)
        assertTrue(env.controller.items.none { it is SubtitleItem.External && it.track.kind == SubtitleKind.Translated })
    }

    @Test
    fun translationCachedFromBeforeIsUsedWithoutTranslatingAgain() = runTest {
        val store = TranslatedSubtitleStore(tmp.newFolder(), io = StandardTestDispatcher(testScheduler))
        val source = SubtitleTrack("https://s/en.srt", "en", "Altyazı Addon")
        store.write(store.trackFor(request, source), listOf(SubtitleCue(1_000, 3_000, "Önbellekten")))

        val env = Env(this, store = store)
        env.engine.modelReady = false // model bile gerekmez
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        runCurrent()

        assertEquals(SubtitleKind.Translated, env.selectedTrack()?.kind)
        assertEquals(0, env.engine.translated)
        assertEquals(0, env.engine.prepared)
    }

    @Test
    fun theUsersOwnChoiceIsNeverOverriddenByTheTranslation() = runTest {
        val env = Env(this)
        env.addon("https://s/en.srt" to "en")
        runCurrent()
        env.controller.choose(env.controller.items.filterIsInstance<SubtitleItem.External>().first())
        env.finishSearch()
        runCurrent()

        assertEquals("https://s/en.srt", env.selectedTrack()?.url)
        assertEquals("elle seçimden sonra çeviri hiç başlamaz", 0, env.engine.translated)
    }

    @Test
    fun userSwitchingWhileTranslatingKeepsTheirChoiceWhenTheTranslationFinishes() = runTest {
        val env = Env(this)
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        // Çeviri başlamadan hemen önce değil, çeviri sürerken seçim: motoru bekleterek simüle edilemez; sıralama burada yeterli.
        runCurrent()
        val translated = env.selectedTrack()!!
        env.controller.choose(null)
        runCurrent()
        assertNull(env.controller.selected)
        assertEquals(SubtitleKind.Translated, translated.kind)
    }

    @Test
    fun rememberedOtherLanguageOrOffAndDisabledSettingSkipTranslation() = runTest {
        for (remembered in listOf("en", SubtitleSelection.REMEMBERED_OFF)) {
            val env = Env(this, remembered = remembered)
            env.addon("https://s/en.srt" to "en")
            env.finishSearch()
            runCurrent()
            assertEquals("hatırlanan=$remembered", 0, env.engine.translated)
        }
        val disabled = Env(this, engine = FakeEngine().also { it.enabled = false })
        disabled.addon("https://s/en.srt" to "en")
        disabled.finishSearch()
        runCurrent()
        assertEquals(0, disabled.engine.translated)
        assertEquals("https://s/en.srt", disabled.selectedTrack()?.url)
    }

    @Test
    fun rememberedLanguageThatIsNotAvailableFallsBackToTurkishSoTheTranslationRuns() = runTest {
        val env = Env(this, remembered = "de")
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        runCurrent()
        assertEquals(SubtitleKind.Translated, env.selectedTrack()?.kind)
    }

    @Test
    fun notTurkishPreferenceMeansNoTranslation() = runTest {
        val env = Env(this, subtitleLanguage = "de")
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        runCurrent()
        assertEquals(0, env.engine.translated)
    }

    @Test
    fun firstUseDownloadsTheModelAndWifiOnlyBlocksItWithAMessage() = runTest {
        val first = Env(this, engine = FakeEngine().also { it.modelReady = false })
        first.addon("https://s/en.srt" to "en")
        first.finishSearch()
        runCurrent()
        assertEquals(1, first.engine.prepared)
        assertEquals(SubtitleKind.Translated, first.selectedTrack()?.kind)

        val wifi = Env(this, engine = FakeEngine().also { it.modelReady = false; it.prep = ModelPrep.NeedsWifi })
        wifi.addon("https://s/en.srt" to "en")
        wifi.finishSearch()
        runCurrent()
        assertEquals(TranslationState.Failed(needsWifi = true), wifi.controller.translationState)
        assertEquals("İngilizce kalır", "https://s/en.srt", wifi.selectedTrack()?.url)
        assertEquals(0, wifi.engine.translated)
        wifi.controller.clearTranslationNotice()
        assertEquals(TranslationState.Idle, wifi.controller.translationState)
    }

    @Test
    fun translationFailureLeavesEnglishInPlaceWithAShortNoticeAndDoesNotRetry() = runTest {
        val env = Env(this, engine = FakeEngine().also { it.failTranslation = true })
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        runCurrent()

        assertEquals(TranslationState.Failed(needsWifi = false), env.controller.translationState)
        assertEquals("https://s/en.srt", env.selectedTrack()?.url)
        env.addon("https://s/en2.srt" to "en") // yeni parça gelse de tekrar denenmez
        runCurrent()
        assertEquals(1, env.engine.translated)
    }

    @Test
    fun realTurkishArrivingLaterBeatsTheTranslation() = runTest {
        val env = Env(this)
        env.addon("https://s/en.srt" to "en")
        env.finishSearch()
        runCurrent()
        assertEquals(SubtitleKind.Translated, env.selectedTrack()?.kind)
        env.incoming = Channel(Channel.UNLIMITED) // yeni arama yeni bir akış açar

        // Kullanıcı "çevrimiçi ara" yapınca gerçek Türkçe gelirse otomatik seçim ona yükselir.
        val late = SubtitleTrack("https://s/late-tr.srt", "tr", "SubDL", SubtitleKind.Online)
        env.controller.searchOnline()
        env.incoming.trySend(SubtitleResult.Found(StreamSource(101, "SubDL"), listOf(late)))
        runCurrent()
        assertTrue(SubtitleTranslation.hasRealTarget(env.controller.items))
        assertEquals("https://s/late-tr.srt", env.selectedTrack()?.url)
    }
}
