package com.lagradost.cloudstream3.utils

import java.net.URLDecoder

object StringUtils {
    fun decodeUri(uri: String): String = try {
        URLDecoder.decode(uri, "UTF-8")
    } catch (_: Throwable) {
        uri
    }
}
