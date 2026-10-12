# CloudStream (.cs3) eklenti desteği — Faz 3 araştırması

Tarih: 2026-10-12. Amaç: Faz 3'e (CloudStream `.cs3`) başlamadan önce **ne gerektiğini, neye mal olacağını ve hangi riskleri getireceğini** tahminle değil, gerçek kaynaklardan bilmek. Bu belge yalnızca teknik ve hukuki-risk bilgisidir; eklentilerin hangi sitelere/içeriğe bağlandığı konusunda bir değerlendirme yapılmaz. **Uygulama koduna dokunulmadı, commit atılmadı.** Hukuki tavsiye değildir; olgular ve riskler yazıldı, kesin hüküm verilmedi.

Etiketler: **[DOĞRULANDI]** bu oturumda gerçek dosya/kaynak/API yanıtından doğrudan okundu ya da ölçüldü; **[VARSAYIM]** çıkarım, tahmin ya da bu oturumda sınanamayan bilgi.

Telif/temiz oda notu: Bu araştırma CloudStream kaynak dosyalarını (GPL-3.0) **okudu** (aşağıda listeli). Belgede kaynaktan kod kopyalanmadı; yalnızca sınıf/alan adları, imza düzeyinde bilgiler ve ölçümler var. Ama bir uyumluluk katmanı (seçenek b) yazılacaksa bu oturumu yazan ya da okuyan kişi "temiz oda" sayılamayabilir (bkz. 4.3).

---

## 0. Kaynaklar ve erişilemeyenler

**Okunanlar (hepsi bu oturumda GitHub API / raw / indirme ile alındı):**

| Kaynak | Ne için |
|---|---|
| `recloudstream/cloudstream` (master, son itme 2026-10-12; son kararlı `v4.8.0` 2026-07-10, `pre-release` 2026-10-11): `LICENSE`, `README.md`, `library/README.md`, `library/build.gradle.kts`, `library/api/jvm/library.api`, `library/src/commonMain/.../plugins/BasePlugin.kt`, `CloudstreamPlugin.kt`, `MainAPI.kt`, `utils/ExtractorApi.kt`, `MainActivity.kt` (global `app`); `app/.../plugins/Plugin.kt`, `RepositoryManager.kt`, `PluginManager.kt`; `AndroidManifest.xml`, `res/values/strings.xml`, `gradle/libs.versions.toml`, `AI-POLICY.md`, dosya ağacı (1529 dosya) | Depo/paket biçimi, yükleme akışı, API yüzeyi, lisans, sürümler |
| `recloudstream/gradle` (eklenti derleme Gradle eklentisi): `LICENSE`, `CompileDexTask`, `GenerateManifestTask`, `PluginManifest`, `PluginEntry` | `.cs3`nin nasıl üretildiği, manifest ve `plugins.json` alanları |
| `recloudstream/extensions` (resmî örnek depo): `build.gradle.kts`, `repo.json`; `builds` dalındaki `plugins.json` ve 5 `.cs3` | Gerçek örnek depo |
| `recloudstream/csdocs`: `devs/create-your-own-json-repository.md`, `Repositories.md` | Resmî `repo.json` belgesi |
| `liberta09/CloudStreamRepoManager/central_repos.json` (kullanıcının Repo Manager projesi, güncelleme 2026-10-12T22:05Z) | 31 "cloudstream" girişi; 2 depodan `repo.json` (resmî + Manitux), 5 depodan `plugins.json` ve **91 gerçek `.cs3`** indirilip statik incelendi (bkz. 2.4). Denenen bir başka depo (Kekik) `builds` dalındaki `.cs3` adresleri 404 verdi, örnekleme alınmadı |
| Topluluk depoları (yalnızca `plugins.json` + `.cs3` indirme, **hiçbiri çalıştırılmadı**): manitux-app/cs-plugins, phisher98/cloudstream-extensions-phisher, Bnyro/GermanProviders, doGior/doGiorsHadEnough | Gerçek dünya API kullanım ölçümü |
| developer.android.com "Android 14 behavior changes", Google Play "Device and Network Abuse" politikası, gnu.org GPL SSS (`GPLPlugins`, `MereAggregation`, `GPLStaticVsDynamic`, `GPLInProprietarySystem`), Android `<service>` belgesi | Android 14 salt-okunur kuralı, Play politikası, GPL yorum ilkeleri, isolatedProcess |
| Web araması özetleri: Google v. Oracle (ABD, 2021), SAS Institute v World Programming (AB Adalet Divanı C-406/10, 2012) | Arayüz/API telif tartışması (yalnızca ikincil özetler) |

**Erişilemeyenler / doğrulanamayanlar (dürüst liste):**
- `https://cloudstream.miraheze.org/wiki/...` (topluluk depo listesi) ve `recloudstream.github.io/csdocs` canlı sitesi **açılmadı**; csdocs yalnızca GitHub kaynak dosyasından okundu. csdocs'ta "depo nasıl eklenir" adımları yok (yalnızca wiki'ye yönlendiren tek satır).
- Uygulamanın depo-ekleme **arayüzü** çalıştırılmadı; adımlar kaynak koddan (`RepositoryManager`, `PluginManager`, `strings.xml`) çıkarıldı.
- `com.github.recloudstream.cloudstream:library` JitPack yapıtı **indirilmedi/derlenmedi**; sadece resmî şablonun ona bağımlılık verdiği doğrulandı. JitPack'in şu an çözümleyip çözümlemediği bilinmiyor. `Blatzar/NiceHttp` JitPack `.aar` boyutu alınamadı (istek hata verdi).
- Hiçbir `.cs3` **çalıştırılmadı**; hiçbir uyumluluk katmanı denenmedi. Aşağıdaki "kaç eklenti çalışır" sayıları **sınıf düzeyinde statik analiz**dir (bkz. 2.4), gerçek çalışma garantisi değil.
- `isolatedProcess` içinde ağ erişimi (INTERNET izni olmayan UID) resmî belgede **yazmıyor**; aşağıda VARSAYIM olarak işaretli.
- Türkiye hukuku (FSEK vb.) incelenmedi.
- Bir cihazda (LDPlayer) hiçbir deneme yapılmadı.
- CloudStream'in Discord/issue arşivlerinde bilinen kötü amaçlı eklenti vakaları **aranmadı**.

---

## 1. Depo biçimi

### 1.1 İki katmanlı yapı [DOĞRULANDI]
CloudStream deposu iki JSON'dan oluşur:

1. **`repo.json`** (kullanıcının eklediği adres). Uygulama modeli `Repository`: `name` (zorunlu), `manifestVersion` (zorunlu, "şimdilik kullanılmıyor"), `pluginLists` (zorunlu, `plugins.json` adresleri listesi; hepsi çekilir), `description?`, `iconUrl?`.
2. **`plugins.json`** (`pluginLists` içindeki her adres): `SitePlugin` **dizisi**.

Resmî örnek depo (`recloudstream/extensions`) `repo.json`'u **tamamen** şu (246 bayt):
```json
{
    "name": "Cloudstream providers repository",
    "description": "Cloudstream extension Repository",
    "manifestVersion": 1,
    "pluginLists": [
      "https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json"
    ]
}
```
Bir topluluk deposu (Manitux, `iconUrl` dahil) aynı biçimi kullanır: `{"name","description","iconUrl","manifestVersion":1,"pluginLists":[".../builds/plugins.json"]}`.

> Not: Play'in Faz 2 sınıflandırıcısı (`PluginRepoManifest`) `pluginLists` görünce bunu "CloudStream tarzı, desteklenmiyor" diye ayırıyor; yani Faz 3'ün giriş kapısı zaten var.

### 1.2 `plugins.json` girişi (`SitePlugin`) [DOĞRULANDI]
Uygulama modelindeki alanlar (kaynak yorumlarıyla):

| Alan | Tür | Anlam |
|---|---|---|
| `url` | metin, zorunlu | `.cs3` dosyasının adresi |
| `status` | int, zorunlu | 0 kapalı (Down), 1 çalışıyor (Ok), 2 yavaş (Slow), 3 yalnız beta (Beta only). `0` olan eklenti uygulamada kapatılır/güncellenmez |
| `version` | int, zorunlu | "0'dan büyük tamsayı"; **herhangi bir değişiklik** otomatik güncellemeyi tetikler (`-1` = hep güncelle) |
| `apiVersion` | int, zorunlu | Kaynak yorumu: "şu an kullanılmıyor, geriye uyumluluk için, 1 yapılır" |
| `name` | metin, zorunlu | Arayüzde görünen ad |
| `internalName` | metin, zorunlu | İç ad (ad/adres değişse de kimlik sabit kalsın diye ayrı) |
| `authors` | metin dizisi, zorunlu | |
| `description`, `repositoryUrl`, `tvTypes`, `language`, `iconUrl` | isteğe bağlı | `tvTypes` "henüz eşlenmedi"; `language` çoğunlukla `en`, `zh-TW` gibi etiket; `iconUrl` içinde `%size%` yer tutucusu olabilir |
| `fileSize`, `fileHash` | isteğe bağlı | Gradle eklentisi üretir. `fileHash` biçimi: `sha256-<küçük harf hex>` |
| `jarUrl`, `jarFileSize`, `jarHash` | Gradle eklentisi çıktısında var ("çapraz platform" için) | Masaüstü/JVM sürümü `.jar`; uygulama modelinde alan yok |

Gerçek giriş (resmî depo, `DailymotionProvider`, 11.472 bayt `.cs3`):
```json
{ "iconUrl": "https://www.google.com/s2/favicons?domain=www.dailymotion.com&sz=%size%",
  "jarFileSize": 49791, "fileHash": "sha256-9036525a64e8b3c8fe04f94e9fe89a744c13d69af684ed0d2ad2a7eb0f332cd9",
  "apiVersion": 1, "repositoryUrl": "https://github.com/recloudstream/extensions",
  "fileSize": 11472, "status": 1, "authors": ["Luna712"], "tvTypes": ["Others"], "version": 4,
  "internalName": "DailymotionProvider",
  "jarUrl": "https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.jar",
  "description": "Watch content from Dailymotion",
  "url": "https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.cs3",
  "name": "DailymotionProvider", "jarHash": "sha256-542c325f..." }
```
**İndirilen dosyanın SHA-256'sı `fileHash` ile birebir eşleşti** (resmî 5/5, Manitux 11/11, toplam 91 indirmenin hepsinde hash tutarlıydı; hash'siz giriş görülmedi).

Gerçek dünya dağılımı (ölçüm): Phisher deposu 86 giriş — `apiVersion` hepsi 1; `status` 85× Ok, 1× Slow; `tvTypes` çok çeşitli (Movie 46, TvSeries 44, Anime 44, AnimeMovie 30, Cartoon 19, AsianDrama 13, OVA 10, Torrent 3, Live 3, Others 2); `language` 13+ farklı değer (en, hi, de, zh, bn, id, ta …); `fileSize` ortanca 106 KB, en büyük 2,0 MB. Manitux: 11 giriş, `status` 3 (yalnız beta) 7 adet, `language` hepsi `tr`. **Resmî depoda `language` alanı yok** (alan isteğe bağlı).

### 1.3 Depoya ulaşım / kısa kodlar [DOĞRULANDI — `RepositoryManager.parseRepoUrl`]
Girdi şu biçimlerden biri olabilir:
- `http(s)://...` doğrudan adres.
- `cloudstreamrepo://host/yol` ya da `https://cs.repo/?host/yol`: önek atılır, başına `https://` eklenir. (`cloudstreamrepo` ayrıca uygulamanın manifest'inde bir intent şemasıdır.)
- **Kısa kod** (`[A-Za-z0-9!_-]+`): `https://cutt.ly/{kod}` adresine yönlendirme **izlenmeden** istek atılır, `Location` başlığı depo adresi sayılır; `!` ile başlıyorsa aynısı `https://py.md/{kod}` ile yapılır (404 / ana sayfa dönerse geçersiz). Repo Manager `central_repos.json`'ındaki `"code": "!megawio"` bu biçimdir.
- İsteğe bağlı ayar: `raw.githubusercontent.com/{kullanıcı}/{depo}/...` adreslerini `cdn.jsdelivr.net/gh/{kullanıcı}/{depo}@...` adresine çevirme (jsDelivr proxy anahtarı).

### 1.4 Uygulamanın depo eklerken yaptığı adımlar [DOĞRULANDI]
1. Girdiyi yukarıdaki kurallarla adrese çevir.
2. `repo.json`'ı GET ile al (5 dk önbellek), `Repository` olarak ayrıştır; bulunamazsa "Repository not found, check the URL and try VPN".
3. `pluginLists`teki her adresi al, `SitePlugin[]` olarak ayrıştır; bozuk liste = boş liste (hata yutulur).
4. Depo kaydı (`RepositoryData`: url, ad, …) anahtar/değer deposunda saklanır, kopyalar elenir.
5. Eklenti indirme: kullanıcı seçerse ya da "otomatik indir" ayarı açıksa **kullanıcının eklediği tüm depolardan henüz kurulmamış tüm eklentiler** kendiliğinden indirilir (`Automatically download plugins`; mod: yalnız NSFW / dil süzgeci). İndirme önce geçici dosyaya, `fileHash` varsa SHA-256 doğrulanır, uyuşmazsa silinip hata atılır, sonra atomik taşıma. Yol: `filesDir/Extensions/<depo adresinden türeyen klasör>/<temizlenmiş ad+hash>.cs3`.
6. Güncelleme: sunucudaki `version` kayıtlı sürümden büyükse ya da `-1` ise yeniden indirilir (`Automatic plugin updates` ayarı). `status == 0` ise yüklü olan boşaltılır.
7. Kullanıcıya gösterilen sorumluluk reddi (`strings.xml`): *"Warning: CloudStream does not take any responsibility for using third-party extensions and does not provide any support for them!"* Varsayılan olarak hiçbir site/eklenti kurulu gelmez (*"CloudStream has no sites installed by default…"*).

---

## 2. `.cs3` paketi

### 2.1 ZIP içeriği [DOĞRULANDI — 91 gerçek dosyada]
`.cs3` düz bir **ZIP**'tir (adı `.zip` değil; standart ZIP araçları uzantıyı tanımayabilir, `tar` açamadı, .NET `ZipFile` açtı). İçerik:

| Giriş | Sayı (91 dosyada) | Anlam |
|---|---|---|
| `manifest.json` | 91/91 | Aşağıdaki 4 alan |
| `classes.dex` | 91/91 (her pakette tek) | Eklenti sınıfları, **D8 ile derlenmiş** (başlıkta "dex 035" görüldü) |
| `res/**`, `resources.arsc` | 15/91'de (%16) | Yalnızca `requiresResources: true` olanlarda (toplam 209 `res/` girişi) |

Kural olarak **yerel `.so` yok**, ek `assets/` yok (örneklemde görülmedi; [VARSAYIM] hiç olmayacağı garanti değil).

### 2.2 `manifest.json` [DOĞRULANDI]
Gerçek örnek (`DailymotionProvider`): `{"requiresResources":false,"version":4,"pluginClassName":"recloudstream.DailymotionPlugin","name":"DailymotionProvider"}`. Alanlar (91 dosyanın hepsinde yalnızca bu dört): `name` (metin), `version` (int), `pluginClassName` (giriş sınıfı, tam adı), `requiresResources` (bool). Gradle eklentisi `pluginClassName`'i `@CloudstreamPlugin` ek açıklamalı **tek** sınıftan bulur ("proje başına bir eklenti sınıfı"). `apiVersion`/asgari uygulama sürümü paket içinde **yok** (uyumluluk kontrolü yok; uyumsuzluk çalışma anında çöker).

### 2.3 Eklenti sınıfı neden türüyor, neyi çağırıyor [DOĞRULANDI — kaynak + dex incelemesi]
- Giriş sınıfı `@CloudstreamPlugin` ile işaretli ve **`Plugin`** (ya da Android'siz `BasePlugin`) alt sınıfıdır. `BasePlugin`: `load()`, `beforeUnload()`, `registerMainAPI(MainAPI)`, `registerExtractorAPI(ExtractorApi)`, `filename`. `Plugin` (uygulama modülünde, **kütüphanede değil**): `load(context: Context)`, `registerVideoClickAction`, `resources: Resources?` (`requiresResources` ise), `openSettings: ((Context)->Unit)?` (eklenti ayar düğmesi).
- Asıl iş **`MainAPI`** alt sınıfındadır (site/sağlayıcı). Soyut sınıfın başlıca üyeleri: `name`, `mainUrl`, `lang`, `supportedTypes` (`TvType` kümesi), `hasMainPage`, `mainPage`, `usesWebView`, zaman aşımı alanları; **açık suspend işlevler**: `getMainPage(page, request)`, `search(query[, page])`, `quickSearch`, `load(url): LoadResponse?`, `loadLinks(data, isCasting, subtitleCallback, callback): Boolean`, `getVideoInterceptor(ExtractorLink): Interceptor?` (oynatma isteğine OkHttp interceptor eklemek için).
- Dönüş modeli: `SearchResponse` (Movie/TvSeries/Anime/Torrent/Live türevleri), `LoadResponse` (Movie/TvSeries/Anime/Torrent/LiveStream türevleri; `Episode`, `Actor`, `Score`, `TrailerData`, `syncData` ile imdb/tmdb/anilist/mal kimlikleri), `HomePageResponse/List`, `SubtitleFile`.
- Akış çıktısı: **`ExtractorLink`** (`name`, `url`, `referer`, `quality`, `headers`, `type` = `VIDEO/M3U8/DASH/TORRENT/MAGNET`, DRM türevi `DrmExtractorLink`), `newExtractorLink(...)`; `loadExtractor(url, referer, subtitleCallback, callback)` bir **yerleşik çıkarıcı kütüphanesine** (kitaplıkta 104 `ExtractorApi` dosyası: StreamWish, VidHide, Filemoon, DoodStream, MixDrop, … ) yönlendirir.
- Yardımcılar: `newMovieSearchResponse`, `newMovieLoadResponse`, `newTvSeriesLoadResponse`, `newEpisode`, `newHomePageResponse`, `mainPageOf`, `fixUrl`, `app` (**NiceHttp `Requests`**: `get/post`, `.text`, `.document` (Jsoup), `.parsed<T>()` Jackson), `AppUtils.parseJson/toJson`, `M3u8Helper`, `JsUnpacker`, `Qualities`, `base64Decode`, ErrorLoadingException, `Levenshtein`, `SubtitleHelper` …

### 2.4 Gerçek eklentilerin ne kullandığı (ölçüm) [DOĞRULANDI — yöntem ve sınırlar]
**Yöntem:** 5 depodan 91 `.cs3` indirildi (resmî 5, Manitux 11, Phisher'ın ilk 40'ı, GermanProviders 20, doGior 15), her `classes.dex` ayrıştırıldı (dize/yöntem tabloları; çalıştırılmadı). **Örneklem yanlı:** ağırlık Phisher/Türkçe/Almanca/İtalyan depolarında; 91, ekosistemin (yüzlerce) küçük bir kesitidir. Kesin oran iddiası değil, büyüklük mertebesi içindir.

Bulgular:
1. **`.cs3` içinde CloudStream sınıfı yok.** Dex yalnızca `com.lagradost.*`, `okhttp3.*`, `org.jsoup.*`, `com.fasterxml.jackson.*`, `kotlin.*` vb. sınıflara **başvurur**; bunlar çalışma anında **ana uygulamanın sınıf yükleyicisinden** gelir. Yani host, bu sınıfları **ikili uyumlu (aynı tam ad, aynı imza)** vermek zorundadır.
2. 91 eklenti toplam **613 farklı (sınıf, yöntem) başvurusu**, **142 farklı `com.lagradost` sınıfı** kullanıyor (dex düzeyinde: `$default` yapay yöntemler, `Companion`, `...Kt` dosya sınıfları, kurucular dahil). Eklenti başına başvuru sayısı: en az 16, **ortanca 51**, %90'lık dilim 80, en çok 440; **birleşimin uzun bir kuyruğu var** (her yeni eklenti birkaç yeni yöntem ekliyor). 35 yöntem yarıdan fazla eklentide ortak (çekirdek: `MainAPI` kurucusu, `app.get`, `loadExtractor`, `newMovieSearchResponse`, `newEpisode`, `newExtractorLink`, `Requests.get/post`, `NiceResponse.text/document`, `mapper`, …).
3. **`library` modülü yetmiyor.** Kütüphane ABI dökümünde (`library.api`) 827 genel tür var; ama eklentilerin başvurduğu üç sınıf **uygulama modülünde**: `Plugin` (91 eklentinin 57'si), `CommonActivity` (50), `CloudStreamApp` (42; ayrıca `utils/DataStore` 7, `network/CloudflareKiller` 4, `AniListApi`, `PluginManager` …). Yalnızca kütüphane sınıflarıyla tam karşılanan eklenti: **5/91 (%5; hepsi resmî depodan)**. Aşağıdaki sınıfları host kendisi sağlarsa, sınıf düzeyinde (yöntem düzeyinde değil) karşılanan oran:

| Host'un ek sağladığı (uygulama modülünden) | Karşılanan eklenti |
|---|---|
| yok (yalnız kütüphane) | 5/91 (%5) |
| + `Plugin` | 37/91 (%41) |
| + `Plugin`, `CommonActivity`, `CloudStreamApp` | 77/91 (%85) |
| + `DataStore`, `CloudflareKiller` | 86/91 (%95) |

4. Android sınıfı başvuruları (eklenti sayısı, 91 üzerinden): `SharedPreferences` 60, `View` 60, `AlertDialog/DialogInterface` 54, `AppCompatActivity` 49, `FragmentManager` 49, `LayoutInflater` 49, `CookieManager` 47. `setOpenSettings` (eklenti ayar düğmesi) yalnızca **16/91** eklentide; `WebViewResolver` 10/91, `CloudflareKiller` 4/91; `getVideoInterceptor` 6/91. Arayüz sınıflarının çoğu `openSettings` / bazı çözümleme yollarında çalışır; çalıştırılmadıkça sınıf hatası vermesi beklenmez [VARSAYIM: ART doğrulamasının gevşek (soft-fail) olduğu varsayımıyla; sınanmadı].
5. Üçüncü taraf (eklenti sayısı, 91 üzerinden): Jackson 85, Jsoup 69, `okhttp3.RequestBody` 65, `okhttp3.Interceptor` 89, `org.json` 49, `io.ktor.http` 17, NewPipe Extractor 2, Rhino 1. `loadExtractor` 61/91 eklentide çağrılıyor; 47/91 eklenti `ExtractorApi` sınıfına başvuruyor (kendi çıkarıcısını yazıyor ya da kullanıyor). Kimi eklentiler kendi bağımlılıklarını dex içine gömüyor (`fileSize` 2 MB'a kadar).

---

## 3. Çalıştırma: Android'de yükleme ve host gereksinimleri

### 3.1 Yükleme [DOĞRULANDI — `PluginManager.loadPlugin`]
- **`PathClassLoader(dosyaYolu, uygulamaClassLoader)`** kullanılıyor (DexClassLoader değil). Üst yükleyici uygulamanın kendisi; bu yüzden eklenti uygulamanın tüm sınıflarını (ve ilkel yansıma ile `BuildConfig` gibi iç sınıflarını) görür.
- `manifest.json` → `loader.getResourceAsStream("manifest.json")` ile okunur; `pluginClassName` ile `loadClass`, parametresiz kurucu `newInstance()`; `Plugin` ise `load(context)`, değilse `load()` çağrılır. `requiresResources` ise yansımayla boş `AssetManager` oluşturup `addAssetPath(dosya)` çağırır, `Resources` sarar ve `plugin.resources`a verir.
- `beforeUnload()` + kayıtlı API'lerin (`MainAPI`, `ExtractorApi`, `VideoClickAction`) listeden çıkarılması ile boşaltma. "Güvenli mod": `safe` adlı dosya ya da son hata varsa tüm eklentiler kapalı açılır.
- Uygulama güncellenince eski `oat` klasörlerinin silinmesi gerekiyor (kaynak yorumu: "eski oat dosyaları yeni uygulama sürümünde yüklenince kurtarılamaz SIGSEGV olabilir") — **bakım yükü kalemi**.

### 3.2 Android 14+ salt-okunur şartı [DOĞRULANDI]
Resmî belge (developer.android.com, "Safer dynamic code loading"): **API 34+ hedefleyen uygulamada dinamik yüklenen her DEX/JAR/APK dosyası salt-okunur olmalı, aksi hâlde sistem istisna atar** (istisna türü belgede yazılı değil; CloudStream kaynağı yorumunda `SecurityException`). Öneri: dosyayı **yazmadan önce** `setReadOnly()` yapıp sonra yazmak (yarış durumu olmasın); eski dosyalar için silip yeniden oluşturmak, yalnızca etiketlemek gerekirse bütünlük/imza doğrulaması. CloudStream `setReadOnly()` çağırır (başarısızlığı yalnızca günlüğe yazar); yerel eklentileri ayrıca uygulamaya özel harici dizine kopyalar. **Play'in hedefi 37 > 34: bu kural bize de uygulanır.** Ayrıca belge, dinamik kod yüklemekten **kaçınmayı** önerir (kod enjeksiyonu/değiştirme riski).

### 3.3 Eklentiler çalışma anında hangi sınıflara ihtiyaç duyar [DOĞRULANDI — kütüphane bağımlılıkları + dex ölçümü]
`library/build.gradle.kts` (commonMain): Jackson Kotlin modülü, Jsoup, kotlinx (atomicfu, coroutines, datetime, io, serialization-json), ksoup, Ktor HTTP, **NiceHttp**, **Rhino (JS)**, bir kriptografi paketi; jvmCommon: **kotlin-reflect**, **NewPipe Extractor**. Sürüm kısıtları (kaynak yorumları): Jackson **2.13.1'e sabitli** ("daha yenisi minSdk<26'da çöker"), Rhino **1.8.1'e sabitli** ("1.9.0+ minSdk 26 ister"). CloudStream uygulaması `minSdk 23 / targetSdk 36`; Play `24 / 37`.

Kütüphanenin kendi belgesi (`library/README.md`): "tüm CloudStream eklentileri için resmî API yüzeyi"; **ikili uyumluluk** zorunlu, yeni eklemeler `@Prerelease` ile işaretlenir, `./gradlew checkKotlinAbi` ile `api/jvm/library.api` korunur. (Bu, seçenek a için bir **kazanç**: sözleşme açık ve denetleniyor. Seçenek b için de **ölçü**: hedeflenecek ABI dosyası 8.980 satır / 592 KB, 827 genel tür, ~6.500 üye.)

---

## 4. "Bu sınıfları uygulamamız nasıl sağlar?" — seçenekler

### 4.1 Seçenek (a): CloudStream `library` modülünü bağımlılık olarak gömmek
Nasıl: `com.github.recloudstream.cloudstream:library` (JitPack; resmî şablon bunu `-SNAPSHOT` ile kullanıyor) + bağımlılıkları (Jackson 2.13.1, Rhino 1.8.1, NiceHttp, Jsoup, kotlinx*, ktor-http, NewPipe, kotlin-reflect …).
- **Tek başına yetmez** [DOĞRULANDI]: `Plugin`, `CommonActivity`, `CloudStreamApp`, `DataStore`, `CloudflareKiller` gibi **uygulama modülündeki** sınıfları host yazmak zorunda (bunlar kütüphane yapıtında yok; yukarıdaki tabloya göre bunlarla %85–95'e çıkar). Bunlar küçük ama davranışsal (anahtar/değer deposu, toast, bağlam, Cloudflare WebView hilesi). Yani (a) gerçekte "kütüphane + dar bir kendi uygulama kabuğu" demektir.
- `library`, `getContext()/setContext()`, `WebViewResolver` (Activity/WebView ister), `app`/`insecureApp` istemcisi gibi çalışma anı başlangıç işleri ister [VARSAYIM: kaynak dosyalarının tamamı okunmadı].
- **Ağ istemcisi, NiceHttp üzerinden kendi OkHttp'sini kurar**; Play'in `NetworkPolicy` (SSRF) korumasını kendiliğinden kullanmaz (bkz. 5).
- OkHttp sürümü: NiceHttp/eklentiler OkHttp 4.x'e karşı derlendi; Play OkHttp **5.5.0** kullanıyor. OkHttp 5 çoğunlukla ikili uyumlu olsa da sınama yok [VARSAYIM: risk].
- R8: eklentiler sınıfları **adla** çözdüğü için kütüphane + Jackson + Kotlin + OkHttp + Jsoup genel yüzeyi için **koruma (keep) kuralları** şart; bu uygulamanın küçülmüş (6,8 MB) sürümünü şişirir.

**APK etkisi (tahmin) [VARSAYIM, bileşen boyutları DOĞRULANDI]:** mevcut release APK 6,77 MB (R8'li, imzasız). Eklenecek ham jar boyutları (Maven/JitPack başlıklarından): jackson-databind 1,53 MB + core 0,37 + annotations 0,08 + kotlin-module 0,13; rhino 1,27; kotlin-reflect 3,47; ktor-http 0,42; kotlinx-datetime 0,69; NewPipeExtractor 0,80 (+ protobuf, nanojson vb.); kütüphanenin kendisi (104 çıkarıcı dahil 167 kaynak dosya); NiceHttp (bilinmiyor, küçük). Toplam ham jar ≈ **8,8 MB + kütüphane**. D8 + koruma kurallarıyla **tahmin: +6 ila +12 MB** (APK yaklaşık 13–19 MB); ölçülmeden kesin sayı verilemez.

### 4.2 Seçenek (b): API yüzeyini sıfırdan yazmak (uyumluluk katmanı)
Gerekenler (ölçümden): sınıf adları ve imzalar **birebir** (`com.lagradost.cloudstream3.*`, Kotlin dosya sınıfı adları `MainAPIKt`, `ExtractorApiKt`, `$default` yapay yöntemler, `Companion` sınıfları, `inline` işlevlerin derlenmiş hâli), davranış olarak Jackson tabanlı `parsed<T>()`/`mapper`, NiceHttp `Requests`/`NiceResponse` (bir ikinci kütüphane!), Jsoup bağlayıcıları.
- **Asgari çekirdek** (35 ortak yöntem + `Plugin`/`CommonActivity`/`CloudStreamApp`): ~25–35 sınıf, ~150–250 üye, kabaca 3–5 bin satır [VARSAYIM]. Sınıf düzeyinde bile bu kümenin kapsadığı oran örneklemde yalnızca %41 (yalnız `Plugin` kabuğu) ile %85 (üç kabuk sınıfı) arasında; yöntem düzeyinde daha düşüktür.
- **Yaygın örtü** (613 yöntem/142 sınıf + 104 yerleşik çıkarıcının çalışan bir alt kümesi): 15–30 bin satır [VARSAYIM; MainAPI.kt tek başına 2.860, ExtractorApi.kt 1.472 satır — bunlar birebir çıkarılamaz, davranışsal içeriği ayrıca yazmak gerekir].
- **Çıkarıcılar:** `loadExtractor` 61/91 eklentide çağrılıyor ve yerleşik çıkarıcıların kendisi **host kütüphanesinde**. Bunlar yoksa eklentiler çoğu zaman bağlantı döndüremez. 104 çıkarıcının her biri üçüncü taraf bir video barındırıcısının sayfa biçimine bağlı (JS çözme, AES, vb.) — sürekli yetişme işi. Bu kısım Faz 2'deki "cheerio/CryptoJS kabuğu"ndan **çok daha büyük** bir yüzey.
- **Bakım:** her CloudStream sürümü yeni yöntem/aşırı yükleme ekleyebilir; yeni eklenti derlendiği an host'ta olmayan yönteme başvurur → çalışma anında `NoSuchMethodError`/`NoClassDefFoundError`. Bu "sonsuz yetişme" demektir.
- **Avantaj:** GPL'li kodu gömmeyiz; ağ/depolama köprülerini kendi kuralımızla (NetworkPolicy, TMDB anahtarı gizleme) yazarız.

### 4.3 Seçenek (b)'nin hukuki/temiz oda boyutu
Bkz. 6. Kısaca: bu oturum kaynağı okudu; temiz oda ayrımı (bir kişi şartname yazar, diğeri kodu yazar) yapılmadıysa "kopyalama yok" kuralının ispatı zorlaşır.

### 4.4 Diğer yollar
- **(c1) Yüklü CloudStream uygulamasına IPC:** uygulamanın `AndroidManifest.xml`'inde üçüncü tarafa açık bir servis/ContentProvider **yok** (yalnızca `cloudstreamrepo://`, `cloudstreamsearch://`, `cloudstreamapp://`, `cloudstreamplayer://`, `csshare://`, `cloudstreamcontinuewatching://`, `magnet:` intent süzgeçleri) [DOĞRULANDI]. Yani kaynak toplama için programatik bir kapı yok; olsa olsa "CloudStream'de aç" derin bağlantısı var (amacımız bu değil).
- **(c2) Ayrı bir JVM sunucusu (kullanıcı kendi bilgisayarında):** kütüphane `jvm()` hedefi içeriyor ve eklentiler `.jar` olarak da yayımlanıyor (`jarUrl`, 5/5 resmî giriş) [DOĞRULANDI]. Ancak örneklemdeki eklentiler Android sınıflarına (View/Dialog/Activity) de başvuruyor ve JVM'de çalışacakları sınanmadı [VARSAYIM]. Ayrı bir ürün/dağıtım sorunu, Faz 1 hedefinden (tek APK) uzak.
- **(c3) Kendi sürecinde barındırma (izole süreç) + (a) ya da (b):** güvenlik bölümünde (5.3).
- **(c4) Hiçbir şey yapmama:** 7. bölümde.
- **(c5) Popüler sağlayıcıları Nuvio JS kazıyıcılarına çevirmek:** eklentiyi üçüncü taraf yazar; bizim işimiz değil. [Değerlendirilmedi]

---

## 5. Lisans

### 5.1 Olgular [DOĞRULANDI, aksi belirtilmedikçe]
| Bileşen | Lisans | Kanıt |
|---|---|---|
| CloudStream uygulaması (`recloudstream/cloudstream`) | **GPL-3.0** | `LICENSE` metni ("GNU General Public License Version 3, 29 June 2007"), GitHub API `spdx_id: GPL-3.0`. `README.md` lisansı **belirtmiyor** |
| `library` modülü | Ayrı bir `LICENSE` **yok**; kaynak ağaçta yalnızca kök `LICENSE` + iki ilgisiz üçüncü taraf (`colorpicker`, `mihon`). `build.gradle.kts` içinde POM lisans/geliştirici bilgisi **yok**. Okunan 5 dosyada (BasePlugin, MainAPI vb.) dosya başlığı lisans notu görmedim. **Çıkarım: kök GPL-3.0 kapsamında** [VARSAYIM] | Dosya ağacı, build dosyası |
| Gradle derleme eklentisi (`recloudstream/gradle`) | **LGPL-3.0** | `LICENSE` ilk satırlar + API |
| Resmî eklenti şablonu/deposu (`recloudstream/extensions`) | Lisans **belirtilmemiş** (API `null`) | API |
| NiceHttp (`Blatzar/NiceHttp`) | **GPL-3.0** | API |
| NewPipeExtractor (`TeamNewPipe`) | **GPL-3.0** | API |
| Rhino | MPL-2.0 [VARSAYIM; GitHub "NOASSERTION" verdi, metin okunmadı] | |
| Jackson, kotlinx.*, OkHttp, Jsoup, Ktor | Apache-2.0 / MIT [VARSAYIM: bu oturumda lisans metni okunmadı; bilinen lisanslar] | |
| Her `.cs3` eklentisi | Yazarına bağlı; paket içinde lisans dosyası **yok** (`manifest.json` + `classes.dex` dışında dosya yok) | ZIP incelemesi |

Proje politikası: CloudStream "telifli içerik barındıran eklenti oluşturulmamasını/kullanılmamasını" ister, DMCA/EUCD tutumunu `reCloudStream` organizasyonuna bağlar; uygulamalar/eklentiler resmî olarak denetlenmez/onaylanmaz (README). Bu bilgi yalnızca bağlam içindir.

### 5.2 Seçenek (a) bizim lisansımızı nasıl etkiler?
- GPL-3.0 SSS (gnu.org, okundu): *"Linking a GPL covered work statically or dynamically with other modules is making a combined work based on the GPL covered work. Thus, the terms and conditions of the GNU General Public License cover the whole combination."* (`GPLStaticVsDynamic`); *"You cannot incorporate GPL-covered software in a proprietary system"* (`GPLInProprietarySystem`). Yani `library`ı (ve GPL'li NiceHttp/NewPipe'ı) APK'ya gömmek, FSF yorumuna göre Play'i bütün olarak GPL-3.0 altına sokar: **dağıtılırsa** (APK paylaşılırsa) tam karşılık gelen kaynağı GPL-3.0 ile sunma yükümlülüğü doğar. (Bu, GPL'nin genel çerçevesidir; "yükümlülükler dağıtımla doğar, yalnızca kendi cihazında çalıştırmak/değiştirmek yükümlülük doğurmaz" ilkesi GPL'nin genel bilgisidir, bu oturumda ilgili madde metni okunmadı [VARSAYIM].) README'ye göre bu proje şu an **kişisel kullanım** odaklı (TMDB anahtarı notu); dağıtım planı yoksa pratik yük küçüktür, plan varsa kararın ağırlığı büyüktür.
- Mevcut bağımlılıklarımız (OkHttp/Jsoup/quickjs-kt/Compose vb.) GPL-3.0 ile uyumlu izin verici lisanslardır [VARSAYIM: lisanslar tek tek doğrulanmadı]; yani karışım hukuken yapılabilir, ama sonuç GPL-3.0'dır.
- Eklentiler ayrıca: SSS'e göre (`GPLPlugins`) ana programın eklentileri *dinamik bağlayıp karmaşık veri yapılarını paylaşması* bunları tek bir birleşik program yapabilir; FSF bakışıyla eklentilerin de GPL olması beklenir. Topluluk eklentileri bu bakışla yazılmış olmayabilir. **Bu, Play için doğrudan yükümlülük değil ama eklentilerin lisans temizliği belirsizliğini gösterir** (paketlerde lisans bilgisi yok).
- Faz 2 kuralı "Nuvio uygulamaları GPL-3.0; kod kopyalanmaz, yalnızca belgelenen arayüz kendi kodumuzla uygulanır" idi. (a) bu ilkeyi **bırakır**: kod gömülür.

### 5.3 Seçenek (b) için arayüz uyumluluğu hukuken nasıl değerlendirilir? (Karar yok, olgular ve riskler)
- **Lehte olan yönler:** ABD'de *Google LLC v. Oracle America* (2021): Mahkeme 6–2 ile Google'ın Java SE API'sinin ~11.500 satırını Android için kullanmasını **adil kullanım** saydı; API kodunun telife tabi olup olmadığına **karar vermedi**, "tartışma uğruna" telifli varsaydı [DOĞRULANDI — ikincil özetler]. AB'de *SAS Institute v World Programming* (C-406/10, 2 Mayıs 2012): bir programın **işlevselliği, programlama dili ve veri dosyası biçimi** telifle korunan ifade biçimi değil; lisanslı kullanıcı işlevi gözlemleyip inceleyebilir [DOĞRULANDI — ikincil özetler, karar metni okunmadı]. Nuvio'daki "biçim/arayüz kullanımı" yaklaşımı bu çizgiyle uyumludur.
- **Aleyhte/zayıflatan yönler:** (1) Nuvio JS eklentileri **küçük, belgelenmiş bir betik yüzeyi** (≈30 global) ve veri biçimidir; CloudStream ise **~830 türlük, Kotlin'e özgü ikili ABI**dir (sınıf hiyerarşisi, yapay yöntem adları). (b) bu yapıyı (sınıf adları, sıra, organizasyon) çoğaltmayı gerektirir; Oracle davasında tartışmanın merkezi tam da "yapı, sıra, organizasyon" idi. (2) Birebir imzaya ek olarak **davranışı** (ör. kalite/sıralama/`M3u8Helper`, çıkarıcı mantığı) taklit etmek gerekir; bu, kodun mantığını kopyalamaya kayabilir. (3) Temiz oda: bu oturum kaynağı okudu (0. bölüm). (4) Yargı yetkisi: Türkiye'deki durum bu oturumda incelenmedi. (5) "CloudStream" adı/uyumluluk iddiası marka bilgisi konusu olabilir; arayüzde ad kullanılmaması mevcut kuralla (arayüzde "eklenti") uyumlu.
- **Sonuç (hüküm değil):** (b) lisans riski bakımından (a)'dan daha az "kesin", ama "Nuvio gibi basit" değildir; uzman görüşü olmadan "risk yok" denemez.

---

## 6. Güvenlik

### 6.1 Risk: DEX eklentisi uygulamayla aynı süreçte, aynı yetkiyle çalışır [DOĞRULANDI]
`PathClassLoader` + üst yükleyici = uygulama. Sonuçları:
- Uygulamanın **tüm izinleri** (INTERNET vb.) ve **UID**'si eklentinindir; Android'de süreç içi sandbox yoktur (SecurityManager Android'de çalışmaz [VARSAYIM: genel bilgi]).
- Uygulama verisi (Room veritabanı: izleme geçmişi/favoriler/eklenti listesi; DataStore; indirilmiş eklenti kodları) **doğrudan dosya erişimiyle** okunur/yazılır; `context` zaten `Plugin.load(context)` ile verilir.
- Uygulama **sınıflarına yansımayla** erişir: TMDB anahtarı `BuildConfig` içinde; **Faz 2 güvenlik kuralı (2) "Eklentiye gerçek TMDB anahtarı asla verilmez" DEX ile karşılanamaz** (anahtar APK'da gömülü ve aynı süreçte yansımayla okunabilir [VARSAYIM: R8'in alanı silip silmediği denenmedi]). 
- Ağ: eklenti kendi `OkHttpClient`/`URL`/`Socket`ini açabilir; **Faz 2 kuralı (3) NetworkPolicy (yerel/özel ağ engeli, SSRF)** ancak "uygulama kendi `app` nesnesini verirse ve eklenti ona uyarsa" işler — eklenti ona uymak zorunda değil. Yerel ağ cihazlarına (router, NAS) istek atılabilir.
- Dosya sistemi/Intent kuralı (4) ve kaynak sınırları: bellek/CPU/yığın sınırı yok; sonsuz döngü ana süreci tüketir; bellek taşması uygulamayı çökertir (`PluginLimits` QuickJS'e özgüydü).
- `loadLinks` sonucu `getVideoInterceptor` bir OkHttp interceptor'ı verebilir → **oynatma yolunda** eklenti kodu çalışır.
- Tedarik zinciri: depo yazarı bir sürümü sessizce zararlı güncelleyebilir; `fileHash` yalnızca **aktarım bütünlüğü** sağlar (hash aynı depodan geliyor), güven sağlamaz. CloudStream'de eklenti imzası yoktur [DOĞRULANDI: manifest'te yok; hash yalnızca `plugins.json`'da]. Varsayılan "otomatik indir/güncelle" ayarları vardır.
- CloudStream'in kendi kabulü: sorumluluk reddi + "güvenli mod" (çökme olunca hepsini kapat); yani koruma değil, hasar sınırlama.
- Dağıtım: **Google Play, uygulamanın Play dışından yürütülebilir kod (dex/JAR/.so) indirmesini yasaklar** (Device and Network Abuse; yorumlayıcı/VM içindeki kod, örn. WebView JavaScript'i, istisna). Faz 2'nin QuickJS eklentileri bu istisna kapsamına girer; **DEX eklentisi girmez** [DOĞRULANDI — politika metni]. Kişisel kullanım dışı Play dağıtımını fiilen kapatır.

### 6.2 Azaltma seçenekleri ve ne kadar güvenlik sağlarlar
| Önlem | Ne sağlar | Ne sağlamaz | Maliyet |
|---|---|---|---|
| Açık uyarı + varsayılan kapalı + tek tek açma (Faz 2'deki gibi) | Bilinçli onay | Teknik sınır yok | Küçük |
| `fileHash` doğrulama + **ilk kurulumda sabitleme**, hash değişirse yeniden onay, otomatik güncelleme **kapalı** | Aktarımda bozulma / sessiz değişim | Kötü niyetli ilk sürüm | Küçük–orta |
| Yalnızca kullanıcı onaylı depolar, depo listesi gömülmez (mevcut kural) | Hazır güven iddiası yok | Depo içeriği | Yok |
| Süreç sınırı yok ama **zaman aşımı + watchdog** (ayrı süreçte öldürme) | Sonsuz döngü/çökme yalıtımı | Veri sızıntısı | Orta |
| Ayrı süreç (`android:process`, izolesiz) | Çökme yalıtımı | **Aynı UID: veri/izin aynı** | Orta |
| **`android:isolatedProcess="true"` hizmeti** | Resmî belge: *"special process isolated from the rest of the system and has no permissions of its own; the only communication is through the Service API"* [DOĞRULANDI]. Farklı UID → uygulama dosyalarına, veritabanına, BuildConfig'e erişim yok; ağ için INTERNET izni yok [VARSAYIM: belge yazmıyor, Chromium/GeckoView örüntüsü; **cihazda sınanmalı**] | Eklentiyi çalıştıran kodu (host sınıfları) **izole süreçte de** sağlamak gerekir; ağ/depo çağrıları Binder üzerinden **ana sürece vekil** edilir → Faz 2'deki `PluginHttp + NetworkPolicy` ile **aynı model**; ancak NiceHttp'yi (a) olduğu gibi kullanamazsınız, kendi `Requests` uygulamanızı yazmanız gerekir. WebView tabanlı çözücüler (`WebViewResolver` 10/91, `CloudflareKiller` 4/91) izole süreçte çalışmaz. `getVideoInterceptor` döndüren eklentiler oynatma yoluna giremez. `openSettings`/fragment ayar arayüzleri çalışmaz. Dex'i izole sürece **dosya olarak değil bellek/PFD ile** vermek gerekir (UID farkı); `InMemoryDexClassLoader` **API 26+** [VARSAYIM: bilgi, bu oturumda doğrulanmadı] → minSdk 24 (24–25) cihazlarda yok | Büyük |
| Kayıtlı kaynak kodu denetimi / statik tarama | Bilinen kötü kalıpları yakalar | Karartılmış kod | Büyük, kısmi |

### 6.3 Hangi tasarımla ne kadar güvenlik
- **Aynı süreç (CloudStream gibi):** Faz 2'nin 5 güvenlik kuralından (anahtar gizleme, ağ politikası, dosya/Intent kapalı, süre-bellek sınırı, arka plan havuzu) **yalnızca (5) sağlanır**; diğerleri sağlanamaz. Uygulamanın tüm kullanıcı verisi ve izinleri eklenti yazarının eline geçer. Bu, CLAUDE.md'deki Faz 2 güvenlik kurallarını Faz 3 için **gevşetmek** demektir (kullanıcı kararı gerekir).
- **İzole süreç:** (1)–(4) için gerçek bir sınır sağlayabilir ama (a)'dan fazla iş ve uyumluluk kaybı getirir; sınır **kanıtlanana kadar** (cihaz denemesi) güven iddia edilmemeli.
- Hiçbir tasarım, eklentinin **sizin için topladığı bağlantılardaki** içeriğin güvenliğini (kötü amaçlı oynatma adresleri) çözmez; bu Faz 2'de de aynıdır.

---

## 7. Entegrasyon farkları (Play'in kaynak modeline uyum)

Maliyeti etkileyen, istenmemiş ama önemli bir bulgu. Play'in akışı **TMDB kimliği → kaynaklar** (`StreamRequest(tmdbId, imdbId, type, season, episode)`). CloudStream sağlayıcıları ise **site merkezli ve kimliksiz**:
1. `search(başlık)` → site içi `SearchResponse(url)`; 2. `load(url)` → `LoadResponse` (film: `dataUrl`; dizi: `Episode(data, season, episode)` listesi); 3. `loadLinks(data, …)` → `ExtractorLink` akışı (içinde `loadExtractor` ağacı).
- **Eşleme sorunu:** başlık+yıl ile arama, en iyi eşleşmeyi seçme (kütüphanede `Levenshtein` var), dizide sezon/bölüm numaralandırması (anime mutlak numaralandırma, bölüm kaydırmaları). `LoadResponse` bazen IMDb/TMDB kimliği taşır (`addImdbId/addTMDbId`, `syncData`) — doğrulamada kullanılabilir ama zorunlu değil. Yanlış eşleme riski Nuvio'daki `getStreams(tmdbId, …)` çağrısında yoktur.
- Her kaynak aramada en az 3 ağ turu + çıkarıcı yayılması (Nuvio: genelde 1–N). Yavaş; mevcut ortak zaman aşımıyla (varsayılan 15 sn) uyumsuz kalabilir.
- Tür/dil süzgeci: `tvTypes` (Movie, TvSeries, Anime, Torrent, Live, NSFW, …) ve `lang` ile eklenti listesi; yalnızca film/dizi ilgili. `Torrent/Magnet` linkleri kapsam dışı (torrent motoru yok); `DRM`li akışlar ayrı Media3 DRM bağlantısı ister.
- Çıktı eşlemesi (`ExtractorLink` → `StreamResult`: ad, kalite, referer/headers → proxyHeaders, M3U8/DASH türü) Faz 2'deki `PluginStream` işiyle benzer ve **ucuz kısımdır**.
- Eklenti ayarları (`openSettings`) rastgele Android arayüzü kurar; "Ayarlar ≤ 5 kalem" ve Faz 2'nin küçük form modeline sığmaz. Desteklenmezse, API anahtarı/hesap isteyen eklentiler çalışmaz.

---

## 8. Maliyet ve öneri

### 8.1 İş kalemleri (tahmin, tek geliştirici, bu projenin temposu; hepsi [VARSAYIM])
| # | Kalem | Seçenek (a) | Seçenek (b) | (a)+izole süreç |
|---|---|---|---|---|
| 1 | `repo.json`/`plugins.json` ayrıştırma, kısa kod/`cloudstreamrepo://` çözümü, Faz 2 `SourceInstaller` akışına bağlama | 2–3 gün | 2–3 gün | 2–3 gün |
| 2 | `.cs3` indirme/SHA-256/ZIP/manifest okuma, read-only dosya, saklama (Room `plugins` tablosu Faz 3'e hazır) | 2–3 gün | aynı | aynı |
| 3 | `PathClassLoader` yükleme, kayıt/boşaltma, güvenli mod, oat temizliği | 3 gün | 3 gün | 5+ gün |
| 4 | Host sınıfları: **(a)** `Plugin/CommonActivity/CloudStreamApp/DataStore/CloudflareKiller` kabuğu + kütüphane başlangıcı + R8 keep kuralları; **(b)** ~800+ tür ABI'sinin yazılıp ikili uyumlu doğrulanması | 1,5–2,5 hafta | 2–4+ **ay**, sürekli | 3–5 hafta (Binder vekil, kendi `Requests`) |
| 5 | Sağlayıcı: `CloudStreamStreamProvider` (arama→eşleme→load→loadLinks), eşleme sezgileri, Composite'e ekleme, zaman aşımı/önbellek | 1–1,5 hafta | aynı | aynı |
| 6 | Çıkarıcı kapsamı | kütüphane ile gelir (104) | **ayrı büyük iş** (haftalar) | kütüphane ile gelir, izoledeki sınırlarla |
| 7 | Gerçek eklentilerle sınama (sınıf/yöntem boşlukları, çökme izleme) | 1 hafta | sürekli | 1–2 hafta |
| 8 | Test altyapısı (sahte `.cs3` üretimi için Kotlin/D8 derlemesi), güvenlik testleri | 3–5 gün | 3–5 gün | 1 hafta+ |
| 9 | Arayüz: tek metin ("depo ekle" penceresi zaten var), uyarı metinleri (TR/EN), eklenti listesi/anahtar | 2–3 gün | aynı | aynı |
| | **Toplam (kabaca)** | **≈ 5–7 hafta** | **≈ 3–6 ay + sürekli** | **≈ 8–11 hafta** |

### 8.2 Karşılaştırma
| | (0) Yapma | (a) Kütüphane + kabuk | (b) Sıfırdan uyumluluk | (a) + izole süreç |
|---|---|---|---|---|
| Zorluk | – | Orta | Çok yüksek | Yüksek |
| Lisans | Değişmez | **GPL-3.0'a geçiş** (dağıtılırsa kaynak açma) | Belirsiz (arayüz/temiz oda) | GPL-3.0 |
| Güvenlik | Mevcut Faz 2 sınırları | Faz 2 kurallarının çoğu **sağlanamaz** | Aynı süreçte aynı sorun | Sınır olası ama **doğrulanmadı** |
| APK | 6,8 MB | tahmin 13–19 MB | tahmin 8–12 MB | tahmin 13–19 MB |
| Bakım | – | CloudStream ABI'sini izleme (ABI denetimi lehte); Jackson/Rhino sabitleme; OkHttp 4/5 uyumu | Sonsuz yetişme | (a) + Binder arayüzü |
| Play dağıtımı | Mümkün | **Mümkün değil** (dinamik DEX) | Mümkün değil | Mümkün değil |
| Eklenti uyumu | – | sınıf düzeyinde %85–95 (ölçüm, kabuk sınıflarıyla); **gerçek çalışma oranı bilinmiyor** | sınıf düzeyinde %5–41 ile başlar (kütüphane / + `Plugin`), artış yavaş | (a)'dan az: WebView, interceptor, ayar arayüzü eklentileri çalışmaz (10+4+6+16 eklenti, örtüşmeli, 91'de) |

### 8.3 Öneri
**Önerim: Faz 3'ü şimdilik yapmamak (seçenek 0).** Gerekçe, olgulardan:
1. Faz 2'nin bağlayıcı güvenlik kuralları (anahtar gizleme, ağ politikası, süre/bellek sınırı) DEX ile **aynı süreçte karşılanamaz**; karşılayacak tasarım (izole süreç) +%50–100 iş ve uyumluluk kaybı demek ve cihazda kanıtlanmadı.
2. Uyumlu en kısa yol (a), uygulamayı **GPL-3.0**'a bağlar ve Play dağıtımını kapatır; (b) ise yıllık bir yetişme işidir ve lisans/temiz oda belirsizliği taşır.
3. CloudStream sağlayıcıları TMDB-öncelikli akışa uymaz (eşleme + 3 ağ turu); sonuç kalitesi Nuvio kazıyıcılarından daha belirsizdir.
4. Faz 2 zaten (gerçek depoda 43 eklentiden 25'i kaynak döndürüyor) çalışan, sınırlı ve güvenli bir eklenti yolu sunuyor; CLAUDE.md ilkesi 5: "bu olmadan uygulama çalışır mı?" → çalışıyor.

**Yine de istenirse:** önce **zaman kutulu bir deney (3–5 gün, ana koddan ayrı dal, GPL kabulüyle)**: (a)'nın en küçük hâli — `repo.json`/`.cs3` indir, `PathClassLoader` ile yükle, `Plugin/CommonActivity/CloudStreamApp` kabuğu yaz, 10 gerçek eklentiyi bir film/bir dizi için çalıştır. Çıktı: gerçek çalışma oranı, APK boyutu, çökme türleri, izole süreçte ağ denemesi (INTERNET'siz UID soket açabiliyor mu?). Bu sayılarla (a) veya "hayır" kararı verilir; (b) deney kapsamına alınmaz.

### 8.4 Kullanıcıya sorulacak kararlar (Faz 3'e geçilirse)
1. Play yalnızca kişisel kullanım mı kalacak, yoksa paylaşılacak mı? (GPL-3.0 etkisi bu cevaba bağlı.)
2. GPL-3.0'a geçiş (ya da hukuki görüş) kabul mü?
3. APK'da +6–12 MB (tahmin) kabul mü?
4. Güvenlik düzeyi: Faz 2 kurallarını gevşetip uyarıyla mı gidilecek, yoksa izole süreç yatırımı mı yapılacak?
5. Eklenti ayar ekranları (`openSettings`) desteklenmeyecek diye kabul mü?

---

## 9. Ölçümlerin yeniden üretimi ve sınırları
- Betikler oturumun geçici klasöründeydi (repoya girmedi): `.cs3` ZIP okuma + `classes.dex` dize/yöntem tablosu ayrıştırma (Node), `library.api` tür listesi ile karşılaştırma. İndirilen 91 `.cs3` yalnızca **okundu**, hiçbiri yüklenmedi/çalıştırılmadı; repoya konmadı.
- Kapsam oranları **sınıf adı** düzeyindedir (yöntem imzası, alan, davranış denenmedi). Dex'te başvurulan ama hiç çağrılmayan sınıflar da "ihtiyaç" sayılmış olabilir (ölçüm üst sınırdır); öte yandan yansıma/dize ile çözülen sınıflar görünmez (alt sınır).
- Erişilemeyen kaynaklar 0. bölümde listeli.
