package com.lagradost.cloudstream3.utils

object JsUnpacker {
    /**
     * Dean Edwards p,a,c,k,e,d packed JavaScript paketlerini çözer.
     */
    fun unpack(packedJs: String): String? {
        val regex = Regex("""eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\('([\s\S]*?)',(\d+),(\d+),'([\s\S]*?)'\.split\('\|'\)""")
        val match = regex.find(packedJs) ?: return null
        val payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 36
        val symtab = match.groupValues[4].split("|")
        return unbase(payload, radix, symtab)
    }

    /**
     * HTML içindeki tüm packed JavaScript bloklarını bulup çözülmüş hallerini ekler.
     */
    fun unpackAll(html: String): String {
        val regex = Regex("""eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\('([\s\S]*?)',(\d+),(\d+),'([\s\S]*?)'\.split\('\|'\)""")
        val matches = regex.findAll(html).toList()
        if (matches.isEmpty()) return html
        val sb = StringBuilder(html)
        for (m in matches) {
            val payload = m.groupValues[1]
            val radix = m.groupValues[2].toIntOrNull() ?: 36
            val symtab = m.groupValues[4].split("|")
            val unpacked = unbase(payload, radix, symtab)
            sb.append("\n").append(unpacked)
        }
        return sb.toString()
    }

    private fun unbase(payload: String, radix: Int, symtab: List<String>): String {
        return Regex("""\b(\w+)\b""").replace(payload) { m ->
            val word = m.groupValues[1]
            val idx = word.toIntOrNull(radix) ?: return@replace word
            val sub = symtab.getOrNull(idx)
            if (sub.isNullOrEmpty()) word else sub
        }
    }
}
