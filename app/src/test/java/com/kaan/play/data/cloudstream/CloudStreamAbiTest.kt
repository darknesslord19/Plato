package com.kaan.play.data.cloudstream

import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gerçek CloudStream eklentilerinin Play'deki köprü sınıflara yaptığı başvurular (227 gerçek eklentinin dex'inden
 * çıkarıldı; bkz. tools/cs_plugin_abi_check.py). Buradaki her satır bir eklentinin derlenmiş koduna adla ve imzayla
 * yazılıdır: biri kaybolursa o eklentiler Android'de NoClassDefFoundError/NoSuchMethodError ile hiç yüklenmez.
 * Bu yüzden köprü sınıflar değiştirilirken bu liste korunur.
 */
class CloudStreamAbiTest {

    /** "sınıf|metot|(parametreler)dönüş" — JVM tanımlayıcısı (descriptor) biçiminde. */
    private val requiredMethods = listOf(
        // 94/227 eklenti: kurucuda ve load() içinde ayar okur
        "com.lagradost.cloudstream3.CloudStreamApp\$Companion|getContext|()Landroid/content/Context;",
        "com.lagradost.cloudstream3.CloudStreamApp\$Companion|setKey|(Ljava/lang/String;Ljava/lang/Object;)V",
        "com.lagradost.cloudstream3.CloudStreamApp\$Companion|setKey|(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
        "com.lagradost.cloudstream3.AcraApplication\$Companion|getContext|()Landroid/content/Context;",
        // 89/227
        "com.lagradost.cloudstream3.CommonActivity|getActivity|()Landroid/app/Activity;",
        "com.lagradost.cloudstream3.CommonActivity|showToast|(Ljava/lang/String;Ljava/lang/Integer;)V",
        "com.lagradost.cloudstream3.CommonActivity|showToast|(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/Integer;)V",
        "com.lagradost.cloudstream3.CommonActivity|showToast\$default|(Lcom/lagradost/cloudstream3/CommonActivity;Ljava/lang/String;Ljava/lang/Integer;ILjava/lang/Object;)V",
        // 22/227: satır içi getKey'in derlenmiş hâli
        "com.lagradost.cloudstream3.utils.DataStore|getSharedPrefs|(Landroid/content/Context;)Landroid/content/SharedPreferences;",
        "com.lagradost.cloudstream3.utils.DataStore|getDefaultSharedPrefs|(Landroid/content/Context;)Landroid/content/SharedPreferences;",
        "com.lagradost.cloudstream3.utils.DataStore|getFolderName|(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        "com.lagradost.cloudstream3.MainActivity\$Companion|getAfterPluginsLoadedEvent|()Lcom/lagradost/cloudstream3/utils/Event;",
        "com.lagradost.cloudstream3.MainActivity\$Companion|getReloadHomeEvent|()Lcom/lagradost/cloudstream3/utils/Event;",
        "com.lagradost.cloudstream3.MainActivity\$Companion|getBookmarksUpdatedEvent|()Lcom/lagradost/cloudstream3/utils/Event;",
        "com.lagradost.cloudstream3.MainActivity\$Companion|getReloadLibraryEvent|()Lcom/lagradost/cloudstream3/utils/Event;",
        "com.lagradost.cloudstream3.utils.Event|invoke|(Ljava/lang/Object;)V",
        "com.lagradost.cloudstream3.utils.Event|plusAssign|(Lkotlin/jvm/functions/Function1;)V",
        "com.lagradost.cloudstream3.plugins.Plugin|setOpenSettings|(Lkotlin/jvm/functions/Function1;)V",
        "com.lagradost.cloudstream3.plugins.Plugin|getResources|()Landroid/content/res/Resources;",
        "com.lagradost.cloudstream3.network.CloudflareKiller|getSavedCookies|()Ljava/util/Map;",
        "com.lagradost.cloudstream3.utils.AppContextUtils|loadResult|(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V",
    )

    /** Eklentilerin statik alan olarak okuduğu nesne örnekleri. */
    private val requiredFields = listOf(
        "com.lagradost.cloudstream3.CloudStreamApp|Companion",
        "com.lagradost.cloudstream3.AcraApplication|Companion",
        "com.lagradost.cloudstream3.CommonActivity|INSTANCE",
        "com.lagradost.cloudstream3.MainActivity|Companion",
        "com.lagradost.cloudstream3.utils.DataStore|INSTANCE",
        "com.lagradost.cloudstream3.utils.AppContextUtils|INSTANCE",
    )

    private fun descriptor(m: java.lang.reflect.Method): String =
        "(" + m.parameterTypes.joinToString("") { desc(it) } + ")" + desc(m.returnType)

    private fun desc(c: Class<*>): String = when {
        c.isArray -> "[" + desc(c.componentType!!)
        c == Void.TYPE -> "V"
        c == Integer.TYPE -> "I"
        c == java.lang.Long.TYPE -> "J"
        c == java.lang.Boolean.TYPE -> "Z"
        c == java.lang.Float.TYPE -> "F"
        c == java.lang.Double.TYPE -> "D"
        c == java.lang.Byte.TYPE -> "B"
        c == java.lang.Short.TYPE -> "S"
        c == Character.TYPE -> "C"
        else -> "L" + c.name.replace('.', '/') + ";"
    }

    @Test
    fun `eklentilerin bagladigi metotlar ayni imzayla var`() {
        val missing = requiredMethods.filter { line ->
            val (cls, name, sig) = line.split("|")
            val c = try {
                Class.forName(cls, false, javaClass.classLoader)
            } catch (_: ClassNotFoundException) {
                return@filter true
            }
            generateSequence(c) { it.superclass }.none { k -> k.declaredMethods.any { it.name == name && descriptor(it) == sig } }
        }
        if (missing.isNotEmpty()) fail("Eksik ya da imzası değişmiş:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `eklentilerin okudugu statik alanlar var`() {
        val missing = requiredFields.filter { line ->
            val (cls, field) = line.split("|")
            try {
                Class.forName(cls, false, javaClass.classLoader).getDeclaredField(field)
                false
            } catch (_: Exception) {
                true
            }
        }
        if (missing.isNotEmpty()) fail("Eksik alan:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `CloudStreamApp gercekteki gibi uygulama sinifi ve api yardimci nesnede`() {
        val c = Class.forName("com.lagradost.cloudstream3.CloudStreamApp", false, javaClass.classLoader)
        assertEquals("android.app.Application", c.superclass.name)
        assertNotNull(c.getDeclaredField("Companion"))
    }

    @Test
    fun `Event dinleyicisi hata verse de digerleri calisir`() {
        val event = Event<Int>()
        val got = mutableListOf<Int>()
        event += { throw IllegalStateException("eklenti hatası") }
        event += { got += it }
        event(7)
        assertEquals(listOf(7), got)
    }

    @Test
    fun `Play cikaricilari bir kez ve en basta kurulur`() {
        PlayExtractors.install()
        PlayExtractors.install()
        val head: List<ExtractorApi> = extractorApis.take(PlayExtractors.custom.size)
        assertTrue(head.zip(PlayExtractors.custom).all { (a, b) -> a === b })
        val mineCount = extractorApis.count { api -> PlayExtractors.custom.any { it === api } }
        assertEquals(PlayExtractors.custom.size, mineCount)
    }
}
