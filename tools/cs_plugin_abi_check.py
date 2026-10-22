#!/usr/bin/env python3
"""CloudStream eklenti uyumluluk denetimi (statik "bağlayıcı" kontrolü).

Gerçek depolardan .cs3 eklentilerini indirir, her eklentinin classes.dex'inde dışarıya yaptığı sınıf/metot/alan
başvurularını çıkarır ve derlenmiş Play APK'sının dex dosyalarında bunların gerçekten var olup olmadığına bakar
(üst sınıf ve arayüz zinciri dahil). Bulunamayan her başvuru, eklenti o koda geldiğinde Android'de
NoClassDefFoundError / NoSuchMethodError / NoSuchFieldError demektir.

Android çerçevesine (android.*, java.* ...) ait sınıflar APK'da olmadığı için doğrulanamaz; bunlar var sayılır.

Kullanım:
    python3 tools/cs_plugin_abi_check.py --apk app-release.apk --repos tools/cs_repos.list \
        --out build/cs-abi [--cache build/cs-plugins] [--max-plugins-per-repo 200]

Çıktı: <out>/report.md (özet + en sık eksikler + eklenti başına), <out>/report.json. GitHub Actions'ta
özet `::notice` ve en sık eksikler `::warning` notu olarak da yazılır. Ağ hataları eklentiyi "indirilemedi" sayar.
Yalnızca Python standart kütüphanesi kullanılır.
"""
import argparse
import collections
import concurrent.futures
import hashlib
import io
import json
import os
import struct
import sys
import urllib.parse
import urllib.request
import zipfile

FRAMEWORK_PREFIXES = (
    "Ljava/", "Ljavax/", "Landroid/", "Ldalvik/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/", "Lorg/xmlpull/",
    "Lorg/apache/http/", "Lsun/", "Llibcore/", "Lcom/android/internal/",
)
# Yalnızca açıklama (annotation) olarak geçen, çalışma anında gerekmeyen paketler.
IGNORED_PREFIXES = (
    "Lorg/jetbrains/annotations/", "Landroidx/annotation/", "Lcom/android/tools/r8/", "Lkotlin/jvm/internal/SourceDebugExtension",
    "Lcom/android/tools/", "Ljavax/annotation/", "Lorg/intellij/lang/annotations/", "Lkotlin/Metadata",
    "Ldalvik/annotation/",
)
UA = "Mozilla/5.0 (X11; Linux x86_64) Play-CS-ABI-Check"


# ─── DEX ayrıştırma ────────────────────────────────────────────────────────────

def uleb(b, o):
    r = s = 0
    while True:
        x = b[o]
        o += 1
        r |= (x & 0x7F) << s
        s += 7
        if x < 0x80:
            return r, o


class Dex:
    """Bir dex dosyasının tablo bilgisi ve sınıf tanımları."""

    def __init__(self, data):
        self.d = data
        h = struct.unpack_from("<8s4s20sIIIIIIIIIIIIIIIIIIII", data, 0)
        (self.ss, self.so, self.ts, self.to, self.ps, self.po, self.fs, self.fo, self.ms, self.mo,
         self.cs, self.co) = h[9:21]
        self._str = {}
        self.types = [self.string(struct.unpack_from("<I", data, self.to + 4 * i)[0]) for i in range(self.ts)]

    def string(self, i):
        v = self._str.get(i)
        if v is None:
            off = struct.unpack_from("<I", self.d, self.so + 4 * i)[0]
            _, o = uleb(self.d, off)
            e = self.d.index(b"\0", o)
            v = self.d[o:e].decode("utf-8", "replace")
            self._str[i] = v
        return v

    def proto(self, i):
        shorty, ret, params_off = struct.unpack_from("<III", self.d, self.po + 12 * i)
        ps = []
        if params_off:
            n = struct.unpack_from("<I", self.d, params_off)[0]
            ps = [self.types[struct.unpack_from("<H", self.d, params_off + 4 + 2 * k)[0]] for k in range(n)]
        return "(" + "".join(ps) + ")" + self.types[ret]

    def method(self, i):
        c, p, n = struct.unpack_from("<HHI", self.d, self.mo + 8 * i)
        return self.types[c], self.string(n), self.proto(p)

    def field(self, i):
        c, t, n = struct.unpack_from("<HHI", self.d, self.fo + 8 * i)
        return self.types[c], self.string(n), self.types[t]

    def methods(self):
        return [self.method(i) for i in range(self.ms)]

    def fields(self):
        return [self.field(i) for i in range(self.fs)]

    def classes(self):
        """(sınıf, üst sınıf, arayüzler, tanımlı metotlar {(ad, imza)}, tanımlı alanlar {ad})"""
        out = []
        for k in range(self.cs):
            cidx, _, sidx, ioff, _, _, cdo, _ = struct.unpack_from("<IIIIIIII", self.d, self.co + 32 * k)
            name = self.types[cidx]
            sup = self.types[sidx] if sidx != 0xFFFFFFFF else None
            itfs = []
            if ioff:
                n = struct.unpack_from("<I", self.d, ioff)[0]
                itfs = [self.types[struct.unpack_from("<H", self.d, ioff + 4 + 2 * j)[0]] for j in range(n)]
            ms, fs = set(), set()
            if cdo:
                o = cdo
                sf, o = uleb(self.d, o)
                inf, o = uleb(self.d, o)
                dm, o = uleb(self.d, o)
                vm, o = uleb(self.d, o)
                fid = 0
                for j in range(sf + inf):
                    if j == sf:
                        fid = 0
                    d, o = uleb(self.d, o)
                    fid += d
                    _, o = uleb(self.d, o)
                    fs.add(self.field(fid)[1])
                mid = 0
                for j in range(dm + vm):
                    if j == dm:
                        mid = 0
                    d, o = uleb(self.d, o)
                    mid += d
                    _, o = uleb(self.d, o)
                    _, o = uleb(self.d, o)
                    _, n, p = self.method(mid)
                    ms.add((n, p))
            out.append((name, sup, itfs, ms, fs))
        return out


def dex_entries(zf):
    return sorted(n for n in zf.namelist() if n.startswith("classes") and n.endswith(".dex"))


# ─── APK dizini ────────────────────────────────────────────────────────────────

class Apk:
    def __init__(self, path):
        self.cls = {}
        with zipfile.ZipFile(path) as z:
            for n in dex_entries(z):
                for name, sup, itfs, ms, fs in Dex(z.read(n)).classes():
                    self.cls.setdefault(name, (sup, itfs, ms, fs))

    def has_class(self, t):
        return t in self.cls

    def _walk(self, t):
        seen, stack = set(), [t]
        while stack:
            c = stack.pop()
            if c in seen or c is None:
                continue
            seen.add(c)
            yield c
            info = self.cls.get(c)
            if info:
                stack.append(info[0])
                stack.extend(info[1])

    def resolve_method(self, owner, name, proto):
        """True: bulundu, None: çerçeveye ulaşıldı (doğrulanamaz), False: yok."""
        unknown = False
        for c in self._walk(owner):
            info = self.cls.get(c)
            if info is None:
                if c.startswith(FRAMEWORK_PREFIXES):
                    unknown = True
                continue
            if (name, proto) in info[2]:
                return True
        return None if unknown else False

    def resolve_field(self, owner, name):
        unknown = False
        for c in self._walk(owner):
            info = self.cls.get(c)
            if info is None:
                if c.startswith(FRAMEWORK_PREFIXES):
                    unknown = True
                continue
            if name in info[3]:
                return True
        return None if unknown else False


# ─── Eklenti indirme ───────────────────────────────────────────────────────────

def fetch(url, cache_dir, timeout=40):
    key = hashlib.sha256(url.encode()).hexdigest()[:32]
    path = os.path.join(cache_dir, key)
    if os.path.exists(path):
        with open(path, "rb") as f:
            return f.read()
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read(40 * 1024 * 1024)
    with open(path, "wb") as f:
        f.write(data)
    return data


def load_repo_plugins(repo_url, cache_dir, limit):
    raw = json.loads(fetch(repo_url, cache_dir).decode("utf-8-sig"))
    name = raw.get("name") or urllib.parse.urlparse(repo_url).path.split("/")[2]
    out = []
    for lst in raw.get("pluginLists") or []:
        lst_url = urllib.parse.urljoin(repo_url, lst)
        try:
            plugins = json.loads(fetch(lst_url, cache_dir).decode("utf-8-sig"))
        except Exception as e:  # noqa: BLE001
            out.append({"repo": name, "name": f"(liste) {lst_url}", "error": f"liste indirilemedi: {e}"})
            continue
        for p in plugins[:limit]:
            if p.get("status", 1) == 0:
                continue  # depo sahibi "kapalı" işaretlemiş
            out.append({"repo": name, "name": p.get("name") or p.get("internalName"),
                        "internalName": p.get("internalName"), "url": urllib.parse.urljoin(lst_url, p["url"])})
    return out


# ─── Denetim ───────────────────────────────────────────────────────────────────

def check_plugin(entry, apk, cache_dir):
    try:
        data = fetch(entry["url"], cache_dir)
        z = zipfile.ZipFile(io.BytesIO(data))
    except Exception as e:  # noqa: BLE001
        return dict(entry, status="download", missing=[], error=str(e)[:200])
    defined, types, methods, fields = set(), set(), set(), set()
    try:
        for n in dex_entries(z):
            dx = Dex(z.read(n))
            defined |= {c[0] for c in dx.classes()}
            types |= set(dx.types)
            methods |= set(dx.methods())
            fields |= set(dx.fields())
    except Exception as e:  # noqa: BLE001
        return dict(entry, status="invalid", missing=[], error=f"dex okunamadı: {e}"[:200])

    def external(t):
        return t.startswith("L") and t not in defined and not t.startswith(IGNORED_PREFIXES)

    missing = set()
    for t in types:
        if external(t) and not t.startswith(FRAMEWORK_PREFIXES) and not apk.has_class(t):
            missing.add("class " + t[1:-1].replace("/", "."))
    for owner, name, proto in methods:
        o = owner.lstrip("[")
        if not external(o) or o.startswith(FRAMEWORK_PREFIXES) or not apk.has_class(o):
            continue
        if apk.resolve_method(o, name, proto) is False:
            missing.add(f"method {o[1:-1].replace('/', '.')}.{name}{proto}")
    for owner, name, _t in fields:
        if not external(owner) or owner.startswith(FRAMEWORK_PREFIXES) or not apk.has_class(owner):
            continue
        if apk.resolve_field(owner, name) is False:
            missing.add(f"field {owner[1:-1].replace('/', '.')}#{name}")
    return dict(entry, status="ok" if not missing else "broken", missing=sorted(missing))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--repos", required=True, help="Her satırda bir repo.json adresi (# ile yorum)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--cache", default=None)
    ap.add_argument("--max-plugins-per-repo", type=int, default=300)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    cache = args.cache or os.path.join(args.out, "cache")
    os.makedirs(cache, exist_ok=True)

    apk = Apk(args.apk)
    repos = [l.split("#")[0].strip() for l in open(args.repos, encoding="utf-8")]
    repos = [r for r in repos if r]

    entries, repo_errors = [], []
    for r in repos:
        try:
            entries += load_repo_plugins(r, cache, args.max_plugins_per_repo)
        except Exception as e:  # noqa: BLE001
            repo_errors.append((r, str(e)[:200]))

    results = []
    todo = [e for e in entries if "url" in e]
    results += [dict(e, status="download", missing=[]) for e in entries if "url" not in e]
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as ex:
        for res in ex.map(lambda e: check_plugin(e, apk, cache), todo):
            results.append(res)

    counts = collections.Counter(r["status"] for r in results)
    sym = collections.Counter(m for r in results for m in r["missing"])
    checked = counts["ok"] + counts["broken"]
    ok_pct = (100.0 * counts["ok"] / checked) if checked else 0.0

    lines = ["# CloudStream eklenti uyumluluk raporu", "",
             f"- APK: `{os.path.basename(args.apk)}` ({len(apk.cls)} sınıf)",
             f"- Depo: {len(repos)} (okunamayan {len(repo_errors)}), eklenti: {len(results)}",
             f"- **Uyumlu: {counts['ok']}/{checked} (%{ok_pct:.1f})**, eksik başvurulu: {counts['broken']}, "
             f"indirilemeyen: {counts['download']}, geçersiz: {counts['invalid']}", ""]
    if repo_errors:
        lines += ["## Okunamayan depolar", ""] + [f"- {u}: {e}" for u, e in repo_errors] + [""]
    lines += ["## En sık eksik başvurular", "", "| Eklenti | Başvuru |", "|---:|---|"]
    lines += [f"| {n} | `{s}` |" for s, n in sym.most_common(60)]
    lines += ["", "## Eksik başvurusu olan eklentiler", ""]
    for r in sorted((r for r in results if r["status"] == "broken"), key=lambda r: (r["repo"], r["name"] or "")):
        lines.append(f"- **{r['repo']} / {r['name']}** ({len(r['missing'])}): " +
                     ", ".join(f"`{m}`" for m in r["missing"][:8]) + (" …" if len(r["missing"]) > 8 else ""))
    with open(os.path.join(args.out, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(args.out, "report.json"), "w", encoding="utf-8") as f:
        json.dump({"counts": counts, "okPercent": ok_pct, "topMissing": sym.most_common(200),
                   "repoErrors": repo_errors, "plugins": results}, f, ensure_ascii=False, indent=1)

    print("\n".join(lines[:6]))
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::notice title=CloudStream uyumluluğu::Uyumlu {counts['ok']}/{checked} (%{ok_pct:.1f}); "
              f"eksik başvurulu {counts['broken']}; indirilemeyen {counts['download']}")
        top = "%0A".join(f"{n} eklenti: {s}" for s, n in sym.most_common(25))
        if top:
            print(f"::warning title=En sık eksik başvurular::{top}")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as f:
                f.write("\n".join(lines[:80]) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
