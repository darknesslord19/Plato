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
-keepattributes KotlinMetadata
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
