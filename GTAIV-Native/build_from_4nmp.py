#!/usr/bin/env python3
"""GTA IV Native APK = 4NMP v502 UI shell (launcher, design, touch editor, MP hub)
+ the native engine built from LibertyRecompAndroid (gta4-recomp).

usage: build_from_4nmp.py <4NMP-ui-only.apk> <engine jniLibs dir> <engine assets dir> <out unsigned.apk>

Patches applied to the shell:
  nmp_mp_hub.html  replaced by shell/nmp_mp_hub.html (working MP / co-op hub).
  classes2.dex  NmpCoopBridge forwards to the engine's LAN discovery port 3076.
  classes2.dex  NmpLauncherActivity.onClick(id 1001 "ИГРАТЬ"): the "engine removed"
                toast becomes mPlayBtn.performClick() (the original Play button, the
                same call the MP hub's launch_coop already makes).
  Manifest      package -> com.rommy.gtaiv, + ACCESS_WIFI_STATE and
                CHANGE_WIFI_MULTICAST_STATE (LAN co-op discovery).
  resources     app label -> "GTA IV Native".
"""
import hashlib
import os
import struct
import sys
import zipfile
import zlib

PACKAGE = "com.rommy.gtaiv"
VERSION_CODE = 614          # above the 4NMP shell's 502 so updates always replace it
VERSION_NAME = "1.1.4-native"
LABEL = "GTA IV Native"
EXTRA_PERMISSIONS = ["android.permission.ACCESS_WIFI_STATE",
                     "android.permission.CHANGE_WIFI_MULTICAST_STATE"]

# ---------------------------------------------------------------- string pool
def read_pool(buf, off):
    typ, hsz, size = struct.unpack_from("<HHI", buf, off)
    assert typ == 0x0001
    count, styles, flags, sstart, ystart = struct.unpack_from("<IIIII", buf, off + 8)
    assert styles == 0, "styled string pools are not supported"
    utf8 = bool(flags & 0x100)
    offs = struct.unpack_from("<%dI" % count, buf, off + hsz)
    out = []
    for o in offs:
        p = off + sstart + o
        if utf8:
            n = buf[p]; p += 1
            if n & 0x80: p += 1
            n = buf[p]; p += 1
            if n & 0x80: n = ((n & 0x7F) << 8) | buf[p]; p += 1
            out.append(buf[p:p + n].decode("utf-8"))
        else:
            n = struct.unpack_from("<H", buf, p)[0]; p += 2
            if n & 0x8000: n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]; p += 2
            out.append(buf[p:p + 2 * n].decode("utf-16-le"))
    return out, utf8, flags, size


def write_pool(strings, utf8, flags):
    data = bytearray(); offs = []
    for s in strings:
        offs.append(len(data))
        if utf8:
            b = s.encode("utf-8"); u = len(s)
            for n in (u, len(b)):
                data += bytes([0x80 | (n >> 8), n & 0xFF]) if n > 0x7F else bytes([n])
            data += b + b"\0"
        else:
            b = s.encode("utf-16-le"); n = len(b) // 2
            data += struct.pack("<H", n) + b + b"\0\0"
    while len(data) % 4: data += b"\0"
    hsz = 28
    sstart = hsz + 4 * len(strings)
    body = struct.pack("<%dI" % len(strings), *offs) + bytes(data)
    head = struct.pack("<HHIIIIII", 0x0001, hsz, hsz + len(body), len(strings), 0, flags, sstart, 0)
    return head + body

# ---------------------------------------------------------------- manifest
def patch_manifest(axml):
    buf = bytes(axml)
    assert struct.unpack_from("<HH", buf, 0)[0] == 0x0003
    strings, utf8, flags, pool_size = read_pool(buf, 8)
    strings = [PACKAGE + s[len("com.rommy_nmp"):] if s == "com.rommy_nmp" or s.startswith("com.rommy_nmp.") else s
               for s in strings]
    perm_idx = []
    for p in EXTRA_PERMISSIONS:
        if p not in strings: strings.append(p)
        perm_idx.append(strings.index(p))
    rest = bytearray(buf[8 + pool_size:])
    # find the INTERNET uses-permission element (start + end) and clone it
    internet = strings.index("android.permission.INTERNET")
    off = 0; clone = None; insert_at = None
    while off < len(rest):
        typ, hsz, size = struct.unpack_from("<HHI", rest, off)
        if typ == 0x0102:
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", rest, off + 16 + 8)
            for i in range(attr_count):
                a = off + 16 + attr_start + i * attr_size
                raw = struct.unpack_from("<I", rest, a + 8)[0]
                if raw == internet:
                    end = off + size
                    etyp, _, esize = struct.unpack_from("<HHI", rest, end)
                    assert etyp == 0x0103
                    clone = (off, size, end, esize, a - off)
                    insert_at = end + esize
        off += size
    assert clone, "INTERNET permission element not found"
    s_off, s_size, e_off, e_size, attr_rel = clone
    start = bytes(rest[s_off:s_off + s_size]); end = bytes(rest[e_off:e_off + e_size])
    extra = bytearray()
    for idx in perm_idx:
        el = bytearray(start)
        struct.pack_into("<I", el, attr_rel + 8, idx)       # raw value
        struct.pack_into("<I", el, attr_rel + 16, idx)      # typed value data
        extra += el + end
    rest[insert_at:insert_at] = extra
    # versionCode / versionName (android attr ids 0x0101021b / 0x0101021c)
    if VERSION_NAME not in strings: strings.append(VERSION_NAME)
    name_index = strings.index(VERSION_NAME)
    typ, _, size = struct.unpack_from("<HHI", rest, 0)
    ids = list(struct.unpack_from("<%dI" % ((size - 8) // 4), rest, 8)) if typ == 0x0180 else []
    off = 0
    while off < len(rest):
        typ, hsz, size = struct.unpack_from("<HHI", rest, off)
        if typ == 0x0102:
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", rest, off + 16 + 8)
            for i in range(attr_count):
                a = off + 16 + attr_start + i * attr_size
                name = struct.unpack_from("<I", rest, a + 4)[0]
                rid = ids[name] if name < len(ids) else 0
                if rid == 0x0101021b:
                    struct.pack_into("<I", rest, a + 16, VERSION_CODE)
                elif rid == 0x0101021c:
                    struct.pack_into("<I", rest, a + 8, name_index)
                    struct.pack_into("<I", rest, a + 16, name_index)
            break  # <manifest> is the first element
        off += size
    pool = write_pool(strings, utf8, flags)
    total = 8 + len(pool) + len(rest)
    return struct.pack("<HHI", 0x0003, 8, total) + pool + bytes(rest)

# ---------------------------------------------------------------- resources.arsc label
def patch_arsc(arsc):
    buf = bytes(arsc)
    typ, hsz, size, pkgs = struct.unpack_from("<HHII", buf, 0)
    assert typ == 0x0002
    strings, utf8, flags, pool_size = read_pool(buf, hsz)
    strings[strings.index("4NMP")] = LABEL
    pool = write_pool(strings, utf8, flags)
    tail = buf[hsz + pool_size:]
    return struct.pack("<HHII", 0x0002, hsz, hsz + len(pool) + len(tail), pkgs) + pool + tail

# ---------------------------------------------------------------- classes2.dex
def patch_dex(dex):
    d = bytearray(dex)
    # NmpLauncherActivity.onClick: 0008..0012 (11 code units) of the id==1001 branch
    i = d.find(bytes.fromhex("1a01e600"))
    assert i > 0 and d.count(bytes.fromhex("1a01e600")) == 1, "unexpected classes2.dex"
    assert d[i + 20:i + 22] == b"\x0e\x00", "unexpected onClick layout"
    new = bytes.fromhex(
        "54321300"      # iget-object v2, v3, mPlayBtn (field@0013)
        "38020600"      # if-eqz v2, +6
        "6e1024000200"  # invoke-virtual {v2}, View.performClick (method@0024)
        "0e00"          # return-void
        "0000" "0000"   # nop nop
        "0e00")         # return-void
    assert len(new) == 22
    d[i:i + 22] = new
    # NmpCoopBridge.run: frames from the remote peer go to the engine's LAN
    # discovery port (3076), not to the game traffic port 3074.
    bridge = bytes.fromhex("1306020c")          # const/16 v6, 3074
    assert d.count(bridge) == 1, "unexpected NmpCoopBridge layout"
    j = d.find(bridge)
    d[j:j + 4] = bytes.fromhex("1306040c")      # const/16 v6, 3076
    # NmpLauncherActivity.onCreate deletes args.txt once per marker name: a new
    # marker (same length, same sort position) resets args written by the 4NMP
    # shell profile (30 FPS cap, reduced quality) to this engine's profile.
    marker = b"nmp_v502_clean"
    assert d.count(marker) == 1
    j = d.find(marker)
    d[j:j + len(marker)] = b"nmp_v612_clean"
    d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
    struct.pack_into("<I", d, 8, zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)
    return bytes(d)


def main():
    src, jnilibs, assets, out = sys.argv[1:5]
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        names = set()
        overridden = set()
        for root, _, files in os.walk(assets):
            for f in files:
                overridden.add("assets/" + os.path.relpath(os.path.join(root, f), assets).replace(os.sep, "/"))
        for item in zin.infolist():
            n = item.filename
            if n.startswith("assets/Resources/") and n in overridden:
                continue  # the engine's copy (e.g. GTA-style touch icons) wins
            if n.startswith("META-INF/") and n.split("/")[-1].upper().endswith((".SF", ".RSA", ".MF", ".EC", ".DSA")):
                continue
            if n.startswith("lib/"):
                continue  # replaced by the engine's libraries
            if n in ("assets/default_args.txt", "assets/default_args_g99.txt", "assets/default_args_mali.txt"):
                continue  # replaced by the engine's profiles
            data = zin.read(n)
            if n == "AndroidManifest.xml": data = patch_manifest(data)
            elif n == "resources.arsc": data = patch_arsc(data)
            elif n == "classes2.dex": data = patch_dex(data)
            elif n == "assets/nmp_mp_hub.html":
                with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "shell", "nmp_mp_hub.html"), "rb") as hub:
                    data = hub.read()  # working LAN / co-op hub instead of the "in development" page
            store = n == "resources.arsc" or n.endswith((".png", ".jpg", ".mp3"))
            zout.writestr(zipfile.ZipInfo(n, item.date_time), data,
                          zipfile.ZIP_STORED if store else zipfile.ZIP_DEFLATED)
            names.add(n)
        for so in sorted(os.listdir(jnilibs)):
            zout.write(os.path.join(jnilibs, so), "lib/arm64-v8a/" + so)
        for root, _, files in os.walk(assets):
            for f in sorted(files):
                path = os.path.join(root, f)
                name = "assets/" + os.path.relpath(path, assets).replace(os.sep, "/")
                if name in names: continue
                zout.write(path, name, zipfile.ZIP_STORED if f.endswith(".zip") else zipfile.ZIP_DEFLATED)
    print("wrote", out)


if __name__ == "__main__":
    main()
