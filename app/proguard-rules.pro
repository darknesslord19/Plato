# kotlinx.serialization: @Serializable sınıfların serializer'larını koru
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kaan.play.**$$serializer { *; }
-keepclassmembers class com.kaan.play.** { *** Companion; }
-keepclasseswithmembers class com.kaan.play.** { kotlinx.serialization.KSerializer serializer(...); }
