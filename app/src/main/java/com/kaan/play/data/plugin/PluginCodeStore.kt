package com.kaan.play.data.plugin

import java.io.File

/** Kazıyıcı kodunun saklandığı yer. Arayüz olarak ayrı: testlerde bellek içi/geçici dizin kullanılır. */
interface PluginCodeStore {
    /** Kodu (varsa üzerine) atomik yazar. */
    fun write(pluginId: Long, code: String)

    fun read(pluginId: Long): String?

    fun delete(pluginId: Long)
}

/**
 * Uygulamaya özel dizinde (`filesDir/plugins`) `{satır kimliği}.js` olarak saklar. Dosya adı yalnızca sayıdır: eklentinin
 * kendi kimliği ya da adı yol olarak kullanılmaz (yol gezinme sorunu yok). Yazma önce geçici dosyaya, sonra yeniden adlandırma.
 */
class FilePluginCodeStore(private val dir: File) : PluginCodeStore {

    override fun write(pluginId: Long, code: String) {
        dir.mkdirs()
        val target = fileOf(pluginId)
        val tmp = File(dir, "${pluginId}.js.tmp")
        tmp.writeText(code, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Kod dosyası yazılamadı" }
        }
    }

    override fun read(pluginId: Long): String? =
        fileOf(pluginId).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    override fun delete(pluginId: Long) {
        fileOf(pluginId).delete()
    }

    private fun fileOf(pluginId: Long) = File(dir, "$pluginId.js")
}
