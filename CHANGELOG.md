# Changelog / Değişiklik günlüğü

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
