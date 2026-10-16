import os
import glob
import zipfile
import analyze_dex_methods as a

# 1. Collect all methods called by 40 plugins on com.lagradost
dex_files = glob.glob(r"build\dex_cache\*.dex")
needed_methods = set()
plugin_callers = {}

for df in dex_files:
    fname = os.path.basename(df)
    methods = a.parse_dex(df)
    for cls, name, params, ret in methods:
        if cls.startswith('Lcom/lagradost/'):
            key = (cls, name, tuple(params), ret)
            needed_methods.add(key)
            if key not in plugin_callers:
                plugin_callers[key] = []
            plugin_callers[key].append(fname)

print(f"Total needed com.lagradost methods: {len(needed_methods)}")

# 2. Extract and parse all classes*.dex from app-x86_64-debug.apk
apk_path = r"app\build\outputs\apk\debug\app-x86_64-debug.apk"
defined_methods = set()

with zipfile.ZipFile(apk_path, 'r') as zf:
    for name in zf.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            dex_data = zf.read(name)
            # Temporarily save and parse
            temp_path = f"build\\temp_{name}"
            with open(temp_path, 'wb') as tf:
                tf.write(dex_data)
            methods = a.parse_dex(temp_path)
            for cls, mname, params, ret in methods:
                defined_methods.add((cls, mname, tuple(params), ret))

print(f"Total methods in APK: {len(defined_methods)}")

# 3. Find missing
missing = []
for m in sorted(needed_methods):
    if m not in defined_methods:
        missing.append(m)

print(f"\n==========================================")
print(f"MISSING METHODS IN APK: {len(missing)}")
print(f"==========================================")
for cls, name, params, ret in missing:
    callers = ", ".join(plugin_callers[(cls, name, params, ret)])
    print(f"MISSING: {cls}->{name}({', '.join(params)}) -> {ret} [Used by: {callers}]")
