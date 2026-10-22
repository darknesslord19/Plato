"""Cihaz denemesi raporunu GitHub notlarına ve özet sayfasına çevirir."""
import collections, json, os, sys

path = sys.argv[1]
try:
    data = json.load(open(path, encoding="utf-8"))
except Exception as e:  # noqa: BLE001
    d = os.path.dirname(path)
    def tail(name, n=25):
        try:
            lines = open(os.path.join(d, name), encoding="utf-8", errors="replace").read().splitlines()
            return "%0A".join(l.replace("%", "%25")[:200] for l in lines[-n:])
        except OSError:
            return "(yok)"
    print(f"::error title=Cihaz denemesi::Rapor okunamadı: {e}")
    print(f"::error title=Cihaz: am instrument çıktısı::{tail('instrument.txt')}")
    print(f"::error title=Cihaz: adb::{tail('run.txt', 30)}")
    print(f"::error title=Cihaz: logcat::{tail('logcat.txt', 40)}")
    sys.exit(0)
plugins = data.get("plugins", [])
c = collections.Counter(p.get("stage") for p in plugins)
loaded = c["ok"] + c["empty"]
tried = loaded + c["load"]
print(f"::notice title=Cihaz denemesi (gerçek eklentiler)::Yüklenen {loaded}/{tried}; "
      f"katalog/arama sonucu veren {c['ok']}; sonuç vermeyen (site/ağ) {c['empty']}; "
      f"yüklenemeyen {c['load']}; indirilemeyen {c['download']}")
errs = collections.Counter((p.get("error") or "?").split(":")[0] for p in plugins if p.get("stage") == "load")
if errs:
    print("::warning title=Yüklenemeyen eklentilerin hata türleri::" +
          "%0A".join(f"{n} × {k}" for k, n in errs.most_common(15)))
detail = [f"{p['repo']}/{p['name']}: {p.get('error')}" for p in plugins if p.get("stage") == "load"]
if detail:
    print("::warning title=Yüklenemeyen eklentiler::" + "%0A".join(d.replace("\n", " ")[:220] for d in detail[:40]))
lines = ["## Cihaz denemesi: gerçek CloudStream eklentileri", "",
         f"Yüklenen **{loaded}/{tried}**, sonuç veren **{c['ok']}**, sonuç vermeyen {c['empty']}, yüklenemeyen {c['load']}, indirilemeyen {c['download']}", "",
         "| Depo | Eklenti | Durum | Katalog | Arama | Hata |", "|---|---|---|---:|---:|---|"]
for p in plugins:
    e = p.get("error") or p.get("homeError") or p.get("searchError") or ""
    lines.append(f"| {p.get('repo')} | {p.get('name')} | {p.get('stage')} | {p.get('homeItems', '')} | {p.get('searchItems', '')} | {str(e)[:120]} |")
s = os.environ.get("GITHUB_STEP_SUMMARY")
if s:
    open(s, "a", encoding="utf-8").write("\n".join(lines) + "\n")
print("\n".join(lines[:4]))
