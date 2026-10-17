import os
import urllib.request
import zipfile
import subprocess
import glob
import sys

# Add tools to sys.path
sys.path.append(os.path.abspath('tools'))
import analyze_dex_methods as a

os.makedirs('build/cs_abi_test', exist_ok=True)
aar_url = 'https://jitpack.io/com/github/recloudstream/cloudstream/library-android/d9c55e2e5b/library-android-d9c55e2e5b.aar'
nice_url = 'https://jitpack.io/com/github/Blatzar/NiceHttp/0.4.18/NiceHttp-0.4.18.jar'

if not os.path.exists('build/cs_abi_test/library_d9.aar'):
    print('Downloading d9c55e2e5b library AAR...')
    urllib.request.urlretrieve(aar_url, 'build/cs_abi_test/library_d9.aar')
    with zipfile.ZipFile('build/cs_abi_test/library_d9.aar', 'r') as z:
        z.extract('classes.jar', 'build/cs_abi_test')
    if os.path.exists('build/cs_abi_test/library_classes.jar'):
        os.remove('build/cs_abi_test/library_classes.jar')
    os.rename('build/cs_abi_test/classes.jar', 'build/cs_abi_test/library_classes.jar')


if not os.path.exists('build/cs_abi_test/nicehttp.jar'):
    print('Downloading NiceHttp JAR...')
    urllib.request.urlretrieve(nice_url, 'build/cs_abi_test/nicehttp.jar')

d8_path = r'C:\Users\okay\AppData\Local\Android\Sdk\build-tools\36.0.0\d8.bat'
print('Running d8...')
cmd = f'cmd /c "{d8_path}" --output build/cs_abi_test build/cs_abi_test/library_classes.jar build/cs_abi_test/nicehttp.jar'
res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
print('d8 result:', res.returncode, res.stdout, res.stderr)

# Parse defined methods in output classes.dex
defined = set()
for name, mname, params, ret in a.parse_dex('build/cs_abi_test/classes.dex'):
    defined.add((name, mname, tuple(params), ret))

print(f'Total methods defined in real CloudStream library + NiceHttp: {len(defined)}')

# Parse needed methods from 40 plugins
needed = set()
plugin_callers = {}
for df in glob.glob('build/dex_cache/*.dex'):
    fname = os.path.basename(df)
    for cls, name, params, ret in a.parse_dex(df):
        if cls.startswith('Lcom/lagradost/'):
            key = (cls, name, tuple(params), ret)
            needed.add(key)
            if key not in plugin_callers:
                plugin_callers[key] = []
            plugin_callers[key].append(fname)

print(f'Total com.lagradost methods needed by 40 plugins: {len(needed)}')

cs_needed = [m for m in needed if m[0].startswith('Lcom/lagradost/cloudstream3/') or m[0].startswith('Lcom/lagradost/nicehttp/')]
print(f'Total actual CloudStream + NiceHttp methods needed: {len(cs_needed)}')

cs_missing = [m for m in sorted(cs_needed) if m not in defined]
print(f'TRULY MISSING in real CloudStream v4.8.0 + NiceHttp 0.4.18: {len(cs_missing)}')
for cls, name, params, ret in cs_missing:
    callers = ', '.join(set(plugin_callers[(cls, name, params, ret)]))
    p_str = ', '.join(params)
    print(f'MISSING: {cls}->{name}({p_str}) -> {ret} [Used by: {callers}]')

