## Play 1.0.3

### Türkçe
- **Uygulama içi güncelleme:** yeni sürüm çıkınca "Güncelle" APK'yı uygulama içinde indirir (ilerleme çubuğuyla), cihazın işlemci türüne uyan dosyayı kendisi seçer (arm64-v8a, armeabi-v7a, x86_64), release'teki SHA-256 dosyasıyla doğrular ve sistem kurulum ekranını açar. Artık hangi APK'yı indireceğini seçmen gerekmez.
- "Bilinmeyen uygulamaları yükle" izni yoksa ilgili ayar sayfasına yönlendirir; geri dönünce kurulum kaldığı yerden sürer.
- İndirme hatasında, ağ kesilmesinde ve özet uyuşmazlığında uygulama çökmez; dosya kurulmaz ve "Tarayıcıda aç" ile release sayfasına gidilebilir. Uygun APK bulunamazsa eski yöntem (tarayıcı) sürer.
- Not: bu özellik 1.0.3 ile geldiği için 1.0.2 ve öncesi bu sürüme **elle** güncellenir (aşağıdaki APK'lardan biri); bundan sonraki sürümler uygulama içinden kurulur.

### English
- **In-app updates:** when a new version is out, "Update" downloads the APK inside the app (with a progress bar), picks the file that matches your device's CPU type (arm64-v8a, armeabi-v7a, x86_64), verifies it against the release's SHA-256 file and opens the system installer. You no longer have to pick an APK.
- If the "Install unknown apps" permission is off, it sends you to the right settings page and carries on when you come back.
- A failed download, a dropped connection or a checksum mismatch never crashes the app; the file isn't installed and "Open in browser" takes you to the release page. If no matching APK is found, the old browser method is used.
- Note: this arrives in 1.0.3, so 1.0.2 and earlier have to be updated to this version **manually** (one of the APKs below); later versions install from inside the app.

### Hangi APK? / Which APK?
- **arm64-v8a:** çoğu güncel telefon ve tablet / most current phones and tablets
- **armeabi-v7a:** eski 32 bit cihazlar / older 32-bit devices
- **x86_64:** emülatörler ve x86 cihazlar / emulators and x86 devices

Doğrulama / Verify: `Play-1.0.3-SHA256.txt` (SHA-256). 1.0.2'nin üzerine güncellenir; veriler korunur / installs over 1.0.2, your data is kept.
