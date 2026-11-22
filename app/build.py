import os
import subprocess
import shutil
import zipfile
import hashlib
import re
import tarfile

APP_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(APP_DIR, "src")
RES_DIR = os.path.join(APP_DIR, "res")
CLASSES_DIR = os.path.join(APP_DIR, "build", "classes")
DEX_DIR = os.path.join(APP_DIR, "build", "dex")
SDK_JAR = r"C:\Users\Usuario\AppData\Local\Android\Sdk\platforms\android-35\android.jar"
BUILD_TOOLS = r"C:\Users\Usuario\AppData\Local\Android\Sdk\build-tools\35.0.0"

AAPT = os.path.join(BUILD_TOOLS, "aapt.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
KEYSTORE = os.path.join(APP_DIR, "test.jks")
MANIFEST = os.path.join(APP_DIR, "AndroidManifest.xml")
OUT_APK = os.path.join(APP_DIR, "OneiOS-A56.apk")

if os.path.exists(CLASSES_DIR):
    shutil.rmtree(CLASSES_DIR)
os.makedirs(CLASSES_DIR, exist_ok=True)

if os.path.exists(DEX_DIR):
    shutil.rmtree(DEX_DIR)
os.makedirs(DEX_DIR, exist_ok=True)

print("[0] Checking assets/bootstrap.tar.gz against the pin in Manager.java...")
java_src = os.path.join(SRC_DIR, "Manager.java")
with open(java_src, encoding="utf-8") as f:
    pin = re.search(r'ROOT_SHA = "([0-9a-f]{64})"', f.read()).group(1)
boot = os.path.join(APP_DIR, "assets", "bootstrap.tar.gz")
need = {"bin/oneios_root.sh", "oneios_switch.sh", "oneios_return.sh", "stage_ios16.sh",
        "runtime.conf", "runtime.sha256", "runtime/runtime.json"}
with tarfile.open(boot, "r:gz") as t:
    names = set(m.name.lstrip("./") for m in t.getmembers())
    missing = need - names
    if missing:
        print("BOOTSTRAP ERROR: missing", sorted(missing))
        exit(1)
    root_sha = hashlib.sha256(t.extractfile("bin/oneios_root.sh").read()).hexdigest()
    if root_sha != pin:
        print(f"BOOTSTRAP ERROR: bin/oneios_root.sh is {root_sha}, Manager.java pins {pin}")
        exit(1)
    for m in t.getmembers():
        if m.isfile() and m.name.endswith(".sh"):
            head = t.extractfile(m).read(3)
            if head.startswith(b"\xef\xbb\xbf") or not head.startswith(b"#!"):
                print(f"BOOTSTRAP ERROR: {m.name} does not start with '#!' (UTF-8 BOM?)")
                exit(1)
            if b"\r" in t.extractfile(m).read():
                print(f"BOOTSTRAP ERROR: {m.name} has CRLF line endings")
                exit(1)
print("Bootstrap OK (oneios_root.sh matches the pin, scripts are plain LF).")

print("[1] Compiling Manager.java with javac...")
cmd_javac = ["javac", "-encoding", "UTF-8", "-cp", SDK_JAR, "-d", CLASSES_DIR, java_src]
res = subprocess.run(cmd_javac, capture_output=True, text=True)
if res.returncode != 0:
    print("JAVAC ERROR:\n", res.stderr)
    exit(1)
print("Javac compiled cleanly.")

print("[2] Running D8 to create classes.dex...")
class_files = []
for root, dirs, files in os.walk(CLASSES_DIR):
    for f in files:
        if f.endswith(".class"):
            class_files.append(os.path.join(root, f))
cmd_d8 = [D8, "--output", DEX_DIR] + class_files
res = subprocess.run(cmd_d8, capture_output=True, text=True, shell=True)
if res.returncode != 0:
    print("D8 ERROR:\n", res.stderr)
    exit(1)
print("D8 generated classes.dex.")

print("[3] Packaging with AAPT (including resources & icon)...")
unsigned_apk = os.path.join(APP_DIR, "build", "manager-unsigned.apk")
if os.path.exists(unsigned_apk):
    os.remove(unsigned_apk)
ASSETS_DIR = os.path.join(APP_DIR, "assets")
cmd_aapt = [AAPT, "package", "-f", "-M", MANIFEST, "-S", RES_DIR, "-A", ASSETS_DIR, "-I", SDK_JAR, "-F", unsigned_apk]
res = subprocess.run(cmd_aapt, capture_output=True, text=True)
if res.returncode != 0:
    print("AAPT ERROR:\n", res.stderr)
    exit(1)
print("AAPT created base APK with icon resources.")

print("[4] Adding classes.dex to APK...")
dex_file = os.path.join(DEX_DIR, "classes.dex")
with zipfile.ZipFile(unsigned_apk, "a") as z:
    z.write(dex_file, "classes.dex")

print("[5] Running Zipalign...")
aligned_apk = os.path.join(APP_DIR, "build", "manager-aligned.apk")
if os.path.exists(aligned_apk):
    os.remove(aligned_apk)
cmd_zipalign = [ZIPALIGN, "-p", "-f", "4", unsigned_apk, aligned_apk]
res = subprocess.run(cmd_zipalign, capture_output=True, text=True)
if res.returncode != 0:
    print("ZIPALIGN ERROR:\n", res.stderr)
    exit(1)
print("Zipalign finished.")

print("[6] Signing with apksigner...")
cmd_sign = [APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-pass", "pass:android", "--key-pass", "pass:android", aligned_apk]
res = subprocess.run(cmd_sign, capture_output=True, text=True, shell=True)
if res.returncode != 0:
    print("APKSIGNER ERROR:\n", res.stderr)
    exit(1)
print("Signed APK successfully.")

shutil.copy2(aligned_apk, OUT_APK)
print(f"Copied final APK to {OUT_APK} ({os.path.getsize(OUT_APK)} bytes).")
