# tools\devtest.ps1 - LDPlayer otomatik sağlayıcı katalog test komut dosyası
param(
    [string[]]$Providers = @("cs:12", "cs:5", "cs:4", "cs:7", "cs:8", "cs:9", "cs:14", "cs:13", "cs:10", "nuvio_addon:1"),
    [int]$WaitSeconds = 12,
    [string]$Device = "127.0.0.1:5555"
)

$env:PATH += ";C:\Users\okay\AppData\Local\Android\Sdk\platform-tools"
Write-Host "Connecting to LDPlayer at $Device..."
adb connect $Device | Out-Null

$screenDir = Join-Path $PSScriptRoot "..\build\devtest_screens"
if (-not (Test-Path $screenDir)) {
    New-Item -ItemType Directory -Path $screenDir -Force | Out-Null
}

$providerNames = @{
    "cs:12" = "HDFilmCehennemi"
    "cs:5"  = "Dizilla"
    "cs:4"  = "DiziBox"
    "cs:7"  = "FilmMakinesi"
    "cs:8"  = "SetFilm"
    "cs:9"  = "FullHDFilmizlesene"
    "cs:14" = "SelcukFlix"
    "cs:13" = "YesilcamTV"
    "cs:10" = "Dizipal"
    "cs:6"  = "DiziYou"
    "cs:11" = "WebDramaTurkey"
    "nuvio_addon:1" = "Cinemeta"
}

$results = @()

foreach ($id in $Providers) {
    $name = if ($providerNames.ContainsKey($id)) { $providerNames[$id] } else { $id }
    Write-Host "`n=========================================="
    Write-Host "Test ediliyor: $name ($id)..."
    Write-Host "=========================================="

    # Temiz başlangıç: önceki işlemi durdur ve logcat temizle
    adb -s $Device shell am force-stop com.kaan.play | Out-Null
    Start-Sleep -Milliseconds 500
    adb -s $Device logcat -c

    # Sağlayıcıyı seç ve MainActivity'yi başlat
    adb -s $Device shell am start -n com.kaan.play/.MainActivity --es select_provider "$id" | Out-Null

    # Kataloğun yüklenmesini bekle
    Start-Sleep -Seconds $WaitSeconds

    # Ekran görüntüsü al
    $screenPathOnDevice = "/sdcard/screen_$name.png"
    $localScreenPath = Join-Path $screenDir "screen_$name.png"
    adb -s $Device shell screencap -p $screenPathOnDevice
    adb -s $Device pull $screenPathOnDevice $localScreenPath | Out-Null

    $fileSize = if (Test-Path $localScreenPath) { (Get-Item $localScreenPath).Length } else { 0 }

    # Logcat'ten sonuçları topla
    $logs = adb -s $Device logcat -d -s PlayCs PlayCatalog CloudflareSolver CloudflareKiller NiceHttp

    $successCount = 0
    $cfDetected = $false
    $cfBypassed = $false
    $cfBlocked = $false
    $errorReason = ""

    foreach ($line in $logs) {
        if ($line -match "başarılı,\s+(\d+)\s+öğe") {
            $successCount++
        }
        if ($line -match "Cloudflare challenge detected") {
            $cfDetected = $true
        }
        if ($line -match "cf_clearance başarıyla alındı") {
            $cfBypassed = $true
        }
        if ($line -match "Bot koruması aşılamadı" -or $line -match "wasBlocked" -or $line -match "zaman aşımına uğradı") {
            $cfBlocked = $true
        }
        if ($line -match "istisna -> (.+)") {
            if (-not $errorReason) { $errorReason = $matches[1] }
        }
    }

    $isRendered = $fileSize -gt 300000

    $status = if ($isRendered -or $successCount -gt 0) {
        if ($successCount -gt 0) { "BAŞARILI ($successCount raf dolu)" } else { "BAŞARILI (Katalog ekranda)" }
    } elseif ($cfBlocked -or ($cfDetected -and -not $cfBypassed)) {
        "ENGEL (Cloudflare / Turnstile)"
    } elseif ($errorReason) {
        "HATA: $errorReason"
    } else {
        "BOŞ"
    }

    Write-Host "Sonuç: $status (Ekran boyutu: $fileSize byte)"
    if ($cfDetected) {
        Write-Host "Cloudflare: Tespit=$cfDetected, Aşıldı=$cfBypassed, Engellendi=$cfBlocked"
    }

    $results += [PSCustomObject]@{
        Id         = $id
        Name       = $name
        Status     = $status
        RowsLoaded = $successCount
        Cloudflare = if ($cfDetected) { if ($cfBypassed) { "Aşıldı" } else { "Aşılamadı (Turnstile/Bot)" } } else { "Yok" }
        Notes      = if ($isRendered) { "Katalog başarıyla listelendi ve afişler yüklendi" } elseif ($cfBlocked) { "Turnstile interaktif bot koruması" } elseif ($errorReason) { $errorReason } else { "Katalog boş döndü" }
        ScreenSize = "$([math]::Round($fileSize / 1024)) KB"
    }
}

Write-Host "`n`n=========================================="
Write-Host "GENEL SAĞLAYICI TEST RAPORU"
Write-Host "=========================================="
$results | Format-Table -AutoSize -Property Name, Status, Cloudflare, ScreenSize, Notes
