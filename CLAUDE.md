# Play — proje kuralları

Sade, özgün bir Android film/dizi istemcisi. Kotlin + Jetpack Compose + Material 3. applicationId `com.kaan.play`, minSdk 24, compileSdk/targetSdk 37.

## Kopyalama yok, özgün kimlik (bağlayıcı kural)
- Her şey sıfırdan yazılır. Başka bir uygulamadan kod, ikon, logo, metin, renk veya kart biçimi kopyalanmaz.
- `referans/` içindeki ekran görüntüleri yalnızca **düzen ve bilgi hiyerarşisi** için bakılır. Renk, ikon, metin ve kart biçimi özgün olur. Referanstaki fazla ayar ve süsler taşınmaz.
- Görsel kimlik: soğuk koyu zemin (`#0E1116`) + tek turkuaz vurgu (`#2DD4BF`; referanstaki altın/sarıdan bilerek uzak), kendi çizgi ikonlarımız (`ui/nav/PlayIcons.kt`), kendi launcher ikonumuz.
- Arayüzde "Stremio" ismi geçmez; "eklenti/addon" denir (İngilizcede "addon").

## Dil desteği (İngilizce + Türkçe, baştan)
- Arayüzdeki **hiçbir metin koda gömülmez**; hepsi string resource. Varsayılan `values/strings.xml` İngilizce, `values-tr/strings.xml` Türkçe. Yeni string eklerken iki dosyaya birden eklenir (`StringResourcesTest` anahtar eşitliğini denetler). Çevrilmeyecek metinler (dil adları, uygulama adı) `translatable="false"`.
- Cihaz dili Türkçeyse Türkçe, değilse İngilizce açılır. Ayarlar'da "Dil": Sistem / English / Türkçe; AppCompat per-app language ile uygulanır (`i18n/AppLanguage.kt`, `MainActivity` bu yüzden `AppCompatActivity`). Seçimin kaynağı AppCompat'tir, DataStore'a kopyalanmaz.
- **TMDB** isteklerinde `language` parametresi `AppLanguage.effective().tmdbTag` (`tr-TR` / `en-US`), her istekte yeniden okunur. Detayda Türkçe özet boşsa, bölümde boş bölüm özetleri varsa `en-US` ile bir kez daha istenip boşluk doldurulur (yedek isteği başarısız olursa Türkçe sonuç döner). Listelerde (trend/popüler/arama) yedek yok; kartlar başlık+poster gösterir. Poster yolu `language` parametresiyle gelen `poster_path`tir. Başlık logosu (10. adım): Detay'te aynı istekle (`append_to_response=images,credits,release_dates|content_ratings`, `include_image_language=<dil>,en,null`), kapak karuselinde kapak başına ayrı bir `images` çağrısıyla (en iyi çaba; olmazsa metin başlık) gelir.
- **Varsayılan altyazı dili** ayarı, kullanıcı seçmediyse seçili arayüz diline göre başlar (DataStore'da null = arayüz dilini izle).
- Çok sayılı/biçimli metinler için `plurals` ve biçim argümanları kullanılır; metin birleştirme yapılmaz.

## Arayüz ilkeleri (kalıcı kural)
1. **Tek ana iş:** her ekranın tek bir ana işi vardır. İkincil seçenekler gizli menüde ya da altta küçük durur.
2. **Sade görünüm:** ekranda aynı anda en fazla bir vurgu rengi ve iki yazı boyutu/ağırlığı kullanılır.
3. **Teknik ayrıntı yok:** codec, HDR, ses kanalı vb. arayüzde gösterilmez; kaynak satırında yalnızca eklenti adı, kalite, boyut, ses dili ve "altyazılı" bilgisi (10. adımda dil/altyazı eklendi).
4. **Ayarlar ≤ 5 kalem:** her kalem tek satırda anlaşılır olmalı.
5. **Yeni özellik testi:** eklemeden önce "bu olmadan uygulama çalışır mı?" diye sorulur; çalışıyorsa eklenmez.
6. **İki dilde taşmasın:** her ekran Türkçe ve İngilizce metinle taşmadan görünür; uzun Türkçe metinlerle (ve büyük yazı boyutuyla) test edilir.

## Kapsam
- Ekosistemler: (1) Nuvio addon'ları (manifest.json URL'li HTTP addon), (2) Faz 2 Nuvio plugin'leri (JS scraper), (3) Faz 3 CloudStream .cs3.
- **Faz 1 tamamlandı (1–10).** **Faz 2 (Nuvio plugin'leri / JS kazıyıcılar), kullanıcının açık isteği ve onayıyla tur tur ilerliyor;** Faz 2 turlarına ve Faz 3'e kendiliğinden geçilmez, her tur sonunda kullanıcı onayı beklenir. Faz 2'nin ayrıntıları ve güvenlik kuralları aşağıdaki "Faz 2" bölümündedir; doğruluk kaynağı `docs/nuvio-plugin-format.md`. (8. adım "web sitesi ekle" Faz 1'e kullanıcının isteğiyle eklenen reklamsız web görünümüdür; kaynak toplama/oynatıcıyla ilgisi yoktur ve Faz 2 scraper'ı değildir.)
- Faz 1 hedefi: kullanıcı addon manifest URL'si ekler → ana sayfa TMDB'den beslenir → içeriğe girince addon'lardan kaynaklar toplanır → Media3 ile oynatılır → geçmiş, kaldığı yerden devam ve favoriler yerelde tutulur.

## Faz 2 — Nuvio eklenti depoları (JS kazıyıcılar)
Doğruluk kaynağı: `docs/nuvio-plugin-format.md` (gerçek dosyalardan ve resmî uygulamanın çalışma ortamından türetildi). Nuvio uygulamaları GPL-3.0'dır: **kod kopyalanmaz**, yalnızca belgelenen arayüz kendi kodumuzla uygulanır.
- **1. tur ✅ (yönetim + çalıştırıcı, kaynak listesine bağlı değil):** "Eklenti ekle" adresin **içeriğine** bakıp türü kendisi anlar (`resources` → addon, `scrapers` dizisi → eklenti deposu, `pluginLists` → desteklenmiyor; `data/plugin/PluginRepoManifest.kt`, `SourceInstaller`). Depo eklenince kazıyıcı dosyaları indirilir (1 MB sınırı, HTML/boş dosya reddi, SHA-256), kapalı kurulur. Ayarlar > Eklentiler > "Kurulu tüm depolar" (güncelleme denetimi = sürüm karşılaştırma, kaldırma) ve "Yüklü eklentiler" (kazıyıcı başına anahtar) dolu; bilgi notu: "Eklentiler üçüncü taraflarca yazılır; Play içerik sağlamaz." QuickJS çalıştırıcısı `data/plugin/runtime/PluginRuntime`; `AppContainer.pluginRuntime`.
- **2. tur (onay bekliyor):** kaynak listesine/Oynat'a bağlama (`StreamProvider`), eklenti ayarları (`onSettings`/`SCRAPER_SETTINGS`).
- **Güvenlik kuralları (bağlayıcı):** (1) Hiçbir varsayılan depo/eklenti gömülmez. (2) Eklentiye gerçek TMDB anahtarı **asla** verilmez: `TMDB_API_KEY` global'i yer tutucudur; ağ köprüsü yalnızca `https://api.themoviedb.org` isteklerinde yerine koyar (yönlendirme izlenmez); anahtar günlüğe yazılmaz, `console` çıktısı maskelenir. (3) Eklenti ağı yalnızca `PluginHttp` + `NetworkPolicy` ile: http/https, yerel/özel/link-local/CGNAT adresleri ve `localhost` reddedilir (çözümlenen adres ve her yönlendirme adımı denetlenir). (4) Dosya sistemi, veritabanı, Intent köprülenmez; `require` yalnızca `cheerio` ve `crypto-js`; her çağrı yeni QuickJS örneği, çerezler çağrıya özel bellek içi; süre/bellek/yığın/istek/bayt sınırları (`PluginLimits`). (5) Çalıştırma arka plan havuzunda, ana ekran donmaz.

## Kapsam dışı
Aniyomi, ayrı Stremio ekosistemi, manga, IPTV, Telegram, tracker, profil/kilit, ayrı bir Android TV arayüzü (Compose for TV / Leanback ekranları; mevcut ekranlar kumandayla da kullanılır), torrent motoru, toplu indirme.

## Mimari kararlar
- **Yığın:** Compose + M3, Navigation Compose, Coroutines/Flow, OkHttp, kotlinx.serialization (tek JSON kütüphanesi), Media3 (exoplayer, hls, dash, ui, datasource-okhttp), Coil, Room, DataStore. DI framework yok; elle kurulan `AppContainer`.
- **Metadata:** TMDB (trend, popüler, arama, detay, sezon/bölüm, poster). Anahtar `local.properties` → `TMDB_API_KEY` → `BuildConfig`. Anahtar git'e girmez. Hakkında ekranı TMDB'nin zorunlu atıf metnini gösterir.
  Katman: `data/tmdb` (`TmdbService` ince HTTP/DTO, `TmdbRepository` alan modeli + dil yedeği, `TmdbException` tek hata türü, `TmdbImages` görsel adresleri). Anahtar `api_key` sorgu parametresiyle gider (v3); URL asla loglanmaz ve hatalara girmez.
- **Kimlik:** birincil kimlik TMDB id. Addon'lar IMDb `tt...` ister; TMDB `external_ids` ile türetilir.
- **Akış kaynağı arayüzü:** `StreamProvider.streams(request: StreamRequest(tmdbId, imdbId, type, season?, episode?)): Flow<StreamResult>`. Faz 1'de tek implementasyon `AddonStreamProvider`; Faz 2/3 kaynakları aynı arayüze takılacak şekilde tasarlanır.
- **Addon protokolü:** `GET {base}/manifest.json`, `GET {base}/stream/{movie|series}/{imdbId}.json` (dizide `{imdbId}:{sezon}:{bölüm}`). Manifest'teki `resources/types/idPrefixes` dikkate alınır; addon'a yalnızca desteklediği istek gider. Sadece `url` tipli akışlar gösterilir, `infoHash` yok sayılır. `behaviorHints.proxyHeaders` Media3 OkHttp datasource'una aktarılır. Akıştaki altyazılar oynatıcıya eklenir.
- **Kaynak toplama:** aktif tüm addon'lara paralel istek, addon başına ayrı zaman aşımı (varsayılan 15 sn, ayarlanabilir). Biri takılırsa diğerleri beklemez; sonuçlar Flow ile akar. Hatalar yutulmaz; addon bazında "yanıt vermedi" bilgisi tutulur.
- **Veri:** Room (`PlayDatabase` v2, şema `app/schemas/`'e dışa aktarılır; 1→2 göçü `plugins` tablosunu yeniden kurar, diğer tablolara dokunmaz): `addons` (`type`: NUVIO_ADDON / NUVIO_PLUGIN_REPO / CLOUDSTREAM_REPO / WEB_SITE; Faz 1'de addon olarak yalnızca NUVIO_ADDON eklenebilir ve okunur, WEB_SITE yalnızca web sitesi listesi içindir), `plugins` (depo → plugin; Faz 2: kazıyıcı üst bilgisi, SHA-256 özeti, `enabled` varsayılan KAPALI; kodlar `filesDir/plugins/{satır kimliği}.js` dosyasında, Faz 3 için de aynı tablo), `watch_history`, `favorites` (şema hazır, davranış 7. adımda). Ayarlar DataStore'da. Oynatma konumu ~10 sn'de bir kaydedilir.
- **Web siteleri (8. adım):** Ayarlar > Eklentiler içinde "Web sitesi ekle" + "Web siteleri" listesi (Ayarlar'a yeni kalem eklenmez). `addons` tablosunda `type = WEB_SITE` satırı (şema/göç yok); eklenti listeleri ve kaynak toplama bunları görmez. Site `WebActivity` içinde (WebView) açılır; kurallar `data/web/WebPolicy` (Android'den bağımsız, birim testli), reklam alan adı listesi ayrı dosyada `data/web/AdDomains.kt` (kendi derlediğimiz küçük liste; başkasının lisanslı filtre listesi kopyalanmaz). Engellenenler: listedeki alan adlarına istekler, hareketsiz/yabancı açılır pencereler, http/https dışı gezinme (`intent://`, `market://` …), hareketsiz başka siteye atlama, JS alert/confirm/prompt, üçüncü taraf çerezleri. **Her reklamın engelleneceği vaat edilmez** (Hakkında/README'de not var). Site kaldırılınca çerez/depolama/önbellek silinir. Varsayılan site gömülmez. Çubuk ve sayfa D-pad ile erişilebilir olmalı (TV kuralı).
- **Gömülü içerik yok:** uygulamaya hiçbir varsayılan addon/içerik kaynağı gömülmez. Addon'lar yalnızca kullanıcının girdiği URL'lerden gelir. https manifest bağlantıları uygulamayı açıp "addon ekle" akışını başlatır.

## Görünüm kimliği (10. adım)
- Koyu, sinematik, büyük afişli, sade. Tek vurgu turkuaz; yeni renk eklenmez. Afiş köşe yuvarlaklığı 14dp (`MaterialTheme.shapes.medium`); birincil düğmeler turkuaz dolgulu (beyaz hap değil).
- Kendi puan rozetimiz: turkuaz çerçeveli "TMDB 8.0" (`ui/components/Badges.kt`); yaş sınırı çipi nötr çerçeveli. IMDb rozeti yok.
- Başlık logosu TMDB `images`ten (arayüz dili, yoksa İngilizce/dilsiz); yoksa metin başlık. Fragman bölümü yoktur.
- Ana sayfa ve Detay durum çubuğunun arkasına uzanır (`StatusBarScrim`); alt/yan menü etiketleri en çok 1.0 yazı ölçeğiyle çizilir (büyük yazıda "Kütüphane" kısalmasın).

## Ekranlar
Ana sayfa (üstte kapak karuseli; altında "devam et" ve TMDB afiş sıraları, her sıranın "tümü" ekranı), Ara, Detay (arka plan görseli, başlık logosu, Oynat/sezon seçici + "daha fazla", puan rozeti, oyuncular), Kaynak seçimi (alttan liste; yenile + eklenti süzgeci, eklenti başlıkları altında kaynaklar; en uygun seçili, tek dokunuş; satırda eklenti adı + "kalite · boyut · dil · altyazılı"), Oynatıcı (tam ekran, oynat/durdur, ileri-geri, altyazı, hız), Kütüphane (geçmiş + favoriler), Ayarlar. Alt menü: Ana sayfa, Ara, Kütüphane, Ayarlar.
Web görünümü (Ayarlar > Eklentiler > Web siteleri'nden açılır): üstte sade çubuk (kapat, site adı, geri, ileri, yenile) + WebView.
Ayarlar en çok beş kalem: Eklentiler (ekle/sil/aç-kapat; web sitesi ekle/sil; kaynak zaman aşımı bu ekranın altında küçük bir ayar), Oynatıcı (altyazı görünümü, kaldığın yerden devam; 7. adım), Tema, Dil, Hakkında. Addon yokken Ana sayfa: ortada tek cümle ve tek buton ("Başlamak için bir eklenti ekle"). Koyu tema varsayılan.

## Çalışma şekli
- Adımlar sırayla: (1) iskelet ✅ → (2) TMDB katmanı ✅ → (3) Room + addon yönetimi ✅ → (4) ana sayfa/arama/detay ✅ → (5) kaynak toplama ✅ → (6) oynatıcı ✅ → (7) kütüphane + ayarlar ✅ → (8) web sitesi ekle (reklamsız web görünümü; kullanıcı isteğiyle eklendi) ✅ → (9) TV, tablet ve kumanda desteği ✅ → (10) görünüm cilası (kapak karuseli, sinematik Detay, kaynak listesi, alt çubuk, kumanda düzeltmeleri) ✅. **Faz 1 tamamlandı.** Faz 2: 1. tur ✅ (eklenti deposu yönetimi + QuickJS çalıştırıcı; kaynak listesine bağlı değil); 2. tur (kaynak listesine bağlama + eklenti ayarları) için kullanıcı onayı beklenir.
- 1. adımdan sonra kullanıcı onayı beklenir. Her adımın sonunda derle, çalışan testleri çalıştır, iki cümleyle özetle.
- Parser ve protokol kodu sahte JSON ile birim testlerle doğrulanır.
- Belirsiz tasarım kararında en sade seçenek seçilir ve README'ye not düşülür.
- Küçük, anlamlı commit'ler.

## TV ve tablet uyumluluğu
- Şimdilik hedef telefon; ama kod TV/kumanda (normal kumanda + air kumanda/imleç) ve tablet için sonradan ayrı bir adımda genişletilebilir olmalı.
- Tüm tıklanabilir öğeler D-pad/klavye ile odaklanabilir olmalı (Compose'da `clickable`/`focusable` kullan; yalnızca `pointerInput` ile dokunma yakalama yapma).
- Yalnızca dokunmaya bağlı hareketlere (sürükle, çift dokun, uzun bas) dayanma; her işlemin düğmeyle bir karşılığı olsun.
- Oynatıcı kontrolleri kumanda ile erişilebilir olsun.
- Sabit piksel yerine esnek düzen kullan; ızgara sütun sayısı ekran genişliğinden hesaplansın.
- Android TV/tablet/kumanda desteği 9. adımda yapıldı: leanback girişi + afiş, her tıklanabilir öğede turkuaz odak halkası (`ui/tv`: `PlayIndication`, `focusRing`, `PlayButtons`), ekran başına başlangıç odağı ve geri dönüşte odak hafızası (`focusMemory`), oynatıcı kumanda eşlemesi (`PlayerKeys`), geniş ekranda yan menü ve iki sütunlu Detay (`PlayLayout`). Yeni ekran/öğe eklerken: Material düğmeleri yerine `ui/components/PlayButtons.kt` içindekileri içe aktar, kendi `clickable` satırında ek bir şey gerekmez, menü/anahtar gibi öğelere `Modifier.focusRing`, listelere `rememberFocusMemory`. Ayrı bir TV arayüzü yoktur; aynı ekranlar kumandayla kullanılır. Hava kumandası imleci için ekstra bir şey yapılmaz.

## Ortam
Windows + VS Code (Android Studio yok). JDK 17, SDK `%LOCALAPPDATA%\Android\Sdk`. Derleme: `.\gradlew assembleDebug`. Test cihazı LDPlayer: `adb connect 127.0.0.1:5555`. Sürümler `gradle/libs.versions.toml` içinde sabit.
