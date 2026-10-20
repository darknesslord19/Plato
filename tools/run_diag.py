import os
import subprocess
import sys
import time

ADB = r"C:\Users\okay\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if sys.stderr.encoding != 'utf-8':
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

def get_device():
    res = subprocess.run([ADB, "devices"], capture_output=True, text=True)
    lines = [line.strip() for line in res.stdout.strip().splitlines()[1:] if line.strip() and "offline" not in line]
    if not lines:
        raise RuntimeError("No ADB device connected!")
    dev = lines[0].split()[0]
    return dev

def run_adb(dev, args):
    cmd = [ADB, "-s", dev] + args
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

def main():
    dev = get_device()
    print(f"Connected ADB device: {dev}")

    # Clear logcat
    subprocess.run([ADB, "-s", dev, "logcat", "-c"])

    print("Launching Play with --ez play_diag true ...")
    cmd = [
        ADB, "-s", dev, "shell", "am", "start",
        "-n", "com.kaan.play/.MainActivity",
        "--ez", "play_diag", "true"
    ]
    if len(sys.argv) > 1:
        plugins_filter = sys.argv[1]
        cmd.extend(["--es", "diag_plugins", plugins_filter])
        print(f"Filtering plugins: {plugins_filter}")

    res = subprocess.run(cmd, capture_output=True, text=True)
    print(res.stdout.strip())

    print("Waiting for diagnostic to finish (monitoring logcat)...")
    log_proc = subprocess.Popen(
        [ADB, "-s", dev, "logcat", "-v", "time", "-s", "PlayDiag:I", "PlayCs:D"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace"
    )

    start_time = time.time()
    completed = False
    try:
        while time.time() - start_time < 300: # 5 minutes max
            line = log_proc.stdout.readline()
            if not line:
                time.sleep(0.5)
                continue
            line = line.strip()
            print(line)
            if "DIAGNOSTIC_COMPLETED" in line:
                completed = True
                break
    finally:
        log_proc.terminate()

    if not completed:
        print("WARNING: Diagnostic did not log DIAGNOSTIC_COMPLETED within timeout.")

    print("\n--- Pulling oynatma-tanisi.md ---")
    cat_res = run_adb(dev, ["shell", "run-as", "com.kaan.play", "cat", "files/diag/oynatma-tanisi.md"])
    if cat_res.returncode == 0 and cat_res.stdout.strip():
        diag_content = cat_res.stdout.strip()
        print("\n" + diag_content + "\n")
        
        # Save to local file as well
        local_out = os.path.join(os.path.dirname(__file__), "..", "docs", "oynatma-tanisi.md")
        with open(local_out, "w", encoding="utf-8") as f:
            f.write(diag_content)
        print(f"Saved locally to: {os.path.abspath(local_out)}")
    else:
        print("ERROR: Could not read files/diag/oynatma-tanisi.md")
        print(cat_res.stderr)

if __name__ == "__main__":
    main()
