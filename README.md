# Play

Sade, özgün bir Android film/dizi istemcisi (Kotlin, Jetpack Compose, Material 3). Uygulama hiçbir içerik veya addon içermez; addon'lar kullanıcının girdiği manifest URL'lerinden gelir. Ayrıntılı kapsam ve kurallar: [CLAUDE.md](CLAUDE.md).

## Derleme ve çalıştırma
```powershell
.\gradlew assembleDebug
adb connect 127.0.0.1:5555        # LDPlayer
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.kaan.play/.MainActivity
```
`local.properties` içinde şunlar olmalı (dosya git'e girmez):
```
sdk.dir=C\:\\Users\\<kullanıcı>\\AppData\\Local\\Android\\Sdk
TMDB_API_KEY=<tmdb v3 anahtarı>
```

## Tasarım notları (belirsiz kararlarda seçilen en sade yol)
- **AGP 9 + yerleşik Kotlin:** `kotlin-android` eklentisi kullanılmaz; AGP 9 Kotlin'i kendisi derler.
- **Düz metin HTTP:** manifest URL'lerini kullanıcı girer ve yerel ağdaki `http://` addon'lar yaygındır; bu yüzden `usesCleartextTraffic=true`. Bağlantı ile uygulamayı açma (deep link) yalnızca `https` için olacak.
- **Alt menü ikonları:** `material-icons-extended` yerine kendi çizdiğimiz birkaç vektör ikon (`ui/nav/PlayIcons.kt`); hem özgün hem küçük.
- **Diller:** İngilizce (varsayılan) ve Türkçe. Dil seçimi AppCompat per-app language'e devredildi (API 33+ sistem saklar, altında AppCompat'in metadata servisi); kendi DataStore kopyamız yok. APK'ya yalnızca `en` ve `tr` paketlenir (`localeFilters`). İngilizce arayüzde "addon" yazıyoruz.
- **Yedekleme kapalı** (`allowBackup=false`): izleme geçmişi ve addon listesi buluta gitmez.
- **TMDB:** v3 API anahtarı `api_key` sorgu parametresiyle gider (v3'te başka yol yok) ve istemci tarafında APK'ya girer; kişisel kullanım için kabul edildi. Sezon 0 ("Özel bölümler") listelenmez. Listelerde dil yedeği yok, yalnızca detay ve bölüm özetlerinde var. Sayfalama modeli (`Page`) hazır; Ana sayfa satırları ilk sayfayı kullanır.
- **Addon ekleme:** girilen metin kanonik manifest adresine çevrilir (şema yoksa `https://`, yol `manifest.json` ile bitmiyorsa eklenir, `#` atılır). `?` parametreli adresler reddedilir, çünkü akış adresleri taban yola eklenerek kurulur. Manifest en çok 2 MB, çağrı başına 15 sn sınırlıdır. Zorunlu alanlar: `id`, `name`, `types`, `resources`; ayrıca film/dizi için bir `stream` kaynağı. Saklanan manifest, kendi modelimizle yeniden kodlanmış hâlidir (sunucudan gelen ham metin değil).
- **Kaynak/tür/önek kuralı:** `AddonManifest.supportsStream` kaynak düzeyindeki `types`/`idPrefixes` değerini manifest düzeyindekine tercih eder; önek yoksa her kimlik kabul edilir.
- **https bağlantıları:** `*/manifest.json` ile biten https bağlantıları Play'e yönlendirilebilir, ancak Android doğrulanmamış web bağlantılarında varsayılanı tarayıcıya bırakır; kullanıcı "Birlikte aç" listesinden Play'i seçmelidir. Bağlantı geldiğinde "eklenti ekle" penceresi adres dolu açılır.
- **Faz 2/3 satırları:** `addons` tablosunda `NUVIO_ADDON` dışındaki türler Faz 1 listesine ve kaynak toplamaya girmez (depo kayıtlarının manifest biçimi farklı olacak).
