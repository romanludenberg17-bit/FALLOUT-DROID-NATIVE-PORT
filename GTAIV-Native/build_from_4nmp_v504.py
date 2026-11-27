#!/usr/bin/env python3
"""GTA IV Native on the full 4NMP v504 build (its engine + UI, 60-80 FPS) with:
  - libq.so LAN shim (shim/): the engine's own LAN co-op advertised 127.0.0.1;
    libmain.so's DT_NEEDED "libz.so" is renamed "libq.so" (libq depends on libz)
  - working MP / co-op hub (shell/nmp_mp_hub.html), bridge -> UDP 3076
  - package com.rommy.gtaiv, label, Wi-Fi multicast permissions, version
  - LAN backend + discovery port 3076 added to the launcher's profiles
usage: build_from_4nmp_v504.py <4NMP-v504.apk> <libq.so> <out unsigned.apk>
"""
import hashlib, os, struct, sys, zipfile, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_from_4nmp as base

base.VERSION_CODE = 623
base.VERSION_NAME = "1.2.3-v504"
HERE = os.path.dirname(os.path.abspath(__file__))

def patch_dex(dex):
    d = bytearray(dex)
    for old, new in ((bytes.fromhex("1306020c"), bytes.fromhex("1306040c")),   # bridge -> 3076
                     (b"nmp_v504_clean", b"nmp_v622_clean")):                 # args.txt reset once
        assert d.count(old) == 1, old
        j = d.find(old); d[j:j + len(old)] = new
    d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
    struct.pack_into("<I", d, 8, zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)
    return bytes(d)

# BuildContextTouchLayout's compact(action, label, buttons, trigger, visible=w5, ...)
# calls: force visible=false for buttons the mobile layout does not need.
HIDDEN_BUTTONS = {
    0x1654b60: ("TURBO (qte_turbo)", bytes.fromhex("05010012")),       # and  w5, w8, #0x1
    0x1654d98: ("ENGINE OFF (engine_off)", bytes.fromhex("e5079f1a")), # cset w5, ne
}
MOV_W5_WZR = bytes.fromhex("e5031f2a")

def patch_libmain(so):
    d = bytearray(so)
    for addr, (what, expected) in HIDDEN_BUTTONS.items():  # .text: file offset == vaddr
        assert bytes(d[addr:addr + 4]) == expected, f"unexpected code for {what}"
        d[addr:addr + 4] = MOV_W5_WZR
    old = b"\0libz.so\0"
    assert d.count(old) == 1, "libz.so NEEDED string not unique"
    j = d.find(old); d[j:j + len(old)] = b"\0libq.so\0"
    return bytes(d)

def patch_args(text):
    # Frame limit belongs to the in-game menu (a line here overrides it on every launch).
    lines = [l for l in text.splitlines() if not l.startswith("--gta4_frame_limit=")]
    extra = ["", "# LAN co-op (serverless)", "--gta4_multiplayer_backend=lan", "--gta4_lan_discovery_port=3076",
             ""]
    return "\n".join(lines + extra).encode()

def main():
    src, libq, out = sys.argv[1:4]
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            n = item.filename
            if n.startswith("META-INF/") and n.split("/")[-1].upper().endswith((".SF", ".RSA", ".MF", ".EC", ".DSA")):
                continue
            data = zin.read(n)
            if n == "AndroidManifest.xml": data = base.patch_manifest(data)
            elif n == "resources.arsc": data = base.patch_arsc(data)
            elif n == "classes2.dex": data = patch_dex(data)
            elif n == "lib/arm64-v8a/libmain.so": data = patch_libmain(data)
            elif n.startswith("assets/Resources/touch-icons/") and os.path.isfile(
                    os.path.join(HERE, "touch-icons-v504", os.path.basename(n))):
                data = open(os.path.join(HERE, "touch-icons-v504", os.path.basename(n)), "rb").read()
            elif n == "assets/nmp_mp_hub.html":
                data = open(os.path.join(HERE, "shell", "nmp_mp_hub.html"), "rb").read()
            elif n.startswith("assets/default_args") and n.endswith(".txt"):
                data = patch_args(data.decode())
            store = n == "resources.arsc" or n.endswith((".png", ".jpg", ".mp3", ".zip"))
            zout.writestr(zipfile.ZipInfo(n, item.date_time), data,
                          zipfile.ZIP_STORED if store else zipfile.ZIP_DEFLATED)
        zout.write(libq, "lib/arm64-v8a/libq.so")
        zout.write(os.path.join(HERE, "saves", "SGTA412"), "assets/Resources/saves/SGTA412")
    print("wrote", out)

if __name__ == "__main__":
    main()
