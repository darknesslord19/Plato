#!/usr/bin/env bash
# Android SDK'yı hazırlar: sunucuda kuruluysa onu kullanır, değilse komut satırı araçlarını indirir.
# Lisansları kabul eder; eksik platform/build-tools'u Gradle (AGP) kendisi indirir.
# Hata olursa nedenini ::error:: notu olarak yazar (kayıtlar giriş yapmadan da okunabilsin).
set -uo pipefail

fail() { echo "::error title=Android SDK::$1"; exit 1; }

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] || [ ! -d "$sdk" ]; then
  for c in /usr/local/lib/android/sdk "$HOME/Android/Sdk"; do
    [ -d "$c" ] && sdk="$c" && break
  done
fi

sdkmanager=""
if [ -n "$sdk" ]; then
  sdkmanager=$(ls -d "$sdk"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | sort -V | tail -1)
fi

if [ -z "$sdkmanager" ]; then
  echo "SDK bulunamadı; komut satırı araçları indiriliyor."
  sdk="$HOME/android-sdk"
  mkdir -p "$sdk/cmdline-tools"
  url="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  curl -fsSL -o /tmp/cmdline-tools.zip "$url" || fail "Komut satırı araçları indirilemedi ($url)."
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools || fail "Komut satırı araçları açılamadı."
  rm -rf "$sdk/cmdline-tools/latest"
  mv /tmp/cmdline-tools/cmdline-tools "$sdk/cmdline-tools/latest"
  sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
fi

echo "ANDROID_HOME=$sdk" >> "$GITHUB_ENV"
echo "ANDROID_SDK_ROOT=$sdk" >> "$GITHUB_ENV"
echo "SDK: $sdk"

yes | "$sdkmanager" --sdk_root="$sdk" --licenses > /tmp/licenses.log 2>&1
if ! grep -qiE "accepted|All SDK package licenses" /tmp/licenses.log; then
  tail -5 /tmp/licenses.log
  fail "SDK lisansları kabul edilemedi: $(tail -2 /tmp/licenses.log | tr '\n' ' ')"
fi

# Derleme araçları (apksigner) için en az bir build-tools sürümü olsun.
if ! ls "$sdk"/build-tools/*/apksigner >/dev/null 2>&1; then
  "$sdkmanager" --sdk_root="$sdk" "build-tools;36.0.0" "platform-tools" > /tmp/sdk-install.log 2>&1 \
    || fail "build-tools kurulamadı: $(tail -2 /tmp/sdk-install.log | tr '\n' ' ')"
fi
echo "Android SDK hazır."
