## Play 1.0.2

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

### Hangi APK? / Which APK?
- **arm64-v8a:** çoğu güncel telefon ve tablet / most current phones and tablets
- **armeabi-v7a:** eski 32 bit cihazlar / older 32-bit devices
- **x86_64:** emülatörler ve x86 cihazlar / emulators and x86 devices

Doğrulama / Verify: `Play-1.0.2-SHA256.txt` (SHA-256). 1.0.1'in üzerine güncellenir; veriler korunur / installs over 1.0.1, your data is kept.
