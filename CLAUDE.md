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
- **TMDB** isteklerinde `language` parametresi `AppLanguage.effective().tmdbTag` (`tr-TR` / `en-US`), her istekte yeniden okunur. Detayda Türkçe özet boşsa, bölümde boş bölüm özetleri varsa `en-US` ile bir kez daha istenip boşluk doldurulur (yedek isteği başarısız olursa Türkçe sonuç döner). Listelerde (trend/popüler/arama) yedek yok; kartlar başlık+poster gösterir. Poster yolu `language` parametresiyle gelen `poster_path`tir; ayrı bir images çağrısı yok.
- **Varsayılan altyazı dili** ayarı, kullanıcı seçmediyse seçili arayüz diline göre başlar (DataStore'da null = arayüz dilini izle).
- Çok sayılı/biçimli metinler için `plurals` ve biçim argümanları kullanılır; metin birleştirme yapılmaz.

## Arayüz ilkeleri (kalıcı kural)
1. **Tek ana iş:** her ekranın tek bir ana işi vardır. İkincil seçenekler gizli menüde ya da altta küçük durur.
2. **Sade görünüm:** ekranda aynı anda en fazla bir vurgu rengi ve iki yazı boyutu/ağırlığı kullanılır.
3. **Teknik ayrıntı yok:** codec, HDR, ses kanalı vb. arayüzde gösterilmez; kaynak satırında yalnızca kalite ve boyut.
4. **Ayarlar ≤ 5 kalem:** her kalem tek satırda anlaşılır olmalı.
5. **Yeni özellik testi:** eklemeden önce "bu olmadan uygulama çalışır mı?" diye sorulur; çalışıyorsa eklenmez.
6. **İki dilde taşmasın:** her ekran Türkçe ve İngilizce metinle taşmadan görünür; uzun Türkçe metinlerle (ve büyük yazı boyutuyla) test edilir.

## Kapsam
- Ekosistemler: (1) Nuvio addon'ları (manifest.json URL'li HTTP addon), (2) Faz 2 Nuvio plugin'leri (JS scraper), (3) Faz 3 CloudStream .cs3.
- **Şu an yalnızca Faz 1.** Faz 2 ve 3'e başlanmaz. Faz 1 bitince kullanıcıya "Faz 1 tamam" denir ve durulur. (8. adım "web sitesi ekle", kullanıcının isteğiyle Faz 1'e eklenen reklamsız web görünümüdür; kaynak toplama/oynatıcıyla ilgisi yoktur ve Faz 2 scraper'ı değildir.)
- Faz 1 hedefi: kullanıcı addon manifest URL'si ekler → ana sayfa TMDB'den beslenir → içeriğe girince addon'lardan kaynaklar toplanır → Media3 ile oynatılır → geçmiş, kaldığı yerden devam ve favoriler yerelde tutulur.

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
- **Veri:** Room (`PlayDatabase` v1, şema `app/schemas/`'e dışa aktarılır): `addons` (`type`: NUVIO_ADDON / NUVIO_PLUGIN_REPO / CLOUDSTREAM_REPO / WEB_SITE; Faz 1'de addon olarak yalnızca NUVIO_ADDON eklenebilir ve okunur, WEB_SITE yalnızca web sitesi listesi içindir), `plugins` (depo → plugin, Faz 2/3 için hazır, Faz 1'de boş), `watch_history`, `favorites` (şema hazır, davranış 7. adımda). Ayarlar DataStore'da. Oynatma konumu ~10 sn'de bir kaydedilir.
- **Web siteleri (8. adım):** Ayarlar > Eklentiler içinde "Web sitesi ekle" + "Web siteleri" listesi (Ayarlar'a yeni kalem eklenmez). `addons` tablosunda `type = WEB_SITE` satırı (şema/göç yok); eklenti listeleri ve kaynak toplama bunları görmez. Site `WebActivity` içinde (WebView) açılır; kurallar `data/web/WebPolicy` (Android'den bağımsız, birim testli), reklam alan adı listesi ayrı dosyada `data/web/AdDomains.kt` (kendi derlediğimiz küçük liste; başkasının lisanslı filtre listesi kopyalanmaz). Engellenenler: listedeki alan adlarına istekler, hareketsiz/yabancı açılır pencereler, http/https dışı gezinme (`intent://`, `market://` …), hareketsiz başka siteye atlama, JS alert/confirm/prompt, üçüncü taraf çerezleri. **Her reklamın engelleneceği vaat edilmez** (Hakkında/README'de not var). Site kaldırılınca çerez/depolama/önbellek silinir. Varsayılan site gömülmez. Çubuk ve sayfa D-pad ile erişilebilir olmalı (TV kuralı).
- **Gömülü içerik yok:** uygulamaya hiçbir varsayılan addon/içerik kaynağı gömülmez. Addon'lar yalnızca kullanıcının girdiği URL'lerden gelir. https manifest bağlantıları uygulamayı açıp "addon ekle" akışını başlatır.

## Ekranlar
Ana sayfa (TMDB satırları + "devam et"), Ara, Detay, Kaynak seçimi (alttan liste; en uygun seçili, tek dokunuş; satırda yalnızca kalite ve boyut), Oynatıcı (tam ekran, oynat/durdur, ileri-geri, altyazı, hız), Kütüphane (geçmiş + favoriler), Ayarlar. Alt menü: Ana sayfa, Ara, Kütüphane, Ayarlar.
Web görünümü (Ayarlar > Eklentiler > Web siteleri'nden açılır): üstte sade çubuk (kapat, site adı, geri, ileri, yenile) + WebView.
Ayarlar en çok beş kalem: Eklentiler (ekle/sil/aç-kapat; web sitesi ekle/sil; kaynak zaman aşımı bu ekranın altında küçük bir ayar), Oynatıcı (altyazı görünümü, kaldığın yerden devam; 7. adım), Tema, Dil, Hakkında. Addon yokken Ana sayfa: ortada tek cümle ve tek buton ("Başlamak için bir eklenti ekle"). Koyu tema varsayılan.

## Çalışma şekli
- Adımlar sırayla: (1) iskelet ✅ → (2) TMDB katmanı ✅ → (3) Room + addon yönetimi ✅ → (4) ana sayfa/arama/detay ✅ → (5) kaynak toplama ✅ → (6) oynatıcı ✅ → (7) kütüphane + ayarlar ✅ → (8) web sitesi ekle (reklamsız web görünümü; kullanıcı isteğiyle eklendi) ✅ → (9) TV, tablet ve kumanda desteği ✅. **Faz 1 tamamlandı.** Faz 2'ye kendiliğinden başlanmaz; sıradaki adım için kullanıcı onayı beklenir.
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
