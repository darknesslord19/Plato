import os
import glob
import subprocess

# Let's inspect all 418 methods and see what classes they belong to
import analyze_dex_methods as a

dex_files = glob.glob(r"build\dex_cache\*.dex")
cs_methods = set()

for df in dex_files:
    fname = os.path.basename(df)
    methods = a.parse_dex(df)
    for cls, name, params, ret in methods:
        if cls.startswith('Lcom/lagradost/'):
            cs_methods.add((cls, name, tuple(params), ret))

print(f"Total unique methods on com.lagradost: {len(cs_methods)}")

# Let's categorize by class
from collections import defaultdict
by_class = defaultdict(list)
for cls, name, params, ret in sorted(cs_methods):
    by_class[cls].append((name, params, ret))

for cls in sorted(by_class.keys()):
    print(f"\nClass: {cls} ({len(by_class[cls])} methods)")
    for name, params, ret in by_class[cls]:
        print(f"  {name}({', '.join(params)}) -> {ret}")
