#!/usr/bin/env python3
"""ReMaf2 (Mafia II Droid): build, verify and sign in one command.

    python3 mafia2/build_m2.py [--apk path/to/original.apk]

Takes the original port APK (VinTeRuS, com.mafia2re.game) and produces
mafia2/output/ReMaf2 <version>.apk:
  * classes.dex and lib/arm64-v8a/* are taken from the original unchanged;
  * classes2.dex is our launcher / controls / editor (java/), compiled with javac + d8;
  * manifest, icons, fonts come from this folder (aapt2);
  * zipalign + apksigner v1+v2+v3 with mafia2/.signing_keys (created on first run).
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

VERSION_NAME = "1.2.3"
VERSION_CODE = 9

HERE = os.path.dirname(os.path.abspath(__file__))
SDK = os.environ.get("ANDROID_HOME", "/opt/android-sdk")
BT = os.path.join(SDK, "build-tools", "36.0.0")
PLATFORM = os.path.join(SDK, "platforms", "android-36", "android.jar")
KEYS = os.path.join(HERE, ".signing_keys")
MIN_SDK, TARGET_SDK = 26, 35


def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd[:6]), "..." if len(cmd) > 6 else "")
    subprocess.run(cmd, check=True, **kw)


def ensure_keys():
    key, cert = os.path.join(KEYS, "release.key.pem"), os.path.join(KEYS, "release.cert.pem")
    if not (os.path.exists(key) and os.path.exists(cert)):
        os.makedirs(KEYS, exist_ok=True)
        run(["openssl", "req", "-x509", "-newkey", "rsa:4096", "-nodes", "-keyout", key, "-out", cert,
             "-days", "10950", "-subj", "/CN=Mafia II Droid/O=Mafia II Droid"])
    return key, cert


def compile_java(build):
    stubs_out = os.path.join(build, "stubs")
    cls_out = os.path.join(build, "classes")
    for d in (stubs_out, cls_out):
        shutil.rmtree(d, ignore_errors=True)
        os.makedirs(d)
    # java.* from the JDK's release-11 API, android.* from android.jar (as AGP does); d8 then
    # checks everything against android.jar.
    common = ["javac", "-encoding", "UTF-8", "--release", "11", "-Xlint:-options"]
    run(common + ["-cp", PLATFORM, "-d", stubs_out] + glob.glob(os.path.join(HERE, "stubs", "**", "*.java"), recursive=True))
    run(common + ["-cp", stubs_out + os.pathsep + PLATFORM, "-d", cls_out] + glob.glob(os.path.join(HERE, "java", "**", "*.java"), recursive=True))
    dex_out = os.path.join(build, "dex")
    shutil.rmtree(dex_out, ignore_errors=True)
    os.makedirs(dex_out)
    classes = glob.glob(os.path.join(cls_out, "**", "*.class"), recursive=True)
    run([os.path.join(BT, "d8"), "--release", "--min-api", str(MIN_SDK), "--lib", PLATFORM,
         "--classpath", stubs_out, "--output", dex_out] + classes)
    return os.path.join(dex_out, "classes.dex")


def link_resources(build):
    flat = os.path.join(build, "res.zip")
    run([os.path.join(BT, "aapt2"), "compile", "--dir", os.path.join(HERE, "res"), "-o", flat])
    base = os.path.join(build, "base.apk")
    run([os.path.join(BT, "aapt2"), "link", "-I", PLATFORM, "--manifest", os.path.join(HERE, "AndroidManifest.xml"),
         "--version-code", str(VERSION_CODE), "--version-name", VERSION_NAME,
         "--min-sdk-version", str(MIN_SDK), "--target-sdk-version", str(TARGET_SDK),
         "-A", os.path.join(HERE, "assets"), "-o", base, flat])
    return base


def assemble(orig, base, dex2, out_unsigned):
    with zipfile.ZipFile(orig) as zo:
        names = zo.namelist()
        dexes = sorted(n for n in names if n.startswith("classes") and n.endswith(".dex"))
        if dexes != ["classes.dex"]:
            sys.exit("unexpected dex set in the original APK: %s" % dexes)
        libs = [n for n in names if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
        if not any(n.endswith("/libmain.so") for n in libs):
            sys.exit("original APK has no lib/arm64-v8a/libmain.so")
        with zipfile.ZipFile(out_unsigned, "w") as zn:
            with zipfile.ZipFile(base) as zb:
                for info in zb.infolist():
                    data = zb.read(info.filename)
                    comp = zipfile.ZIP_STORED if info.filename == "resources.arsc" else zipfile.ZIP_DEFLATED
                    zn.writestr(zipfile.ZipInfo(info.filename, date_time=(2026, 1, 1, 0, 0, 0)), data, compress_type=comp)
            for n in ["classes.dex"] + libs:
                zi = zipfile.ZipInfo(n, date_time=(2026, 1, 1, 0, 0, 0))
                zn.writestr(zi, zo.read(n), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
            zn.write(dex2, "classes2.dex", compress_type=zipfile.ZIP_DEFLATED)


def sign(unsigned, out):
    key, cert = ensure_keys()
    tmp = tempfile.mkdtemp()
    try:
        pk8 = os.path.join(tmp, "key.pk8")
        run(["openssl", "pkcs8", "-topk8", "-nocrypt", "-in", key, "-outform", "DER", "-out", pk8])
        aligned = os.path.join(tmp, "aligned.apk")
        run([os.path.join(BT, "zipalign"), "-f", "-p", "4", unsigned, aligned])
        run([os.path.join(BT, "apksigner"), "sign", "--key", pk8, "--cert", cert,
             "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
             "--min-sdk-version", str(MIN_SDK), "--out", out, aligned])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def verify(apk):
    run([os.path.join(BT, "apksigner"), "verify", "--min-sdk-version", str(MIN_SDK), "-v", apk], stdout=subprocess.DEVNULL)
    badge = subprocess.run([os.path.join(BT, "aapt2"), "dump", "badging", apk], check=True, capture_output=True, text=True).stdout
    assert "package: name='com.mafia2re.droid'" in badge, badge[:300]
    assert "launchable-activity: name='com.mafia2re.game.LauncherActivity'" in badge
    # Our dex must not redefine any class of the original one.
    dump = os.path.join(BT, "dexdump")
    with zipfile.ZipFile(apk) as z, tempfile.TemporaryDirectory() as t:
        sets = []
        for n in ("classes.dex", "classes2.dex"):
            p = os.path.join(t, n)
            open(p, "wb").write(z.read(n))
            out = subprocess.run([dump, p], check=True, capture_output=True, text=True).stdout
            sets.append({l.split("'")[1] for l in out.splitlines() if "Class descriptor" in l})
        dup = sets[0] & sets[1]
        assert not dup, "duplicate classes: %s" % sorted(dup)
        for need in ("Lcom/mafia2re/game/LauncherActivity;", "Lcom/mafia2re/game/M2GameActivity;", "Lcom/mafia2re/game/ControlsOverlay;"):
            assert need in sets[1], need
        for need in ("Lcom/mafia2re/game/GameActivity;", "Lcom/mafia2re/game/TouchControls;", "Lio/avm/r7/s;", "Lorg/libsdl/app/SDLActivity;"):
            assert need in sets[0], need
    print("verify: signature v1/v2/v3 ok, manifest ok, %d + %d classes, no duplicates" % (len(sets[0]), len(sets[1])))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", default=os.path.join(HERE, "orig", "m2.apk"), help="original port APK")
    a = ap.parse_args()
    if not os.path.exists(a.apk):
        sys.exit("original APK not found: %s (put it at mafia2/orig/m2.apk)" % a.apk)
    build = os.path.join(HERE, "build")
    os.makedirs(build, exist_ok=True)
    dex2 = compile_java(build)
    base = link_resources(build)
    unsigned = os.path.join(build, "unsigned.apk")
    assemble(a.apk, base, dex2, unsigned)
    os.makedirs(os.path.join(HERE, "output"), exist_ok=True)
    out = os.path.join(HERE, "output", "ReMaf2 %s.apk" % VERSION_NAME)
    sign(unsigned, out)
    verify(out)
    print("OK -> %s (%.1f MB)" % (out, os.path.getsize(out) / 1e6))


if __name__ == "__main__":
    main()
