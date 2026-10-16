import os
import glob
from collections import defaultdict
import analyze_dex_methods as a

dex_files = glob.glob(r"build\dex_cache\*.dex")
cs_methods = set()

for df in dex_files:
    fname = os.path.basename(df)
    methods = a.parse_dex(df)
    for cls, name, params, ret in methods:
        if cls.startswith('Lcom/lagradost/cloudstream3/'):
            cs_methods.add((cls, name, tuple(params), ret))

by_class = defaultdict(list)
for cls, name, params, ret in sorted(cs_methods):
    by_class[cls].append((name, params, ret))

with open(r'tools\cs_methods_clean.txt', 'w', encoding='utf-8') as out:
    for cls in sorted(by_class.keys()):
        out.write(f"\nClass: {cls} ({len(by_class[cls])} methods)\n")
        for name, params, ret in by_class[cls]:
            out.write(f"  {name}({', '.join(params)}) -> {ret}\n")

print(f"Written {len(cs_methods)} methods across {len(by_class)} classes.")
