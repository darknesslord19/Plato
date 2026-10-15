package com.kaan.play.data.subtitle.translate

import com.kaan.play.data.subtitle.SubtitleCue
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Tek metin parçasını çeviren ince arayüz (cihaz üstü ML Kit'i sarar; testlerde sahtesi kullanılır). */
fun interface TextTranslator {
    suspend fun translate(text: String): String
}

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Altyazı satırlarını çeviriye hazırlar ve çevrilmiş metni geri birleştirir (Android'den bağımsız). Zamanlar olduğu gibi kalır;
 * yalnızca metin değişir. Metin zaten düz metindir ([com.kaan.play.data.subtitle.SubtitleParser] ASS/HTML etiketlerini atar).
 *
 * - Tek satırlı replik olduğu gibi çevrilir.
 * - Her satırı "-" ile başlayan replik (iki kişi konuşuyor) satır satır çevrilir, konuşmacı çizgileri korunur.
 * - Diğer çok satırlı replikler tek cümle gibi çevrilir (satır sonu yerine boşluk) ve özgün satır sayısına, özgün satır
 *   uzunluklarıyla orantılı bölünür.
 * - Harf içermeyen satırlar (♪, sayılar) çevrilmez. Aynı metin bir kez çevrilir.
 */
object SubtitleTranslation {

    /** Aynı anda çalışan çeviri çağrısı sayısı. */
    const val PARALLELISM = 4

    const val TARGET = "tr"
    const val SOURCE = "en"

    private const val EARLY_ABORT = 10
    private const val SENTENCE_BONUS = 15.0
    private const val COMMA_BONUS = 5.0
    private const val MIN_TOLERATED = 5

    /** Çevrilecek parçalar + çevrilmişleri satırlara geri koyan işlev. */
    class Plan(val parts: List<String>, private val joiner: (List<String>) -> String) {
        fun combine(translated: List<String>): String = joiner(translated)
    }

    private val DASHES = charArrayOf('-', '–', '—')

    fun plan(text: String): Plan {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return Plan(lines) { it.firstOrNull() ?: "" }
        if (lines.all { it.first() in DASHES }) {
            val stripped = lines.map { it.trimStart(*DASHES).trim() }
            return Plan(stripped) { out -> out.joinToString("\n") { "- " + it.trimStart(*DASHES).trim() } }
        }
        return Plan(listOf(lines.joinToString(" "))) { out -> redistribute(out[0], lines.map { it.length }) }
    }

    fun isTranslatable(part: String): Boolean = part.any { it.isLetter() }

    /**
     * [translated] metnini [originalLengths].size satıra böler; bölme noktaları kelime sınırındadır ve toplam uzunluğu özgün satır
     * uzunluklarının oranına en çok yaklaştıran yerdir. Kelime sayısı satır sayısından azsa daha az satır çıkar.
     */
    fun redistribute(translated: String, originalLengths: List<Int>): String {
        val words = translated.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lineCount = minOf(originalLengths.size, words.size)
        if (lineCount <= 1) return words.joinToString(" ")
        // ends[j]: ilk j kelimenin toplam uzunluğu (aralardaki boşluklar dahil).
        val ends = IntArray(words.size + 1)
        for (i in words.indices) ends[i + 1] = ends[i] + words[i].length + (if (i > 0) 1 else 0)
        val total = ends[words.size].toDouble()
        val weightSum = originalLengths.sum().coerceAtLeast(1).toDouble()
        val lines = ArrayList<String>(lineCount)
        var from = 0
        var cumulative = 0
        for (k in 0 until lineCount - 1) {
            cumulative += originalLengths[k]
            val target = total * cumulative / weightSum
            // Bu satırda en az bir kelime olmalı, kalan satırlara da birer kelime kalmalı.
            val lo = from + 1
            val hi = words.size - (lineCount - 1 - k)
            var best = lo
            var bestCost = Double.MAX_VALUE
            for (j in lo..hi) {
                // Cümle sonu (ya da virgül) hemen öncesindeki kesim, hedefe biraz uzak olsa da tercih edilir.
                val cost = kotlin.math.abs(ends[j] - target) - breakBonus(words[j - 1])
                if (cost < bestCost) {
                    best = j
                    bestCost = cost
                }
            }
            lines += words.subList(from, best).joinToString(" ")
            from = best
        }
        lines += words.subList(from, words.size).joinToString(" ")
        return lines.joinToString("\n")
    }

    /** Kelimenin sonundaki noktalamaya göre kesim ödülü (karakter cinsinden): cümle sonu > virgül/noktalı virgül. */
    private fun breakBonus(word: String): Double = when (word.trimEnd('"', '\'', ')', ']', '»', '”').lastOrNull()) {
        '.', '!', '?', '…' -> SENTENCE_BONUS
        ',', ';', ':' -> COMMA_BONUS
        else -> 0.0
    }

    /**
     * Tüm satırları çevirir. [onProgress]: 0..100 (artınca çağrılır; birden çok iş parçacığından gelebilir). Çok sayıda çağrı
     * başarısız olursa [TranslationException] ile durur; tek tük başarısız parça özgün metinle kalır.
     */
    suspend fun translateCues(
        cues: List<SubtitleCue>,
        translator: TextTranslator,
        parallelism: Int = PARALLELISM,
        onProgress: (Int) -> Unit = {},
    ): List<SubtitleCue> {
        val plans = cues.map { plan(it.text) }
        val unique = plans.flatMap { it.parts }.filter(::isTranslatable).distinct()
        val results = ConcurrentHashMap<String, String>()
        val succeeded = AtomicInteger()
        val failed = AtomicInteger()
        val finished = AtomicInteger()
        val permits = Semaphore(parallelism.coerceAtLeast(1))
        val lock = Any()
        var lastPercent = -1
        coroutineScope {
            unique.map { part ->
                async {
                    permits.withPermit {
                        try {
                            val out = translator.translate(part).trim()
                            if (out.isNotEmpty()) results[part] = out
                            succeeded.incrementAndGet()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Hiçbiri çalışmıyorsa (model yok, motor bozuk) bütün işi boşuna sürdürme.
                            if (failed.incrementAndGet() > EARLY_ABORT && succeeded.get() == 0) {
                                throw TranslationException("Çeviri çalışmıyor", e)
                            }
                        }
                        val percent = finished.incrementAndGet() * 100 / unique.size
                        synchronized(lock) {
                            if (percent > lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        if (failed.get() > maxOf(MIN_TOLERATED, unique.size / 10)) throw TranslationException("Çok sayıda satır çevrilemedi")
        return cues.mapIndexed { i, cue ->
            val plan = plans[i]
            val text = plan.combine(plan.parts.map { results[it] ?: it })
            cue.copy(text = text.ifBlank { cue.text })
        }
    }

    /**
     * Çevrilecek İngilizce altyazı adayları (listedeki sırayla): Türkçe, tercih sırasında İngilizceden önce geliyorsa ve hiçbir
     * kaynakta (videonun içi dahil) gerçek Türkçe yoksa, İngilizce harici altyazılar. Yoksa boş. [skip]: denenmiş adresler.
     */
    fun candidates(items: List<SubtitleItem>, preferred: List<String>, skip: Set<String> = emptySet()): List<SubtitleTrack> {
        val tr = preferred.indexOf(TARGET)
        val en = preferred.indexOf(SOURCE)
        if (tr < 0 || (en in 0 until tr)) return emptyList()
        if (hasRealTarget(items)) return emptyList()
        return items.filterIsInstance<SubtitleItem.External>()
            .map { it.track }
            .filter { it.language == SOURCE && it.kind != SubtitleKind.Translated && it.url !in skip }
    }

    /** Listede çeviri olmayan gerçek bir Türkçe altyazı (videonun içinde ya da herhangi bir kaynakta) var mı? */
    fun hasRealTarget(items: List<SubtitleItem>): Boolean = items.any {
        it.language == TARGET && !(it is SubtitleItem.External && it.track.kind == SubtitleKind.Translated)
    }
}

/** Çevrilmiş altyazıyı SRT metnine yazar (UTF-8; önbellek biçimi). */
object SubtitleSrt {
    fun write(cues: List<SubtitleCue>): String = buildString {
        cues.forEachIndexed { i, cue ->
            append(i + 1).append('\n')
            append(time(cue.startMs)).append(" --> ").append(time(cue.endMs)).append('\n')
            append(cue.text.trim()).append("\n\n")
        }
    }

    fun time(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return "%02d:%02d:%02d,%03d".format(t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, t % 1000)
    }
}
