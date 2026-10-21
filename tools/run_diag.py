#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Play Oynatma ve Duman Tanısı (run_diag.py sarmalayıcısı).
Mevcut run_diag.py çağrılarını tools/smoke_test.py'ye yönlendirir.
"""

import os
import sys

tools_dir = os.path.dirname(os.path.abspath(__file__))
smoke_test_script = os.path.join(tools_dir, "smoke_test.py")

if __name__ == "__main__":
    import subprocess
    args = [sys.executable, smoke_test_script]
    # Eğer parametre verildiyse ve '--' ile başlamıyorsa filtre olarak ilet
    if len(sys.argv) > 1:
        if sys.argv[1].startswith("--"):
            args.extend(sys.argv[1:])
        else:
            args.extend(["--filter", sys.argv[1]])
            args.extend(sys.argv[2:])
    res = subprocess.run(args)
    sys.exit(res.returncode)
