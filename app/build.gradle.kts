import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// TMDB anahtarı local.properties'ten (ya da CI için ortam değişkeninden) okunur; git'e girmez.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tmdbApiKey: String = localProps.getProperty("TMDB_API_KEY")
    ?: System.getenv("TMDB_API_KEY")
    ?: ""

android {
    namespace = "com.kaan.play"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kaan.play"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    // Yayın imzası: keystore yolu repoda, şifreler yalnızca local.properties'te (ya da ortam değişkeninde).
    signingConfigs {
        create("release") {
            val storePassword = localProps.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            val keyPasswordValue = localProps.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")
            if (storePassword != null && keyPasswordValue != null) {
                storeFile = rootProject.file("keystore/play-release.keystore")
                this.storePassword = storePassword
                keyAlias = "play"
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            // Şifreler yoksa (ör. başka makine) imzasız derlenir; derleme kırılmaz.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Yalnızca desteklenen diller paketlensin (kütüphane çevirileri dahil).
    androidResources {
        localeFilters += listOf("en", "tr")
    }

    // Yerel kütüphaneler APK içinde sıkıştırılır (libtorrent işlemci başına ~16 MB; sıkıştırılmadan APK çok büyür).
    // İşlemci türüne göre ayrı APK (libtorrent her türde ~14-16 MB): her cihaz yalnızca kendi türünü indirir.
    // AAB (Play Store) bundan etkilenmez; mağaza bölmeyi kendisi yapar.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.quickjs.kt)
    // Faz 3 (CloudStream): gerçek eklentiler doğrudan Jackson bytecode'una derlenmiş durumda
    // (`com.fasterxml.jackson...`); kendi JSON kütüphanemiz (kotlinx.serialization) bunun yerini
    // tutamaz, bu yüzden eklenti uyumluluğu için gerçek Jackson çalışma zamanda bulunur.
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    // Yerleşik torrent motoru: Java API + işlemci başına yerel kütüphane (.so).
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.libtorrent4j.android.arm)
    implementation(libs.libtorrent4j.android.x86)
    implementation(libs.libtorrent4j.android.x64)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Room şeması sürüm sürüm repoya yazılır; göçler bunlara karşı doğrulanır.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Yerel JVM birim testleri Android'e özgü yerel kütüphaneyi yükleyemez: QuickJS testleri masaüstü (-jvm) paketini kullanır.
// Cihaz testleri (androidTest) ve uygulama kendi Android paketini kullanır.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.dokar3:quickjs-kt-android"))
            .using(module(libs.quickjs.kt.jvm.get().toString()))
    }
}
