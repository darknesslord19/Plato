#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Play Otomatik Duman Testi (Smoke Test)

Kullanım:
    python tools/smoke_test.py
    python tools/smoke_test.py --install
    python tools/smoke_test.py --filter AnimeciX
    python tools/smoke_test.py --timeout 900

Gereksinimler (2026-10-21 Kullanıcı Kararı):
1. Tek komutla insan müdahalesi olmadan en çok 15 dakikada uygulamanın ana yollarını dener.
2. adb connect 127.0.0.1:5555 (takılırsa kill-server/start-server, bir kez).
3. İsteğe bağlı --install ile gradlew installDebug.
4. Cihazdaki sonuç JSON'unu (smoke_test.json) çeker, docs/smoke-test.md'ye yazar.
5. docs/smoke-test.prev.json ile karşılaştırır; ÖNCE OK olup ŞİMDİ X olanlar en üstte
   "GERİLEMELER" başlığıyla listelenir. Gerileme varsa çıkış kodu 1, yoksa 0.
6. Hiçbir URL, çerez, anahtar, token dosyaya veya loga yazılmaz.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import time

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOCS_DIR = os.path.join(REPO_ROOT, "docs")
DEFAULT_MD_OUT = os.path.join(DOCS_DIR, "smoke-test.md")
DEFAULT_PREV_JSON = os.path.join(DOCS_DIR, "smoke-test.prev.json")

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if sys.stderr.encoding != 'utf-8':
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')


def find_adb(custom_path=None):
    if custom_path and os.path.isfile(custom_path):
        return custom_path
    candidates = [
        r"C:\Users\okay\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        r"C:\LDPlayer\LDPlayer9\adb.exe",
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    which = shutil.which("adb")
    if which:
        return which
    return candidates[0]


def connect_adb(adb, target="127.0.0.1:5555"):
    print(f"ADB baglantisi kontrol ediliyor ({target})...")
    subprocess.run([adb, "connect", target], capture_output=True, text=True, timeout=15)
    
    def check_connected():
        res = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=10)
        lines = [line.strip() for line in res.stdout.strip().splitlines()[1:] if line.strip()]
        for l in lines:
            if target in l and "device" in l and "offline" not in l:
                return True
        return False

    if check_connected():
        print(f"Cihaz bagli: {target}")
        return target

    print("Baglanti takildi ya da cihaz cevapsiz. ADB sunucusu yeniden baslatiliyor (1 kez)...")
    subprocess.run([adb, "kill-server"], capture_output=True, timeout=10)
    time.sleep(1)
    subprocess.run([adb, "start-server"], capture_output=True, timeout=10)
    time.sleep(2)
    subprocess.run([adb, "connect", target], capture_output=True, text=True, timeout=15)

    if check_connected():
        print(f"Cihaz bagli: {target}")
        return target

    raise RuntimeError(f"{target} cihazina baglanilamadi! Lutfen emulatorun (LDPlayer) acik oldugundan emin olun.")


def run_install_debug():
    print("Debug APK derlenip cihaza yukleniyor (gradlew.bat installDebug)...")
    gradlew = os.path.join(REPO_ROOT, "gradlew.bat")
    if not os.path.isfile(gradlew):
        gradlew = "gradlew"
    res = subprocess.run([gradlew, "installDebug"], cwd=REPO_ROOT)
    if res.returncode != 0:
        raise RuntimeError(f"installDebug basarisiz oldu! (kod {res.returncode})")
    print("Kurulum basarili.")


def format_duration(seconds):
    secs = int(round(seconds))
    mins = secs // 60
    rem = secs % 60
    if mins > 0:
        return f"{mins} dk {rem} sn"
    return f"{rem} sn"


def pull_device_json(adb, dev):
    # run-as ile smoke_test.json dosyasını çek
    res = subprocess.run(
        [adb, "-s", dev, "shell", "run-as", "com.kaan.play", "cat", "files/diag/smoke_test.json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace"
    )
    if res.returncode == 0 and res.stdout.strip():
        text = res.stdout.strip()
        # Bazen başında ekstra uyarılar çıkabilir, ilk '{' karakterini bul
        idx = text.find("{")
        if idx != -1:
            try:
                return json.loads(text[idx:])
            except Exception as e:
                print(f"JSON parse hatasi: {e}")
    return None


def detect_regressions(curr_data, prev_data):
    """
    Önceki çalıştırmada 'OK' olup bu çalıştırmada 'X' olanları listeler.
    """
    if not prev_data:
        return []

    regressions = []

    def make_map(data):
        m = {}
        for item in data.get("cloudstream", []):
            m[("CloudStream", item.get("name", ""))] = item
        for item in data.get("nuvio_addons", []):
            m[("Nuvio Addon", item.get("name", ""))] = item
        for item in data.get("nuvio_js", []):
            m[("Nuvio JS", f"{item.get('name', '')} - {item.get('target', '')}")] = item
        for item in data.get("subtitles", []):
            m[("Altyazı", item.get("name", ""))] = item
        for item in data.get("continue_watching", []):
            m[("Devam Et", item.get("name", ""))] = item
        for item in data.get("updates_and_announcements", []):
            m[("Güncelleme/Duyuru", item.get("name", ""))] = item
        return m

    prev_map = make_map(prev_data)
    curr_map = make_map(curr_data)

    for key, prev_item in prev_map.items():
        curr_item = curr_map.get(key)
        if not curr_item:
            continue
        prev_status = prev_item.get("status", "")
        curr_status = curr_item.get("status", "")
        if prev_status == "OK" and curr_status == "X":
            section, name = key
            regressions.append({
                "section": section,
                "name": name,
                "prev": prev_status,
                "curr": curr_status,
                "reason": curr_item.get("reason", "-"),
            })

    return regressions


def generate_markdown(curr_data, duration_str, regressions):
    timestamp = curr_data.get("timestamp", time.strftime("%Y-%m-%d %H:%M:%S"))
    app_version = curr_data.get("appVersion", "1.1.0")

    cs_list = curr_data.get("cloudstream", [])
    addon_list = curr_data.get("nuvio_addons", [])
    js_list = curr_data.get("nuvio_js", [])
    sub_list = curr_data.get("subtitles", [])
    cont_list = curr_data.get("continue_watching", [])
    upd_list = curr_data.get("updates_and_announcements", [])

    cs_ok = sum(1 for x in cs_list if x.get("status") == "OK")
    cs_total = len(cs_list)
    addon_ok = sum(1 for x in addon_list if x.get("status") == "OK")
    addon_total = len(addon_list)
    js_ok = sum(1 for x in js_list if x.get("status") == "OK")
    js_total = sum(1 for x in js_list if x.get("status") != "atlandı")
    sub_ok = sum(1 for x in sub_list if x.get("status") == "OK")
    sub_total = len(sub_list)

    lines = []
    lines.append("# Play Otomatik Duman Testi (Smoke Test)")
    lines.append("")
    lines.append(f"- **Tarih:** {timestamp}")
    lines.append(f"- **Uygulama Sürümü:** {app_version}")
    lines.append(f"- **Toplam Süre:** {duration_str}")
    lines.append("")

    if regressions:
        lines.append("## ⚠️ GERİLEMELER")
        lines.append("Daha önce çalışan ancak bu çalıştırmada başarısız olan kontroller:")
        lines.append("")
        lines.append("| Bölüm | Öğe / Hedef | Önceki Durum | Şimdiki Durum | Hata / Neden |")
        lines.append("| --- | --- | --- | --- | --- |")
        for r in regressions:
            lines.append(f"| {r['section']} | {r['name']} | {r['prev']} | {r['curr']} | {r['reason']} |")
        lines.append("")

    lines.append("## Özet")
    lines.append(f"- **CloudStream:** {cs_ok}/{cs_total} oynatılabilir")
    lines.append(f"- **Nuvio Addon:** {addon_ok}/{addon_total} oynatılabilir")
    lines.append(f"- **Nuvio JS Eklentileri:** {js_ok}/{js_total} akış/medya başarılı")
    lines.append(f"- **Altyazı Sağlayıcıları:** {sub_ok}/{sub_total} Türkçe altyazı başarılı")
    
    cont_status = cont_list[0].get("status", "-") if cont_list else "-"
    cont_details = cont_list[0].get("details", "-") if cont_list else "-"
    lines.append(f"- **Bölüm Devam:** {cont_status} ({cont_details})")
    
    upd_ok = sum(1 for x in upd_list if x.get("status") == "OK")
    upd_total = len(upd_list)
    lines.append(f"- **Güncelleme ve Duyuru:** {upd_ok}/{upd_total} başarılı")
    lines.append("")

    # 1. CloudStream Tablosu
    lines.append("## 1. CloudStream Eklentileri")
    lines.append("| Eklenti | Katalog | Load | Bölüm | Link | Medya | Altyazı | Durum | Hata/Neden |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in cs_list:
        lines.append(f"| {r.get('name','-')} | {r.get('catalog','-')} | {r.get('load','-')} | {r.get('episode','-')} | {r.get('link','-')} | {r.get('media','-')} | {r.get('subtitle','-')} | {r.get('status','-')} | {r.get('reason','-')} |")
    lines.append("")

    # 2. Nuvio Addon Tablosu
    lines.append("## 2. Nuvio Addon'ları")
    lines.append("| Addon | Tür | Akış | Medya | Altyazı | Durum | Hata/Neden |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- |")
    for r in addon_list:
        lines.append(f"| {r.get('name','-')} | {r.get('kind','Video Addon')} | {r.get('stream','-')} | {r.get('media','-')} | {r.get('subtitle','-')} | {r.get('status','-')} | {r.get('reason','-')} |")
    lines.append("")

    # 3. Nuvio JS Eklentileri Tablosu
    lines.append("## 3. Nuvio JS Eklentileri (Scrapers)")
    lines.append("| Eklenti | Hedef | Akış | Medya | Altyazı | Durum | Sınıf | Hata/Neden |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in js_list:
        lines.append(f"| {r.get('name','-')} | {r.get('target','-')} | {r.get('stream','-')} | {r.get('media','-')} | {r.get('subtitle','-')} | {r.get('status','-')} | {r.get('category','-')} | {r.get('reason','-')} |")
    lines.append("")

    # 4. Altyazı Sağlayıcıları Tablosu
    lines.append("## 4. Altyazı Sağlayıcıları")
    lines.append("| Sağlayıcı | Hedef | Türkçe Arama | İndirme & Ayrıştırma | Durum | Hata/Neden |")
    lines.append("| --- | --- | --- | --- | --- | --- |")
    for r in sub_list:
        lines.append(f"| {r.get('name','-')} | {r.get('target','-')} | {r.get('search','-')} | {r.get('parse','-')} | {r.get('status','-')} | {r.get('reason','-')} |")
    lines.append("")

    # 5. Devam Et Tablosu
    lines.append("## 5. Devam Et (CsNextEpisodeFinder)")
    lines.append("| Bileşen | Hedef | Sonuç | Durum | Hata/Neden |")
    lines.append("| --- | --- | --- | --- | --- |")
    for r in cont_list:
        lines.append(f"| {r.get('name','-')} | {r.get('target','-')} | {r.get('details','-')} | {r.get('status','-')} | {r.get('reason','-')} |")
    lines.append("")

    # 6. Güncelleme ve Duyuru Kontrolü Tablosu
    lines.append("## 6. Güncelleme ve Duyuru Kontrolü")
    lines.append("| Kaynak | Detay | Durum | Hata/Neden |")
    lines.append("| --- | --- | --- | --- |")
    for r in upd_list:
        lines.append(f"| {r.get('name','-')} | {r.get('details','-')} | {r.get('status','-')} | {r.get('reason','-')} |")
    lines.append("")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Play Tek Komutluk Otomatik Duman Testi (Smoke Test)")
    parser.add_argument("--install", action="store_true", help="Testten önce gradlew installDebug çalıştırır")
    parser.add_argument("--filter", type=str, default="", help="Yalnızca belirtilen eklentileri test eder (virgülle ayrılmış)")
    parser.add_argument("--timeout", type=int, default=900, help="Toplam zaman aşımı süresi (saniye, varsayılan 900 = 15 dk)")
    parser.add_argument("--adb", type=str, default="", help="Özel ADB yolu")
    parser.add_argument("--prev", type=str, default=DEFAULT_PREV_JSON, help="Önceki test JSON dosyası")
    parser.add_argument("--out-md", type=str, default=DEFAULT_MD_OUT, help="Çıktı Markdown dosyası yolu")
    args = parser.parse_args()

    adb = find_adb(args.adb)
    dev = connect_adb(adb)

    if args.install:
        run_install_debug()

    # Logcat ve uygulamayı sıfırla
    print("Uygulama durduruluyor ve logcat temizleniyor...")
    subprocess.run([adb, "-s", dev, "shell", "am", "force-stop", "com.kaan.play"], capture_output=True)
    subprocess.run([adb, "-s", dev, "logcat", "-c"], capture_output=True)

    print("Tanı başlatılıyor: am start com.kaan.play/.MainActivity --ez play_diag true ...")
    cmd = [
        adb, "-s", dev, "shell", "am", "start",
        "-n", "com.kaan.play/.MainActivity",
        "--ez", "play_diag", "true"
    ]
    if args.filter:
        cmd.extend(["--es", "diag_plugins", args.filter])
        print(f"Filtre uygulaniyor: {args.filter}")

    res = subprocess.run(cmd, capture_output=True, text=True)
    print(res.stdout.strip())

    print(f"Tanı sonuçları bekleniyor (en çok {args.timeout} saniye)...")
    log_proc = subprocess.Popen(
        [adb, "-s", dev, "logcat", "-v", "time", "-s", "PlayDiag:I", "PlayCs:D"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace"
    )

    start_time = time.time()
    completed = False
    try:
        while time.time() - start_time < args.timeout:
            line = log_proc.stdout.readline()
            if not line:
                time.sleep(0.3)
                continue
            line = line.strip()
            if line:
                print(line)
            if "SMOKE_TEST_COMPLETED" in line or "DIAGNOSTIC_COMPLETED" in line:
                completed = True
                break
    finally:
        log_proc.terminate()

    elapsed = time.time() - start_time
    duration_str = format_duration(elapsed)
    if not completed:
        print(f"\nUYARI: {args.timeout} saniye zaman aşımına ulaşıldı! O ana kadarki sonuçlar çekiliyor...")
    else:
        print(f"\nTanı tamamlandı! Toplam süre: {duration_str}")

    # Cihazdan JSON çek
    curr_data = pull_device_json(adb, dev)
    if not curr_data:
        print("HATA: Cihazdan smoke_test.json çekilemedi!")
        sys.exit(2)

    # Önceki çalıştırmayı oku
    prev_data = None
    if os.path.isfile(args.prev):
        try:
            with open(args.prev, "r", encoding="utf-8") as f:
                prev_data = json.load(f)
        except Exception as e:
            print(f"Önceki JSON okunamadı ({args.prev}): {e}")

    # Gerilemeleri belirle
    regressions = detect_regressions(curr_data, prev_data)

    # Markdown üret
    md_content = generate_markdown(curr_data, duration_str, regressions)

    # Markdown dosyasına yaz
    md_dir = os.path.dirname(os.path.abspath(args.out_md))
    if md_dir:
        os.makedirs(md_dir, exist_ok=True)
    with open(args.out_md, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"\nMarkdown raporu kaydedildi: {os.path.abspath(args.out_md)}")

    # Mevcut sonucu prev JSON olarak kaydet
    prev_dir = os.path.dirname(os.path.abspath(args.prev))
    if prev_dir:
        os.makedirs(prev_dir, exist_ok=True)
    with open(args.prev, "w", encoding="utf-8") as f:
        json.dump(curr_data, f, ensure_ascii=False, indent=2)
    print(f"Önceki çalıştırma JSON dosyası güncellendi: {os.path.abspath(args.prev)}")

    print("\n" + "=" * 60)
    print(md_content)
    print("=" * 60 + "\n")

    if regressions:
        print(f"DİKKAT: {len(regressions)} GERİLEME TESPİT EDİLDİ! Betik çıkış kodu 1 ile sonlanıyor.")
        sys.exit(1)
    else:
        print("BAŞARILI: Hiçbir gerileme tespit edilmedi. Çıkış kodu 0.")
        sys.exit(0)


if __name__ == "__main__":
    main()
