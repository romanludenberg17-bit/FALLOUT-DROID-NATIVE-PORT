#!/usr/bin/env python3
"""
Fallout Droid NV Native — single-command build, patch, verify & sign.

    python3 build_fnv_native.py

Steps
  1. classes2.dex      <- gen_classes2.py (pure-Python DEX generator)
  2. libfnv.so         <- orig/libfnv.so.xz + native/fnv_stubs.S (code cave)
  3. AndroidManifest   <- versionName / versionCode (proper AXML string pool rewrite)
  4. APK               <- sign_apk.py (v1 + v2, .signing_keys/release.key.pem)

All patches to libfnv.so are checked against a whitelist of byte ranges: any
change outside the expected hook sites / cave / strings aborts the build.
"""
import json, lzma, os, shutil, struct, subprocess, sys, tempfile, zipfile
from pathlib import Path

# ---------------------------------------------------------------- version ----
VERSION_NAME = "1.3.0"
VERSION_CODE = 26

BASE = Path(__file__).resolve().parent
WORK = BASE / "FalloutNVNativeWork"
NATIVE = BASE / "native"
OUT_DIR = BASE / "output"
OUT_APK = OUT_DIR / "Fallout Droid NV Native.apk"
OUT_ZIP = OUT_DIR / "Fallout Droid NV Native.zip"
KEYS = BASE / ".signing_keys"

# --------------------------------------------------------- libfnv layout ----
CAVE_START = 0x53FB664          # dead FNV_INPUT_SCRIPT handler (entry NOP'ed)
CAVE_END = 0x53FB9A0            # 0x53fb9a0..0x53fb9c8 is LIVE (stack_chk_fail + QUIT_AFTER exit)
LIVE_GUARD = (0x53FB9A0, 0x53FBA74)   # must stay byte-identical to the original

# The audio stub keeps its state in a small extension of .bss (the last
# PT_LOAD); 0x58d40a0..0x58d40c0 used by v1.2.15 belongs to live globals
# (MT19937 init flags, cached CTR_EL0 for __clear_cache, LSE-atomics flag).
BSS_EXTRA = 0x40
STATE_ADDR = 0x58D40C0          # == original end of .bss

SYMS = {                        # external symbols for the stubs (PLT / GOT / code)
    "getenv": 0x54BE1E0, "open": 0x54BE720, "fprintf": 0x54BE2B0,
    "memset": 0x54BE3A0, "dlsym": 0x54BEA60,
    "AAudioStreamBuilder_setPerformanceMode": 0x54BF080,
    "DSOUND_BUFFER_RESET": 0x5414110,
    "GOT_STDERR": 0x5593190,
    "STATE": STATE_ADDR,
}

# ------------------------------------------------------- A64 encoders -------
def enc_bl(pc, target):  return 0x94000000 | (((target - pc) >> 2) & 0x3FFFFFF)
def enc_b(pc, target):   return 0x14000000 | (((target - pc) >> 2) & 0x3FFFFFF)
def enc_adrp(pc, target, rd):
    imm = ((target & ~0xFFF) - (pc & ~0xFFF)) >> 12
    imm &= 0x1FFFFF
    return 0x90000000 | ((imm & 3) << 29) | ((imm >> 2) << 5) | rd
def enc_add_imm(rd, rn, imm12): return 0x91000000 | (imm12 << 10) | (rn << 5) | rd
def enc_movz_w(rd, imm16, hw=0): return 0x52800000 | (hw << 21) | (imm16 << 5) | rd
NOP = 0xD503201F

def rd32(buf, off): return struct.unpack_from("<I", buf, off)[0]


# ============================================================ 1. classes2 ====
def build_classes2():
    subprocess.run([sys.executable, str(BASE / "gen_classes2.py"), str(WORK / "classes2.dex")], check=True)
    print("[1/4] classes2.dex generated")


# ============================================================ 2. libfnv ======
def assemble_stubs():
    """Assemble native/fnv_stubs.S at CAVE_START. Returns (blob, symbols).
    Uses llvm-mc + ld.lld when available and refreshes the committed
    prebuilt (native/fnv_stubs.bin + .json); otherwise uses the prebuilt."""
    src = NATIVE / "fnv_stubs.S"
    pre_bin, pre_sym = NATIVE / "fnv_stubs.bin", NATIVE / "fnv_stubs.json"
    mc, lld, objcopy, nm = (shutil.which(t) for t in ("llvm-mc", "ld.lld", "llvm-objcopy", "llvm-nm"))
    if mc and lld and objcopy and nm:
        with tempfile.TemporaryDirectory() as td:
            obj, elf, binf = (os.path.join(td, n) for n in ("s.o", "s.elf", "s.bin"))
            subprocess.run([mc, "-triple=aarch64", "-mattr=+v8.2a", "-filetype=obj", str(src), "-o", obj], check=True)
            subprocess.run([lld, "-o", elf, obj, f"--section-start=.text={CAVE_START:#x}", "-e", "stub_audio"]
                           + [f"--defsym={k}={v:#x}" for k, v in SYMS.items()], check=True)
            subprocess.run([objcopy, "-O", "binary", "-j", ".text", elf, binf], check=True)
            blob = Path(binf).read_bytes()
            out = subprocess.run([nm, elf], check=True, capture_output=True, text=True).stdout
        syms = {}
        for line in out.splitlines():
            a, t, n = line.split()
            if t in "Tt" and (n.startswith("stub_") or n.startswith("str_") or n == "cave_end"):
                syms[n] = int(a, 16)
        pre_bin.write_bytes(blob)
        pre_sym.write_text(json.dumps({k: hex(v) for k, v in sorted(syms.items())}, indent=1) + "\n")
        how = "llvm-mc/ld.lld"
    else:
        blob = pre_bin.read_bytes()
        syms = {k: int(v, 16) for k, v in json.loads(pre_sym.read_text()).items()}
        how = "prebuilt native/fnv_stubs.bin (no LLVM toolchain found)"
    return blob, syms, how


def patch_libfnv():
    orig = lzma.decompress((BASE / "orig/libfnv.so.xz").read_bytes())
    so = bytearray(orig)
    allowed = []                                   # (start, end) byte ranges that may change

    def put32(off, val):
        struct.pack_into("<I", so, off, val)
        allowed.append((off, off + 4))

    blob, sym, how = assemble_stubs()
    end = CAVE_START + len(blob)
    assert sym["cave_end"] == end, "stub blob/symbol mismatch"
    assert end <= CAVE_END, f"code cave overflow: {end:#x} > {CAVE_END:#x}"
    so[CAVE_START:end] = blob
    allowed.append((CAVE_START, end))

    # --- hook sites (each original instruction verified before patching) ---
    hooks = [  # (site, expected original word, stub)
        (0x54130E0, 0x2A1F03E0, "stub_audio"),     # mov w0, wzr      (AAudio data callback tail)
        (0x5412B88, enc_bl(0x5412B88, SYMS["AAudioStreamBuilder_setPerformanceMode"]), "stub_builder"),
        (0x54134D0, enc_bl(0x54134D0, SYMS["memset"]), "stub_dscaps"),
        (0x5413CC8, 0xF94002A8, "stub_play"),      # ldr x8, [x21]
        (0x5414434, 0x1E2E1000, "stub_pan"),       # fmov s0, #1.0
    ]
    for site, want, name in hooks:
        assert rd32(orig, site) == want, f"unexpected original insn at {site:#x}"
        put32(site, enc_bl(site, sym[name]))

    # Mixer: skip the hard [-1,1] clamp (b.lt -> b); the limiter in stub_audio
    # needs the real peaks and guarantees |out| <= 1 itself.
    assert rd32(orig, 0x5413040) == 0x5400050B          # b.lt 0x54130e0
    put32(0x5413040, enc_b(0x5413040, 0x54130E0))

    # AAudio stream setup: performance NONE, usage MEDIA, content MUSIC
    # (from v1.2.13-15), buffer size = capacity (was 3 * burst).
    for site, want, new in [
        (0x5412B84, enc_movz_w(1, 12), enc_movz_w(1, 10)),   # PERFORMANCE_MODE_NONE
        (0x5412B9C, enc_movz_w(1, 14), enc_movz_w(1, 1)),    # USAGE_MEDIA
        (0x5412BA8, enc_movz_w(1, 4),  enc_movz_w(1, 2)),    # CONTENT_TYPE_MUSIC
        (0x5412C10, 0x0B130661,        enc_movz_w(1, 1, 1)), # add w1,w19,w19,lsl#1 -> mov w1,#0x10000
    ]:
        assert rd32(orig, site) == want, f"unexpected original insn at {site:#x}"
        put32(site, new)
    # startup log line now identifies the patched audio path
    assert rd32(orig, 0x5412C48) == enc_adrp(0x5412C48, 0x9B510, 1)
    put32(0x5412C48, enc_adrp(0x5412C48, sym["str_aaudio_log"], 1))
    put32(0x5412C4C, enc_add_imm(1, 1, sym["str_aaudio_log"] & 0xFFF))

    # DirectSound3D listener IID fix (from v1.2.x)
    put32(0x5413664, 0x529F508A)
    put32(0x5413674, 0x72A4F34A)

    # FNV_INPUT_SCRIPT handler entry -> NOP (its body is our code cave)
    assert rd32(orig, 0x53FB218) == 0xB5002260
    put32(0x53FB218, NOP)

    # --- grow the last PT_LOAD (.bss) by BSS_EXTRA for the stub state ---
    e_phoff, = struct.unpack_from("<Q", so, 0x20)
    e_phentsize, e_phnum = struct.unpack_from("<HH", so, 0x36)
    loads = [i for i in range(e_phnum)
             if struct.unpack_from("<I", so, e_phoff + i * e_phentsize)[0] == 1]
    ph = e_phoff + loads[-1] * e_phentsize
    p_vaddr, = struct.unpack_from("<Q", so, ph + 0x10)
    p_memsz, = struct.unpack_from("<Q", so, ph + 0x28)
    assert p_vaddr + p_memsz == STATE_ADDR, "unexpected .bss end"
    struct.pack_into("<Q", so, ph + 0x28, p_memsz + BSS_EXTRA)
    allowed.append((ph + 0x28, ph + 0x30))
    e_shoff, = struct.unpack_from("<Q", so, 0x28)
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from("<HHH", so, 0x3A)
    shstr_off, = struct.unpack_from("<Q", so, e_shoff + e_shstrndx * e_shentsize + 0x18)
    for i in range(e_shnum):
        sh = e_shoff + i * e_shentsize
        name = so[shstr_off + struct.unpack_from("<I", so, sh)[0]:].split(b"\0", 1)[0]
        if name == b".bss":
            addr, = struct.unpack_from("<Q", so, sh + 0x10)
            size, = struct.unpack_from("<Q", so, sh + 0x20)
            assert addr + size == STATE_ADDR
            struct.pack_into("<Q", so, sh + 0x20, size + BSS_EXTRA)
            allowed.append((sh + 0x20, sh + 0x28))

    # --- verification ---
    assert so[LIVE_GUARD[0]:LIVE_GUARD[1]] == orig[LIVE_GUARD[0]:LIVE_GUARD[1]], "live code after cave touched!"
    assert len(so) == len(orig)
    bad, n = [], len(so)
    mv_a, mv_b = memoryview(so), memoryview(orig)
    step = 1 << 16
    for blk in range(0, n, step):
        if mv_a[blk:blk + step] != mv_b[blk:blk + step]:
            for j in range(blk, min(blk + step, n)):
                if so[j] != orig[j] and not any(a <= j < b for a, b in allowed):
                    bad.append(j)
    assert not bad, f"unexpected libfnv.so changes at {[hex(x) for x in bad[:8]]}"

    out = WORK / "lib/arm64-v8a/libfnv.so"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(so)
    print(f"[2/4] libfnv.so patched ({how}); cave {CAVE_START:#x}..{end:#x} "
          f"({end - CAVE_START}/{CAVE_END - CAVE_START} B), .bss +{BSS_EXTRA:#x} @ {STATE_ADDR:#x}")
    return so


# ============================================================ 3. manifest ====
def axml_set_version(path, name, code):
    """Rewrite android:versionName / versionCode in a binary AndroidManifest.xml.
    The versionName string is replaced in the (UTF-16) string pool, which is
    rebuilt so the new value may have any length."""
    mf = bytearray(path.read_bytes())
    assert struct.unpack_from("<HHI", mf, 0)[0] == 0x0003
    sp = 8
    ctype, hsz, csz = struct.unpack_from("<HHI", mf, sp)
    assert ctype == 0x0001
    scount, stcount, flags, sstart, ststart = struct.unpack_from("<IIIII", mf, sp + 8)
    assert not flags & 0x100, "UTF-8 string pool not supported"
    assert stcount == 0, "styled strings not supported"
    offs = struct.unpack_from(f"<{scount}I", mf, sp + 28)
    strs = []
    for o in offs:
        p = sp + sstart + o
        ln, = struct.unpack_from("<H", mf, p)
        assert ln < 0x8000
        strs.append(mf[p + 2:p + 2 + ln * 2].decode("utf-16le"))
    rest = bytes(mf[sp + csz:])

    # locate the <manifest> element attributes (in the original layout)
    def find_manifest_attrs(buf, base, names):
        pos = base
        while pos + 8 <= len(buf):
            ct, hs, cs = struct.unpack_from("<HHI", buf, pos)
            if ct == 0x0102:
                nm, = struct.unpack_from("<I", buf, pos + 20)
                astart, asz, acnt = struct.unpack_from("<HHH", buf, pos + 24)
                if names[nm] == "manifest":
                    return [(pos + 16 + astart + k * asz) for k in range(acnt)]
            pos += cs
        raise RuntimeError("<manifest> not found")

    attrs = find_manifest_attrs(mf, sp + csz, strs)
    vn_idx = None
    for ao in attrs:
        aname, araw = struct.unpack_from("<II", mf, ao + 4)
        if strs[aname] == "versionName":
            vn_idx = araw
            old_name = strs[araw]
        elif strs[aname] == "versionCode":
            old_code = struct.unpack_from("<i", mf, ao + 16)[0]
            struct.pack_into("<i", mf, ao + 16, code)
    assert vn_idx is not None
    rest = bytes(mf[sp + csz:])
    strs[vn_idx] = name

    # rebuild string pool
    data = bytearray()
    new_offs = []
    for s in strs:
        new_offs.append(len(data))
        u = s.encode("utf-16le")
        data += struct.pack("<H", len(s)) + u + b"\0\0"
    while len(data) % 4:
        data += b"\0"
    hdr_size = 28
    new_sstart = hdr_size + 4 * scount
    pool = bytearray(struct.pack("<HHI", 0x0001, hdr_size, new_sstart + len(data)))
    pool += struct.pack("<IIIII", scount, 0, flags, new_sstart, 0)
    pool += struct.pack(f"<{scount}I", *new_offs)
    pool += data
    out = bytearray(mf[:8]) + pool + rest
    struct.pack_into("<I", out, 4, len(out))
    path.write_bytes(out)
    print(f"[3/4] Manifest: versionName {old_name} -> {name}, versionCode {old_code} -> {code}")


# ============================================================ 4. sign ========
def sign_and_package():
    for f in ("release.key.pem", "release.cert.pem", "release.cert.der"):
        assert (KEYS / f).exists(), f"missing signing key {KEYS / f} (refusing to generate a new one)"
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(BASE / "sign_apk.py"), str(WORK), str(OUT_APK)], check=True)
    data = OUT_APK.read_bytes()
    assert b"APK Sig Block 42" in data
    with zipfile.ZipFile(OUT_APK) as z:
        assert z.testzip() is None
        names = set(z.namelist())
        assert {"AndroidManifest.xml", "classes.dex", "classes2.dex", "lib/arm64-v8a/libfnv.so"} <= names
    with zipfile.ZipFile(OUT_ZIP, "w", compression=zipfile.ZIP_STORED) as z:
        z.write(OUT_APK, OUT_APK.name)
    with zipfile.ZipFile(OUT_ZIP) as z:
        assert z.testzip() is None
    print(f"[4/4] Signed {OUT_APK.name} ({len(data) / 1048576:.2f} MB) + {OUT_ZIP.name}")


if __name__ == "__main__":
    build_classes2()
    patch_libfnv()
    axml_set_version(WORK / "AndroidManifest.xml", VERSION_NAME, VERSION_CODE)
    sign_and_package()
    print(f"OK: Fallout Droid NV Native v{VERSION_NAME} (versionCode {VERSION_CODE})")
