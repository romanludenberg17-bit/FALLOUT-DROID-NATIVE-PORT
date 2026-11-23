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
VERSION_NAME = "1.3.4"
VERSION_CODE = 30

BASE = Path(__file__).resolve().parent
WORK = BASE / "FalloutNVNativeWork"
NATIVE = BASE / "native"
OUT_DIR = BASE / "output"
OUT_APK = OUT_DIR / "Fallout Droid NV Native.apk"
OUT_ZIP = OUT_DIR / "Fallout Droid NV Native.zip"
KEYS = BASE / ".signing_keys"

# --------------------------------------------------------- libfnv layout ----
# All our native code lives in a NEW R+X PT_LOAD segment appended to the file
# (the unused PT_NOTE program header is converted). The old code cave
# 0x53fb664..0x53fb9a0 is no longer touched (0x53fb9a0.. is live code anyway).
FNVX_VADDR = 0x58E0000          # > end of .bss (0x58d40c0 + BSS_EXTRA), 16 KiB aligned
SEG_ALIGN = 0x4000              # 16 KiB page compatible

# RW state for the stubs: extension of .bss (the last PT_LOAD). The original
# .bss ends at 0x58d40c0; 0x58d40a0..0x58d40c0 are live globals (MT19937 init
# flags, cached CTR_EL0 for __clear_cache, LSE-atomics flag) - never touch.
BSS_EXTRA = 0x480
STATE_ADDR = 0x58D40C0          # == original end of .bss

SYMS = {                        # external symbols for the stubs (PLT / GOT / code / data)
    "getenv": 0x54BE1E0, "open": 0x54BE720, "fprintf": 0x54BE2B0,
    "memset": 0x54BE3A0, "dlsym": 0x54BEA60,
    "AAudioStreamBuilder_setPerformanceMode": 0x54BF080,
    "DSOUND_BUFFER_RESET": 0x5414110,     # IDirectSoundBuffer status sync (frees finished voice)
    "voice_create": 0x54125B0,
    "UPDATE_VOLUME": 0x5414258,           # dsound_update_volume(obj)
    "DS3D_DISTFACTOR": 0x559A498,         # listener DistanceFactor (float)
    "DS3D_ROLLOFF": 0x559A49C,            # listener RolloffFactor (float)
    "VOICES": 0x58C5F18,                  # 256 x 0x58 mixer voices
    "GOT_STDERR": 0x5593190,
    "GOT_GUESTMEM": 0x55931A8,            # -> host base of guest memory
    "GUEST_BASE": 0x5646868,              # .bss variable: host base of guest memory
    "FMT_IMP": 0x95FE4,                   # "[imp] %s!%s(%#x, %#x, %#x, %#x)\n"
    "FMT_IMP_RET": 0x99C33,               # "[imp]   -> %#x\n"
    "STATE": STATE_ADDR,
}

# ------------------------------------------------------- A64 encoders -------
def enc_bl(pc, target):  return 0x94000000 | (((target - pc) >> 2) & 0x3FFFFFF)
def enc_b(pc, target):   return 0x14000000 | (((target - pc) >> 2) & 0x3FFFFFF)
def enc_adrp(pc, target, rd):
    imm = ((target & ~0xFFF) - (pc & ~0xFFF)) >> 12
    imm &= 0x1FFFFF
    return 0x90000000 | ((imm & 3) << 29) | ((imm >> 2) << 5) | rd
def enc_cbnz_w(pc, target, rt): return 0x35000000 | ((((target - pc) >> 2) & 0x7FFFF) << 5) | rt
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
    """Assemble native/fnv_stubs.S at FNVX_VADDR. Returns (blob, symbols).
    Uses llvm-mc + ld.lld when available and refreshes the committed
    prebuilt (native/fnv_stubs.bin + .json); otherwise uses the prebuilt."""
    src = NATIVE / "fnv_stubs.S"
    pre_bin, pre_sym = NATIVE / "fnv_stubs.bin", NATIVE / "fnv_stubs.json"
    mc, lld, objcopy, nm = (shutil.which(t) for t in ("llvm-mc", "ld.lld", "llvm-objcopy", "llvm-nm"))
    if mc and lld and objcopy and nm:
        with tempfile.TemporaryDirectory() as td:
            obj, elf, binf = (os.path.join(td, n) for n in ("s.o", "s.elf", "s.bin"))
            subprocess.run([mc, "-triple=aarch64", "-mattr=+v8.2a", "-filetype=obj", str(src), "-o", obj], check=True)
            subprocess.run([lld, "-o", elf, obj, f"--section-start=.text={FNVX_VADDR:#x}", "-e", "stub_audio"]
                           + [f"--defsym={k}={v:#x}" for k, v in SYMS.items()], check=True)
            subprocess.run([objcopy, "-O", "binary", "-j", ".text", elf, binf], check=True)
            blob = Path(binf).read_bytes()
            out = subprocess.run([nm, elf], check=True, capture_output=True, text=True).stdout
        syms = {}
        for line in out.splitlines():
            a, t, n = line.split()
            if t in "Tt" and (n.startswith(("stub_", "str_", "trace_")) or n == "fnvx_end"):
                syms[n] = int(a, 16)
        pre_bin.write_bytes(blob)
        pre_sym.write_text(json.dumps({k: hex(v) for k, v in sorted(syms.items())}, indent=1) + "\n")
        how = "llvm-mc/ld.lld"
    else:
        blob = pre_bin.read_bytes()
        syms = {k: int(v, 16) for k, v in json.loads(pre_sym.read_text()).items()}
        how = "prebuilt native/fnv_stubs.bin (no LLVM toolchain found)"
    return blob, syms, how


def add_exec_segment(so, vaddr, blob):
    """Append blob as a new R+X PT_LOAD by converting the PT_NOTE phdr.
    Returns the list of changed header byte ranges."""
    e_phoff, = struct.unpack_from("<Q", so, 0x20)
    e_phentsize, e_phnum = struct.unpack_from("<HH", so, 0x36)
    phs = [e_phoff + i * e_phentsize for i in range(e_phnum)]
    types = [struct.unpack_from("<I", so, p)[0] for p in phs]
    notes = [p for p, t in zip(phs, types) if t == 4]
    assert len(notes) == 1, "expected exactly one PT_NOTE"
    loads = [p for p, t in zip(phs, types) if t == 1]
    last = loads[-1]
    lv, = struct.unpack_from("<Q", so, last + 0x10)
    lm, = struct.unpack_from("<Q", so, last + 0x28)
    assert vaddr >= lv + lm and vaddr % SEG_ALIGN == 0
    assert phs.index(notes[0]) > phs.index(last), "new PT_LOAD must come after the others"
    off = (len(so) + SEG_ALIGN - 1) // SEG_ALIGN * SEG_ALIGN
    so.extend(b"\0" * (off - len(so)))
    so.extend(blob)
    # p_type, p_flags(R|X), p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align
    struct.pack_into("<IIQQQQQQ", so, notes[0], 1, 5, off, vaddr, vaddr, len(blob), len(blob), SEG_ALIGN)
    return [(notes[0], notes[0] + e_phentsize)], off


def patch_libfnv():
    orig = lzma.decompress((BASE / "orig/libfnv.so.xz").read_bytes())
    so = bytearray(orig)
    allowed = []                                   # (start, end) byte ranges that may change

    def put32(off, val):
        struct.pack_into("<I", so, off, val)
        allowed.append((off, off + 4))

    blob, sym, how = assemble_stubs()
    assert sym["fnvx_end"] == FNVX_VADDR + len(blob), "stub blob/symbol mismatch"

    # --- hook sites (each original instruction verified before patching) ---
    hooks = [  # (site, expected original word, stub)
        (0x54130E0, 0x2A1F03E0, "stub_audio"),     # mov w0, wzr      (AAudio data callback tail)
        (0x5412B88, enc_bl(0x5412B88, SYMS["AAudioStreamBuilder_setPerformanceMode"]), "stub_builder"),
        (0x54134D0, enc_bl(0x54134D0, SYMS["memset"]), "stub_dscaps"),
        (0x5413CC8, 0xF94002A8, "stub_play"),      # ldr x8, [x21]
        (0x5414434, 0x1E2E1000, "stub_pan"),       # fmov s0, #1.0
        (0x5413D64, enc_bl(0x5413D64, SYMS["voice_create"]), "stub_vcreate"),
        (0x5413D7C, enc_bl(0x5413D7C, SYMS["UPDATE_VOLUME"]), "stub_postplay"),
        (0x53E4A50, enc_cbnz_w(0x53E4A50, 0x53E4A74, 8), "trace_pre"),     # FNV_TRACE filter
        (0x53E4A60, enc_cbnz_w(0x53E4A60, 0x53E4AC8, 8), "trace_post"),
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

    # DirectSound3D listener/buffer IID fix (from v1.2.x): 0x279f9a84 -> 0x279afa84
    assert rd32(orig, 0x5413664) == enc_movz_w(10, 0x9A84) and rd32(orig, 0x5413674) == 0x72A4F3EA
    put32(0x5413664, enc_movz_w(10, 0xFA84))
    put32(0x5413674, 0x72A4F34A)                         # movk w10, #0x279a, lsl #16

    # --- grow the last PT_LOAD (.bss) by BSS_EXTRA for the stub state ---
    e_phoff, = struct.unpack_from("<Q", so, 0x20)
    e_phentsize, e_phnum = struct.unpack_from("<HH", so, 0x36)
    loads = [i for i in range(e_phnum)
             if struct.unpack_from("<I", so, e_phoff + i * e_phentsize)[0] == 1]
    ph = e_phoff + loads[-1] * e_phentsize
    p_vaddr, = struct.unpack_from("<Q", so, ph + 0x10)
    p_memsz, = struct.unpack_from("<Q", so, ph + 0x28)
    assert p_vaddr + p_memsz == STATE_ADDR, "unexpected .bss end"
    assert STATE_ADDR + BSS_EXTRA <= FNVX_VADDR
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

    # --- new executable segment with all our code ---
    hdr_ranges, seg_off = add_exec_segment(so, FNVX_VADDR, blob)
    allowed += hdr_ranges

    # --- verification: every changed byte of the original image is whitelisted ---
    n = len(orig)
    bad = []
    step = 1 << 16
    mv_a, mv_b = memoryview(so)[:n], memoryview(orig)
    for blk in range(0, n, step):
        if mv_a[blk:blk + step] != mv_b[blk:blk + step]:
            for j in range(blk, min(blk + step, n)):
                if so[j] != orig[j] and not any(a <= j < b for a, b in allowed):
                    bad.append(j)
    assert not bad, f"unexpected libfnv.so changes at {[hex(x) for x in bad[:8]]}"
    assert so[0x53FB000:0x53FC000] == orig[0x53FB000:0x53FC000], "old code cave must stay original"

    out = WORK / "lib/arm64-v8a/libfnv.so"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(so)
    print(f"[2/4] libfnv.so patched ({how}); .fnvx R+X segment {FNVX_VADDR:#x} "
          f"({len(blob)} B @ file {seg_off:#x}), .bss +{BSS_EXTRA:#x} @ {STATE_ADDR:#x}")
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
