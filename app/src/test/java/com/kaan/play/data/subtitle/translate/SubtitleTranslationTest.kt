package com.kaan.play.data.subtitle.translate

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleParser
import com.kaan.play.data.subtitle.SubtitleTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

/** Çeviriye hazırlama, geri birleştirme, SRT üretimi ve önbellek; çevirici sahtedir (ML Kit gerekmez). */
class SubtitleTranslationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** "[tr] metin" biçiminde işaretleyen sahte çevirici; çağrı sayısını tutar. */
    private class FakeTranslator(val fail: (String) -> Boolean = { false }) : TextTranslator {
        val calls = AtomicInteger()
        override suspend fun translate(text: String): String {
            calls.incrementAndGet()
            if (fail(text)) error("çeviri hatası")
            return "[tr] $text"
        }
    }

    private fun cue(start: Long, end: Long, text: String) = SubtitleCue(start, end, text)

    // ---- hazırlama / birleştirme ----

    @Test
    fun singleLineIsTranslatedAsIs() {
        val plan = SubtitleTranslation.plan("Hello there.")
        assertEquals(listOf("Hello there."), plan.parts)
        assertEquals("Merhaba.", plan.combine(listOf("Merhaba.")))
    }

    @Test
    fun dashDialogueIsTranslatedLineByLineKeepingSpeakerDashes() {
        val plan = SubtitleTranslation.plan("- Where are you going?\n- Home.")
        assertEquals(listOf("Where are you going?", "Home."), plan.parts)
        assertEquals("- Nereye gidiyorsun?\n- Eve.", plan.combine(listOf("Nereye gidiyorsun?", "Eve.")))
        // Çevirici baştaki çizgiyi geri verse bile çift çizgi olmaz.
        assertEquals("- Eve.", SubtitleTranslation.plan("- A\n- B").combine(listOf("- x", "- Eve.")).lines()[1])
    }

    @Test
    fun multiLineCueIsTranslatedAsOneSentenceAndSplitBackToTheSameLineCount() {
        val plan = SubtitleTranslation.plan("I told you that\nwe should leave before the storm arrives.")
        assertEquals(listOf("I told you that we should leave before the storm arrives."), plan.parts)
        val out = plan.combine(listOf("Sana fırtına gelmeden önce ayrılmamız gerektiğini söylemiştim."))
        val lines = out.lines()
        assertEquals(2, lines.size)
        assertEquals("Sana fırtına gelmeden önce ayrılmamız gerektiğini söylemiştim.", lines.joinToString(" "))
        // Özgün satırlar 15 ve 38 karakterdi: ikinci satır daha uzun olmalı.
        assertTrue(lines[1].length > lines[0].length)
    }

    @Test
    fun redistributePrefersSentenceBoundariesOverExactProportions() {
        // Özgün: 48 + 25 karakterlik iki satır. Oransal kesim "herkesin|bilmesine" olurdu; cümle sonu tercih edilir.
        val out = SubtitleTranslation.redistribute("Toplantı Perşembe sabahına taşındı. Lütfen herkesin bilmesine izin verin.", listOf(48, 25))
        assertEquals("Toplantı Perşembe sabahına taşındı.\nLütfen herkesin bilmesine izin verin.", out)
        // Cümle sonu yoksa oran belirler.
        assertEquals("bir iki üç\ndört beş altı",SubtitleTranslation.redistribute("bir iki üç dört beş altı", listOf(10, 10)))
    }

    @Test
    fun redistributeNeverProducesEmptyLinesAndHandlesFewWords() {
        assertEquals("Evet", SubtitleTranslation.redistribute("Evet", listOf(5, 5, 5)))
        assertEquals("Evet\nhayır", SubtitleTranslation.redistribute("Evet hayır", listOf(5, 5, 5)))
        val three = SubtitleTranslation.redistribute("bir iki üç dört beş altı yedi sekiz", listOf(10, 10, 10)).lines()
        assertEquals(3, three.size)
        assertTrue(three.all { it.isNotBlank() })
        assertEquals("bir iki üç dört beş altı yedi sekiz", three.joinToString(" "))
    }

    @Test
    fun symbolOnlyPartsAreNotTranslatable() {
        assertFalse(SubtitleTranslation.isTranslatable("♪ ♪"))
        assertFalse(SubtitleTranslation.isTranslatable("123"))
        assertTrue(SubtitleTranslation.isTranslatable("♪ La la ♪"))
    }

    // ---- satırları çevirme ----

    @Test
    fun translateCuesKeepsTimingsTranslatesTextAndSkipsSymbolsAndRepeats() = runTest {
        val translator = FakeTranslator()
        val cues = listOf(
            cue(1_000, 2_000, "Yes."),
            cue(3_000, 4_000, "♪"),
            cue(5_000, 6_000, "Yes."),
            cue(7_000, 9_000, "- Hi!\n- Hello!"),
        )
        val progress = mutableListOf<Int>()
        val out = SubtitleTranslation.translateCues(cues, translator, onProgress = { synchronized(progress) { progress += it } })

        assertEquals(cues.map { it.startMs to it.endMs }, out.map { it.startMs to it.endMs })
        assertEquals(listOf("[tr] Yes.", "♪", "[tr] Yes.", "- [tr] Hi!\n- [tr] Hello!"), out.map { it.text })
        assertEquals("aynı metin bir kez çevrilir", 3, translator.calls.get())
        assertEquals(100, progress.max())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun aFewFailedPartsKeepTheOriginalTextButManyFailuresAbort() = runTest {
        val cues = (1..50).map { cue(it * 1000L, it * 1000L + 500, "Line $it") }
        val some = SubtitleTranslation.translateCues(cues, FakeTranslator { it == "Line 7" })
        assertEquals("Line 7", some[6].text)
        assertEquals("[tr] Line 8", some[7].text)

        val outcome = runCatching { SubtitleTranslation.translateCues(cues, FakeTranslator { true }) }
        assertTrue(outcome.exceptionOrNull() is TranslationException)
        val partial = runCatching { SubtitleTranslation.translateCues(cues, FakeTranslator { it.removePrefix("Line ").toInt() % 2 == 0 }) }
        assertTrue("yarısı başarısızsa iş başarısız sayılır", partial.exceptionOrNull() is TranslationException)
    }

    // ---- aday seçimi ----

    private fun ext(url: String, lang: String?, kind: SubtitleKind = SubtitleKind.Source) =
        SubtitleItem.External(SubtitleTrack(url, lang, "Kaynak", kind))

    @Test
    fun candidatesOnlyWhenNoRealTurkishAndEnglishExists() {
        val preferred = listOf("tr", "en")
        val english = ext("https://s/en.srt", "en")
        assertEquals(listOf(english.track), SubtitleTranslation.candidates(listOf(english), preferred))
        // Gerçek Türkçe (harici ya da videonun içinde) varsa çeviri yok.
        assertTrue(SubtitleTranslation.candidates(listOf(english, ext("https://s/tr.srt", "tr")), preferred).isEmpty())
        assertTrue(SubtitleTranslation.candidates(listOf(english, SubtitleItem.Embedded(0, "tr")), preferred).isEmpty())
        // Çevrilmiş parça Türkçe sayılmaz (yeniden çevirmeyi tetiklemez ama gerçek Türkçe yerine geçmez).
        val translated = ext("translated:abc", "tr", SubtitleKind.Translated)
        assertFalse(SubtitleTranslation.hasRealTarget(listOf(english, translated)))
        assertEquals(listOf(english.track), SubtitleTranslation.candidates(listOf(english, translated), preferred))
        // Yalnızca videonun içindeki İngilizce çevrilemez; İngilizce hiç yoksa aday yok.
        assertTrue(SubtitleTranslation.candidates(listOf(SubtitleItem.Embedded(0, "en")), preferred).isEmpty())
        assertTrue(SubtitleTranslation.candidates(listOf(ext("https://s/de.srt", "de")), preferred).isEmpty())
        // Denenmiş adres atlanır.
        assertTrue(SubtitleTranslation.candidates(listOf(english), preferred, skip = setOf("https://s/en.srt")).isEmpty())
    }

    @Test
    fun candidatesAreEmptyWhenTurkishIsNotThePreferredLanguage() {
        val english = ext("https://s/en.srt", "en")
        assertTrue(SubtitleTranslation.candidates(listOf(english), listOf("de", "en")).isEmpty())
        assertTrue(SubtitleTranslation.candidates(listOf(english), listOf("en", "tr")).isEmpty())
        assertTrue(SubtitleTranslation.candidates(listOf(english), emptyList()).isEmpty())
    }

    // ---- SRT ----

    @Test
    fun srtRoundTripsThroughTheParserWithTimingsAndLineBreaks() {
        val cues = listOf(cue(0, 1_500, "Merhaba"), cue(3_661_007, 3_663_000, "İki satır\nşöyle böyle ğüşıöç"))
        val srt = SubtitleSrt.write(cues)
        assertTrue(srt.startsWith("1\n00:00:00,000 --> 00:00:01,500\nMerhaba\n\n2\n01:01:01,007 --> 01:01:03,000\n"))
        assertEquals(cues, SubtitleParser.parse(srt))
    }

    // ---- önbellek ----

    private val request = StreamRequest(603, "tt0133093", MediaType.Movie)
    private val source = SubtitleTrack("https://s/en.srt", "en", "Kaynak")

    @Test
    fun storeWritesAndReadsBackAndIsKeyedByContentAndSourceUrl() {
        val store = TranslatedSubtitleStore(tmp.newFolder("subs"))
        assertNull(store.cached(request, source))

        val track = store.trackFor(request, source)
        assertEquals(SubtitleKind.Translated, track.kind)
        assertEquals("tr", track.language)
        assertTrue(track.url.startsWith("translated:"))
        assertFalse("özgün adres parça adresine girmez", track.url.contains("s/en.srt"))

        assertTrue(store.write(track, listOf(cue(1_000, 2_000, "Merhaba dünya"))))
        assertEquals(track, store.cached(request, source))
        assertEquals(listOf(cue(1_000, 2_000, "Merhaba dünya")), store.read(track))

        // Başka içerik ya da başka kaynak adresi başka kayıttır.
        assertNull(store.cached(request.copy(tmdbId = 604), source))
        assertNull(store.cached(request, source.copy(url = "https://s/other.srt")))
        assertNotNull(store.cached(request, source.copy(origin = "Başka ad")))
    }

    @Test
    fun storeRejectsForeignTracks() {
        val store = TranslatedSubtitleStore(tmp.newFolder("subs"))
        val fake = SubtitleTrack("translated:../../x", "tr", "x", SubtitleKind.Translated)
        assertNull(store.read(fake))
        assertFalse(store.write(fake, listOf(cue(0, 1, "x"))))
        assertNull(store.read(SubtitleTrack("translated:abc", "tr", "x", SubtitleKind.Source)))
    }

    @Test
    fun loaderReadsTranslatedTracksFromCacheAndDelegatesOthers() = runTest {
        val store = TranslatedSubtitleStore(tmp.newFolder("subs"))
        val track = store.trackFor(request, source)
        val delegated = mutableListOf<String>()
        val loader = store.loader { t -> delegated += t.url; SubtitleLoad.Failed(SubtitleLoad.Reason.Http) }

        assertTrue(loader.load(track) is SubtitleLoad.Failed) // henüz yazılmadı
        store.write(track, listOf(cue(0, 1_000, "Selam")))
        val loaded = loader.load(track) as SubtitleLoad.Loaded
        assertEquals("Selam", loaded.cues.single().text)
        assertTrue(loaded.fromCache)
        loader.load(source)
        assertEquals(listOf(source.url), delegated)
    }

    @Test
    fun storePrunesOldFiles() {
        var now = System.currentTimeMillis()
        val dir = tmp.newFolder("subs")
        val store = TranslatedSubtitleStore(dir, clock = { now })
        val old = store.trackFor(request, source)
        store.write(old, listOf(cue(0, 1_000, "Eski")))
        now += TranslatedSubtitleStore.MAX_AGE_MS + 24 * 60 * 60 * 1000L
        store.write(store.trackFor(request, source.copy(url = "https://s/new.srt")), listOf(cue(0, 1_000, "Yeni")))
        assertNull("30 günden eski çeviri silinir", store.read(old))
    }
}
