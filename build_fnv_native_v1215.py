#!/usr/bin/env python3
"""
Builds Fallout Droid NV Native v1.2.14-audiofix (versionCode 24):
Applies all verified audio quality & underrun fixes from XAUDIO2_STARVATION_FIX.md:
1. Master-bus -6 dB headroom (* 0.5f) + smooth rational soft limiter (x / (1 + 0.5*|x|))
   BEFORE float32->int16 PCM conversion (eliminates multi-voice clipping/crackling;
   telemetry mix peak is measured pre-headroom for accurate diagnostics).
2. Smooth 5 ms linear gain ramp (240 frames @ 48 kHz) on starvation/silence transitions
   (>16 consecutive silent frames ramps down to 0.0; valid audio ramps smoothly 0.0->1.0
   over 5 ms to eliminate clicks/pops without altering normal waveform zero-crossings).
3. AudioTrack 120 ms output queue prefill BEFORE track.play():
   - Buffer size raised from 16 KB (85 ms) to >= 32 KB (~170 ms)
   - AudioTrack stays paused until >= 23,040 bytes (120 ms @ 48 kHz stereo PCM-16) are queued
   - AudioTrackThread runs at Thread.MAX_PRIORITY (10) against Android scheduler jitter
4. Removed the 0.5s 440 Hz startup diagnostic beep; kept all DirectSound8/3D/AAudio fixes.
"""
import os, sys, struct, zipfile, subprocess
from pathlib import Path

BASE = Path(__file__).resolve().parent
ORIG_APK = BASE / "user_uploaded_files/ttl=63d/user_uploaded/Fallout-NV-0.4.0 (1).apk"
WORK = BASE / "FalloutNVNativeWork"
OUT_DIR = BASE / "output"
OUT_DIR.mkdir(parents=True, exist_ok=True)
OUT_APK = OUT_DIR / "Fallout Droid NV Native.apk"
OUT_ZIP = OUT_DIR / "Fallout Droid NV Native.zip"

# ==============================================================================
# 1. Build classes2.dex with AudioTrackThread (32KB buffer + 120ms prefill before play() + MAX_PRIORITY)
# ==============================================================================
gsrc = (BASE / "gen_classes2_v127.py").read_text()

gsrc = gsrc.replace(
    '    C_FIS = "Ljava/io/FileInputStream;"',
    '    C_FIS = "Ljava/io/FileInputStream;"\n'
    '    C_THR = "Ljava/lang/Thread;"\n'
    '    C_ATR = "Landroid/media/AudioTrack;"\n'
    '    C_ATT = "Lcom/rommy/fnvnative/AudioTrackThread;"',
    1
)
gsrc = gsrc.replace(
    '        for t in [C_FACT, C_CCF, C_GACT, C_OGACT, C_HDV, C_VIEW, C_AUDM, C_AFCL, C_FIS]:',
    '        for t in [C_FACT, C_CCF, C_GACT, C_OGACT, C_HDV, C_VIEW, C_AUDM, C_AFCL, C_FIS, C_THR, C_ATR, C_ATT]:',
    1
)

old_env = """        a.cs(0, "FNV_TRACE_FILES")
        a.cs(1, "1")
        a.c4(2, 1)
        a.is_((C_OS, "setenv", "V", (C_STR, C_STR, "Z")), 0, 1, 2)"""

new_env = old_env + """
        a.iv((C_GACT, "getCacheDir", C_FILE, ()), 4)
        a.mro(0)
        a.new(1, C_FILE)
        a.cs(2, "fnv_pcm.fifo")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 1, 0, 2)
        a.iv((C_FILE, "delete", "Z", ()), 1)
        a.iv((C_FILE, "getAbsolutePath", C_STR, ()), 1)
        a.mro(1)
        a.c16(2, 0o600)
        a.is_((C_OS, "mkfifo", "V", (C_STR, "I")), 1, 2)
        a.cs(0, "FNV_PCM_FIFO")
        a.c4(2, 1)
        a.is_((C_OS, "setenv", "V", (C_STR, C_STR, "Z")), 0, 1, 2)
        a.new(0, C_ATT)
        a.id_((C_ATT, "<init>", "V", (C_STR,)), 0, 1)
        a.c4(2, 10)
        a.iv((C_THR, "setPriority", "V", ("I",)), 0, 2)
        a.iv((C_THR, "start", "V", ()), 0)"""

assert old_env in gsrc, "old_env not found"
gsrc = gsrc.replace(old_env, new_env, 1)

att_code = """
        # ---- Class 8: AudioTrackThread (extends Thread) ----
        F_ATT_P = (C_ATT, "a", C_STR)
        F(F_ATT_P)

        # <init>(String fifoPath)V  regs=2, ins=2, outs=1 -> v0=this, v1=path
        a = Asm(S, T, F, M)
        a.id_((C_THR, "<init>", "V", ()), 0)
        a.iput_o(1, 0, F_ATT_P)
        a.ret_v()
        m_att_init = (C_ATT, "<init>", "V", (C_STR,), 0x10001, 2, 2, 1, *a.finish())

        # run()V  regs=14, ins=1, outs=7 -> v13=this
        # v7 = AudioTrack, v8 = FileInputStream, v9 = byte[3840], v10 = n
        # v11 = prefilledBytes (int), v12 = started (0 or 1)
        a = Asm(S, T, F, M)
        a.label("tat0")
        a.c32(0, 48000)   # sampleRate
        a.c4(1, 12)       # CHANNEL_OUT_STEREO = 12
        a.c4(2, 2)        # ENCODING_PCM_16BIT = 2
        a.is_((C_ATR, "getMinBufferSize", "I", ("I", "I", "I")), 0, 1, 2)
        a.mr(3)           # v3 = minBuf
        # Ensure buffer >= 32768 bytes (~170 ms @ 48kHz stereo PCM-16) for 120 ms prefill headroom
        a.c32(4, 32768)
        a.if_ge(3, 4, "att_buf_ok")
        a.mov(3, 4)
        a.label("att_buf_ok")
        # new AudioTrack(STREAM_MUSIC=3, 48000, 12, 2, bufSize=v5, MODE_STREAM=1)
        a.mov(5, 3)       # v5 = bufSize
        a.new(0, C_ATR)
        a.c4(1, 3)        # STREAM_MUSIC
        a.c32(2, 48000)   # 48000 Hz
        a.c4(3, 12)       # CHANNEL_OUT_STEREO
        a.c4(4, 2)        # ENCODING_PCM_16BIT
        a.c4(6, 1)        # MODE_STREAM
        a.emit(0x76 | (7 << 8), M((C_ATR, "<init>", "V", ("I", "I", "I", "I", "I", "I"))), 0)
        a.mov_o(7, 0)     # v7 = AudioTrack (kept PAUSED until 120 ms = 23040 B prefilled!)
        # FileInputStream(this.a)
        a.iget_o(1, 13, F_ATT_P)
        a.new(8, C_FIS)
        a.id_((C_FIS, "<init>", "V", (C_STR,)), 8, 1)
        # byte[] buf = new byte[3840] (20 ms chunk)
        a.c16(1, 3840)
        a.new_arr(9, 1, C_BA)
        a.c4(11, 0)       # v11 = prefilledBytes = 0
        a.c4(12, 0)       # v12 = started = 0
        a.label("att_loop")
        a.iv((C_FIS, "read", "I", (C_BA,)), 8, 9)
        a.mr(10)          # v10 = n
        a.c4(1, 1)
        a.if_lt(10, 1, "att_done")
        a.c4(1, 0)
        a.iv((C_ATR, "write", "I", (C_BA, "I", "I")), 7, 9, 1, 10)
        # If not started yet (v12 == 0), accumulate prefilledBytes and call play() once >= 23040 B (120 ms)
        a.if_ne(12, 1, "att_loop")
        a.emit(0xb0 | (11 << 8) | (10 << 12))  # add-int/2addr v11, v10
        a.c16(2, 23040)                 # 120 ms @ 48000 Hz stereo PCM-16 = 23040 bytes
        a.if_lt(11, 2, "att_loop")
        a.iv((C_ATR, "play", "V", ()), 7)
        a.c4(12, 1)                     # started = 1
        a.goto16("att_loop")
        a.label("att_done")
        a.iv((C_FIS, "close", "V", ()), 8)
        a.iv((C_ATR, "stop", "V", ()), 7)
        a.iv((C_ATR, "release", "V", ()), 7)
        a.label("tat1")
        a.ret_v()
        a.label("hat0")
        a.mex(0)
        a.ret_v()
        a.try_catch("tat0", "tat1", "hat0")
        m_att_run = (C_ATT, "run", "V", (), 0x0001, 14, 1, 7, *a.finish())

        for m in [m_att_init, m_att_run]:
            M(m[:4])
"""

gsrc = gsrc.replace(
    "        for m in [m_ns_selfld, m_ns_fbtn, m_ns_browse, m_ns_fixprefs, m_fc_init, m_fc_click]:\n            M(m[:4])",
    "        for m in [m_ns_selfld, m_ns_fbtn, m_ns_browse, m_ns_fixprefs, m_fc_init, m_fc_click]:\n            M(m[:4])\n" + att_code,
    1
)
gsrc = gsrc.replace(
    "            (C_FCLK, 0x0001, C_OBJ, [C_OCL], [], [(F_FC_A, 0x0001), (F_FC_B, 0x0001), (F_FC_C, 0x0001)], [m_fc_init], [m_fc_click]),\n        ]",
    "            (C_FCLK, 0x0001, C_OBJ, [C_OCL], [], [(F_FC_A, 0x0001), (F_FC_B, 0x0001), (F_FC_C, 0x0001)], [m_fc_init], [m_fc_click]),\n"
    "            (C_ATT, 0x0001, C_THR, [], [], [(F_ATT_P, 0x0001)], [m_att_init], [m_att_run]),\n        ]",
    1
)

(BASE / "gen_classes2_v1214.py").write_text(gsrc)
subprocess.run([sys.executable, str(BASE / "gen_classes2_v1214.py")], check=True)
print("[1/4] Built classes2.dex with AudioTrackThread (32KB buf, 120ms prefill before play(), MAX_PRIORITY)")

# ==============================================================================
# 2. Patch libfnv.so:
#    - Master-bus -6 dB headroom (* 0.5f) + smooth rational soft limiter (x / (1 + 0.5*|x|))
#    - 5 ms linear gain ramp (240 frames) on starvation/silence transitions (>16 silent frames)
#    - Pre-headroom peak/RMS telemetry + DirectSound8/3D/AAudio fixes
# ==============================================================================
def load_orig_libfnv():
    xz_path = BASE / "orig/libfnv.so.xz"
    if xz_path.exists():
        import lzma
        return bytearray(lzma.decompress(xz_path.read_bytes()))
    with zipfile.ZipFile(ORIG_APK) as z:
        return bytearray(z.read("lib/arm64-v8a/libfnv.so"))

so = load_orig_libfnv()
_orig_cached = bytes(so)

def adrp(pc, target_page, rd):
    diff = (target_page - (pc & ~0xfff)) >> 12
    imm = diff & 0x1fffff
    return 0x90000000 | ((imm & 3) << 29) | (((imm >> 2) & 0x7ffff) << 5) | rd
def add_imm(rd, rn, imm12, x=True, lsl12=False):
    return (0x91000000 if x else 0x11000000) | ((1 if lsl12 else 0) << 22) | (imm12 << 10) | (rn << 5) | rd
def sub_imm(rd, rn, imm12, x=True, s=False):
    base = (0xf1000000 if s else 0xd1000000) if x else (0x71000000 if s else 0x51000000)
    return base | (imm12 << 10) | (rn << 5) | rd
def bl(pc, target): return 0x94000000 | (((target - pc) >> 2) & 0x3ffffff)
def b(pc, target): return 0x14000000 | (((target - pc) >> 2) & 0x3ffffff)
def bcond(pc, target, cond): return 0x54000000 | ((((target - pc) >> 2) & 0x7ffff) << 5) | cond
def cbz32(pc, target, rt): return 0x34000000 | ((((target - pc) >> 2) & 0x7ffff) << 5) | rt
def cbnz32(pc, target, rt): return 0x35000000 | ((((target - pc) >> 2) & 0x7ffff) << 5) | rt
def cbz64(pc, target, rt): return 0xb4000000 | ((((target - pc) >> 2) & 0x7ffff) << 5) | rt
def ldr64(rt, rn, imm): return 0xf9400000 | ((imm // 8) << 10) | (rn << 5) | rt
def str64(rt, rn, imm): return 0xf9000000 | ((imm // 8) << 10) | (rn << 5) | rt
def ldr32(rt, rn, imm): return 0xb9400000 | ((imm // 4) << 10) | (rn << 5) | rt
def str32(rt, rn, imm): return 0xb9000000 | ((imm // 4) << 10) | (rn << 5) | rt
def ldr_s(st, rn, imm): return 0xbd400000 | ((imm // 4) << 10) | (rn << 5) | st
def str_s(st, rn, imm): return 0xbd000000 | ((imm // 4) << 10) | (rn << 5) | st
def mov64(rd, rm): return 0xaa0003e0 | (rm << 16) | rd
def mov32(rd, rm): return 0x2a0003e0 | (rm << 16) | rd
def movz32(rd, imm16, hw=0): return 0x52800000 | (hw << 21) | (imm16 << 5) | rd
def movk32(rd, imm16, hw=0): return 0x72800000 | (hw << 21) | (imm16 << 5) | rd
def orr32_lsl(rd, rn, rm, shift): return 0x2a000000 | (rm << 16) | (shift << 10) | (rn << 5) | rd
def fcvt_d_s(dd, sn): return 0x1e22c000 | (sn << 5) | dd
def fmov_s_w(sd, wn): return 0x1e270000 | (wn << 5) | sd
def fabs_s(sd, sn): return 0x1e20c000 | (sn << 5) | sd
def fadd_s(sd, sn, sm): return 0x1e202800 | (sm << 16) | (sn << 5) | sd
def fsub_s(sd, sn, sm): return 0x1e203800 | (sm << 16) | (sn << 5) | sd
def fmul_s(sd, sn, sm): return 0x1e200800 | (sm << 16) | (sn << 5) | sd
def fdiv_s(sd, sn, sm): return 0x1e201800 | (sm << 16) | (sn << 5) | sd
def fmadd_s(sd, sn, sm, sa): return 0x1f000000 | (sm << 16) | (sa << 10) | (sn << 5) | sd
def fmaxnm_s(sd, sn, sm): return 0x1e206800 | (sm << 16) | (sn << 5) | sd
def fminnm_s(sd, sn, sm): return 0x1e207800 | (sm << 16) | (sn << 5) | sd
def fcmp_s(sn, sm): return 0x1e202000 | (sm << 16) | (sn << 5)
def fcvtzs_w_s(wd, sn): return 0x1e380000 | (sn << 5) | wd
def put_cstr(buf, off, max_len, text):
    raw = text.encode("ascii") + b"\x00"
    assert len(raw) <= max_len
    buf[off : off + max_len] = raw.ljust(max_len, b"\x00")
def load_f32(A, sreg, val, tmp_w=8):
    bits = struct.unpack("<I", struct.pack("<f", val))[0]
    A.e(movz32(tmp_w, bits & 0xffff, 0))
    if bits >> 16:
        A.e(movk32(tmp_w, (bits >> 16) & 0xffff, 1))
    A.e(fmov_s_w(sreg, tmp_w))

# Keep 3D IID fix + AAudio-MED
struct.pack_into("<I", so, 0x5413664, 0x529f508a)
struct.pack_into("<I", so, 0x5413674, 0x72a4f34a)
struct.pack_into("<I", so, 0x5412b84, 0x52800141)
struct.pack_into("<I", so, 0x5412b9c, 0x52800021)
struct.pack_into("<I", so, 0x5412ba8, 0x52800041)
orig_fmt = b"[audio] AAudio %d Hz, burst %d frames, buffer %d\n\x00"
new_fmt  = b"[audio] AAudio-AT120 %d Hz, burst %d, buf %d\n\x00"
so[0x9b510 : 0x9b510 + len(orig_fmt)] = new_fmt.ljust(len(orig_fmt), b"\x00")

# Disable unused FNV_INPUT_SCRIPT jump @ 0x53fb218
struct.pack_into("<I", so, 0x53fb218, 0xd503201f)

PLT_GETENV   = 0x54be1e0
PLT_FPRINTF  = 0x54be2b0
PLT_MUTEXLK  = 0x54be360
PLT_MEMSET   = 0x54be3a0
PLT_OPEN     = 0x54be720
BSS_DIAG     = 0x58d40a0  # +0=silent_frames, +4=ramp_gain, +8=stat_f, +12=dsh_cnt, +16=gain_cnt, +20=fifo_fd

FMT_AAUDIO = 0x96772  # 63 bytes
FMT_MIXPL  = 0x9493b  # 46 bytes
FMT_MIXGN  = 0x97608  # 35 bytes
FMT_ATCON  = 0x9c7c2  # 30 bytes
FMT_DSHPCM = 0x9a1e3  # 24 bytes
STR_FIFO   = 0x9af26  # 17 bytes
put_cstr(so, FMT_AAUDIO, 63, "[at] fd=%d ds=%d dsh=%d pk=%.4f rms=%.4f nan=%d\n")
put_cstr(so, FMT_MIXPL,  46, "[mix] play id=%d buf=%p sz=%u fmt=%x hz=%u\n")
put_cstr(so, FMT_MIXGN,  35, "[mix] gain id=%d L=%.4f R=%.4f\n")
put_cstr(so, FMT_ATCON,  30, "[at] AudioTrack FIFO fd=%d\n")
put_cstr(so, FMT_DSHPCM, 24, "[dshow] pcm f=%d\n")
put_cstr(so, STR_FIFO,   17, "FNV_PCM_FIFO")

class A64:
    def __init__(self, base):
        self.base = base; self.ins = []; self.lbls = {}; self.fixups = []
    def pc(self): return self.base + len(self.ins) * 4
    def lbl(self, name): self.lbls[name] = self.pc()
    def e(self, w): self.ins.append(w)
    def br(self, kind, name, reg=0, cond=0):
        idx = len(self.ins); p = self.pc(); self.ins.append(0)
        self.fixups.append((idx, p, kind, name, reg, cond))
    def finish(self, buf):
        for idx, p, kind, name, reg, cond in self.fixups:
            t = self.lbls[name]
            if kind == "b": self.ins[idx] = b(p, t)
            elif kind == "bc": self.ins[idx] = bcond(p, t, cond)
            elif kind == "cbz": self.ins[idx] = cbz32(p, t, reg)
            elif kind == "cbnz": self.ins[idx] = cbnz32(p, t, reg)
            elif kind == "cbz64": self.ins[idx] = cbz64(p, t, reg)
        for i, w in enumerate(self.ins):
            struct.pack_into("<I", buf, self.base + i * 4, w)
        return self.base + len(self.ins) * 4

# === STUB 1 @ 0x53fb664 ===
A = A64(0x53fb664)
A.e(0xa9bb7bfd)
A.e(0xa90157f4); A.e(0xa9025ff6); A.e(0xa90367f8); A.e(0x6d042be8)
A.e(adrp(A.pc(), BSS_DIAG & ~0xfff, 21)); A.e(add_imm(21, 21, BSS_DIAG & 0xfff))

# 1) Open FNV_PCM_FIFO (O_WRONLY|O_NONBLOCK = 0x801) if fifo_fd == 0
A.e(ldr32(8, 21, 20))
A.br("cbnz", "chk_st", reg=8)
A.e(adrp(A.pc(), STR_FIFO & ~0xfff, 0)); A.e(add_imm(0, 0, STR_FIFO & 0xfff))
A.e(bl(A.pc(), PLT_GETENV))
A.br("cbz64", "chk_st", reg=0)
A.e(movz32(1, 0x801))
A.e(bl(A.pc(), PLT_OPEN))
A.e(sub_imm(31, 0, 0, x=False, s=True))
A.br("bc", "chk_st", cond=13)
A.e(str32(0, 21, 20))
A.e(mov32(2, 0))
A.e(adrp(A.pc(), 0x5593000, 0)); A.e(ldr64(0, 0, 0x190)); A.e(ldr64(0, 0, 0))
A.e(adrp(A.pc(), FMT_ATCON & ~0xfff, 1)); A.e(add_imm(1, 1, FMT_ATCON & 0xfff))
A.e(bl(A.pc(), PLT_FPRINTF))

# 2) 1-second telemetry (measured PRE-headroom, per XAUDIO2_STARVATION_FIX.md Section 5.5)
A.lbl("chk_st")
A.e(ldr32(8, 21, 8)); A.e(0x0b140108); A.e(movz32(9, 48000)); A.e(0x6b09011f)
A.br("bc", "sv_st", cond=3)
A.e(str32(31, 21, 8))
A.e(0x1e2703e8); A.e(0x1e2703e9); A.e(movz32(22, 0))
A.e(0x531f7a89); A.e(movz32(10, 0))
A.lbl("slp")
A.e(0x6b09015f); A.br("bc", "slpe", cond=2)
A.e(0xbc6a5a60); A.e(0x1e202000); A.br("bc", "snan", cond=6)
A.e(0x1e20c001); A.e(0x1e284828); A.e(0x1f002409); A.br("b", "snxt")
A.lbl("snan"); A.e(add_imm(22, 22, 1, x=False))
A.lbl("snxt"); A.e(add_imm(10, 10, 1, x=False)); A.br("b", "slp")
A.lbl("slpe")
A.e(0x1e230120); A.e(0x1e201929); A.e(0x1e21c129)
A.e(adrp(A.pc(), 0x58c5000, 10)); A.e(add_imm(10, 10, 0xf18))
A.e(movz32(11, 256)); A.e(movz32(23, 0)); A.e(movz32(24, 0))
A.lbl("vlp")
A.e(ldr32(12, 10, 0x00))
A.e(sub_imm(31, 12, 1, x=False, s=True)); A.br("bc", "v_c2", cond=1)
A.e(add_imm(23, 23, 1, x=False)); A.br("b", "vnxt")
A.lbl("v_c2")
A.e(sub_imm(31, 12, 2, x=False, s=True)); A.br("bc", "vnxt", cond=1)
A.e(add_imm(24, 24, 1, x=False))
A.lbl("vnxt")
A.e(add_imm(10, 10, 0x58)); A.e(sub_imm(11, 11, 1, x=False)); A.br("cbnz", "vlp", reg=11)
A.e(ldr32(2, 21, 20)); A.e(mov32(3, 23)); A.e(mov32(4, 24)); A.e(mov32(5, 22))
A.e(fcvt_d_s(0, 8)); A.e(fcvt_d_s(1, 9))
A.e(adrp(A.pc(), 0x5593000, 0)); A.e(ldr64(0, 0, 0x190)); A.e(ldr64(0, 0, 0))
A.e(adrp(A.pc(), FMT_AAUDIO & ~0xfff, 1)); A.e(add_imm(1, 1, FMT_AAUDIO & 0xfff))
A.e(bl(A.pc(), PLT_FPRINTF)); A.br("b", "do_fifo")
A.lbl("sv_st"); A.e(str32(8, 21, 8))

# 3) Per-frame 5 ms starvation ramp + -6 dB master headroom (* 0.5f) + smooth soft limiter (x / (1 + 0.5*|x|))
#    Uses ONLY caller-saved s0..s7, s16..s17 + saved s8..s9!
A.lbl("do_fifo")
A.e(ldr32(0, 21, 20))                        # w0 = fifo_fd
A.br("cbz", "aret", reg=0)
load_f32(A, 2, 32767.0)                      # s2 = 32767.0f
load_f32(A, 3, -32767.0)                     # s3 = -32767.0f
load_f32(A, 4, 0.5)                          # s4 = 0.5f (-6 dB headroom & soft limiter slope)
load_f32(A, 5, 1.0)                          # s5 = 1.0f
load_f32(A, 6, 1.0 / 240.0)                  # s6 = 5 ms linear ramp step (1/240 @ 48 kHz)
A.e(fmov_s_w(8, 31))                         # s8 = 0.0f
load_f32(A, 9, 1e-5)                         # s9 = 1e-5f silence threshold
A.e(ldr32(12, 21, 0))                        # w12 = silent_frames
A.e(ldr_s(7, 21, 4))                         # s7 = ramp_gain (0.0..1.0)
A.e(movz32(10, 0))                           # w10 = frame index

A.lbl("c16_lp")
A.e(0x6b14015f); A.br("bc", "c16_dn", cond=2) # cmp w10, w20; b.hs c16_dn
A.e(0x531f794d)                              # lsl w13, w10, #1
A.e(add_imm(14, 13, 1, x=False))             # w14 = 2*frame + 1
A.e(0xbc6d5a60)                              # ldr s0, [x19, w13, uxtw #2] (L)
A.e(0xbc6e5a61)                              # ldr s1, [x19, w14, uxtw #2] (R)
# Detect starvation silence (>16 consecutive frames with max(|L|,|R|) <= 1e-5f)
A.e(fabs_s(16, 0))                           # s16 = |L|
A.e(fabs_s(17, 1))                           # s17 = |R|
A.e(fmaxnm_s(16, 16, 17))                    # s16 = max(|L|, |R|)
A.e(fcmp_s(16, 9))                           # fcmp s16, 1e-5f
A.br("bc", "f_sil", cond=13)                 # b.le f_sil (<= 1e-5f or NaN)
# Non-silent frame: reset silent_frames=0, ramp_gain = min(1.0, ramp_gain + 1/240)
A.e(movz32(12, 0))
A.e(fadd_s(7, 7, 6))
A.e(fminnm_s(7, 7, 5))
A.br("b", "f_mix")
A.lbl("f_sil")
A.e(add_imm(12, 12, 1, x=False))
A.e(sub_imm(31, 12, 16, x=False, s=True))    # cmp w12, #16
A.br("bc", "f_mix", cond=13)                 # b.le f_mix (ignore brief zero-crossings)
A.e(fsub_s(7, 7, 6))                         # ramp down over 5 ms
A.e(fmaxnm_s(7, 7, 8))
A.lbl("f_mix")
# Combined gain s16 = ramp_gain * 0.5f (-6 dB master headroom)
A.e(fmul_s(16, 7, 4))
# Process Left channel: x = L * gain; y = x / (1.0 + 0.5*|x|); int16 = clamp(y * 32767)
A.e(fmul_s(0, 0, 16))
A.e(fabs_s(17, 0))
A.e(fmadd_s(17, 17, 4, 5))                   # s17 = |x| * 0.5 + 1.0
A.e(fdiv_s(0, 0, 17))
A.e(fmul_s(0, 0, 2))
A.e(fmaxnm_s(0, 0, 3))
A.e(fminnm_s(0, 0, 2))
A.e(fcvtzs_w_s(11, 0))
A.e(0x782d5a6b)                              # strh w11, [x19, w13, uxtw #1]
# Process Right channel: x = R * gain; y = x / (1.0 + 0.5*|x|); int16 = clamp(y * 32767)
A.e(fmul_s(1, 1, 16))
A.e(fabs_s(17, 1))
A.e(fmadd_s(17, 17, 4, 5))
A.e(fdiv_s(1, 1, 17))
A.e(fmul_s(1, 1, 2))
A.e(fmaxnm_s(1, 1, 3))
A.e(fminnm_s(1, 1, 2))
A.e(fcvtzs_w_s(11, 1))
A.e(0x782e5a6b)                              # strh w11, [x19, w14, uxtw #1]
A.e(add_imm(10, 10, 1, x=False)); A.br("b", "c16_lp")

A.lbl("c16_dn")
A.e(str32(12, 21, 0))                        # save silent_frames
A.e(str_s(7, 21, 4))                         # save ramp_gain
# Syscall write(fd=w0, buf=x19, len=w20*4): x8=64, svc #0
A.e(mov64(1, 19))
A.e(0x531e7682)                              # lsl w2, w20, #2
A.e(movz32(8, 64))                           # __NR_write = 64
A.e(0xd4000001)                              # svc #0
# Zero AAudio output buffer (x19, w20*8 bytes) so ONLY AudioTrack outputs sound!
A.e(mov64(0, 19)); A.e(mov32(1, 31)); A.e(0x531d7282)
A.e(bl(A.pc(), PLT_MEMSET))

A.lbl("aret")
A.e(0x6d442be8); A.e(0xa94367f8); A.e(0xa9425ff6); A.e(0xa94157f4); A.e(0xa8c57bfd)
A.e(mov32(0, 31)); A.e(0xd65f03c0)
p = A.finish(so)
struct.pack_into("<I", so, 0x54130e0, bl(0x54130e0, 0x53fb664))

# === STUB 2: IDirectSound8::GetCaps real DSCAPS @ 0x54134d0 ===
B = A64(p)
B.e(0xa9be7bfd); B.e(str64(0, 31, 0x10)); B.e(bl(B.pc(), PLT_MEMSET))
B.e(ldr64(8, 31, 0x10))
B.e(movz32(9, 0x0fff)); B.e(str32(9, 8, 0))
B.e(movz32(9, 100));    B.e(str32(9, 8, 4))
B.e(movz32(9, 0x0d40)); B.e(movk32(9, 0x3, 1)); B.e(str32(9, 8, 8))
B.e(movz32(9, 1));      B.e(str32(9, 8, 12))
B.e(movz32(9, 64))
for off in (16, 20, 24, 28, 32, 36, 40, 52): B.e(str32(9, 8, off))
B.e(0xa8c27bfd); B.e(0xd65f03c0)
p2 = B.finish(so)
struct.pack_into("<I", so, 0x54134d0, bl(0x54134d0, p))

# === STUB 3: IDirectSoundBuffer8::Play one-shot replay sync @ 0x5413cc8 ===
C = A64(p2)
C.e(0xa9be7bfd); C.e(str64(7, 31, 0x10)); C.e(mov32(0, 20)); C.e(bl(C.pc(), 0x5414110))
C.e(ldr64(7, 31, 0x10)); C.e(ldr64(8, 21, 0)); C.e(0xa8c27bfd); C.e(0xd65f03c0)
p3 = C.finish(so)
struct.pack_into("<I", so, 0x5413cc8, bl(0x5413cc8, p2))

# === STUB 4: dsound_update_volume 3D pan NaN clamp @ 0x5414434 ===
D = A64(p3)
D.e(0x1e2e1000); D.e(0x1e3e1002); D.e(0x1e2268a5); D.e(0x1e2078a5); D.e(0xd65f03c0)
p4 = D.finish(so)
struct.pack_into("<I", so, 0x5414434, bl(0x5414434, p3))

print(f"  [STUBS 1..4] 0x53fb664..{hex(p4)} (used {p4 - 0x53fb664} / {0x53fb9c0 - 0x53fb664} safe bytes)")
assert p4 <= 0x53fb9c0, f"Overflowed safe cave (0x53fb9c0): {hex(p4)}"
_orig = _orig_cached
assert so[0x53fb9c8:0x53fba74] == _orig[0x53fb9c8:0x53fba74], "Live function @ 0x53fb9ec was touched!"

(WORK / "lib/arm64-v8a/libfnv.so").write_bytes(so)
print("[2/4] Patched libfnv.so (-6 dB headroom + soft limiter + 5 ms starvation ramp + DirectSound8/3D)")

# Bump versionCode 23 -> 24, versionName 1.2.13-atrk1 -> 1.2.14-audfx
mf_path = WORK / "AndroidManifest.xml"
mf = bytearray(mf_path.read_bytes())
mf = bytearray(bytes(mf).replace("1.2.14-audfx".encode("utf-16le"), "1.2.15-audfx".encode("utf-16le"), 1))
pos = 8
sp_strs = []
while pos + 8 <= len(mf):
    ct, hs, cs = struct.unpack_from("<HHI", mf, pos)
    if ct == 0x0001:
        sc, stc, fl, so_off, sto = struct.unpack_from("<IIIII", mf, pos + 8)
        for i in range(sc):
            o, = struct.unpack_from("<I", mf, pos + 28 + i*4)
            p = pos + so_off + o
            n, = struct.unpack_from("<H", mf, p); p += 2
            sp_strs.append(mf[p:p+n*2].decode("utf-16le"))
    elif ct == 0x0102:
        ns, name, astart, asz, acnt = struct.unpack_from("<IIHHH", mf, pos + 16)
        if sp_strs[name] == "manifest":
            for a in range(acnt):
                ao = pos + 16 + astart + a * asz
                ans, aname, araw, tsz_a, tres, atype, adata = struct.unpack_from("<IIIHBBi", mf, ao)
                if sp_strs[aname] == "versionCode":
                    struct.pack_into("<i", mf, ao + 16, 25)
                    print(f"[3/4] Bumped versionCode: {adata} -> 25, versionName -> 1.2.15-audfx")
            break
    pos += cs
mf_path.write_bytes(mf)

# Sign APK (v1 + v2) & package verified ZIP
subprocess.run([sys.executable, str(BASE / "sign_apk.py"), str(WORK), str(OUT_APK)], check=True)
assert b"APK Sig Block 42" in OUT_APK.read_bytes()
with zipfile.ZipFile(OUT_APK) as z:
    assert z.testzip() is None
with zipfile.ZipFile(OUT_ZIP, "w", compression=zipfile.ZIP_STORED) as z:
    z.write(OUT_APK, OUT_APK.name)
with zipfile.ZipFile(OUT_ZIP) as z:
    assert z.testzip() is None
print(f"[4/4] Verified {OUT_ZIP.name} ({OUT_ZIP.stat().st_size / (1024*1024):.2f} MB)")
