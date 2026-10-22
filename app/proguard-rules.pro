# ── kotlinx.serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kaan.play.**$$serializer { *; }
-keepclassmembers class com.kaan.play.** { *** Companion; }
-keepclasseswithmembers class com.kaan.play.** { kotlinx.serialization.KSerializer serializer(...); }

# ── Room ───────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── OkHttp ─────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Coil ───────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Media3 / ExoPlayer ─────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── CloudStream eklentileri (Faz 3) ──────────────────────────────────────────────
# .cs3 eklentileri çalışma anında yüklenen ayrı DEX'lerdir; uygulamadaki sınıflara ADLA ve İMZAYLA
# bağlanırlar (Kotlin stdlib, coroutines, Jackson, jsoup, OkHttp dahil). R8 bunları kırpar ya da
# yeniden adlandırırsa eklenti NoClassDefFoundError/NoSuchMethodError verir. Kökler, gerçek
# eklentilerin dex başvurularının release APK ile karşılaştırılmasından çıkarıldı (README, Faz 3).
-keep class com.lagradost.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.jsoup.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# ── Play Provider & CloudStream modelleri ──────────────────────────────────────
-keep class com.kaan.play.data.provider.** { *; }
-keep interface com.kaan.play.data.provider.** { *; }
-keep class com.kaan.play.data.cloudstream.** { *; }
-keep class com.kaan.play.data.model.** { *; }
-keep class com.kaan.play.data.stream.** { *; }
-keep class com.kaan.play.data.db.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**

# ── QuickJS (JNI sembolleri) ────────────────────────────────────────────────────
-keep class app.cash.quickjs.** { *; }
-dontwarn app.cash.quickjs.**

# ── Kotlin coroutines ───────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── Genel: yansımayla erişilen sınıf üyeleri ───────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod

# ── libtorrent4j (yerleşik torrent motoru) ──────────────────────────────────────
# SWIG JNI: yerel kod Java sınıflarına ve yöntemlerine adla bağlanır (geri çağrılar dahil).
-keep class org.libtorrent4j.swig.** { *; }
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# ── ML Kit cihaz üstü çeviri (Türkçe altyazı otomatik çeviri) ────────────────────────────────
# ML Kit bileşenlerini ComponentDiscovery ile yansıma üzerinden, parametresiz kurucuyla yükler; R8 bu kurucuları
# silerse çeviri release'te hemen "başarısız" olur (debug'da çalışır). Kaydediciler ve yerel (JNI) çağrılar adla kalır.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.**.*Registrar { <init>(); }
-keep class com.google.mlkit.nl.translate.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.google.mlkit.**

# ── CloudStream eklentilerinin bağlandığı diğer kütüphaneler ─────────────────────
# Gerçek depolardaki 227 eklentinin dex başvurularından çıkarıldı (tools/cs_plugin_abi_check.py, CI raporu).
# R8 bunları Play kullanmıyor diye kırpar ya da yeniden adlandırırsa eklenti yalnızca release'te bozulur.
-keep class kotlinx.serialization.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.util.** { *; }
-keep class com.fleeksoft.ksoup.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class dev.whyoleg.cryptography.** { *; }
-keep class com.google.gson.** { *; }
-keep class me.xdrop.fuzzywuzzy.** { *; }
-keep class com.google.android.material.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.recyclerview.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn com.google.android.material.**
-dontwarn me.xdrop.fuzzywuzzy.**
# ── CloudStream Library Ek Bağımlılıkları ──────────────────────────────────────
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.**
-dontwarn com.fleeksoft.ksoup.**
-dontwarn dev.whyoleg.cryptography.**

