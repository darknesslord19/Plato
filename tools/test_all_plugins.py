import subprocess
import time
import re
import os

adb = r"C:\Users\okay\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# Get all cloudstream plugins from DB
proc = subprocess.Popen([adb, "-s", "127.0.0.1:5555", "shell", "run-as", "com.kaan.play", "sqlite3", "databases/play.db"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
sql = "SELECT p.id, p.pluginId, p.name FROM plugins p JOIN addons a ON p.repoId = a.id WHERE a.type = 'CLOUDSTREAM_REPO' ORDER BY p.id;\n.exit\n"
out, _ = proc.communicate(sql.encode('utf-8'))
lines = [l.strip() for l in out.decode('utf-8', errors='replace').splitlines() if l.strip() and '|' in l]

plugins = []
for line in lines:
    parts = line.split('|')
    pid, ppluginId, pname = parts[0], parts[1], parts[2]
    plugins.append((pid, pname))

print(f"Total CloudStream plugins to test: {len(plugins)}")

results = []
os.makedirs(r"build\plugin_screens", exist_ok=True)

for pid, pname in plugins:
    provider_id = f"cs:{pid}"
    print(f"\n==========================================")
    print(f"Testing {pname} ({provider_id})...")
    print(f"==========================================")
    
    # Force stop & clear logcat
    subprocess.run([adb, "-s", "127.0.0.1:5555", "shell", "am force-stop com.kaan.play"], capture_output=True)
    time.sleep(0.4)
    subprocess.run([adb, "-s", "127.0.0.1:5555", "logcat", "-c"], capture_output=True)
    
    # Start app with select_provider
    subprocess.run([adb, "-s", "127.0.0.1:5555", "shell", f"am start -n com.kaan.play/.MainActivity --es select_provider {provider_id}"], capture_output=True)
    
    # Wait for catalog loading
    time.sleep(6)
    
    # Grab logcat
    log_proc = subprocess.run([adb, "-s", "127.0.0.1:5555", "logcat", "-d", "-s", "PlayCs", "PlayCatalog", "CloudflareSolver", "CloudflareKiller", "NiceHttp", "AndroidRuntime"], capture_output=True, text=True, encoding='utf-8', errors='replace')
    logs = log_proc.stdout.splitlines()
    
    success_sections = []
    error_reasons = []
    cf_detected = False
    cf_bypassed = False
    cf_blocked = False
    
    for l in logs:
        if "başarılı," in l:
            m = re.search(r"DEBUG_MAINPAGE\]\s*([^:]+):\s*başarılı,\s*(\d+)\s*öğe", l)
            if m:
                success_sections.append(f"{m.group(1)} ({m.group(2)})")
            elif "öğe" in l:
                success_sections.append("raf")
        if "istisna ->" in l:
            m = re.search(r"istisna -> (.+)", l)
            if m:
                error_reasons.append(m.group(1))
        if "Cloudflare challenge detected" in l:
            cf_detected = True
        if "cf_clearance başarıyla alındı" in l:
            cf_bypassed = True
        if "Bot koruması aşılamadı" in l or "wasBlocked" in l:
            cf_blocked = True
        if "AndroidRuntime: FATAL EXCEPTION" in l or "NoSuchMethodError" in l or "NoClassDefFoundError" in l:
            error_reasons.append(l)

    # Screencap
    screen_name = f"screen_{pid}_{pname}.png"
    screen_device = f"/sdcard/{screen_name}"
    local_screen = os.path.join(r"build\plugin_screens", screen_name)
    subprocess.run([adb, "-s", "127.0.0.1:5555", "shell", f"screencap -p {screen_device}"], capture_output=True)
    subprocess.run([adb, "-s", "127.0.0.1:5555", "pull", screen_device, local_screen], capture_output=True)
    size = os.path.getsize(local_screen) if os.path.exists(local_screen) else 0

    if success_sections:
        status = f"BAŞARILI ({len(success_sections)} raf: {', '.join(success_sections[:3])})"
    elif cf_blocked:
        status = "ENGEL (Cloudflare / Turnstile)"
    elif error_reasons:
        status = f"HATA: {error_reasons[0][:90]}"
    else:
        status = "BOŞ (Katalog Yok veya Boş Liste)"

    print(f"Status: {status} (Screen: {size} bytes)")
    results.append((pid, pname, status, size, error_reasons))

print("\n==========================================")
print("TEST SUMMARY:")
print("==========================================")
for pid, pname, status, size, err in results:
    print(f"[{pid}] {pname}: {status}")
