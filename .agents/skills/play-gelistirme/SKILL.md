---
name: play-gelistirme
description: Play Android uygulamasinda (com.kaan.play) her kod degisikligi, hata duzeltme, yeni ozellik, CloudStream/Nuvio eklenti sorunu ve "test et" istegi icin calisma kurallari. Dal/commit kurallari, derleme ve birim test, LDPlayer'da ozellik ozellik gercek cihaz testi (ekran goruntusuyle), CloudStream oynatma tanisi, APK'yi masaustune koyma ve rapor bicimi. Play projesinde kod yazmadan ya da test etmeden once bunu kullan.
---

# Play gelistirme ve test kurallari

Proje kurallarinin asil kaynagi kok dizindeki `CLAUDE.md`'dir. Ise baslamadan onu oku; bu skill onun yerine gecmez, her iste tekrar eden calisma bicimini tanimlar. Celiskide `CLAUDE.md` ve kullanicinin o anki istegi gecerlidir.

Kullanici Turkce konusur; raporlari Turkce, kisa ve sade yaz. Kullaniciya soru sorma: en makul secimi yap, kararini rapora ve gerekiyorsa README'ye yaz. Yalnizca geri alinamaz bir karar (veri silme, yayin, main'e birlestirme) varsa dur ve sor.

## 1. Kesin yasaklar

- `git push --force` / `--force-with-lease` YOK.
- Dosya silen kabuk komutu YOK (`rm`, `del`, `Remove-Item`, `rmdir`). Takip edilen dosyayi kaldirmak gerekiyorsa `git rm` kullan.
- `git reset --hard`, `git checkout .`, `git clean` YOK.
- `main`'e birlestirme (merge/rebase/push) YOK. Kullanici acikca isterse yapilir.
- Release, tag, GitHub yayini YOK. Kullanici acikca isterse yapilir.
- `local.properties`, keystore, sifreler, API anahtarlari (TMDB, SubDL) commit edilmez, loga ve rapora yazilmaz.
- `adb shell pm clear com.kaan.play` ve uygulamayi kaldirma YOK (kullanicinin eklentileri ve gecmisi silinir). Gerekirse sor.
- Ekran goruntuleri (`ekran_*.png`), dex dokumleri, `build/` ciktilari commit edilmez.

## 2. Dal ve commit

1. Calisilacak dal kullanici ne dediyse odur; demediyse su anki dalda kal ve basta `git status`, `git log --oneline -3` ile durumu raporla.
2. Baslamadan `git pull` (ayni dal). Push reddedilirse `git pull --no-rebase` yap, catismayi coz, tekrar push et.
3. Kucuk, anlamli commit'ler. Mesaj bicimi: `tur(alan): kisa aciklama` (ornek `fix(cloudstream): ...`, `feat(player): ...`).
4. Is bitince dali push et.

## 3. Kod kurallari (CLAUDE.md'den, en sik unutulanlar)

- Arayuzdeki her metin string resource; `values/strings.xml` (EN) ve `values-tr/strings.xml` (TR) **ikisine birden** eklenir. `StringResourcesTest` gecmeli.
- Ayarlar'a yeni ana kalem eklenmez (en cok 5 kalem). Yeni ayar ilgili alt ekrana girer.
- Renkler ve sekiller yalniz ortak temadan (`ui/theme`, `MaterialTheme.colorScheme` / `shapes` / `typography`) ve `ui/components`'ten gelir; ekran icine `Color(0x...)`, sabit kose ya da yazi boyutu gomulmez. Guncel renk kimligi CLAUDE.md "Gorunum kimligi" bolumundedir (2026-10-19: elektrik camgobegi vurgu); yeni vurgu rengi eklenmez.
- Her tiklanabilir oge D-pad ile odaklanabilir (`ui/components/PlayButtons.kt`, `Modifier.focusRing`, `rememberFocusMemory`). Yalniz dokunmaya bagli islem olmaz.
- Agir isler `Dispatchers.IO`; ana ekran donmaz. Hata yutulmaz ama uygulama cokmez, kullaniciya kisa mesaj gosterilir, siyah/bos ekran birakilmaz.
- Guvenlik: `PlayNetworkGuard` eklenti agini denetler (yerel/ozel adres engeli). Bu engeli genisletme; bir eklenti gerektiriyorsa yalnizca o ihtiyac icin dar bir istisna yaz ve testle.
- CloudStream: Play GPL-3.0'dir ve CloudStream'in gercek kutuphanesini kullanir (`libs.cloudstream.library`, `libs.nicehttp`). Nuvio kodu kopyalanmaz kurali surer.
- Room semasi degisirse goc (migration) yaz ve test et; kullanici verisi kaybolmaz.

## 4. Her isin sonunda dogrulama sirasi

Hepsi gecmeden "bitti" deme:

1. `.\gradlew testDebugUnitTest` - hepsi gecmeli. Yeni mantik icin birim test ekle.
2. `.\gradlew assembleDebug` - derleme hatasi kalmamali.
3. Kod R8/proguard, CloudStream ya da kutuphane tarafini etkilediyse ayrica `.\gradlew assembleRelease`.
4. LDPlayer'da gercek test (bolum 5: varsayilan HIZLI, gerektiginde TAM). Kullanici "test et" dediyse bu adim zorunludur.

## 5. LDPlayer'da test (hizli / tam)

Kullanici gercek cihaz testini onemser ama gereksiz uzun surmesini istemez. Yalnizca loga ya da birim teste bakip "calisiyor" denmez; test suresi isin buyuklugune gore secilir.

### Zaman asimi ve takilma kurali (her zaman)
- Her komut ve ag istegi zaman asimiyla calisir: `curl -m 20`, `Invoke-WebRequest -TimeoutSec 20`, adb icin PowerShell'de `Start-Process ... -Wait` yerine `$p = Start-Process -PassThru ...; if (-not $p.WaitForExit(180000)) { $p.Kill() }` gibi ust sinir.
- Herhangi bir adim **3 dakikayi gecerse** bekleme: durdur, `adb kill-server`, `adb start-server`, `adb connect 127.0.0.1:5555`, `adb devices` ile cihazi dogrula ve adimi **bir kez** tekrar dene. Yine olmazsa o adimi atla, rapora "takildi: <adim>, neden" yaz ve isin geri kalanina devam et.
- `adb devices` "offline" ya da bos ise kullaniciya LDPlayer'i yeniden baslatmasini soyle ve bekle (bu tek istisna).
- Uzun suren derlemeleri (gradle) arka planda baslatip bitmesini bekle; derleme 15 dakikayi gecerse Gradle daemon'unu durdur (`.\gradlew --stop`) ve bir kez yeniden dene.

### Kurulum
```
adb connect 127.0.0.1:5555
.\gradlew installDebug
adb shell am force-stop com.kaan.play
adb shell am start -n com.kaan.play/.MainActivity
```
Komut sayisini azaltmak icin adimlari `tools/devtest.ps1` icinde topla.

### HIZLI TEST (varsayilan)
Normal islerde (mantik/veri degisikligi, hata duzeltme, tek ekranda kucuk degisiklik):
- Yalniz **bu iste degisen** ekranlari/akislari ac, her biri icin bir ekran goruntusu al (`adb exec-out screencap -p > ekran_N.png`) ve goruntuyu **kendin incele** (bos/siyah ekran, tasma, beklenen veri).
- Istenen ozelligi bir kez bastan sona calistir.
- `adb logcat -d -s AndroidRuntime:E` bos olmali.
- Hedef sure: 10 dakikanin altinda.

### TAM TEST (yalniz su durumlarda)
(a) Arayuz metni, duzen, tema ya da yeni ekran degistiyse, (b) kullanici "tam test et" dediyse, (c) surum/yayin oncesi. Hizli teste ek olarak:
- Kumanda: degisen ekranlarda `adb shell input keyevent KEYCODE_DPAD_UP|DOWN|LEFT|RIGHT|CENTER|BACK` ile odak, odak halkasi, Geri.
- Dil ve yazi: Ingilizce (`adb shell cmd locale set-app-locales com.kaan.play --locales en`) ve buyuk yazi (`adb shell settings put system font_scale 1.3`); sonunda `--locales tr` ve `font_scale 1.0`'a geri al.
- Kalicilik gerekiyorsa `force-stop` + yeniden ac.
- Tema degisikliginde yalniz degisen ekranlari koyu ve acik temada ac; butun uygulamayi bastan dolasma.

### Ortak
- Dokunma: `adb shell input tap X Y`; yazi: `adb shell input text "..."`. Koordinatlari ekran goruntusunden bul.
- Ilgili log etiketleri: `PlayCs` (CloudStream), `PlayCatalog`, `PlaySubs`, `PlayTorrent`.
- Hata bulursan duzelt, yeniden kur, yalniz o ozelligi tekrar test et.
- Hazir kisayollar: `--es select_provider cs:<id>` (ana sayfada eklenti secer), `--ez play_diag true` (CloudStream oynatma tanisi).
- Raporda hangi test modunu (HIZLI/TAM) uyguladigini yaz.

## 6. CloudStream eklenti sorunlari

"Eklenti acilmiyor / katalog bos / video oynamiyor" turu islerde once kanit topla, tahmin yurutme:

1. `python tools/run_diag.py [eklenti-adi]` ile oynatma tanisini calistir (tum acik CloudStream eklentileri: katalog -> load -> bolum -> loadLinks -> medya kontrolu). Sonuc `docs/oynatma-tanisi.md`'ye yazilir.
2. Sorunlu eklentinin hangi adimda kaldigini ve logdaki gercek hatayi (`adb logcat -d -s PlayCs:*`) rapora aynen yaz.
3. Duzeltmeden sonra taniyi tekrar calistir, once/sonra tablosunu rapora koy.
4. Site kapali, Cloudflare Turnstile, ISP engeli gibi bizden bagimsiz nedenler duzeltilmez; kanitiyla raporlanir.
5. Cloudflare: `CloudflareSolver` + `CloudflareVerifyActivity` ("Dogrula"). Eski cerez kurali CLAUDE.md'de; cerez/UA loglanmaz.

## 7. APK'yi kullaniciya ver

Kullanici telefonunda deneyecekse (ya da is bir test surumuyle bittiyse) debug APK'lari masaustune kopyala:
```
$d=[Environment]::GetFolderPath("Desktop"); Get-ChildItem app\build\outputs\apk\debug -Filter *.apk | Copy-Item -Destination $d -Force
```
Kullanicinin karari (2026-10-19): Play TEK APK'dir; telefon, tablet ve TV kutusu ayni dosyayi kullanir. Tek APK'ya gecis yapildiysa masaustune yalnizca o tek dosyayi koy. Gecis henuz yapilmadiysa (splits.abi hala aciksa) yalnizca `arm64-v8a` APK'sini kopyala ve raporda bunu belirt; `x86_64` sadece LDPlayer icindir, kullaniciya verilmez.

## 8. Belgeler

Davranis degistiyse ayni is icinde guncelle:
- `CLAUDE.md`: baglayici kural degistiyse (tarih + kullanicinin karari).
- `README.md`: ayrinti, test sonucu, bilinen sinirlar.
- `CHANGELOG.md`: "Yayinlanmamis" altina kullanicinin anlayacagi dille TR (ve EN) madde.

## 9. Rapor bicimi

Is bitince kisa ve su sirayla:
1. **Ne yapildi** - 3-6 madde, sade dil.
2. **Test sonucu** - test edilen her ozellik icin `GECTI` / `KALDI` + ne gordugun + ekran goruntusu dosya adi. CloudStream isinde tani tablosu AYNEN.
3. **Bilinen sinirlar / calismayanlar** - kanitli nedenleriyle.
4. **Dal ve son commit** - ornek `cloudstream-kutuphane @ abc1234`, push edildi mi.
5. **APK** - masaustundeki dosya adi (verildiyse).

Bir adim yapilamadiysa (cihaz bagli degil, derleme kirik) "bitti" deme; nerede durdugunu ve nedenini yaz.
