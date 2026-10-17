import os
import zipfile

orig_jar = r"C:\Users\okay\.gradle\caches\modules-2\files-2.1\org.jsoup\jsoup\1.23.2\514805efcc430bfb04e82f20bb998e39e3fd5c9c\jsoup-1.23.2.jar"
patched_dir = r"app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes\org\jsoup\helper"
w3cdom_cls = os.path.join(patched_dir, "W3CDom.class")
w3cbuilder_cls = os.path.join(patched_dir, "W3CDom$W3CBuilder.class")

with open(w3cdom_cls, "rb") as f:
    w3cdom_bytes = f.read()
with open(w3cbuilder_cls, "rb") as f:
    w3cbuilder_bytes = f.read()

os.makedirs("app/libs", exist_ok=True)
out_jar = r"app\libs\jsoup-1.23.2-patched.jar"

with zipfile.ZipFile(orig_jar, "r") as zin, zipfile.ZipFile(out_jar, "w", compression=zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == "org/jsoup/helper/W3CDom.class":
            zout.writestr(item, w3cdom_bytes)
            print("Replaced org/jsoup/helper/W3CDom.class, len=", len(w3cdom_bytes))
        elif item.filename == "org/jsoup/helper/W3CDom$W3CBuilder.class":
            zout.writestr(item, w3cbuilder_bytes)
            print("Replaced org/jsoup/helper/W3CDom$W3CBuilder.class, len=", len(w3cbuilder_bytes))
        else:
            zout.writestr(item, zin.read(item.filename))

print("Created successfully:", out_jar, "Size:", os.path.getsize(out_jar))
