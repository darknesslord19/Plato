package com.kaan.play.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.TrackLoader
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleOffset
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleSelection
import com.kaan.play.data.subtitle.SubtitleTimeline
import com.kaan.play.data.subtitle.SubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Altyazı katmanının oynatıcıdan ihtiyaç duyduğu şeyler ([PlaybackController] uygular; testlerde sahtesi kullanılır). */
interface SubtitleHost {
    fun positionMs(): Long

    fun embeddedSubtitles(): List<EmbeddedSubtitle>

    fun selectEmbeddedSubtitle(index: Int)

    fun disableEmbeddedSubtitles()
}

/** Altyazı yükleme durumu (yalnızca oynatıcıda küçük bir bildirim olarak görünür). */
enum class SubtitleStatus { Idle, Loading, Failed }

/**
 * Oynatıcının altyazı katmanı. Altyazı kaynakları tek listede toplanır: videonun içindekiler, akışın kendi altyazıları ve
 * altyazı addon'ları ([SubtitleProvider]); aynı adres tek kez görünür. Seçim:
 *
 * - Varsayılan: bu içerik için hatırlanan dil, sonra uygulama dili, sonra İngilizce ([SubtitleSelection]). Kullanıcı
 *   kendisi seçene kadar, daha iyi bir dil geç gelirse (addon geç yanıt verirse) seçim ona yükselir, asla geri düşmez.
 *   Kullanıcının seçimi (kapatmak dahil) o film/dizi için hatırlanır ve dizide sonraki bölümlerde uygulanır.
 * - Videonun içindeki parça Media3 ile çizilir. Harici altyazı [SubtitleLoader] ile indirilir (kodlama UTF-8'e çevrilir),
 *   ayrıştırılır ve satırlar burada, oynatma konumuna göre seçilip `SubtitleView`'a verilir. Böylece senkron kaydırması
 *   (±0,1 / ±0,5 sn) oynatmayı hiç kesmeden, anında uygulanır; kaydırma (içerik + altyazı) başına kalıcıdır.
 *
 * Her şey ana iş parçacığında çalışır ([scope] = etkinliğin yaşam döngüsü); ağ/disk işi yükleyicide arka planda yapılır.
 */
class SubtitleController(
    private val scope: CoroutineScope,
    private val playback: SubtitleHost,
    private val request: StreamRequest,
    private val loader: TrackLoader,
    private val memory: SubtitleMemory,
    private val provider: SubtitleProvider,
    /** Uygulama dili, iki harfli (`tr`/`en`). */
    private val appLanguage: () -> String,
    /** Etkinlik kapansa da tamamlanması gereken yazmalar (kalıcı hafıza) için uygulama kapsamı. */
    private val persist: CoroutineScope,
) {

    /** Menüde gösterilen tüm seçenekler (aynı adresler ayıklanmış). */
    var items by mutableStateOf<List<SubtitleItem>>(emptyList())
        private set

    /** Açık olan altyazı; null: kapalı. */
    var selected by mutableStateOf<SubtitleItem?>(null)
        private set

    var status by mutableStateOf(SubtitleStatus.Idle)
        private set

    /** Açık harici altyazının senkron kaydırması (ms); + geç, − erken. */
    var offsetMs by mutableLongStateOf(0L)
        private set

    /** Altyazı addon'larından cevap bekleniyor mu? */
    var searching by mutableStateOf(true)
        private set

    /** Cevap vermeyen altyazı addon'ları. */
    var failedSources by mutableStateOf<List<StreamSource>>(emptyList())
        private set

    private var embedded: List<SubtitleItem.Embedded> = emptyList()
    private var streamTracks: List<SubtitleTrack> = emptyList()
    private var addonTracks: List<SubtitleTrack> = emptyList()
    private val badUrls = HashSet<String>()

    private var preferred: List<String> = emptyList()
    private var ready = false
    private var userChose = false
    private var autoRank: Int? = null

    private var timeline: SubtitleTimeline? = null
    private var shown: List<SubtitleCue> = emptyList()
    private var sink: ((List<SubtitleCue>) -> Unit)? = null
    private var loadJob: Job? = null
    private var saveOffsetJob: Job? = null

    /** Altyazı katmanını çizen görünüm (oynatıcı ekranı bağlar). */
    fun attach(cueSink: ((List<SubtitleCue>) -> Unit)?) {
        sink = cueSink
        shown = emptyList()
        cueSink?.invoke(emptyList())
        refresh()
    }

    /** Hatırlanan dili okur, altyazı addon'larını sorar, satır zamanlayıcısını başlatır. Bir kez çağrılır. */
    fun start() {
        scope.launch {
            val remembered = memory.language(request)
            preferred = SubtitleSelection.preferredLanguages(remembered, appLanguage())
            ready = true
            autoSelect()
        }
        scope.launch {
            provider.subtitles(request)
                .onCompletion { searching = false }
                .collect { result ->
                    when (result) {
                        is SubtitleResult.Found -> {
                            addonTracks = addonTracks + result.tracks
                            rebuild()
                        }
                        is SubtitleResult.Failed -> failedSources = failedSources + result.source
                    }
                }
        }
        scope.launch {
            while (isActive) {
                refresh()
                delay(TICK_MS)
            }
        }
    }

    /** Akış (yeniden) yüklendi: akışın kendi altyazıları listeye girer. */
    fun setStream(stream: PlayableStream) {
        streamTracks = stream.subtitles.map {
            SubtitleTrack(it.url, SubtitleLanguages.normalize(it.language), stream.source.addonName)
        }
        rebuild()
    }

    /** Videonun içindeki parçalar değişti (Media3 parça listesi güncellendi). */
    fun onTracksChanged() {
        val next = playback.embeddedSubtitles().map { SubtitleItem.Embedded(it.index, SubtitleLanguages.normalize(it.language)) }
        if (next == embedded) return
        embedded = next
        rebuild()
    }

    /**
     * Kullanıcı bir seçenek seçti ya da altyazıyı kapattı ([item] null). Dil hatırlanır (dizide sonraki bölümlerde uygulanır) ve
     * otomatik seçim bir daha karışmaz. İndirilen altyazının dili, altyazı gerçekten yüklenince hatırlanır: yüklenemeyen bir seçim
     * dil tercihi sayılmaz.
     */
    fun choose(item: SubtitleItem?) {
        userChose = true
        val alreadyLoaded = item != null && item == selected && status == SubtitleStatus.Idle && (item is SubtitleItem.Embedded || timeline != null)
        if (!alreadyLoaded) apply(item)
        when (item) {
            null -> remember(SubtitleSelection.REMEMBERED_OFF)
            is SubtitleItem.Embedded -> remember(item.language)
            is SubtitleItem.External -> if (alreadyLoaded) remember(item.language)
        }
    }

    private fun remember(language: String?) {
        persist.launch { memory.rememberLanguage(request, language) }
    }

    /** Elle senkron: [deltaMs] kadar kaydırır (sınırlı), anında uygular, kalıcı saklar. */
    fun adjustOffset(deltaMs: Long) = setOffset(SubtitleOffset.step(offsetMs, deltaMs))

    fun resetOffset() = setOffset(0L)

    /** Etkinlik durunca bekleyen kaydırma yazmasını hemen tamamlar. */
    fun flush() {
        val job = saveOffsetJob ?: return
        saveOffsetJob = null
        job.cancel()
        saveOffset()
    }

    fun clearStatus() {
        if (status == SubtitleStatus.Failed) status = SubtitleStatus.Idle
    }

    // ---- iç işler ----

    private fun rebuild() {
        val all = embedded + streamTracks.map(SubtitleItem::External) + addonTracks.map(SubtitleItem::External)
        items = SubtitleSelection.dedupe(all)
        // Açık videonun-içi parça artık yoksa (kaynak değişti), seçim düşer ve otomatik seçim yeniden değerlendirilir.
        val current = selected
        if (current is SubtitleItem.Embedded && current !in items) {
            selected = null
            userChose = false
            autoRank = null
        }
        autoSelect()
    }

    private fun autoSelect() {
        if (!ready || userChose) return
        val candidates = items.filter { it !is SubtitleItem.External || it.track.url !in badUrls }
        val pick = SubtitleSelection.pickDefault(candidates, preferred) ?: return
        val rank = SubtitleSelection.rank(pick.language, preferred) ?: return
        val best = autoRank
        if (selected != null && best != null && rank >= best) return
        autoRank = rank
        apply(pick)
    }

    /** Hafızaya yazmadan seçimi uygular. */
    private fun apply(item: SubtitleItem?) {
        loadJob?.cancel()
        timeline = null
        offsetMs = 0L
        selected = item
        status = SubtitleStatus.Idle
        when (item) {
            null -> {
                playback.disableEmbeddedSubtitles()
                refresh()
            }
            is SubtitleItem.Embedded -> {
                playback.selectEmbeddedSubtitle(item.index)
                refresh()
            }
            is SubtitleItem.External -> {
                playback.disableEmbeddedSubtitles()
                status = SubtitleStatus.Loading
                refresh()
                loadJob = scope.launch { loadExternal(item) }
            }
        }
    }

    private suspend fun loadExternal(item: SubtitleItem.External) {
        val savedOffset = memory.offset(request, item.track.url)
        when (val result = loader.load(item.track)) {
            is SubtitleLoad.Loaded -> {
                if (selected != item) return
                timeline = SubtitleTimeline(result.cues)
                offsetMs = savedOffset
                status = SubtitleStatus.Idle
                if (userChose) remember(item.language)
                refresh()
            }
            is SubtitleLoad.Failed -> {
                if (selected != item) return
                badUrls += item.track.url
                selected = null
                autoRank = null
                status = SubtitleStatus.Failed
                refresh()
                // Kullanıcı seçmediyse sıradaki aday denenir; seçtiyse hata bildirimi yeter.
                autoSelect()
            }
        }
    }

    private fun setOffset(value: Long) {
        val track = (selected as? SubtitleItem.External)?.track ?: return
        if (timeline == null || value == offsetMs) return
        offsetMs = value
        refresh()
        saveOffsetJob?.cancel()
        saveOffsetJob = persist.launch {
            delay(SAVE_DEBOUNCE_MS)
            memory.saveOffset(request, track.url, value)
        }
    }

    private fun saveOffset() {
        val track = (selected as? SubtitleItem.External)?.track ?: return
        val value = offsetMs
        persist.launch { memory.saveOffset(request, track.url, value) }
    }

    /** Şimdiki konumda görünmesi gereken satırları hesaplar; değiştiyse görünüme verir. */
    private fun refresh() {
        val sink = sink ?: return
        val active = timeline?.activeAt(playback.positionMs(), offsetMs).orEmpty()
        if (active == shown) return
        shown = active
        sink(active)
    }

    private companion object {
        const val TICK_MS = 50L
        const val SAVE_DEBOUNCE_MS = 300L
    }
}
