# Changelog / Değişiklik günlüğü

## [1.0.4] — 2026-10-14

### Türkçe
- **Sonraki bölüm otomatik açılır:** dizide bölüm bitmeye yaklaşınca (son ~20 saniye) köşede "Sonraki bölüm: S1 B4 · 10 sn" kartı çıkar; süre dolunca sonraki bölüm kendiliğinden başlar. Kartta "Şimdi oynat" ve "İptal" var; iptal edersen o bölümde kart bir daha çıkmaz. Filmlerde ve dizinin son bölümünde kart çıkmaz.
- Sonraki bölümün kaynağı kendiliğinden seçilir: önce izlediğin eklenti ve sunucuyla aynısı denenir, açılmazsa sıradaki kaynağa geçilir. Hiçbiri açılmazsa kart "Başka kaynak seç" der, ekran siyah kalmaz. Bölüm indirilmişse indirilmiş kopya oynar.
- Biten bölüm izlendi olarak kaydedilir; altyazı dili ve görünümü sonraki bölümde de geçerlidir (Türkçe otomatik aranır). Torrent kaynağında önceki torrent oturumu kapatılır.
- **Ayarlar > Oynatıcı:** "Sonraki bölümü otomatik oynat" anahtarı (varsayılan açık) ve geri sayım süresi (5 / 10 / 15 sn).
- Kumanda (TV): kart odaklanır; Tamam "Şimdi oynat", Geri "İptal".
- **Güncelleme indirmesi arka planda:** indirme artık bildirimde ilerlemeyle (İptal düğmeli) sürer, uygulamadan çıksan da kesilmez; bitince bildirime dokununca kurulum ekranı açılır. Ağ kesilirse ya da uygulama kapanırsa kaldığı yerden devam eder (sunucu desteklemezse baştan başlar); SHA-256 doğrulaması aynen sürer. İndirilemezse "Tekrar dene" var.
- Android 13+'ta bildirim izni istenir; reddedersen indirme uygulama açıkken yine çalışır.

### English
- **The next episode plays automatically:** near the end of an episode (last ~20 seconds) a "Next episode: S1 E4 · 10 s" card appears in the corner; when the countdown ends the next episode starts by itself. The card has "Play now" and "Cancel"; cancel it and it won't come back for that episode. No card for movies or a show's last episode.
- The next episode's source is picked for you: the addon and server you were watching are tried first, then the next source in line. If none opens, the card says "Pick another source" — the screen never stays black. If the episode is downloaded, the downloaded copy plays.
- The finished episode is marked as watched; your subtitle language and appearance carry over (Turkish is searched automatically). With a torrent source the previous torrent session is closed.
- **Settings > Player:** an "Autoplay next episode" switch (on by default) and the countdown length (5 / 10 / 15 s).
- Remote (TV): the card takes focus; OK is "Play now", Back is "Cancel".
- **Update downloads in the background:** the download now runs with a progress notification (with a Cancel button) and keeps going when you leave the app; tap the notification when it's done to open the installer. If the connection drops or the app closes it resumes where it stopped (or starts over if the server can't resume); SHA-256 verification is unchanged. If it fails there's a "Try again" button.
- On Android 13+ the notification permission is requested; if you decline, the download still works while the app is open.

## [1.0.3] — 2026-10-14

### Türkçe
- **Uygulama içi güncelleme:** yeni sürüm çıkınca "Güncelle" APK'yı uygulama içinde indirir (ilerleme çubuğuyla), cihazın işlemci türüne uyan dosyayı kendisi seçer (arm64-v8a, armeabi-v7a, x86_64), release'teki SHA-256 dosyasıyla doğrular ve sistem kurulum ekranını açar. Artık hangi APK'yı indireceğini seçmen gerekmez.
- "Bilinmeyen uygulamaları yükle" izni yoksa ilgili ayar sayfasına yönlendirir; geri dönünce kurulum kaldığı yerden sürer.
- İndirme hatasında, ağ kesilmesinde ve özet uyuşmazlığında uygulama çökmez; dosya kurulmaz ve "Tarayıcıda aç" ile release sayfasına gidilebilir. Uygun APK bulunamazsa eski yöntem (tarayıcı) sürer.

### English
- **In-app updates:** when a new version is out, "Update" downloads the APK inside the app (with a progress bar), picks the file that matches your device's CPU type (arm64-v8a, armeabi-v7a, x86_64), verifies it against the release's SHA-256 file and opens the system installer. You no longer have to pick an APK.
- If the "Install unknown apps" permission is off, it sends you to the right settings page and carries on when you come back.
- A failed download, a dropped connection or a checksum mismatch never crashes the app; the file isn't installed and "Open in browser" takes you to the release page. If no matching APK is found, the old browser method is used.

## [1.0.2] — 2026-10-14

### Türkçe
- **Yerleşik torrent/P2P motoru:** infoHash veya magnet döndüren addon ve Nuvio eklentilerinin kaynakları artık ek kurulum olmadan uygulama içinde oynatılıyor (libtorrent4j; yalnızca izlenen dosya indirilir, oynatıcı kapanınca geçici dosyalar silinir). İsteyen kendi TorrServer uyumlu sunucusunu Eklentiler ekranından girebilir.
- **Düzeltme:** Patron (PatronMulti) gibi eklentilerin bazı kaynaklarında, eklentinin gönderdiği başlıkları reddeden sunucular yüzünden çıkan "Bu kaynak şu an kullanılamıyor" hatası giderildi; reddedilen istek başlıksız yeniden denenir.
- **Çevrimiçi altyazı arama:** altyazı menüsünde "Çevrimiçi altyazılar" bölümü; Türkçe öncelikli (Ayarlar > Oynatıcı > Tercih edilen altyazı dili: Türkçe / İngilizce), ardından İngilizce. Altyazı addon'u kurmak gerekmez.
- **Dosyadan altyazı ekleme:** altyazı menüsünden cihazdaki .srt / .vtt / .ass dosyası seçilip hemen açılır; menüde ses parçası seçimi de var.
- **Oynatıcıda jestler (dokunmatik):** ekranın sağ yarısında dikey kaydırma ses, sol yarısında parlaklık (yalnızca oynatıcı penceresi; sistem ayarı değişmez, çıkışta eski haline döner). Kontrol çubukları ve TV/kumanda etkilenmez.
- Torrent kaynaklarında çevrimdışı indirme simgesi yoktur; önerilen kaynakta doğrudan bağlantı torrentten önce gelir.
- APK'lar işlemci türüne göre ayrı yayımlanır (arm64-v8a, armeabi-v7a, x86_64).

### English
- **Built-in torrent/P2P engine:** sources from addons and Nuvio plugins that return an infoHash or magnet link now play inside the app with no extra setup (libtorrent4j; only the watched file is downloaded, temporary files are deleted when the player closes). Users who run their own TorrServer-compatible server can enter it on the Addons screen.
- **Fix:** "This source isn't available right now" on some sources of plugins such as Patron (PatronMulti), caused by servers rejecting the headers the plugin sends; the rejected request is retried without them.
- **Online subtitle search:** an "Online subtitles" section in the subtitle menu, Turkish first (Settings > Player > Preferred subtitle language: Turkish / English), then English. No subtitle addon needed.
- **Add subtitles from a file:** pick a .srt / .vtt / .ass file from the device in the subtitle menu and it opens right away; the menu also lets you choose the audio track.
- **Player gestures (touch):** vertical swipe on the right half of the screen changes volume, on the left half brightness (player window only; the system setting is untouched and restored on exit). Control bars and TV/remote use are unaffected.
- Torrent sources have no offline download button; direct links are recommended before torrents.
- APKs are published per CPU type (arm64-v8a, armeabi-v7a, x86_64).

## [1.0.0] — 2026-10-13

İlk sürüm / First release.

### Türkçe
- **Ana sayfa ve arama:** TMDB'den beslenen kapak karuseli, "devam et" sırası, trend/popüler sıraları ve "tümü" ekranları; film ve dizi arama.
- **Detay:** arka plan görseli, başlık logosu, puan ve yaş sınırı, oyuncular, sezon/bölüm seçici; Türkçe özet boşsa İngilizce yedek.
- **Kaynak seçimi:** eklentilerden paralel kaynak toplama, eklenti süzgeci, en uygun kaynak seçili; satırda eklenti adı, kalite, boyut, ses dili ve altyazı bilgisi.
- **Oynatıcı:** Media3 ile tam ekran oynatma (düz dosya, HLS, DASH), ileri-geri, hız, kaldığın yerden devam.
- **Kütüphane:** favoriler, izleme geçmişi ve indirilenler.
- **Ayarlar:** eklentiler, oynatıcı, tema, dil (Sistem / English / Türkçe), hakkında. Arayüz Türkçe ve İngilizce.
- **Web sitesi ekleme:** reklam ve açılır pencere engelli sade web görünümü.
- **TV, tablet ve kumanda desteği:** Android TV girişi ve afişi, kumanda odak halkası, geniş ekranda yan menü ve iki sütunlu Detay.
- **Nuvio eklenti sistemi:** manifest URL'li addon'lar ve JS kazıyıcı depoları; eklenti türünü adresin içeriğinden anlama, depo güncelleme denetimi, eklenti başına anahtar ve ayarlar.
- **QuickJS çalıştırıcı:** kazıyıcılar yalıtılmış QuickJS ortamında çalışır; ağ yalnızca genel adreslere, süre/bellek/istek sınırlı, dosya sistemi erişimi yok.
- **Altyazı:** altyazı addon'larında arama, tek birleşik seçici (dile göre gruplu), otomatik varsayılan dil, karakter kodlaması tespiti (windows-1254 dahil), elle senkron (−0,5 / −0,1 / +0,1 / +0,5 sn) ve görünüm ayarları.
- **Çevrimdışı indirme:** kaynak listesinden film/bölüm indirme, ilerleme bildirimi, "Sadece Wi-Fi'de indir", Kütüphane > İndirilenler, altyazılarıyla çevrimdışı oynatma ve aynı geçmişe yazma.
- Uygulama hiçbir içerik, addon veya eklenti içermez; hepsi kullanıcının eklediği adreslerden gelir.

### English
- **Home and search:** TMDB-powered cover carousel, "continue watching" row, trending/popular rows with "see all" screens; movie and series search.
- **Details:** backdrop, title logo, rating and age badge, cast, season/episode picker; English fallback when the Turkish overview is empty.
- **Source picker:** parallel source gathering from addons, addon filter, best source preselected; each row shows addon name, quality, size, audio language and subtitle info.
- **Player:** full-screen Media3 playback (progressive, HLS, DASH), seek, speed, resume where you left off.
- **Library:** favorites, watch history and downloads.
- **Settings:** addons, player, theme, language (System / English / Türkçe), about. UI in English and Turkish.
- **Add a website:** a simple web view with ad and pop-up blocking.
- **TV, tablet and remote support:** Android TV launcher entry and banner, remote focus ring, side navigation and two-column details on wide screens.
- **Nuvio addon system:** manifest-URL addons and JS scraper repositories; the type is detected from the URL's content, repository update checks, per-plugin toggles and settings.
- **QuickJS runtime:** scrapers run in a sandboxed QuickJS environment; network limited to public addresses, time/memory/request limits, no file-system access.
- **Subtitles:** subtitle addon search, a single merged picker (grouped by language), automatic default language, character-encoding detection (including windows-1254), manual sync (−0.5 / −0.1 / +0.1 / +0.5 s) and appearance settings.
- **Offline downloads:** download movies/episodes from the source list, progress notification, "Download on Wi-Fi only", Library > Downloads, offline playback with subtitles, writing to the same watch history.
- The app ships with no content, addons or plugins; everything comes from URLs you add.
