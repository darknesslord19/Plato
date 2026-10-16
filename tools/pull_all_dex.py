import os
import subprocess

adb = r"C:\Users\okay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
out_dir = r"build\dex_cache"
os.makedirs(out_dir, exist_ok=True)

# List files
proc = subprocess.run([adb, "-s", "127.0.0.1:5555", "shell", "run-as com.kaan.play ls files/cloudstream"], capture_output=True, text=True)
files = [f.strip() for f in proc.stdout.split() if f.strip().endswith('.dex')]

print(f"Found {len(files)} dex files")
for f in files:
    dest = os.path.join(out_dir, f)
    res = subprocess.run([adb, "-s", "127.0.0.1:5555", "exec-out", f"run-as com.kaan.play cat files/cloudstream/{f}"], capture_output=True)
    with open(dest, 'wb') as df:
        df.write(res.stdout)
    print(f"Pulled {f}: {len(res.stdout)} bytes, magic: {res.stdout[:4]}")

print("All dex files pulled successfully.")
