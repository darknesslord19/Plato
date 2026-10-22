#!/usr/bin/env bash
# Emülatörde gerçek CloudStream eklentilerini Play'in yükleyicisiyle çalıştırır ve raporu build/cs-device/ altına alır.
set -uo pipefail
repos=$(grep -vE '^\s*(#|$)' tools/cs_repos_device.list | tr '\n' ',' | sed 's/,$//')
mkdir -p build/cs-device
exec > >(tee -a build/cs-device/run.txt) 2>&1
adb devices
ls -l app/build/outputs/apk/debug/ app/build/outputs/apk/androidTest/debug/ || true
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true
adb shell am instrument -w -r \
  -e class com.kaan.play.data.cloudstream.CsRealPluginsDeviceTest \
  -e cs_repos "$repos" -e cs_limit "${CS_LIMIT:-10}" -e cs_query "${CS_QUERY:-a}" \
  com.kaan.play.test/androidx.test.runner.AndroidJUnitRunner | tee build/cs-device/instrument.txt
adb logcat -d -s PlayCsDevice:* PlayCs:* TestRunner:* AndroidRuntime:* > build/cs-device/logcat.txt 2>&1 || true
adb exec-out run-as com.kaan.play cat files/cs-device-report.json > build/cs-device/report.json || true
ls -l build/cs-device
