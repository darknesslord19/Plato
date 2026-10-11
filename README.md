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
- **Yedekleme kapalı** (`allowBackup=false`): izleme geçmişi ve addon listesi buluta gitmez.
