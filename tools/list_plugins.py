import subprocess

adb = r"C:\Users\okay\AppData\Local\Android\Sdk\platform-tools\adb.exe"
proc = subprocess.Popen([adb, "-s", "127.0.0.1:5555", "shell", "run-as", "com.kaan.play", "sqlite3", "databases/play.db"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
sql = "SELECT p.id, p.pluginId, p.name, p.enabled FROM plugins p JOIN addons a ON p.repoId = a.id WHERE a.type = 'CLOUDSTREAM_REPO' ORDER BY p.id;\n.exit\n"
out, err = proc.communicate(sql.encode('utf-8'))
print(out.decode('utf-8', errors='replace'))
if err:
    print("ERR:", err.decode('utf-8', errors='replace'))
