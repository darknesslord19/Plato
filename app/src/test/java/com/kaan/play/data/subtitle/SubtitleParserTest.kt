package com.kaan.play.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun parsesSubRipWithBomCrlfAndStripsHtmlTags() {
        val srt = "﻿1\r\n00:00:01,500 --> 00:00:03,250\r\n<i>Merhaba</i> dünya\r\n<font color=\"#ff0000\">İkinci</font> satır\r\n\r\n" +
            "2\r\n00:01:02,000 --> 00:01:04,000\r\nTek satır &amp; &lt;3\r\n\r\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1_500, 3_250, "Merhaba dünya\nİkinci satır"), cues[0])
        assertEquals(SubtitleCue(62_000, 64_000, "Tek satır & <3"), cues[1])
        assertEquals(SubtitleFormat.SubRip, SubtitleParser.sniff(srt))
    }

    @Test
    fun parsesSubRipWithoutBlankLinesBetweenRecordsAndIgnoresIndexNumbers() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nİlk\n2\n00:00:03,000 --> 00:00:04,000\nİkinci\n3\n00:00:05,000 --> 00:00:06,000\nÜçüncü\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(listOf("İlk", "İkinci", "Üçüncü"), cues.map { it.text }) // "2" ve "3" metne karışmaz
    }

    @Test
    fun keepsLegitimateNumbersInsideCueText() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nYıl\n1984\n\n2\n00:00:03,000 --> 00:00:04,000\nTamam\n"
        assertEquals("Yıl\n1984", SubtitleParser.parse(srt)[0].text) // boş satırlı kayıtta sondaki sayı atılmaz
    }

    @Test
    fun srtTimestampFractionsAreRightPadded() {
        val srt = "1\n00:00:01,5 --> 00:00:02,05\nx\n"
        val cue = SubtitleParser.parse(srt).single()
        assertEquals(1_500, cue.startMs)
        assertEquals(2_050, cue.endMs)
    }

    @Test
    fun parsesWebVttWithOptionalHoursSettingsNotesAndTags() {
        val vtt = """
            WEBVTT - başlık

            NOTE bu bir not
            --> değil

            STYLE
            ::cue { color: red }

            intro
            00:01.000 --> 00:02.500 align:start position:10%
            <v Ahmet>Merhaba <c.yellow>dünya</c>
            <00:00:01.800>ve <b>herkes</b>

            01:00:00.000 --> 01:00:01.000
            Bir saat sonra
        """.trimIndent()
        val cues = SubtitleParser.parse(vtt)
        assertEquals(SubtitleFormat.WebVtt, SubtitleParser.sniff(vtt))
        assertEquals(2, cues.size)
        assertEquals(SubtitleCue(1_000, 2_500, "Merhaba dünya\nve herkes"), cues[0])
        assertEquals(SubtitleCue(3_600_000, 3_601_000, "Bir saat sonra"), cues[1])
    }

    @Test
    fun parsesAssWithCommasInTextOverridesAndLineBreaks() {
        val ass = """
            [Script Info]
            Title: test
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,20

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\an8}Merhaba, dünya\NŞimdi {\i1}eğik{\i0} yazı
            Dialogue: 0,0:01:02.05,0:01:04.00,Default,,0,0,0,,Ğ, ş, ı, İ
            Dialogue: 0,0:00:10.00,0:00:11.00,Default,,0,0,0,,{\p1}m 0 0 l 10 10{\p0}
            Comment: 0,0:00:20.00,0:00:21.00,Default,,0,0,0,,not shown
        """.trimIndent()
        val cues = SubtitleParser.parse(ass)
        assertEquals(SubtitleFormat.Ssa, SubtitleParser.sniff(ass))
        assertEquals(2, cues.size) // çizim komutu ve yorum atıldı
        assertEquals(SubtitleCue(1_500, 3_000, "Merhaba, dünya\nŞimdi eğik yazı"), cues[0])
        assertEquals(SubtitleCue(62_050, 64_000, "Ğ, ş, ı, İ"), cues[1])
    }

    @Test
    fun assWithoutFormatLineUsesTheStandardColumnOrder() {
        val ass = "[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Düz\n"
        assertEquals("Düz", SubtitleParser.parse(ass).single().text)
    }

    @Test
    fun cuesAreSortedAndBrokenRecordsAreDropped() {
        val srt = "2\n00:00:05,000 --> 00:00:06,000\nSon\n\n1\n00:00:01,000 --> 00:00:02,000\nİlk\n\n3\n00:00:09,000 --> 00:00:08,000\nTers\n\n4\n00:00:10,000 --> 00:00:11,000\n\n"
        assertEquals(listOf("İlk", "Son"), SubtitleParser.parse(srt).map { it.text }) // bitişi başlangıçtan önce / metni boş olanlar düşer
    }

    @Test
    fun garbageProducesNoCues() {
        assertTrue(SubtitleParser.parse("").isEmpty())
        assertTrue(SubtitleParser.parse("<html><body>404 Not Found</body></html>").isEmpty())
        assertTrue(SubtitleParser.parse("just some text\nwithout timing").isEmpty())
    }
}
