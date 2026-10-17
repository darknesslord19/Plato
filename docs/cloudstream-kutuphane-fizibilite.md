# CloudStream Gerçek Kütüphane Entegrasyonu - Fizibilite Raporu

**Tarih:** 17 Ekim 2026  
**Durum:** ONAYLANDI (Aşama 1'e geçilebilir)  
**Hedef Dal:** `cloudstream-kutuphane` (taban: `saglayici-katalog` commit `aef5234`)  
**Kullanıcı Kararı:** Play lisansı GPL-3.0 olarak belirlendi; taklit (stub) sınıflar yerine CloudStream'in resmi kütüphanesi kullanılacak.

---

## 1. CloudStream Kütüphanesi ve Dağıtım Kanalları

### 1.1 Artefakt ve Koordinatlar
`recloudstream/cloudstream` deposu Kotlin Multiplatform (KMP) ve Android kütüphanesi mimarisine sahiptir. JitPack üzerinde düzenli olarak yayımlanmaktadır:

- **CloudStream Library Android AAR:**
  - Koordinat: `com.github.recloudstream.cloudstream:library-android:d9c55e2e5b` (master son commit) veya `v4.8.0`
  - Boyut: ~2.0 MB (v4.8.0) / ~2.7 MB (master `d9c55e2e5b`)
  - AAR İçeriği: `com.lagradost.cloudstream3.*`, `com.lagradost.api.*`, 100'den fazla yerleşik video çıkarıcı (`extractors.*`), Jackson/Ksoup JSON/HTML parse araçları, JS yorumlayıcı köprüleri (`Rhino`/`JsInterpreter`), kriptografik çözücüler (`AesHelper`).
- **NiceHttp (OkHttp Scraping Sarmalayıcısı):**
  - Koordinat: `com.github.Blatzar:NiceHttp:0.4.18`
  - Depo: `https://github.com/Blatzar/NiceHttp`
  - Paket: `com.lagradost.nicehttp.*` (`Requests`, `NiceResponse`, `Session`, `cookies` vb.)

### 1.2 Entegrasyon Yolu Seçimi
1. **Yol A - JitPack Bağımlılığı (Seçilen & Önerilen):**
   - Play'in `settings.gradle.kts` dosyasına `maven("https://jitpack.io")` eklenir.
   - `app/build.gradle.kts` içine `library-android` ve `NiceHttp` bağımlılık olarak verilir.
   - Avantajı: Temiz, hızlı derleme, standart Gradle bağımlılık yönetimi.
2. **Yol B - Git Submodule / `cloudstream-lib/` (Alternatif / Gerekirse Yedek):**
   - KMP ve Android Multiplatform eklentileri (`com.android.kotlin.multiplatform.library`, `buildkonfig`) Play projesinin tekil modül yapısına ek karmaşıklık getirdiğinden, öncelikli olarak JitPack artefaktı tercih edilmiştir.

---

## 2. Lisans Uyumluluğu

| Bileşen | Lisans | Play (GPL-3.0) ile Uyumluluk | Notlar |
|---|---|---|---|
| **CloudStream** (`recloudstream/cloudstream`) | **GPL-3.0** | **Tam Uyumlu** | Play'in GPL-3.0'a geçmesiyle doğrudan bağlanabilir ve dağıtılabilir. |
| **NiceHttp** (`Blatzar/NiceHttp`) | **GPL-3.0** | **Tam Uyumlu** | GPL-3.0 uyumlu. |
| **Media3** (`androidx.media3:*`) | Apache-2.0 | Uyumlu | Apache 2.0, GPL-3.0 ile çift taraflı uyumludur (FSF kılavuzu). |
| **Coil** (`io.coil-kt.coil3:*`) | Apache-2.0 | Uyumlu | Sorunsuz bağlanır. |
| **Jackson** (`com.fasterxml.jackson.*`) | Apache-2.0 | Uyumlu | Sorunsuz bağlanır. |
| **Jsoup** (`org.jsoup:jsoup`) | MIT | Uyumlu | MIT lisansı GPL-3.0 ile tam uyumludur. |
| **libtorrent4j** (`org.libtorrent4j:*`) | MIT / BSD | Uyumlu | Torrent oynatma motoru GPL-3.0 ile uyumludur. |
| **QuickJS** (`io.github.dokar3:quickjs-kt`) | Apache-2.0 | Uyumlu | JS çözücü motoru uyumlu. |
| **Google ML Kit Translate** | Google APIs / Apache-2.0 | Uyumlu | Opsiyonel çeviri istemci SDK'sı. |
| **NewPipeExtractor** | GPL-3.0 | Uyumlu | CloudStream kütüphanesinin çalışma zamanı bağımlılığı. |
| **Mozilla Rhino** | MPL-2.0 | Uyumlu | MPL 2.0, GPL-3.0 uyumludur. |

**Sonuç:** Lisans açısından hiçbir engel yoktur. Proje kök dizinine resmi GPL-3.0 `LICENSE` dosyası eklenecek, `README.md` ve "Hakkında" ekranında CloudStream ve NiceHttp kaynakları açıkça atıfta bulunulacaktır. Nuvio için konulan "kod kopyalanmaz" ilkesi geçerliliğini korumaktadır.

---

## 3. ABI Kapsama Analizi (40 Gerçek Eklenti DEX'i)

Cihaz üzerindeki (`files/cloudstream/*.dex` / `build/dex_cache/*.dex`) 40 eklentiye ait DEX dosyaları `tools/analyze_dex_methods.py` ile ayrıştırılmış ve gerçek `library-android` (commit `d9c55e2e5b`) + `NiceHttp 0.4.18` ikilisiyle `d8` üzerinden karşılaştırılmıştır.

- **Toplam Gerekli Metot Referansı:** 418 adet
- **Eklenti Kendi İç Sınıfları (örn. `com.lagradost.Anizm*`):** 229 adet (kütüphaneden beklenmeyen, eklenti içine gömülü sınıflar)
- **Gerçek Kütüphane Çağrısı:** 189 metot
- **Kütüphanede Doğrudan Var Olan:** 184 metot (%97.4)
- **Kütüphanede Olmayıp CloudStream `app` Modülünde Kalan Sınıflar:** 4 adet:
  1. `com.lagradost.cloudstream3.plugins.Plugin`: `BasePlugin`'den türeyen Android taban sınıfı (~40 satır).
  2. `com.lagradost.cloudstream3.network.CloudflareKiller`: OkHttp Interceptor olarak çalışan Cloudflare çözücüsü (~130 satır).
  3. `com.lagradost.cloudstream3.CommonActivity`: Eklentilerin etkinlik bağlamı referansı (`getActivity()`).
  4. `com.lagradost.cloudstream3.utils.SingleSelectionHelper`: UI diyalog yardımcısı (1 eklenti tarafından çağrılıyor).

Bu 4 sınıf Play projesine GPL-3.0 kapsamında doğrudan dahil edildiğinde ABI kapsama oranı: **%100** olmaktadır.

---

## 4. Ağ ve Güvenlik Mimarisi

### 4.1 PlayNetworkGuard Korunumu
CloudStream kütüphanesi tüm ağ isteklerini `com.lagradost.cloudstream3.MainActivityKt.app` nesnesi üzerinden yürütür.
- `MainActivityKt.setApp(Requests(baseClient = playOkHttpClient))` çağrısı ile Play'in özel `OkHttpClient`'ı enjekte edilecektir.
- Play'in `PlayNetworkGuard` interceptor'ı devrede kalacak; eklentilerin yerel/özel IP adreslerine (`127.0.0.1`, `10.x.x.x`, `192.168.x.x` vb.) erişimi engellenmeye devam edecektir.
- Çerez/token ve hassas başlıkların loglanmaması kuralı aynen geçerlidir.

### 4.2 Cloudflare Çözüm Entegrasyonu
- Eklentilerin çağırdığı `CloudflareKiller`, kütüphanedeki `WebViewResolver` ile birlikte çalışır.
- Play'in mevcut `CloudflareSolver` ve kullanıcı etkileşimli `CloudflareVerifyActivity` (Turnstile kutusu doğrulama) mekanizması ile birleştirilerek, hem arka planda otomatik çerez çözme hem de gerekirse ekranda doğrulama penceresi açma kabiliyeti sağlanacaktır.

---

## 5. Derleme ve Boyut Değerlendirmesi

- **SDK ve Araç Sürümleri:**
  - Play `minSdk = 24`, CloudStream kütüphanesi `minSdk = 23` -> Tam uyumlu.
  - Play `compileSdk = 36` (37'ye yükseltilebilir), CloudStream kütüphanesi `compileSdk = 37` -> Uyumlu.
  - Kotlin `2.4.21`, AGP `9.4.1` -> Kütüphanenin derlendiği Kotlin 2.4 / AGP 9.1 serisiyle tam uyumlu.
- **APK Boyutu Etkisi:**
  - Mevcut Debug APK (commit `aef5234`): ~38.6 MB (x86_64) / ~38.2 MB (arm64-v8a).
  - Tahmini Artış: Gerçek kütüphanenin eklenmesi ve eski stub sınıflarının kaldırılması sonucu APK boyutunun yaklaşık **+1.5 MB ile +2.2 MB** artması beklenmektedir. Bu artış kabul edilebilir sınırlar içindedir.

---

## 6. Karar ve Sonraki Adımlar

Fizibilite değerlendirmesi sonucunda herhangi bir teknik veya lisans engeli tespit edilmemiştir.
**Aşama 1 (Geçiş)** adımlarına başlanması onaylanmıştır:
1. `settings.gradle.kts` ve `app/build.gradle.kts` güncellenerek gerçek kütüphane eklenecek.
2. `app/src/main/java/com/lagradost/**` altındaki stub sınıfları `git rm` ile kaldırılacak.
3. Kütüphane dışındaki zorunlu 4 sınıf (`Plugin`, `CloudflareKiller`, `CommonActivity`, `SingleSelectionHelper`) ve Play'e özel köprüler (`CsRuntime`, `CsDexLoader`, `PlayNetworkGuard`, `CloudflareSolver`) kütüphaneye bağlanacak.
4. Kök dizine GPL-3.0 `LICENSE`, `README.md` lisans ve CloudStream atfı, Hakkında ekranına atıf satırları eklenecek.
5. ProGuard/R8 kuralları güncellenecek.
