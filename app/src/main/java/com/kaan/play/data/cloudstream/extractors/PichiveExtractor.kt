package com.kaan.play.data.cloudstream.extractors

import android.net.Uri
import android.util.Log
import com.kaan.play.ui.player.PlaybackController
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONArray
import org.json.JSONObject

class PichiveExtractor(
    override val name: String = "Pichive",
    override val mainUrl: String = "https://four.pichive.online",
    override val requiresReferer: Boolean = false,
) : ExtractorApi() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            // Hotlinger / dplayer alias rewrite
            val actualUrl = url
                .replace("sn.hotlinger.com", "four.pichive.online")
                .replace("sn.dplayer82.site", "four.pichive.online")
                .replace("dplayer82.site", "four.pichive.online")
                .replace("v.pichive.online", "four.pichive.online")

            val headers = mapOf(
                "User-Agent" to PlaybackController.DEFAULT_USER_AGENT,
                "Referer" to (referer ?: "https://selcukflix.app/")
            )

            val res = app.get(actualUrl, headers = headers)
            val html = res.text

            // openPlayer('playlist', ...)
            val openPlayerRegex = Regex("""openPlayer\s*\(\s*['"]([^'"]+)['"]""")
            val match = openPlayerRegex.find(html)
            if (match == null) {
                // Direct m3u8 fallback in html
                val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
                m3u8Regex.findAll(html).forEach { m ->
                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name (Auto)",
                            url = m.value,
                            referer = actualUrl,
                            quality = Qualities.P1080.value,
                            type = ExtractorLinkType.M3U8,
                            headers = mapOf("Referer" to actualUrl)
                        )
                    )
                }
                return
            }

            val playList = match.groupValues[1]
            val uri = Uri.parse(actualUrl)
            val vParam = uri.getQueryParameter("v") ?: ""
            val host = uri.host ?: "four.pichive.online"
            val scheme = uri.scheme ?: "https"
            val baseUrl = "$scheme://$host"

            val sourceUrl = "$baseUrl/source2.php?v=${Uri.encode(playList)}"
            val sourceHeaders = mapOf(
                "User-Agent" to PlaybackController.DEFAULT_USER_AGENT,
                "Referer" to "$baseUrl/iframe.php?v=$vParam",
                "X-Requested-With" to "XMLHttpRequest"
            )

            val sourceRes = app.get(sourceUrl, headers = sourceHeaders)
            val json = JSONObject(sourceRes.text)
            if (!json.optBoolean("state", false)) return

            val playlistArray = json.optJSONArray("playlist") ?: return
            for (i in 0 until playlistArray.length()) {
                val item = playlistArray.optJSONObject(i) ?: continue
                val sourcesArray = item.optJSONArray("sources") ?: continue
                for (j in 0 until sourcesArray.length()) {
                    val s = sourcesArray.optJSONObject(j) ?: continue
                    val file = s.optString("file").ifBlank { null } ?: continue
                    val title = s.optString("title").ifBlank { "Auto" }
                    val isHls = s.optString("type") == "hls" || file.contains(".m3u8")

                    callback(
                        ExtractorLink(
                            source = name,
                            name = "$name ($title)",
                            url = file,
                            referer = "$baseUrl/",
                            quality = Qualities.P1080.value,
                            type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                            headers = mapOf("Referer" to "$baseUrl/")
                        )
                    )
                }
            }

            // Also check for subtitles in openPlayer call: openPlayer(..., [tracks])
            val subTracksRegex = Regex("""openPlayer\s*\([^\)]*?(\[\s*\{.*?\}\s*\])\s*\)""")
            val subMatch = subTracksRegex.find(html)
            if (subMatch != null) {
                try {
                    val tracksJson = JSONArray(subMatch.groupValues[1])
                    for (k in 0 until tracksJson.length()) {
                        val track = tracksJson.getJSONObject(k)
                        val subFile = track.optString("file")
                        val subLabel = track.optString("label", "Türkçe")
                        if (subFile.isNotBlank()) {
                            subtitleCallback(SubtitleFile(subLabel, subFile))
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (t: Throwable) {
            Log.w("PichiveExtractor", "Error extracting from $url: ${t.message}", t)
        }
    }
}
