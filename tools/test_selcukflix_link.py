import urllib.request, re, json, base64
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

key = b"9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"
iv = b"\x00" * 16

urls = [
    ('Spider-Man', 'https://selcukflix.app/film/spider-man-brand-new-day/izle'),
    ('Kate Berlant', 'https://selcukflix.app/film/kate-berlant-parmesan/izle'),
    ('Siccin 9', 'https://selcukflix.app/film/siccn-9/izle'),
    ('Gibi S5E1', 'https://selcukflix.app/dizi/gibi/sezon-5/bolum-1/izle')
]

for name, url in urls:
    print(f"\n=== {name} ===")
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
        with urllib.request.urlopen(req, timeout=5) as r:
            h = r.read().decode('utf-8', errors='replace')
        m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', h)
        if not m:
            print("No __NEXT_DATA__")
            continue
        data = json.loads(m.group(1))
        secure_data = data['props']['pageProps']['secureData']
        
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
        decryptor = cipher.decryptor()
        raw = decryptor.update(base64.b64decode(secure_data)) + decryptor.finalize()
        pad = raw[-1]
        raw = raw[:-pad]
        decrypted = json.loads(raw.decode('utf-8'))
        print("Keys in decrypted:", list(decrypted.keys()))
        sources = decrypted.get('sources', {})
        print("Sources:", sources)
    except Exception as e:
        print("Error:", e)
