#!/usr/bin/env python3
"""
Pure-Python generator for classes2.dex of Fallout Droid NV Native
(package com.rommy.falloutdroid.nv, classes com.rommy.fnvnative.*).

classes.dex (original com.fnvport launcher/game shell, R8-obfuscated) is kept
as-is; classes2.dex overrides it via FalloutDroidFactory (AppComponentFactory):
  - FalloutDroidActivity     : launcher (extends TermActivity) + setup wizard
  - NativeSetup / SetupClick / FolderClick : first-run setup, folder picker
  - FalloutDroidGameActivity : game activity (extends com.fnvport.GameActivity)
  - HdIconsView              : HD touch-control icons
  - AudioTrackThread         : OPTIONAL PCM16 FIFO -> AudioTrack bridge
                               (only when the marker file
                                /sdcard/FalloutDroidNV/use_audiotrack exists;
                                default audio path is native AAudio)

Usage: python3 gen_classes2.py [out.dex]
"""
import os, sys, struct, hashlib, zlib

# ---------- Helper: LEB128 & MUTF-8 ----------
def uleb128(v):
    out = bytearray()
    while True:
        b = v & 0x7f
        v >>= 7
        if v: b |= 0x80
        out.append(b)
        if not v: break
    return bytes(out)

def read_uleb(data, off):
    v = 0; s = 0
    while True:
        b = data[off]; off += 1
        v |= (b & 0x7f) << s
        if not (b & 0x80): break
        s += 7
    return v, off

def mutf8(s):
    u16 = len(s.encode("utf-16-le")) // 2
    return uleb128(u16) + s.encode("utf-8") + b"\x00"

# ---------- Linear Dalvik Verifier ----------
WIDTH_1 = set([0x00,0x01,0x04,0x07,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,0x10,0x11,0x12,0x1d,0x1e,0x21,0x27,0x28,0x3e,0x3f,0x40,0x41,0x42,0x43,0x7b,0x7c,0x7d,0x7e,0x7f,0x80,0x81,0x82,0x83,0x84,0x85,0x86,0x87,0x88,0x89,0x8a,0x8b,0x8c,0x8d,0x8e,0x8f,0xb0,0xb1,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xbb,0xbc,0xbd,0xbe,0xbf,0xc0,0xc1,0xc2,0xc3,0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xcb,0xcc,0xcd,0xce,0xcf])
WIDTH_2 = set([0x02,0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29,0x2d,0x2e,0x2f,0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x5b,0x5c,0x5d,0x5e,0x5f,0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6a,0x6b,0x6c,0x6d,0x90,0x91,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xa9,0xaa,0xab,0xac,0xad,0xae,0xaf,0xd0,0xd1,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,0xdb,0xdc,0xdd,0xde,0xdf,0xe0,0xe1,0xe2])
WIDTH_3 = set([0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c,0x6e,0x6f,0x70,0x71,0x72,0x74,0x75,0x76,0x77,0x78,0xfc,0xfd])
WIDTH_4 = set([0xfa,0xfb])
WIDTH_5 = set([0x18])

def verify_dex(dex_bytes, label="dex"):
    assert dex_bytes[:4] == b"dex\n", f"{label}: bad magic"
    assert struct.unpack_from("<I", dex_bytes, 8)[0] == (zlib.adler32(dex_bytes[12:]) & 0xFFFFFFFF), f"{label}: bad adler32"
    assert dex_bytes[12:32] == hashlib.sha1(dex_bytes[32:]).digest(), f"{label}: bad sha1"
    class_defs_size, class_defs_off = struct.unpack_from("<II", dex_bytes, 96)
    m_cnt = 0
    for i in range(class_defs_size):
        cdata_off = struct.unpack_from("<I", dex_bytes, class_defs_off + i * 32 + 24)[0]
        if cdata_off == 0: continue
        p = cdata_off
        sf, p = read_uleb(dex_bytes, p); inf, p = read_uleb(dex_bytes, p)
        dm, p = read_uleb(dex_bytes, p); vm, p = read_uleb(dex_bytes, p)
        for _ in range(sf + inf):
            _, p = read_uleb(dex_bytes, p); _, p = read_uleb(dex_bytes, p)
        for count in (dm, vm):
            for _ in range(count):
                _, p = read_uleb(dex_bytes, p); _, p = read_uleb(dex_bytes, p)
                code_off, p = read_uleb(dex_bytes, p)
                if code_off == 0: continue
                regs, ins, outs, tries, dbg, insns_size = struct.unpack_from("<HHHHII", dex_bytes, code_off)
                assert regs >= ins, f"{label}: regs {regs} < ins {ins}"
                insns_off = code_off + 16
                u = 0
                while u < insns_size:
                    w0, = struct.unpack_from("<H", dex_bytes, insns_off + u * 2)
                    op = w0 & 0xFF
                    if w0 == 0x0100:
                        sz, = struct.unpack_from("<H", dex_bytes, insns_off + (u+1)*2)
                        u += 4 + sz * 2
                    elif w0 == 0x0200:
                        sz, = struct.unpack_from("<H", dex_bytes, insns_off + (u+1)*2)
                        u += 2 + sz * 4
                    elif w0 == 0x0300:
                        ew, = struct.unpack_from("<H", dex_bytes, insns_off + (u+1)*2)
                        sz, = struct.unpack_from("<I", dex_bytes, insns_off + (u+2)*2)
                        u += 4 + (sz * ew + 1) // 2
                    elif op in WIDTH_1: u += 1
                    elif op in WIDTH_2: u += 2
                    elif op in WIDTH_3: u += 3
                    elif op in WIDTH_4: u += 4
                    elif op in WIDTH_5: u += 5
                    else: raise RuntimeError(f"{label}: unknown opcode {hex(op)} at u={u}")
                assert u == insns_size, f"{label}: walk mismatch u={u} != {insns_size}"
                m_cnt += 1
    print(f"  [VERIFIED] {label}: {len(dex_bytes)} bytes, {class_defs_size} classes, {m_cnt} methods OK")

# ---------- Build classes2.dex ----------
class Asm:
    def __init__(self, S, T, F, M):
        self.S, self.T, self.F, self.M = S, T, F, M
        self.w = []
        self.lbl = {}
        self.fix = []  # (word_pos, target_label, kind) kind: '16' or '8'
        self.tries = []  # (start_lbl, end_lbl, handler_lbl)

    def emit(self, *ws): self.w.extend(ws)
    def label(self, name): self.lbl[name] = len(self.w)
    def try_catch(self, start_lbl, end_lbl, handler_lbl):
        self.tries.append((start_lbl, end_lbl, handler_lbl))

    def mov_o(self, vd, vs): self.emit(0x07 | ((vd & 0xf) << 8) | ((vs & 0xf) << 12))
    def mov_o16(self, vd, vs): self.emit(0x08 | ((vd & 0xff) << 8), vs & 0xffff)
    def mov(self, vd, vs): self.emit(0x01 | ((vd & 0xf) << 8) | ((vs & 0xf) << 12))
    def mr(self, v): self.emit(0x0a | (v << 8))
    def mro(self, v): self.emit(0x0c | (v << 8))
    def mex(self, v): self.emit(0x0d | (v << 8))
    def ret_v(self): self.emit(0x0e)
    def ret(self, v): self.emit(0x0f | (v << 8))
    def ret_o(self, v): self.emit(0x11 | (v << 8))
    def c4(self, v, val):
        # const/4 holds a signed 4-bit literal: -8..7 only (v1.2.15 silently
        # truncated 10 -> -6 and 12 -> -4, which broke the AudioTrack thread).
        assert -8 <= val <= 7, f"const/4 literal out of range: {val}"
        assert 0 <= v <= 15
        self.emit(0x12 | ((v & 0xf) << 8) | ((val & 0xf) << 12))
    def c16(self, v, val):
        assert -32768 <= val <= 32767, f"const/16 literal out of range: {val}"
        self.emit(0x13 | (v << 8), val & 0xffff)
    def c32(self, v, val): self.emit(0x14 | (v << 8), val & 0xffff, (val >> 16) & 0xffff)
    def cs(self, v, s): self.emit(0x1a | (v << 8), self.S(s))
    def check_cast(self, v, t): self.emit(0x1f | (v << 8), self.T(t))
    def new(self, v, t): self.emit(0x22 | (v << 8), self.T(t))

    def goto16(self, lbl):
        pos = len(self.w)
        self.emit(0x29, 0)
        self.fix.append((pos, lbl, '16'))

    def _ifz(self, op, v, lbl):
        pos = len(self.w)
        self.emit(op | (v << 8), 0)
        self.fix.append((pos, lbl, '16'))
    def if_eqz(self, v, lbl): self._ifz(0x38, v, lbl)
    def if_nez(self, v, lbl): self._ifz(0x39, v, lbl)
    def if_ltz(self, v, lbl): self._ifz(0x3a, v, lbl)
    def if_gez(self, v, lbl): self._ifz(0x3b, v, lbl)
    def if_gtz(self, v, lbl): self._ifz(0x3c, v, lbl)
    def if_lez(self, v, lbl): self._ifz(0x3d, v, lbl)

    def _if2(self, op, va, vb, lbl):
        pos = len(self.w)
        self.emit(op | ((va & 0xf) << 8) | ((vb & 0xf) << 12), 0)
        self.fix.append((pos, lbl, '16'))
    def if_eq(self, va, vb, lbl): self._if2(0x32, va, vb, lbl)
    def if_ne(self, va, vb, lbl): self._if2(0x33, va, vb, lbl)
    def if_lt(self, va, vb, lbl): self._if2(0x34, va, vb, lbl)
    def if_ge(self, va, vb, lbl): self._if2(0x35, va, vb, lbl)

    def iget(self, vd, vo, f): self.emit(0x52 | ((vd & 0xf) << 8) | ((vo & 0xf) << 12), self.F(f))
    def iget_z(self, vd, vo, f): self.emit(0x55 | ((vd & 0xf) << 8) | ((vo & 0xf) << 12), self.F(f))
    def mul_lit8(self, vd, vs, lit): self.emit(0xda | (vd << 8), (vs & 0xff) | ((lit & 0xff) << 8))
    def div_lit8(self, vd, vs, lit): self.emit(0xdb | (vd << 8), (vs & 0xff) | ((lit & 0xff) << 8))
    def iget_o(self, vd, vo, f): self.emit(0x54 | ((vd & 0xf) << 8) | ((vo & 0xf) << 12), self.F(f))
    def iput(self, vs, vo, f): self.emit(0x59 | ((vs & 0xf) << 8) | ((vo & 0xf) << 12), self.F(f))
    def iput_o(self, vs, vo, f): self.emit(0x5b | ((vs & 0xf) << 8) | ((vo & 0xf) << 12), self.F(f))
    def sget(self, vd, f): self.emit(0x60 | (vd << 8), self.F(f))
    def sget_o(self, vd, f): self.emit(0x62 | (vd << 8), self.F(f))
    def sget_z(self, vd, f): self.emit(0x63 | (vd << 8), self.F(f))
    def sput_o(self, vs, f): self.emit(0x69 | (vs << 8), self.F(f))
    def sput_z(self, vs, f): self.emit(0x6a | (vs << 8), self.F(f))

    def add_lit8(self, vd, vs, lit): self.emit(0xd8 | (vd << 8), (vs & 0xff) | ((lit & 0xff) << 8))

    def inv(self, op, m, *rs):
        n = len(rs)
        assert n <= 5
        mid = self.M(m)
        r = list(rs) + [0] * (5 - n)
        w0 = op | ((n & 0xf) << 12) | ((r[4] & 0xf) << 8)
        w2 = (r[0] & 0xf) | ((r[1] & 0xf) << 4) | ((r[2] & 0xf) << 8) | ((r[3] & 0xf) << 12)
        self.emit(w0, mid, w2)

    def inv_range(self, op, m, r0, cnt):
        mid = self.M(m)
        self.emit(op | ((cnt & 0xff) << 8), mid, r0 & 0xffff)

    def iv(self, m, *rs): self.inv(0x6e, m, *rs)
    def isup(self, m, *rs): self.inv(0x6f, m, *rs)
    def id_(self, m, *rs): self.inv(0x70, m, *rs)
    def is_(self, m, *rs): self.inv(0x71, m, *rs)
    def ii(self, m, *rs): self.inv(0x72, m, *rs)
    def isup_r(self, m, r0, cnt): self.inv_range(0x75, m, r0, cnt)

    def arr_len(self, vd, va): self.emit(0x21 | ((vd & 0xf) << 8) | ((va & 0xf) << 12))
    def new_arr(self, vd, vs, t): self.emit(0x23 | ((vd & 0xf) << 8) | ((vs & 0xf) << 12), self.T(t))
    def aget_o(self, vd, va, vi): self.emit(0x46 | (vd << 8), (va & 0xff) | ((vi & 0xff) << 8))
    def aput_o(self, vs, va, vi): self.emit(0x4d | (vs << 8), (va & 0xff) | ((vi & 0xff) << 8))
    def inst_of(self, vd, vo, t): self.emit(0x20 | ((vd & 0xf) << 8) | ((vo & 0xf) << 12), self.T(t))
    def add_f(self, vd, va, vb): self.emit(0xa6 | (vd << 8), (va & 0xff) | ((vb & 0xff) << 8))
    def sub_f(self, vd, va, vb): self.emit(0xa7 | (vd << 8), (va & 0xff) | ((vb & 0xff) << 8))
    def mul_f(self, vd, va, vb): self.emit(0xa8 | (vd << 8), (va & 0xff) | ((vb & 0xff) << 8))
    def c_wide16(self, v, val): self.emit(0x16 | (v << 8), val & 0xffff)
    def finish(self):
        for pos, lbl, kind in self.fix:
            delta = self.lbl[lbl] - pos
            assert -32768 <= delta <= 32767, f"Branch out of range: {delta}"
            self.w[pos + 1] = delta & 0xffff
        resolved_tries = []
        for s_lbl, e_lbl, h_lbl in self.tries:
            s = self.lbl[s_lbl]; e = self.lbl[e_lbl]; h = self.lbl[h_lbl]
            resolved_tries.append((s, e - s, h))
        return self.w, resolved_tries

def build_classes2(out_path):
    strings = set()
    types = set()
    protos = set()
    fields = set()
    methods = set()

    def shorty(ret, ps):
        return (ret if len(ret) == 1 else "L") + "".join(p if len(p) == 1 else "L" for p in ps)

    def add_s(s): strings.add(s)
    def add_t(t): add_s(t); types.add(t)
    def add_p(ret, ps):
        sh = shorty(ret, ps); add_s(sh); add_t(ret)
        for p in ps: add_t(p)
        protos.add((ret, tuple(ps)))
    def add_f(cls, name, typ):
        add_t(cls); add_s(name); add_t(typ); fields.add((cls, name, typ))
    def add_m(cls, name, ret, ps):
        add_t(cls); add_s(name); add_p(ret, ps); methods.add((cls, name, ret, tuple(ps)))

    # Pre-declare all classes, fields, methods, strings
    C_ACT = "Lcom/rommy/fnvnative/FalloutDroidActivity;"
    C_TERM = "Lcom/fnvport/term/TermActivity;"
    C_SETUP = "Lcom/rommy/fnvnative/NativeSetup;"
    C_CLICK = "Lcom/rommy/fnvnative/SetupClick;"
    C_OBJ = "Ljava/lang/Object;"
    C_STR = "Ljava/lang/String;"
    C_SB = "Ljava/lang/StringBuilder;"
    C_VIEW = "Landroid/view/View;"
    C_OCL = "Landroid/view/View$OnClickListener;"
    C_INTENT = "Landroid/content/Intent;"
    C_URI = "Landroid/net/Uri;"
    C_CTX = "Landroid/content/Context;"
    C_PM = "Landroid/os/PowerManager;"
    C_ENV = "Landroid/os/Environment;"
    C_VER = "Landroid/os/Build$VERSION;"
    C_TOAST = "Landroid/widget/Toast;"
    C_AD = "Landroid/app/AlertDialog;"
    C_ADB = "Landroid/app/AlertDialog$Builder;"
    C_SV = "Landroid/widget/ScrollView;"
    C_LL = "Landroid/widget/LinearLayout;"
    C_LLP = "Landroid/widget/LinearLayout$LayoutParams;"
    C_TV = "Landroid/widget/TextView;"
    C_BTN = "Landroid/widget/Button;"
    C_VGLP = "Landroid/view/ViewGroup$LayoutParams;"
    C_CS = "Ljava/lang/CharSequence;"
    C_TF = "Landroid/graphics/Typeface;"
    C_FILE = "Ljava/io/File;"
    C_FA_FILE = "[Ljava/io/File;"
    C_FCLK = "Lcom/rommy/fnvnative/FolderClick;"
    C_ARRS = "Ljava/util/Arrays;"
    C_LS6 = "Ls6;"
    C_LR6 = "Lr6;"
    C_LT6 = "Lt6;"
    C_LW2 = "Lw2;"
    C_FACT = "Lcom/rommy/fnvnative/FalloutDroidFactory;"
    C_CCF = "Landroidx/core/app/CoreComponentFactory;"
    C_GACT = "Lcom/rommy/fnvnative/FalloutDroidGameActivity;"
    C_OGACT = "Lcom/fnvport/GameActivity;"
    C_HDV = "Lcom/rommy/fnvnative/HdIconsView;"
    C_ACT_FW = "Landroid/app/Activity;"
    C_CL = "Ljava/lang/ClassLoader;"
    C_BND = "Landroid/os/Bundle;"
    C_VG = "Landroid/view/ViewGroup;"
    C_VP = "Landroid/view/ViewParent;"
    C_LM8 = "Lm8;"
    C_LR0 = "Lr0;"
    C_LT0 = "Lt0;"
    C_LY2 = "Ly2;"
    C_CAN = "Landroid/graphics/Canvas;"
    C_PNT = "Landroid/graphics/Paint;"
    C_CF = "Landroid/graphics/ColorFilter;"
    C_CMCF = "Landroid/graphics/ColorMatrixColorFilter;"
    C_FA = "[F"
    C_OS = "Landroid/system/Os;"
    C_LOG = "Landroid/util/Log;"
    C_FOS = "Ljava/io/FileOutputStream;"
    C_FIS = "Ljava/io/FileInputStream;"
    C_THR = "Ljava/lang/Thread;"
    C_ATR = "Landroid/media/AudioTrack;"
    C_ATT = "Lcom/rommy/fnvnative/AudioTrackThread;"
    C_BA = "[B"
    C_PSTYLE = "Landroid/graphics/Paint$Style;"
    C_RECT = "Landroid/graphics/Rect;"
    C_RECTF = "Landroid/graphics/RectF;"
    C_BMP = "Landroid/graphics/Bitmap;"
    C_BMPA = "[Landroid/graphics/Bitmap;"
    C_BMPF = "Landroid/graphics/BitmapFactory;"
    C_AM = "Landroid/content/res/AssetManager;"
    C_AUDM = "Landroid/media/AudioManager;"
    C_AFCL = "Landroid/media/AudioManager$OnAudioFocusChangeListener;"
    C_IS = "Ljava/io/InputStream;"
    C_LIST = "Ljava/util/List;"
    C_ENUM = "Ljava/lang/Enum;"


    # Collect everything via two-pass assembly (pass 1 collects symbols, pass 2 emits)
    sym_s, sym_t, sym_f, sym_m = {}, {}, {}, {}

    def define_all(collect_only):
        def S(s):
            if collect_only: add_s(s); return 0
            return sym_s[s]
        def T(t):
            if collect_only: add_t(t); return 0
            return sym_t[t]
        def F(f):
            if collect_only: add_f(*f); return 0
            return sym_f[f]
        def M(m):
            if collect_only: add_m(*m); return 0
            return sym_m[m]

        # Ensure class types & interfaces are registered
        for t in [C_ACT, C_TERM, C_SETUP, C_CLICK, C_OBJ, C_OCL]:
            T(t)

        # ---- Class 1: SetupClick ----
        F_CLK_A = (C_CLICK, "a", C_TERM)
        F_CLK_B = (C_CLICK, "b", "I")
        F(F_CLK_A); F(F_CLK_B)

        # SetupClick.<init>(TermActivity, int)V  regs=3, ins=3, outs=1 (v0=this, v1=act, v2=action)
        a = Asm(S, T, F, M)
        a.id_((C_OBJ, "<init>", "V", ()), 0)
        a.iput_o(1, 0, F_CLK_A)
        a.iput(2, 0, F_CLK_B)
        a.ret_v()
        m_clk_init = (C_CLICK, "<init>", "V", (C_TERM, "I"), 0x10001, 3, 3, 1, *a.finish())

        # SetupClick.onClick(View)V  regs=4, ins=2, outs=1 (v2=this, v3=view)
        a = Asm(S, T, F, M)
        a.iget_o(0, 2, F_CLK_A)   # v0 = act
        a.iget(1, 2, F_CLK_B)     # v1 = action
        a.if_nez(1, "not0")
        a.iv((C_TERM, "v", "V", ()), 0)
        a.ret_v()
        a.label("not0")
        a.c4(3, 1)
        a.if_ne(1, 3, "not1")
        a.is_((C_SETUP, "requestBattery", "V", (C_TERM,)), 0)
        a.ret_v()
        a.label("not1")
        a.c4(3, 2)
        a.if_ne(1, 3, "not2")
        a.is_((C_SETUP, "openFolderPicker", "V", (C_TERM,)), 0)
        a.ret_v()
        a.label("not2")
        a.is_((C_SETUP, "dismiss", "V", (C_TERM,)), 0)
        a.ret_v()
        m_clk_click = (C_CLICK, "onClick", "V", (C_VIEW,), 0x0001, 4, 2, 1, *a.finish())

        # ---- Class 2: NativeSetup ----
        F_DISMISSED = (C_SETUP, "dismissed", "Z")
        F_DLG = (C_SETUP, "dlg", C_AD)
        F_BDLG = (C_SETUP, "bdlg", C_AD)
        F(F_DISMISSED); F(F_DLG); F(F_BDLG)

        # NativeSetup.dismiss(TermActivity)V  regs=2, ins=1, outs=1 (v1=act)
        a = Asm(S, T, F, M)
        a.c4(0, 1)
        a.sput_z(0, F_DISMISSED)
        a.sget_o(0, F_DLG)
        a.if_eqz(0, "end")
        a.label("t0")
        a.iv((C_AD, "dismiss", "V", ()), 0)
        a.label("t1")
        a.goto16("clr")
        a.label("h0")
        a.mex(0)
        a.label("clr")
        a.c4(0, 0)
        a.sput_o(0, F_DLG)
        a.label("end")
        a.ret_v()
        a.try_catch("t0", "t1", "h0")
        m_ns_dismiss = (C_SETUP, "dismiss", "V", (C_TERM,), 0x0009, 2, 1, 1, *a.finish())

        # NativeSetup.requestBattery(TermActivity)V  regs=5, ins=1, outs=3 (v4=act)
        a = Asm(S, T, F, M)
        a.label("t0")
        a.new(0, C_SB)
        a.cs(1, "package:")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 0, 1)
        a.iv((C_CTX, "getPackageName", C_STR, ()), 4)
        a.mro(1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 0, 1)
        a.iv((C_SB, "toString", C_STR, ()), 0)
        a.mro(1)
        a.is_((C_URI, "parse", C_URI, (C_STR,)), 1)
        a.mro(1)
        a.new(0, C_INTENT)
        a.cs(2, "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        a.id_((C_INTENT, "<init>", "V", (C_STR, C_URI)), 0, 2, 1)
        a.iv((C_CTX, "startActivity", "V", (C_INTENT,)), 4, 0)
        a.label("t1")
        a.ret_v()
        a.label("h0")
        a.mex(0)
        a.label("t2")
        a.new(0, C_INTENT)
        a.cs(1, "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
        a.id_((C_INTENT, "<init>", "V", (C_STR,)), 0, 1)
        a.iv((C_CTX, "startActivity", "V", (C_INTENT,)), 4, 0)
        a.label("t3")
        a.ret_v()
        a.label("h1")
        a.mex(0)
        a.ret_v()
        a.try_catch("t0", "t1", "h0")
        a.try_catch("t2", "t3", "h1")
        m_ns_bat = (C_SETUP, "requestBattery", "V", (C_TERM,), 0x0009, 5, 1, 3, *a.finish())

        # NativeSetup.openFolderPicker(TermActivity)V  regs=4, ins=1, outs=2 (v3=act)
        a = Asm(S, T, F, M)
        a.cs(0, "/storage/emulated/0")
        a.is_((C_SETUP, "browseDir", "V", (C_TERM, C_STR)), 3, 0)
        a.ret_v()
        m_ns_pick = (C_SETUP, "openFolderPicker", "V", (C_TERM,), 0x0009, 4, 1, 2, *a.finish())

        # NativeSetup.selectFolder(TermActivity, String path)V  regs=6, ins=2, outs=3 -> v4=act, v5=path
        a = Asm(S, T, F, M)
        # Close browse dialog if open
        a.sget_o(0, F_BDLG)
        a.if_eqz(0, "sf_no_d")
        a.label("tsf0")
        a.iv((C_AD, "dismiss", "V", ()), 0)
        a.label("tsf1")
        a.goto16("sf_clr")
        a.label("hsf0")
        a.mex(0)
        a.label("sf_clr")
        a.c4(0, 0)
        a.sput_o(0, F_BDLG)
        a.label("sf_no_d")
        a.try_catch("tsf0", "tsf1", "hsf0")
        # act.c.s = path; act.p = act.c.c(act, true); act.w(); act.z(act.s, false)
        a.iget_o(0, 4, (C_TERM, "c", C_LS6))
        a.if_eqz(0, "sf_ret")
        a.iput_o(5, 0, (C_LS6, "s", C_STR))
        a.c4(1, 1)
        a.iv((C_LS6, "c", C_LR6, (C_TERM, "Z")), 0, 4, 1)
        a.mro(0)
        a.iput_o(0, 4, (C_TERM, "p", C_LR6))
        a.iv((C_TERM, "w", "V", ()), 4)
        a.iget(0, 4, (C_TERM, "s", "I"))
        a.c4(1, 0)
        a.iv((C_TERM, "z", "V", ("I", "Z")), 4, 0, 1)
        # Toast "Папка игры: " + path
        a.new(0, C_SB)
        a.cs(1, "Папка игры: ")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 0, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 0, 5)
        a.iv((C_SB, "toString", C_STR, ()), 0)
        a.mro(0)
        a.c4(1, 0)
        a.is_((C_TOAST, "makeText", C_TOAST, (C_CTX, C_CS, "I")), 4, 0, 1)
        a.mro(0)
        a.iv((C_TOAST, "show", "V", ()), 0)
        a.is_((C_SETUP, "checkAndShowSetup", "V", (C_TERM,)), 4)
        a.label("sf_ret")
        a.ret_v()
        m_ns_selfld = (C_SETUP, "selectFolder", "V", (C_TERM, C_STR), 0x0009, 6, 2, 3, *a.finish())

        # NativeSetup.addFolderBtn(TermActivity, LinearLayout, String label, String path, boolean isSelect)V
        # regs=10, ins=5, outs=5 -> v5=act, v6=ll, v7=label, v8=path, v9=isSelect
        a = Asm(S, T, F, M)
        a.new(0, C_BTN)
        a.id_((C_BTN, "<init>", "V", (C_CTX,)), 0, 5)
        a.iv((C_TV, "setText", "V", (C_CS,)), 0, 7)
        a.c4(1, 0)
        a.iv((C_TV, "setAllCaps", "V", ("Z",)), 0, 1)
        a.if_eqz(9, "fb_norm")
        a.c32(1, 0xFF143D24)
        a.c32(2, 0xFF22E57A)
        a.goto16("fb_col")
        a.label("fb_norm")
        a.c32(1, 0xFF142219)
        a.c32(2, 0xFFE4F2E8)
        a.label("fb_col")
        a.iv((C_VIEW, "setBackgroundColor", "V", ("I",)), 0, 1)
        a.iv((C_TV, "setTextColor", "V", ("I",)), 0, 2)
        a.c16(1, 20); a.c16(2, 14)
        a.iv((C_VIEW, "setPadding", "V", ("I", "I", "I", "I")), 0, 1, 2, 1, 2)
        a.new(1, C_FCLK)
        a.id_((C_FCLK, "<init>", "V", (C_TERM, C_STR, "Z")), 1, 5, 8, 9)
        a.iv((C_VIEW, "setOnClickListener", "V", (C_OCL,)), 0, 1)
        a.new(1, C_LLP)
        a.c4(2, -1); a.c4(3, -2)
        a.id_((C_LLP, "<init>", "V", ("I", "I")), 1, 2, 3)
        a.c16(2, 8)
        a.iput(2, 1, (C_LLP, "topMargin", "I"))
        a.iv((C_LL, "addView", "V", (C_VIEW, C_VGLP)), 6, 0, 1)
        a.ret_v()
        m_ns_fbtn = (C_SETUP, "addFolderBtn", "V", (C_TERM, C_LL, C_STR, C_STR, "Z"), 0x0009, 10, 5, 5, *a.finish())

        # NativeSetup.browseDir(TermActivity, String path)V
        # regs=14, ins=2, outs=5 -> v12=act, v13=path
        a = Asm(S, T, F, M)
        # Dismiss previous browse dialog
        a.sget_o(0, F_BDLG)
        a.if_eqz(0, "bd_no_old")
        a.label("tbd0")
        a.iv((C_AD, "dismiss", "V", ()), 0)
        a.label("tbd1")
        a.goto16("bd_clr")
        a.label("hbd0")
        a.mex(0)
        a.label("bd_clr")
        a.c4(0, 0)
        a.sput_o(0, F_BDLG)
        a.label("bd_no_old")
        a.try_catch("tbd0", "tbd1", "hbd0")

        # v4 = new File(path)
        a.new(4, C_FILE)
        a.id_((C_FILE, "<init>", "V", (C_STR,)), 4, 13)

        # ScrollView (v5) -> LinearLayout (v6)
        a.new(5, C_SV)
        a.id_((C_SV, "<init>", "V", (C_CTX,)), 5, 12)
        a.c32(0, 0xFF070E09)
        a.iv((C_VIEW, "setBackgroundColor", "V", ("I",)), 5, 0)

        a.new(6, C_LL)
        a.id_((C_LL, "<init>", "V", (C_CTX,)), 6, 12)
        a.c4(0, 1)
        a.iv((C_LL, "setOrientation", "V", ("I",)), 6, 0)
        a.c16(0, 28)
        a.iv((C_VIEW, "setPadding", "V", ("I", "I", "I", "I")), 6, 0, 0, 0, 0)

        # Path header TextView
        a.new(0, C_TV)
        a.id_((C_TV, "<init>", "V", (C_CTX,)), 0, 12)
        a.iv((C_TV, "setText", "V", (C_CS,)), 0, 13)
        a.c32(1, 0xFF22E57A)
        a.iv((C_TV, "setTextColor", "V", ("I",)), 0, 1)
        a.c32(1, 0x41600000)  # 14.0f
        a.iv((C_TV, "setTextSize", "V", ("F",)), 0, 1)
        a.sget_o(1, (C_TF, "DEFAULT_BOLD", C_TF))
        a.iv((C_TV, "setTypeface", "V", (C_TF,)), 0, 1)
        a.iv((C_LL, "addView", "V", (C_VIEW,)), 6, 0)

        # Check if FalloutNV.exe exists in current dir
        a.new(0, C_FILE)
        a.cs(1, "FalloutNV.exe")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 0, 4, 1)
        a.iv((C_FILE, "exists", "Z", ()), 0)
        a.mr(0)
        a.if_eqz(0, "bd_no_exe")
        a.cs(2, "✓ ВЫБРАТЬ ЭТУ ПАПКУ (найден FalloutNV.exe)")
        a.goto16("bd_sel_btn")
        a.label("bd_no_exe")
        a.cs(2, "✓ ВЫБРАТЬ ЭТУ ПАПКУ")
        a.label("bd_sel_btn")
        a.c4(3, 1)
        a.is_((C_SETUP, "addFolderBtn", "V", (C_TERM, C_LL, C_STR, C_STR, "Z")), 12, 6, 2, 13, 3)

        # Parent folder button ("← Назад (..)") if parent != null and not "/"
        a.iv((C_FILE, "getParent", C_STR, ()), 4)
        a.mro(7)
        a.if_eqz(7, "bd_list")
        a.cs(0, "/storage/emulated")
        a.iv((C_STR, "equals", "Z", (C_OBJ,)), 0, 7)
        a.mr(0)
        a.if_nez(0, "bd_list")
        a.cs(2, "← Назад (..)")
        a.c4(3, 0)
        a.is_((C_SETUP, "addFolderBtn", "V", (C_TERM, C_LL, C_STR, C_STR, "Z")), 12, 6, 2, 7, 3)

        # List subdirectories via File.listFiles()
        a.label("bd_list")
        a.iv((C_FILE, "listFiles", C_FA_FILE, ()), 4)
        a.mro(7)  # v7 = File[]
        a.if_eqz(7, "bd_show")
        a.is_((C_ARRS, "sort", "V", ("[Ljava/lang/Object;",)), 7)
        a.arr_len(8, 7)  # v8 = len
        a.c4(9, 0)       # v9 = i
        a.label("bd_loop")
        a.if_ge(9, 8, "bd_show")
        a.aget_o(10, 7, 9)  # v10 = File child
        a.if_eqz(10, "bd_next")
        a.iv((C_FILE, "isDirectory", "Z", ()), 10)
        a.mr(0)
        a.if_eqz(0, "bd_next")
        a.iv((C_FILE, "getName", C_STR, ()), 10)
        a.mro(11)
        a.cs(0, ".")
        a.iv((C_STR, "startsWith", "Z", (C_STR,)), 11, 0)
        a.mr(0)
        a.if_nez(0, "bd_next")
        # Label: "📁 " + name
        a.new(0, C_SB)
        a.cs(1, "[+] ")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 0, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 0, 11)
        a.iv((C_SB, "toString", C_STR, ()), 0)
        a.mro(2)
        a.iv((C_FILE, "getAbsolutePath", C_STR, ()), 10)
        a.mro(11)
        a.c4(3, 0)
        a.is_((C_SETUP, "addFolderBtn", "V", (C_TERM, C_LL, C_STR, C_STR, "Z")), 12, 6, 2, 11, 3)
        a.label("bd_next")
        a.add_lit8(9, 9, 1)
        a.goto16("bd_loop")

        # Show AlertDialog
        a.label("bd_show")
        a.iv((C_SV, "addView", "V", (C_VIEW,)), 5, 6)
        a.new(0, C_ADB)
        a.id_((C_ADB, "<init>", "V", (C_CTX,)), 0, 12)
        a.iv((C_ADB, "setView", C_ADB, (C_VIEW,)), 0, 5)
        a.c4(1, 1)
        a.iv((C_ADB, "setCancelable", C_ADB, ("Z",)), 0, 1)
        a.iv((C_ADB, "create", C_AD, ()), 0)
        a.mro(0)
        a.sput_o(0, F_BDLG)
        a.iv((C_AD, "show", "V", ()), 0)
        a.ret_v()
        m_ns_browse = (C_SETUP, "browseDir", "V", (C_TERM, C_STR), 0x0009, 14, 2, 5, *a.finish())

        # NativeSetup.resolveTreeUri(Uri)String  regs=7, ins=1, outs=3 (v6=uri)
        a = Asm(S, T, F, M)
        a.iv((C_URI, "getPath", C_STR, ()), 6)
        a.mro(0)  # v0 = path
        a.if_nez(0, "nonnull")
        a.c4(0, 0)
        a.ret_o(0)
        a.label("nonnull")
        a.cs(1, "/tree/")
        a.iv((C_STR, "indexOf", "I", (C_STR,)), 0, 1)
        a.mr(2)  # v2 = idx
        a.if_ltz(2, "after_tree")
        a.add_lit8(2, 2, 6)
        a.iv((C_STR, "substring", C_STR, ("I",)), 0, 2)
        a.mro(0)
        a.label("after_tree")
        a.c16(1, 58)  # ':'
        a.iv((C_STR, "indexOf", "I", ("I",)), 0, 1)
        a.mr(2)  # v2 = colon
        a.if_ltz(2, "ret_raw")
        a.c4(3, 0)
        a.iv((C_STR, "substring", C_STR, ("I", "I")), 0, 3, 2)
        a.mro(3)  # v3 = vol
        a.add_lit8(2, 2, 1)
        a.iv((C_STR, "substring", C_STR, ("I",)), 0, 2)
        a.mro(4)  # v4 = rel
        a.cs(1, "primary")
        a.iv((C_STR, "equalsIgnoreCase", "Z", (C_STR,)), 1, 3)
        a.mr(5)
        a.if_eqz(5, "other_vol")
        # primary volume
        a.iv((C_STR, "length", "I", ()), 4)
        a.mr(5)
        a.if_nez(5, "prim_rel")
        a.cs(0, "/storage/emulated/0")
        a.ret_o(0)
        a.label("prim_rel")
        a.new(5, C_SB)
        a.cs(1, "/storage/emulated/0/")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 5, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 5, 4)
        a.iv((C_SB, "toString", C_STR, ()), 5)
        a.mro(0)
        a.ret_o(0)
        # external SD volume
        a.label("other_vol")
        a.new(5, C_SB)
        a.cs(1, "/storage/")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 5, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 5, 3)
        a.iv((C_STR, "length", "I", ()), 4)
        a.mr(2)
        a.if_eqz(2, "ov_done")
        a.cs(1, "/")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 5, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 5, 4)
        a.label("ov_done")
        a.iv((C_SB, "toString", C_STR, ()), 5)
        a.mro(0)
        a.label("ret_raw")
        a.ret_o(0)
        m_ns_res = (C_SETUP, "resolveTreeUri", C_STR, (C_URI,), 0x0009, 7, 1, 3, *a.finish())

        # NativeSetup.handleActivityResult(TermActivity, int req, int res, Intent data)Z
        # regs=8, ins=4, outs=3 -> v4=act, v5=req, v6=res, v7=data
        a = Asm(S, T, F, M)
        a.c16(0, 7701)
        a.if_eq(5, 0, "is_ours")
        a.c4(0, 0)
        a.ret(0)
        a.label("is_ours")
        a.c4(0, -1)
        a.if_ne(6, 0, "ret_true")
        a.if_eqz(7, "ret_true")
        a.iv((C_INTENT, "getData", C_URI, ()), 7)
        a.mro(0)
        a.if_eqz(0, "ret_true")
        a.is_((C_SETUP, "resolveTreeUri", C_STR, (C_URI,)), 0)
        a.mro(1)  # v1 = resolved path
        a.if_eqz(1, "ret_true")
        a.iv((C_STR, "length", "I", ()), 1)
        a.mr(0)
        a.if_eqz(0, "ret_true")
        # act.c.s = path
        a.iget_o(2, 4, (C_TERM, "c", C_LS6))
        a.iput_o(1, 2, (C_LS6, "s", C_STR))
        # act.p = act.c.c(act, true)
        a.c4(3, 1)
        a.iv((C_LS6, "c", C_LR6, (C_TERM, "Z")), 2, 4, 3)
        a.mro(2)
        a.iput_o(2, 4, (C_TERM, "p", C_LR6))
        # act.w(); act.z(act.s, false)
        a.iv((C_TERM, "w", "V", ()), 4)
        a.iget(2, 4, (C_TERM, "s", "I"))
        a.c4(3, 0)
        a.iv((C_TERM, "z", "V", ("I", "Z")), 4, 2, 3)
        # Toast "Папка игры: " + path
        a.new(2, C_SB)
        a.cs(3, "Папка игры: ")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 2, 3)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 1)
        a.iv((C_SB, "toString", C_STR, ()), 2)
        a.mro(2)
        a.c4(3, 0)
        a.is_((C_TOAST, "makeText", C_TOAST, (C_CTX, C_CS, "I")), 4, 2, 3)
        a.mro(2)
        a.iv((C_TOAST, "show", "V", ()), 2)
        # Refresh setup dialog
        a.is_((C_SETUP, "checkAndShowSetup", "V", (C_TERM,)), 4)
        a.label("ret_true")
        a.c4(0, 1)
        a.ret(0)
        m_ns_resact = (C_SETUP, "handleActivityResult", "Z", (C_TERM, "I", "I", C_INTENT), 0x0009, 8, 4, 3, *a.finish())

        # NativeSetup.ensureAudioIni(TermActivity)V  regs=6, ins=1, outs=3 (v5=act)
        AUDIO_INI = (
            "Audio:bEnableAudio=1\n"
            "Audio:bEnableAudioCache=1\n"
            "Audio:iAudioCacheSize=8192\n"
            "Audio:iMaxSizeForCachedSound=1024\n"
            "Audio:bEnableEnviroEffectsOnPC=0\n"
            "Audio:bMultiThreadAudio=0\n"
            "Audio:bUseAudioDebugInformation=0\n"
            "Audio:fDefaultMasterVolume=1.0000\n"
            "Audio:fDefaultEffectsVolume=1.0000\n"
            "Audio:fDefaultFootVolume=1.0000\n"
            "Audio:fDefaultVoiceVolume=1.0000\n"
            "Audio:fDefaultMusicVolume=0.6000\n"
            "Audio:fDefaultRadioVolume=0.8000\n"
            "AudioMenu:fAudioMasterVolume=1.0000\n"
            "AudioMenu:fVal0=1.0000\n"
            "AudioMenu:fVal1=1.0000\n"
            "AudioMenu:fVal2=1.0000\n"
            "AudioMenu:fVal3=1.0000\n"
            "AudioMenu:fVal4=0.6000\n"
            "AudioMenu:fVal5=0.8000\n"
            "General:bPreemptivelyUnloadCells=1\n"
            "General:bSelectivePurgeUnusedOnFastTravel=1\n"
            "General:iPreloadSizeLimit=26214400\n"
            "General:uInterior Cell Buffer=3\n"
            "General:uExterior Cell Buffer=36\n"
            "BackgroundLoad:bBackgroundCellLoads=1\n"
            "BackgroundLoad:bSelectivePurgeUnusedOnFastTravel=1\n"
            "BackgroundLoad:bUseMultiThreadedFaceGen=0\n"
            "Archive:bInvalidateOlderFiles=1\n"
            "Archive:SArchiveList=Fallout - Textures.bsa, Fallout - Textures2.bsa, Fallout - Meshes.bsa, Fallout - Voices1.bsa, Fallout - Sound.bsa, Fallout - Misc.bsa"
        )
        a = Asm(S, T, F, M)
        a.iget_o(0, 5, (C_TERM, "c", C_LS6))
        a.if_eqz(0, "done")
        a.iget_o(1, 0, (C_LS6, "m", C_STR))  # v1 = cur customIni
        a.if_eqz(1, "set_fresh")
        a.cs(2, "SArchiveList")
        a.iv((C_STR, "contains", "Z", (C_CS,)), 1, 2)
        a.mr(3)
        a.if_nez(3, "done")
        a.iv((C_STR, "length", "I", ()), 1)
        a.mr(3)
        a.if_eqz(3, "set_fresh")
        # append
        a.new(2, C_SB)
        a.id_((C_SB, "<init>", "V", (C_STR,)), 2, 1)
        a.cs(3, "\n")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.cs(3, AUDIO_INI)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_SB, "toString", C_STR, ()), 2)
        a.mro(2)
        a.goto16("save")
        a.label("set_fresh")
        a.cs(2, AUDIO_INI)
        a.label("save")
        a.iput_o(2, 0, (C_LS6, "m", C_STR))
        a.c4(3, 0)
        a.iv((C_LS6, "c", C_LR6, (C_TERM, "Z")), 0, 5, 3)
        a.mro(2)
        a.iput_o(2, 5, (C_TERM, "p", C_LR6))
        a.label("done")
        a.ret_v()
        m_ns_audio = (C_SETUP, "ensureAudioIni", "V", (C_TERM,), 0x0009, 6, 1, 3, *a.finish())

        # NativeSetup.makeBtn(TermActivity, LinearLayout, String text, boolean ok, int action)V
        # regs=9, ins=5, outs=5 -> v4=act, v5=ll, v6=text, v7=ok, v8=action
        a = Asm(S, T, F, M)
        a.new(0, C_BTN)
        a.id_((C_BTN, "<init>", "V", (C_CTX,)), 0, 4)
        a.iv((C_TV, "setText", "V", (C_CS,)), 0, 6)
        a.c4(1, 0)
        a.iv((C_TV, "setAllCaps", "V", ("Z",)), 0, 1)
        # colors: if ok -> dark green bg 0xFF102A1A, text 0xFF22E57A; else -> bg 0xFF1A2E20, text 0xFFE4F2E8
        a.if_eqz(7, "not_ok")
        a.c32(1, 0xFF102A1A)
        a.c32(2, 0xFF22E57A)
        a.goto16("apply_c")
        a.label("not_ok")
        a.c32(1, 0xFF1A2E20)
        a.c32(2, 0xFFE4F2E8)
        a.label("apply_c")
        a.iv((C_VIEW, "setBackgroundColor", "V", ("I",)), 0, 1)
        a.iv((C_TV, "setTextColor", "V", ("I",)), 0, 2)
        a.c16(1, 24); a.c16(2, 20)
        a.iv((C_VIEW, "setPadding", "V", ("I", "I", "I", "I")), 0, 1, 2, 1, 2)
        a.new(1, C_CLICK)
        a.id_((C_CLICK, "<init>", "V", (C_TERM, "I")), 1, 4, 8)
        a.iv((C_VIEW, "setOnClickListener", "V", (C_OCL,)), 0, 1)
        a.new(1, C_LLP)
        a.c4(2, -1); a.c4(3, -2)
        a.id_((C_LLP, "<init>", "V", ("I", "I")), 1, 2, 3)
        a.c16(2, 14)
        a.iput(2, 1, (C_LLP, "topMargin", "I"))
        a.iv((C_LL, "addView", "V", (C_VIEW, C_VGLP)), 5, 0, 1)
        a.ret_v()
        m_ns_btn = (C_SETUP, "makeBtn", "V", (C_TERM, C_LL, C_STR, "Z", "I"), 0x0009, 9, 5, 5, *a.finish())

        # NativeSetup.checkAndShowSetup(TermActivity)V
        # regs=11, ins=1, outs=5 -> v10=act
        a = Asm(S, T, F, M)
        # Always close previous dialog if open
        a.sget_o(0, F_DLG)
        a.if_eqz(0, "no_old")
        a.label("td0")
        a.iv((C_AD, "dismiss", "V", ()), 0)
        a.label("td1")
        a.goto16("clr_old")
        a.label("hd0")
        a.mex(0)
        a.label("clr_old")
        a.c4(0, 0)
        a.sput_o(0, F_DLG)
        a.label("no_old")
        a.try_catch("td0", "td1", "hd0")

        # If dismissed -> return
        a.sget_z(0, F_DISMISSED)
        a.if_nez(0, "ret_done")

        # v7 = hasFiles
        a.sget(0, (C_VER, "SDK_INT", "I"))
        a.c16(1, 30)
        a.if_lt(0, 1, "files_ok")
        a.is_((C_ENV, "isExternalStorageManager", "Z", ()), )
        a.mr(7)
        a.goto16("chk_bat")
        a.label("files_ok")
        a.c4(7, 1)

        # v8 = hasBattery
        a.label("chk_bat")
        a.sget(0, (C_VER, "SDK_INT", "I"))
        a.c16(1, 23)
        a.if_lt(0, 1, "bat_ok")
        a.cs(1, "power")
        a.iv((C_CTX, "getSystemService", C_OBJ, (C_STR,)), 10, 1)
        a.mro(0)
        a.if_eqz(0, "bat_ok")
        a.check_cast(0, C_PM)
        a.iv((C_CTX, "getPackageName", C_STR, ()), 10)
        a.mro(1)
        a.iv((C_PM, "isIgnoringBatteryOptimizations", "Z", (C_STR,)), 0, 1)
        a.mr(8)
        a.goto16("chk_game")
        a.label("bat_ok")
        a.c4(8, 1)

        # v9 = hasGame
        a.label("chk_game")
        a.c4(9, 0)
        a.iget_o(0, 10, (C_TERM, "c", C_LS6))
        a.if_eqz(0, "chk_all")
        a.iv((C_LS6, "a", C_LR6, ()), 0)
        a.mro(0)
        a.is_((C_LW2, "g", C_FILE, (C_LR6,)), 0)
        a.mro(0)
        a.if_eqz(0, "chk_all")
        a.c4(9, 1)

        # If hasFiles && hasBat && hasGame -> return
        a.label("chk_all")
        a.if_eqz(7, "show_dlg")
        a.if_eqz(8, "show_dlg")
        a.if_eqz(9, "show_dlg")
        a.ret_v()

        # Build Pip-Boy setup dialog
        a.label("show_dlg")
        a.new(5, C_SV)
        a.id_((C_SV, "<init>", "V", (C_CTX,)), 5, 10)
        a.c32(0, 0xFF070E09)
        a.iv((C_VIEW, "setBackgroundColor", "V", ("I",)), 5, 0)

        a.new(6, C_LL)
        a.id_((C_LL, "<init>", "V", (C_CTX,)), 6, 10)
        a.c4(0, 1)
        a.iv((C_LL, "setOrientation", "V", ("I",)), 6, 0)
        a.c16(0, 40)
        a.iv((C_VIEW, "setPadding", "V", ("I", "I", "I", "I")), 6, 0, 0, 0, 0)

        # Header TextView
        a.new(0, C_TV)
        a.id_((C_TV, "<init>", "V", (C_CTX,)), 0, 10)
        a.cs(1, "FALLOUT DROID NV — НАСТРОЙКА")
        a.iv((C_TV, "setText", "V", (C_CS,)), 0, 1)
        a.c32(1, 0xFF22E57A)
        a.iv((C_TV, "setTextColor", "V", ("I",)), 0, 1)
        a.c32(1, 0x41900000)  # 18.0f
        a.iv((C_TV, "setTextSize", "V", ("F",)), 0, 1)
        a.sget_o(1, (C_TF, "DEFAULT_BOLD", C_TF))
        a.iv((C_TV, "setTypeface", "V", (C_TF,)), 0, 1)
        a.iv((C_LL, "addView", "V", (C_VIEW,)), 6, 0)

        # Subtitle TextView
        a.new(0, C_TV)
        a.id_((C_TV, "<init>", "V", (C_CTX,)), 0, 10)
        a.cs(1, "Выдайте доступ к файлам, отключите ограничения батареи и выберите папку с Fallout: New Vegas (GOG 1.4.0.525).")
        a.iv((C_TV, "setText", "V", (C_CS,)), 0, 1)
        a.c32(1, 0xFF8FB09A)
        a.iv((C_TV, "setTextColor", "V", ("I",)), 0, 1)
        a.c32(1, 0x41500000)  # 13.0f
        a.iv((C_TV, "setTextSize", "V", ("F",)), 0, 1)
        a.c4(1, 0); a.c16(2, 12); a.c16(3, 18)
        a.iv((C_VIEW, "setPadding", "V", ("I", "I", "I", "I")), 0, 1, 2, 1, 3)
        a.iv((C_LL, "addView", "V", (C_VIEW,)), 6, 0)

        # Button 1: Files
        a.if_eqz(7, "b1_no")
        a.cs(2, "✓ 1. Доступ ко всем файлам выдан")
        a.goto16("b1_go")
        a.label("b1_no")
        a.cs(2, "1. Выдать доступ ко всем файлам")
        a.label("b1_go")
        a.c4(3, 0)
        a.is_((C_SETUP, "makeBtn", "V", (C_TERM, C_LL, C_STR, "Z", "I")), 10, 6, 2, 7, 3)

        # Button 2: Battery
        a.if_eqz(8, "b2_no")
        a.cs(2, "✓ 2. Ограничение батареи отключено")
        a.goto16("b2_go")
        a.label("b2_no")
        a.cs(2, "2. Отключить ограничение батареи")
        a.label("b2_go")
        a.c4(3, 1)
        a.is_((C_SETUP, "makeBtn", "V", (C_TERM, C_LL, C_STR, "Z", "I")), 10, 6, 2, 8, 3)

        # Button 3: Folder picker via Explorer
        a.if_eqz(9, "b3_no")
        a.cs(2, "✓ 3. Папка Fallout NV найдена (изменить)")
        a.goto16("b3_go")
        a.label("b3_no")
        a.cs(2, "3. Выбрать папку Fallout NV в проводнике")
        a.label("b3_go")
        a.c4(3, 2)
        a.is_((C_SETUP, "makeBtn", "V", (C_TERM, C_LL, C_STR, "Z", "I")), 10, 6, 2, 9, 3)

        # Button 4: Continue / Close
        a.cs(2, "Продолжить")
        a.c4(1, 1); a.c4(3, 3)
        a.is_((C_SETUP, "makeBtn", "V", (C_TERM, C_LL, C_STR, "Z", "I")), 10, 6, 2, 1, 3)

        a.iv((C_SV, "addView", "V", (C_VIEW,)), 5, 6)

        # Show AlertDialog
        a.new(0, C_ADB)
        a.id_((C_ADB, "<init>", "V", (C_CTX,)), 0, 10)
        a.iv((C_ADB, "setView", C_ADB, (C_VIEW,)), 0, 5)
        a.c4(1, 1)
        a.iv((C_ADB, "setCancelable", C_ADB, ("Z",)), 0, 1)
        a.iv((C_ADB, "create", C_AD, ()), 0)
        a.mro(0)
        a.sput_o(0, F_DLG)
        a.iv((C_AD, "show", "V", ()), 0)
        a.label("ret_done")
        a.ret_v()
        m_ns_show = (C_SETUP, "checkAndShowSetup", "V", (C_TERM,), 0x0009, 11, 1, 5, *a.finish())


        # NativeSetup.exportLog(Context)V  regs=7, ins=1, outs=3 -> v6=ctx
        # Copies <externalFilesDir>/last_run.log (game log, not reachable on
        # Android 11+ without adb) to /sdcard/FalloutDroidNV/last_run.log.
        C_PATH = "Ljava/nio/file/Path;"
        C_COPT_A = "[Ljava/nio/file/CopyOption;"
        C_SCO = "Ljava/nio/file/StandardCopyOption;"
        a = Asm(S, T, F, M)
        a.label("el0")
        a.c4(0, 0)
        a.iv((C_CTX, "getExternalFilesDir", C_FILE, (C_STR,)), 6, 0)
        a.mro(0)
        a.if_eqz(0, "el_ret")
        a.new(1, C_FILE)
        a.cs(2, "last_run.log")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 1, 0, 2)
        a.iv((C_FILE, "exists", "Z", ()), 1)
        a.mr(2)
        a.if_eqz(2, "el_ret")
        a.is_((C_ENV, "getExternalStorageDirectory", C_FILE, ()), )
        a.mro(0)
        a.new(3, C_FILE)
        a.cs(2, "FalloutDroidNV")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 3, 0, 2)
        a.iv((C_FILE, "mkdirs", "Z", ()), 3)
        a.new(4, C_FILE)
        a.cs(2, "last_run.log")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 4, 3, 2)
        a.iv((C_FILE, "toPath", C_PATH, ()), 1)
        a.mro(1)
        a.iv((C_FILE, "toPath", C_PATH, ()), 4)
        a.mro(4)
        a.c4(2, 1)
        a.new_arr(5, 2, C_COPT_A)
        a.sget_o(2, (C_SCO, "REPLACE_EXISTING", C_SCO))
        a.c4(0, 0)
        a.aput_o(2, 5, 0)
        a.is_(("Ljava/nio/file/Files;", "copy", C_PATH, (C_PATH, C_PATH, C_COPT_A)), 1, 4, 5)
        a.label("el_ret")
        a.label("el1")
        a.ret_v()
        a.label("hel0")
        a.mex(0)
        a.ret_v()
        a.try_catch("el0", "el1", "hel0")
        m_ns_exportlog = (C_SETUP, "exportLog", "V", (C_CTX,), 0x0009, 7, 1, 3, *a.finish())

        # NativeSetup.applyDebugEnv()V  regs=3, ins=0, outs=3
        # /sdcard/FalloutDroidNV/trace_hle exists -> FNV_TRACE=1 (full HLE API
        # trace into the game log; very slow, diagnostics only).
        a = Asm(S, T, F, M)
        a.label("de0")
        a.is_((C_ENV, "getExternalStorageDirectory", C_FILE, ()), )
        a.mro(0)
        a.new(1, C_FILE)
        a.cs(2, "FalloutDroidNV/trace_hle")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 1, 0, 2)
        a.iv((C_FILE, "exists", "Z", ()), 1)
        a.mr(0)
        a.if_eqz(0, "de_ret")
        a.cs(0, "FNV_TRACE")
        a.cs(1, "1")
        a.c4(2, 1)
        a.is_((C_OS, "setenv", "V", (C_STR, C_STR, "Z")), 0, 1, 2)
        a.label("de_ret")
        a.label("de1")
        a.ret_v()
        a.label("hde0")
        a.mex(0)
        a.ret_v()
        a.try_catch("de0", "de1", "hde0")
        m_ns_debugenv = (C_SETUP, "applyDebugEnv", "V", (), 0x0009, 3, 0, 3, *a.finish())

        # ---- Class 3: FalloutDroidActivity (extends TermActivity) ----
        # <init>()V  regs=1, ins=1, outs=1
        a = Asm(S, T, F, M)
        a.id_((C_TERM, "<init>", "V", ()), 0)
        a.ret_v()
        m_act_init = (C_ACT, "<init>", "V", (), 0x10001, 1, 1, 1, *a.finish())

        # onResume()V  regs=2, ins=1, outs=1  (v1=this, v0=ex)
        a = Asm(S, T, F, M)
        a.isup((C_TERM, "onResume", "V", ()), 1)
        a.label("tr0")
        a.is_((C_SETUP, "ensureAudioIni", "V", (C_TERM,)), 1)
        a.is_((C_SETUP, "exportLog", "V", (C_CTX,)), 1)
        a.is_((C_SETUP, "checkAndShowSetup", "V", (C_TERM,)), 1)
        a.label("tr1")
        a.ret_v()
        a.label("hr0")
        a.mex(0)
        a.ret_v()
        a.try_catch("tr0", "tr1", "hr0")
        m_act_res = (C_ACT, "onResume", "V", (), 0x0001, 2, 1, 1, *a.finish())

        # onActivityResult(I, I, Intent)V  regs=5, ins=4, outs=4 -> v1=this, v2=req, v3=res, v4=data
        a = Asm(S, T, F, M)
        a.is_((C_SETUP, "handleActivityResult", "Z", (C_TERM, "I", "I", C_INTENT)), 1, 2, 3, 4)
        a.mr(0)
        a.if_nez(0, "handled")
        a.isup((C_TERM, "onActivityResult", "V", ("I", "I", C_INTENT)), 1, 2, 3, 4)
        a.label("handled")
        a.ret_v()
        m_act_resact = (C_ACT, "onActivityResult", "V", ("I", "I", C_INTENT), 0x0001, 5, 4, 4, *a.finish())


        
        # NativeSetup.fixAudioPrefs(File gameDir, StringBuilder sb)V
        # regs=10, ins=2, outs=3 -> v8=gameDir, v9=sb
        a = Asm(S, T, F, M)
        a.label("fp0")
        a.new(0, C_FILE)
        a.cs(1, "UserDocuments/My Games/FalloutNV/FalloutPrefs.ini")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 0, 8, 1)
        a.cs(1, "\nFalloutPrefs.ini exists=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 1)
        a.iv((C_FILE, "exists", "Z", ()), 0)
        a.mr(2)
        a.iv((C_SB, "append", C_SB, ("Z",)), 9, 2)
        a.if_eqz(2, "fp_ret")
        # Read file into byte[] (up to length)
        a.iv((C_FILE, "length", "J", ()), 0)
        a.emit(0x0b | (2 << 8))  # move-result-wide v2,v3
        a.emit(0x84 | (2 << 8) | (2 << 12))  # long-to-int v2, v2
        a.if_eqz(2, "fp_ret")
        a.new_arr(3, 2, C_BA)
        a.new(4, C_FIS)
        a.id_((C_FIS, "<init>", "V", (C_FILE,)), 4, 0)
        a.iv((C_FIS, "read", "I", (C_BA,)), 4, 3)
        a.iv((C_FIS, "close", "V", ()), 4)
        a.new(4, C_STR)
        a.id_((C_STR, "<init>", "V", (C_BA,)), 4, 3)  # v4 = prefs text
        # Log if [AudioMenu] exists
        a.cs(1, "[AudioMenu]")
        a.iv((C_STR, "indexOf", "I", (C_STR,)), 4, 1)
        a.mr(5)
        a.cs(1, " audioMenuIdx=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 1)
        a.iv((C_SB, "append", C_SB, ("I",)), 9, 5)
        # If found, log a 180-char slice of [AudioMenu] BEFORE patching
        a.c4(6, 0)
        a.if_lt(5, 6, "fp_append_sec")
        a.iv((C_STR, "length", "I", ()), 4)
        a.mr(6)
        a.add_lit8(7, 5, 120)
        a.add_lit8(7, 7, 80)
        a.if_lt(7, 6, "fp_sub")
        a.mov(7, 6)
        a.label("fp_sub")
        a.iv((C_STR, "substring", C_STR, ("I", "I")), 4, 5, 7)
        a.mro(6)
        a.cs(1, "\n--- Prefs [AudioMenu] BEFORE ---\n")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 6)
        # Unmute ONLY volumes that are exactly 0 (v1.3.0: user volume settings are kept)
        a.cs(1, "(?m)^fAudioMasterVolume=[0.]*\\s*$")
        a.cs(2, "fAudioMasterVolume=1.0000")
        a.iv((C_STR, "replaceAll", C_STR, (C_STR, C_STR)), 4, 1, 2)
        a.mro(4)
        a.cs(1, "(?m)^fVal([0-5])=[0.]*\\s*$")
        a.cs(2, "fVal$1=1.0000")
        a.iv((C_STR, "replaceAll", C_STR, (C_STR, C_STR)), 4, 1, 2)
        a.mro(4)
        a.cs(1, "(?m)^bEnableAudio=0\\s*$")
        a.cs(2, "bEnableAudio=1")
        a.iv((C_STR, "replaceAll", C_STR, (C_STR, C_STR)), 4, 1, 2)
        a.mro(4)
        a.goto16("fp_write")
        a.label("fp_append_sec")
        a.new(6, C_SB)
        a.id_((C_SB, "<init>", "V", (C_STR,)), 6, 4)
        a.cs(1, "\n[AudioMenu]\nfAudioMasterVolume=1.0000\nfVal0=1.0000\nfVal1=1.0000\nfVal2=1.0000\nfVal3=1.0000\nfVal4=0.6000\nfVal5=0.8000\n")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 6, 1)
        a.iv((C_SB, "toString", C_STR, ()), 6)
        a.mro(4)
        a.label("fp_write")
        # Log [AudioMenu] AFTER patching
        a.cs(1, "[AudioMenu]")
        a.iv((C_STR, "indexOf", "I", (C_STR,)), 4, 1)
        a.mr(5)
        a.c4(6, 0)
        a.if_lt(5, 6, "fp_do_write")
        a.iv((C_STR, "length", "I", ()), 4)
        a.mr(6)
        a.add_lit8(7, 5, 120)
        a.add_lit8(7, 7, 80)
        a.if_lt(7, 6, "fp_sub2")
        a.mov(7, 6)
        a.label("fp_sub2")
        a.iv((C_STR, "substring", C_STR, ("I", "I")), 4, 5, 7)
        a.mro(6)
        a.cs(1, "\n--- Prefs [AudioMenu] AFTER ---\n")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 1)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 6)
        a.label("fp_do_write")
        a.new(5, C_FOS)
        a.id_((C_FOS, "<init>", "V", (C_FILE,)), 5, 0)
        a.iv((C_STR, "getBytes", C_BA, ()), 4)
        a.mro(6)
        a.iv((C_FOS, "write", "V", (C_BA,)), 5, 6)
        a.iv((C_FOS, "close", "V", ()), 5)
        a.cs(1, "\nFalloutPrefs.ini patched=OK")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 9, 1)
        a.label("fp_ret")
        a.label("fp1")
        a.ret_v()
        a.label("hfp0")
        a.mex(0)
        a.ret_v()
        a.try_catch("fp0", "fp1", "hfp0")
        m_ns_fixprefs = (C_SETUP, "fixAudioPrefs", "V", (C_FILE, C_SB), 0x0009, 10, 2, 3, *a.finish())

        # NativeSetup.diagAudio(TermActivity)V  regs=10, ins=1, outs=3 -> v9=act
        a = Asm(S, T, F, M)
        a.label("da0")
        a.iget_o(0, 9, (C_TERM, "c", C_LS6))
        a.if_eqz(0, "da_ret")
        a.iv((C_LS6, "a", C_LR6, ()), 0)
        a.mro(0)
        a.is_((C_LW2, "g", C_FILE, (C_LR6,)), 0)
        a.mro(1)  # v1 = gameDir File
        a.if_eqz(1, "da_ret")
        a.new(2, C_SB)
        a.cs(3, "=== FNV AUDIO DIAG ===\nGameDir: ")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 2, 3)
        a.iv((C_FILE, "getAbsolutePath", C_STR, ()), 1)
        a.mro(3)
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        # Check Data/Fallout - Sound.bsa
        a.new(4, C_FILE)
        a.cs(3, "Data/Fallout - Sound.bsa")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 4, 1, 3)
        a.cs(3, "\nSound.bsa exists=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_FILE, "exists", "Z", ()), 4)
        a.mr(5)
        a.iv((C_SB, "append", C_SB, ("Z",)), 2, 5)
        a.cs(3, " size=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_FILE, "length", "J", ()), 4)
        a.emit(0x0b | (6 << 8))  # move-result-wide v6, v7
        a.iv((C_SB, "append", C_SB, ("J",)), 2, 6, 7)
        # Check Data/Fallout - Voices1.bsa
        a.new(4, C_FILE)
        a.cs(3, "Data/Fallout - Voices1.bsa")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 4, 1, 3)
        a.cs(3, "\nVoices1.bsa size=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_FILE, "length", "J", ()), 4)
        a.emit(0x0b | (6 << 8))
        a.iv((C_SB, "append", C_SB, ("J",)), 2, 6, 7)
        # Check Data/Music/Special/MainTitle.mp3
        a.new(4, C_FILE)
        a.cs(3, "Data/Music/Special/MainTitle.mp3")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 4, 1, 3)
        a.cs(3, "\nMainTitle.mp3 size=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_FILE, "length", "J", ()), 4)
        a.emit(0x0b | (6 << 8))
        a.iv((C_SB, "append", C_SB, ("J",)), 2, 6, 7)
        # Check FalloutNV.exe size
        a.new(4, C_FILE)
        a.cs(3, "FalloutNV.exe")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 4, 1, 3)
        a.cs(3, "\nFalloutNV.exe size=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_FILE, "length", "J", ()), 4)
        a.emit(0x0b | (6 << 8))
        a.iv((C_SB, "append", C_SB, ("J",)), 2, 6, 7)
        # Check AudioManager STREAM_MUSIC vol/max/mode
        a.cs(3, "audio")
        a.iv((C_TERM, "getSystemService", C_OBJ, (C_STR,)), 9, 3)
        a.mro(4)
        a.if_eqz(4, "da_write")
        a.check_cast(4, C_AUDM)
        a.c4(0, 3)
        a.iv((C_AUDM, "getStreamVolume", "I", ("I",)), 4, 0)
        a.mr(6)
        a.iv((C_AUDM, "getStreamMaxVolume", "I", ("I",)), 4, 0)
        a.mr(7)
        a.iv((C_AUDM, "getMode", "I", ()), 4)
        a.mr(8)
        a.cs(3, "\nAudioManager vol=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_SB, "append", C_SB, ("I",)), 2, 6)
        a.cs(3, "/")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_SB, "append", C_SB, ("I",)), 2, 7)
        a.cs(3, " mode=")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 2, 3)
        a.iv((C_SB, "append", C_SB, ("I",)), 2, 8)
        a.label("da_write")
        a.is_((C_SETUP, "fixAudioPrefs", "V", (C_FILE, C_SB)), 1, 2)
        # Log to Logcat only (v1.3.0: no more fnv_audio_diag.txt in Download/)
        a.iv((C_SB, "toString", C_STR, ()), 2)
        a.mro(3)  # v3 = report string
        a.cs(4, "FNV_AUDIO")
        a.is_((C_LOG, "i", "I", (C_STR, C_STR)), 4, 3)
        # If Sound.bsa missing (v5 == 0), show Toast warning!
        a.if_nez(5, "da_ret")
        a.cs(3, "Внимание: Data/Fallout - Sound.bsa не найден!")
        a.c4(4, 1)
        a.is_((C_TOAST, "makeText", C_TOAST, (C_CTX, C_CS, "I")), 9, 3, 4)
        a.mro(3)
        a.iv((C_TOAST, "show", "V", ()), 3)
        a.label("da_ret")
        a.label("da1")
        a.ret_v()
        a.label("hda0")
        a.mex(0)
        a.ret_v()
        a.try_catch("da0", "da1", "hda0")
        m_ns_diag = (C_SETUP, "diagAudio", "V", (C_TERM,), 0x0009, 10, 1, 3, *a.finish())

        # l()V  regs=1, ins=1, outs=1  (Home screen "Играть" -> ensureAudioIni + diagAudio + super.l()!)
        a = Asm(S, T, F, M)
        a.is_((C_SETUP, "ensureAudioIni", "V", (C_TERM,)), 0)
        a.is_((C_SETUP, "diagAudio", "V", (C_TERM,)), 0)
        a.isup((C_TERM, "l", "V", ()), 0)
        a.ret_v()
        m_act_l = (C_ACT, "l", "V", (), 0x0001, 1, 1, 1, *a.finish())

        # j(String, String, String, Z, Lt6)V  regs=8, ins=6, outs=6 -> v2=this, v3=title, v4=hint, v5=val, v6=multi, v7=cb
        a = Asm(S, T, F, M)
        a.cs(0, "ПАПКА ИГРЫ")
        a.iv((C_STR, "equals", "Z", (C_OBJ,)), 0, 3)
        a.mr(1)
        a.if_eqz(1, "call_super")
        a.is_((C_SETUP, "openFolderPicker", "V", (C_TERM,)), 2)
        a.ret_v()
        a.label("call_super")
        a.isup_r((C_TERM, "j", "V", (C_STR, C_STR, C_STR, "Z", C_LT6)), 2, 6)
        a.ret_v()
        m_act_j = (C_ACT, "j", "V", (C_STR, C_STR, C_STR, "Z", C_LT6), 0x0001, 8, 6, 6, *a.finish())

        # r()V  regs=1, ins=1, outs=1  (Home screen "Указать папку игры" -> Explorer!)
        a = Asm(S, T, F, M)
        a.is_((C_SETUP, "openFolderPicker", "V", (C_TERM,)), 0)
        a.ret_v()
        m_act_r = (C_ACT, "r", "V", (), 0x0001, 1, 1, 1, *a.finish())

        for m in [m_ns_exportlog, m_ns_debugenv, m_clk_init, m_clk_click, m_ns_diag, m_ns_dismiss, m_ns_bat, m_ns_pick, m_ns_res, m_ns_resact, m_ns_audio, m_ns_btn, m_ns_show,
                  m_act_init, m_act_res, m_act_resact, m_act_l, m_act_j, m_act_r]:
            M(m[:4])


        # ---- Class 4: FalloutDroidFactory (extends CoreComponentFactory) ----
        for t in [C_FACT, C_CCF, C_GACT, C_OGACT, C_HDV, C_VIEW, C_AUDM, C_AFCL, C_FIS, C_THR, C_ATR, C_ATT]:
            T(t)

        # <init>()V  regs=1, ins=1, outs=1
        a = Asm(S, T, F, M)
        a.id_((C_CCF, "<init>", "V", ()), 0)
        a.ret_v()
        m_fac_init = (C_FACT, "<init>", "V", (), 0x10001, 1, 1, 1, *a.finish())

        # instantiateActivity(ClassLoader, String, Intent)Activity  regs=6, ins=4, outs=4 -> v2=this, v3=cl, v4=cls, v5=intent
        a = Asm(S, T, F, M)
        a.cs(0, "com.fnvport.GameActivity")
        a.iv((C_STR, "equals", "Z", (C_OBJ,)), 0, 4)
        a.mr(1)
        a.if_eqz(1, "sup_act")
        a.new(0, C_GACT)
        a.id_((C_GACT, "<init>", "V", ()), 0)
        a.ret_o(0)
        a.label("sup_act")
        a.isup((C_CCF, "instantiateActivity", C_ACT_FW, (C_CL, C_STR, C_INTENT)), 2, 3, 4, 5)
        a.mro(0)
        a.ret_o(0)
        m_fac_inst = (C_FACT, "instantiateActivity", C_ACT_FW, (C_CL, C_STR, C_INTENT), 0x0001, 6, 4, 4, *a.finish())

        # ---- Class 5: FalloutDroidGameActivity (extends com.fnvport.GameActivity) ----
        # <init>()V  regs=1, ins=1, outs=1
        a = Asm(S, T, F, M)
        a.id_((C_OGACT, "<init>", "V", ()), 0)
        a.ret_v()
        m_ga_init = (C_GACT, "<init>", "V", (), 0x10001, 1, 1, 1, *a.finish())

        # onCreate(Bundle)V  regs=6, ins=2, outs=3 -> v4=this, v5=bundle
        a = Asm(S, T, F, M)
        a.is_((C_SETUP, "applyDebugEnv", "V", ()), )
        a.label("te0")
        # FNV_TRACE_FILES debug tracing removed (v1.3.0): it logged every file
        # access of the game and cost I/O + CPU on every device.
        # Default audio path = native AAudio (float, limiter in libfnv.so).
        # The PCM16 FIFO -> AudioTrack bridge is opt-in for problem devices:
        #   create /sdcard/FalloutDroidNV/use_audiotrack
        a.is_((C_ENV, "getExternalStorageDirectory", C_FILE, ()), )
        a.mro(0)
        a.new(1, C_FILE)
        a.cs(2, "FalloutDroidNV/use_audiotrack")
        a.id_((C_FILE, "<init>", "V", (C_FILE, C_STR)), 1, 0, 2)
        a.iv((C_FILE, "exists", "Z", ()), 1)
        a.mr(0)
        a.if_eqz(0, "te1")
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
        a.c16(2, 10)      # Thread.MAX_PRIORITY
        a.iv((C_THR, "setPriority", "V", ("I",)), 0, 2)
        a.iv((C_THR, "start", "V", ()), 0)
        a.label("te1")
        a.goto16("after_env")
        a.label("he0")
        a.mex(0)
        a.label("after_env")
        a.try_catch("te0", "te1", "he0")
        a.label("ta0")
        a.c4(0, 3)
        a.iv((C_GACT, "setVolumeControlStream", "V", ("I",)), 4, 0)
        a.cs(0, "audio")
        a.iv((C_GACT, "getSystemService", C_OBJ, (C_STR,)), 4, 0)
        a.mro(0)
        a.if_eqz(0, "ta1")
        a.check_cast(0, C_AUDM)
        a.c4(1, 0)
        a.c4(2, 3)
        a.c4(3, 1)
        a.iv((C_AUDM, "requestAudioFocus", "I", (C_AFCL, "I", "I")), 0, 1, 2, 3)
        a.label("ta1")
        a.goto16("after_aud")
        a.label("ha0")
        a.mex(0)
        a.label("after_aud")
        a.try_catch("ta0", "ta1", "ha0")
        a.isup((C_OGACT, "onCreate", "V", (C_BND,)), 4, 5)
        a.label("tg0")
        a.iget_o(0, 4, (C_OGACT, "b", C_LM8))
        a.if_eqz(0, "tg_done")
        a.iv((C_VIEW, "getParent", C_VP, ()), 0)
        a.mro(1)
        a.inst_of(2, 1, C_VG)
        a.if_eqz(2, "tg_done")
        a.check_cast(1, C_VG)
        a.new(2, C_HDV)
        a.id_((C_HDV, "<init>", "V", (C_CTX, C_LM8)), 2, 4, 0)
        a.new(3, C_VGLP)
        a.c4(0, -1)
        a.id_((C_VGLP, "<init>", "V", ("I", "I")), 3, 0, 0)
        a.iv((C_VG, "addView", "V", (C_VIEW, C_VGLP)), 1, 2, 3)
        a.label("tg_done")
        a.label("tg1")
        a.ret_v()
        a.label("hg0")
        a.mex(0)
        a.ret_v()
        a.try_catch("tg0", "tg1", "hg0")
        m_ga_create = (C_GACT, "onCreate", "V", (C_BND,), 0x0001, 6, 2, 4, *a.finish())

        # ---- Class 6: HdIconsView (extends android.view.View) ----
        F_HD_TOUCH = (C_HDV, "a", C_LM8)
        F_HD_BG = (C_HDV, "b", C_PNT)
        F_HD_RING = (C_HDV, "c", C_PNT)
        F_HD_BMPP = (C_HDV, "d", C_PNT)
        F_HD_RF = (C_HDV, "e", C_RECTF)
        F_HD_ICONS = (C_HDV, "s", C_BMPA)
        for ff in [F_HD_TOUCH, F_HD_BG, F_HD_RING, F_HD_BMPP, F_HD_RF, F_HD_ICONS]:
            F(ff)

        # HdIconsView.loadIcons(Context)V  regs=8, ins=1, outs=3 -> v7=ctx
        a = Asm(S, T, F, M)
        a.sget_o(0, F_HD_ICONS)
        a.if_nez(0, "li_ret")
        a.c16(1, 24)
        a.new_arr(0, 1, C_BMPA)
        a.sput_o(0, F_HD_ICONS)
        a.iv((C_CTX, "getAssets", C_AM, ()), 7)
        a.mro(2)  # v2 = AssetManager
        a.c4(3, 0)  # v3 = i = 0
        a.label("li_loop")
        a.if_ge(3, 1, "li_ret")
        a.label("tl0")
        a.new(4, C_SB)
        a.cs(5, "controls/icon_")
        a.id_((C_SB, "<init>", "V", (C_STR,)), 4, 5)
        a.iv((C_SB, "append", C_SB, ("I",)), 4, 3)
        a.cs(5, ".png")
        a.iv((C_SB, "append", C_SB, (C_STR,)), 4, 5)
        a.iv((C_SB, "toString", C_STR, ()), 4)
        a.mro(4)
        a.iv((C_AM, "open", C_IS, (C_STR,)), 2, 4)
        a.mro(4)  # v4 = InputStream
        a.is_((C_BMPF, "decodeStream", C_BMP, (C_IS,)), 4)
        a.mro(5)  # v5 = Bitmap
        a.iv((C_IS, "close", "V", ()), 4)
        a.aput_o(5, 0, 3)
        a.label("tl1")
        a.goto16("li_next")
        a.label("hl0")
        a.mex(4)
        a.label("li_next")
        a.add_lit8(3, 3, 1)
        a.goto16("li_loop")
        a.label("li_ret")
        a.ret_v()
        a.try_catch("tl0", "tl1", "hl0")
        m_hd_load = (C_HDV, "loadIcons", "V", (C_CTX,), 0x0009, 8, 1, 3, *a.finish())

        # HdIconsView.<init>(Context, Lm8)V  regs=6, ins=3, outs=2 -> v3=this, v4=ctx, v5=touch
        a = Asm(S, T, F, M)
        a.id_((C_VIEW, "<init>", "V", (C_CTX,)), 3, 4)
        a.iput_o(5, 3, F_HD_TOUCH)
        a.c4(0, 0)
        a.iv((C_VIEW, "setClickable", "V", ("Z",)), 3, 0)
        a.iv((C_VIEW, "setFocusable", "V", ("Z",)), 3, 0)
        a.is_((C_HDV, "loadIcons", "V", (C_CTX,)), 4)
        # bg paint
        a.new(0, C_PNT)
        a.c4(1, 1)
        a.id_((C_PNT, "<init>", "V", ("I",)), 0, 1)
        a.c32(1, 0xEB0C140F)
        a.iv((C_PNT, "setColor", "V", ("I",)), 0, 1)
        a.sget_o(1, (C_PSTYLE, "FILL", C_PSTYLE))
        a.iv((C_PNT, "setStyle", "V", (C_PSTYLE,)), 0, 1)
        a.iput_o(0, 3, F_HD_BG)
        # ring paint
        a.new(0, C_PNT)
        a.c4(1, 1)
        a.id_((C_PNT, "<init>", "V", ("I",)), 0, 1)
        a.c32(1, 0xB022E57A)
        a.iv((C_PNT, "setColor", "V", ("I",)), 0, 1)
        a.sget_o(1, (C_PSTYLE, "STROKE", C_PSTYLE))
        a.iv((C_PNT, "setStyle", "V", (C_PSTYLE,)), 0, 1)
        a.c32(1, 0x40400000)  # 3.0f strokeWidth
        a.iv((C_PNT, "setStrokeWidth", "V", ("F",)), 0, 1)
        a.iput_o(0, 3, F_HD_RING)
        # bmp paint (anti-alias + filter bitmap = 3)
        a.new(0, C_PNT)
        a.c4(1, 3)
        a.id_((C_PNT, "<init>", "V", ("I",)), 0, 1)
        a.iput_o(0, 3, F_HD_BMPP)
        # RectF
        a.new(0, C_RECTF)
        a.id_((C_RECTF, "<init>", "V", ()), 0)
        a.iput_o(0, 3, F_HD_RF)
        # Hide Lm8's old vector icon paints (touch.r and touch.s) via zero ColorMatrixColorFilter!
        a.label("tf0")
        a.c16(0, 20)
        a.new_arr(0, 0, C_FA)
        a.new(1, C_CMCF)
        a.id_((C_CMCF, "<init>", "V", (C_FA,)), 1, 0)
        a.iget_o(2, 5, (C_LM8, "r", C_PNT))
        a.if_eqz(2, "skip_r")
        a.iv((C_PNT, "setColorFilter", C_CF, (C_CF,)), 2, 1)
        a.label("skip_r")
        a.iget_o(2, 5, (C_LM8, "s", C_PNT))
        a.if_eqz(2, "skip_s")
        a.iv((C_PNT, "setColorFilter", C_CF, (C_CF,)), 2, 1)
        a.label("skip_s")
        a.label("tf1")
        a.ret_v()
        a.label("hf0")
        a.mex(0)
        a.ret_v()
        a.try_catch("tf0", "tf1", "hf0")
        m_hd_init = (C_HDV, "<init>", "V", (C_CTX, C_LM8), 0x10001, 6, 3, 2, *a.finish())

        # HdIconsView.onDraw(Canvas)V  regs=16, ins=2, outs=5 -> v14=this, v15=canvas
        # ONLY draws the transparent HD icon PNG centered inside Lm8's untouched buttons!
        a = Asm(S, T, F, M)
        a.iget_o(0, 14, F_HD_TOUCH)
        a.if_eqz(0, "od_end")
        a.iv((C_LM8, "getHiddenForGamepad", "Z", ()), 0)
        a.mr(3)
        a.if_nez(3, "od_tick")
        a.iv((C_VIEW, "getVisibility", "I", ()), 0)
        a.mr(3)
        a.if_nez(3, "od_tick")
        a.sget_o(1, F_HD_ICONS)
        a.if_eqz(1, "od_tick")
        # Sync paint alpha with Lm8.a.q (buttonOpacity 0..100 -> 0..255)
        a.iget_o(13, 14, F_HD_BMPP)
        a.iget_o(5, 0, (C_LM8, "a", C_LR6))
        a.if_eqz(5, "od_no_a")

        a.iget(6, 5, (C_LR6, "q", "I"))
        a.mul_lit8(7, 6, 2)
        a.div_lit8(8, 6, 2)
        a.emit(0x90 | (6 << 8), (7 & 0xff) | ((8 & 0xff) << 8))  # add-int v6, v7, v8
        a.iv((C_PNT, "setAlpha", "V", ("I",)), 13, 6)
        a.label("od_no_a")
        a.iv((C_LM8, "getControls", C_LIST, ()), 0)
        a.mro(2)
        a.if_eqz(2, "od_tick")
        a.ii((C_LIST, "size", "I", ()), 2)
        a.mr(3)
        a.c4(4, 0)
        a.label("od_loop")
        a.if_ge(4, 3, "od_tick")
        a.ii((C_LIST, "get", C_OBJ, ("I",)), 2, 4)
        a.mro(5)
        a.check_cast(5, C_LR0)
        a.iv((C_LM8, "u", "Z", (C_LR0,)), 0, 5)
        a.mr(6)
        a.if_eqz(6, "od_next")
        # Skip joystick (ctrl.c == Lt0.a)
        a.iget_o(6, 5, (C_LR0, "c", C_LT0))
        a.sget_o(7, (C_LT0, "a", C_LT0))
        a.if_eq(6, 7, "od_next")
        a.iget_o(6, 5, (C_LR0, "j", C_LY2))
        a.if_eqz(6, "od_next")
        a.iv((C_ENUM, "ordinal", "I", ()), 6)
        a.mr(6)
        a.if_ltz(6, "od_next")
        a.c16(12, 24)
        a.if_ge(6, 12, "od_next")
        a.aget_o(6, 1, 6)  # v6 = Bitmap
        a.if_eqz(6, "od_next")
        # Exact Lm8 center (cx, cy) and radius r
        a.iv((C_LM8, "e", "F", (C_LR0,)), 0, 5)
        a.mr(7)
        a.iv((C_LM8, "f", "F", (C_LR0,)), 0, 5)
        a.mr(8)
        a.iv((C_LM8, "o", "F", (C_LR0,)), 0, 5)
        a.mr(9)
        # If buttonLabels (Lm8.a.s) is true and ctrl.b non-empty -> cy -= r * 0.14f (exact Lm8.onDraw offset!)
        a.iget_o(11, 0, (C_LM8, "a", C_LR6))
        a.if_eqz(11, "od_no_lbl")
        a.iget_z(12, 11, (C_LR6, "s", "Z"))
        a.if_eqz(12, "od_no_lbl")
        a.iget_o(11, 5, (C_LR0, "b", C_STR))
        a.if_eqz(11, "od_no_lbl")
        a.iv((C_STR, "length", "I", ()), 11)
        a.mr(12)
        a.if_eqz(12, "od_no_lbl")
        a.c32(12, 0x3E0F5C29)  # 0.14f
        a.mul_f(10, 9, 12)
        a.sub_f(8, 8, 10)
        a.label("od_no_lbl")
        # Draw ONLY the pure transparent HD icon PNG (half-size = r * 0.48f, ZERO black circle!)
        a.c32(12, 0x3EF5C28F)  # 0.48f
        a.mul_f(10, 9, 12)
        a.iget_o(11, 14, F_HD_RF)
        a.sub_f(12, 7, 10)  # left
        a.sub_f(13, 8, 10)  # top
        a.add_f(7, 7, 10)   # right
        a.add_f(8, 8, 10)   # bottom
        a.iv((C_RECTF, "set", "V", ("F", "F", "F", "F")), 11, 12, 13, 7, 8)
        a.c4(12, 0)         # src Rect = null
        a.iget_o(13, 14, F_HD_BMPP)
        a.iv((C_CAN, "drawBitmap", "V", (C_BMP, C_RECT, C_RECTF, C_PNT)), 15, 6, 12, 11, 13)
        a.label("od_next")
        a.add_lit8(4, 4, 1)
        a.goto16("od_loop")
        a.label("od_tick")
        a.c_wide16(10, 120)
        a.iv((C_VIEW, "postInvalidateDelayed", "V", ("J",)), 14, 10, 11)
        a.label("od_end")
        a.ret_v()
        m_hd_draw = (C_HDV, "onDraw", "V", (C_CAN,), 0x0001, 16, 2, 5, *a.finish())

        for m in [m_fac_init, m_fac_inst, m_ga_init, m_ga_create, m_hd_load, m_hd_init, m_hd_draw]:
            M(m[:4])

        # ---- Class 7: FolderClick (implements View$OnClickListener) ----
        T(C_FCLK)
        F_FC_A = (C_FCLK, "a", C_TERM)
        F_FC_B = (C_FCLK, "b", C_STR)
        F_FC_C = (C_FCLK, "c", "Z")
        F(F_FC_A); F(F_FC_B); F(F_FC_C)

        # <init>(TermActivity, String, Z)V  regs=4, ins=4, outs=1 -> v0=this, v1=act, v2=path, v3=isSel
        a = Asm(S, T, F, M)
        a.id_((C_OBJ, "<init>", "V", ()), 0)
        a.iput_o(1, 0, F_FC_A)
        a.iput_o(2, 0, F_FC_B)
        a.iput(3, 0, F_FC_C)  # wait: boolean field uses iput-boolean (0x5c)!
        # Fix: overwrite previous emit with 0x5c (iput-boolean)!
        a.w.pop(); a.w.pop()
        a.emit(0x5c | ((3 & 0xf) << 8) | ((0 & 0xf) << 12), F(F_FC_C))
        a.ret_v()
        m_fc_init = (C_FCLK, "<init>", "V", (C_TERM, C_STR, "Z"), 0x10001, 4, 4, 1, *a.finish())

        # onClick(View)V  regs=5, ins=2, outs=2 -> v3=this, v4=view
        a = Asm(S, T, F, M)
        a.iget_o(0, 3, F_FC_A)
        a.iget_o(1, 3, F_FC_B)
        a.iget_z(2, 3, F_FC_C)
        a.if_eqz(2, "fc_open")
        a.is_((C_SETUP, "selectFolder", "V", (C_TERM, C_STR)), 0, 1)
        a.ret_v()
        a.label("fc_open")
        a.is_((C_SETUP, "browseDir", "V", (C_TERM, C_STR)), 0, 1)
        a.ret_v()
        m_fc_click = (C_FCLK, "onClick", "V", (C_VIEW,), 0x0001, 5, 2, 2, *a.finish())

        for m in [m_ns_selfld, m_ns_fbtn, m_ns_browse, m_ns_fixprefs, m_fc_init, m_fc_click]:
            M(m[:4])

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
        a.c16(1, 12)      # CHANNEL_OUT_STEREO = 12
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
        a.c16(3, 12)      # CHANNEL_OUT_STEREO
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



        return [
            (C_ACT, 0x0001, C_TERM, [], [], [], [m_act_init], [m_act_j, m_act_l, m_act_resact, m_act_res, m_act_r]),
            (C_SETUP, 0x0001, C_OBJ, [], [(F_DISMISSED, 0x0009), (F_DLG, 0x0009), (F_BDLG, 0x0009)], [],
             [m_ns_show, m_ns_diag, m_ns_dismiss, m_ns_audio, m_ns_resact, m_ns_btn, m_ns_pick, m_ns_bat, m_ns_res, m_ns_selfld, m_ns_fbtn, m_ns_browse, m_ns_fixprefs, m_ns_exportlog, m_ns_debugenv], []),
            (C_CLICK, 0x0001, C_OBJ, [C_OCL], [], [(F_CLK_A, 0x0001), (F_CLK_B, 0x0001)], [m_clk_init], [m_clk_click]),
            (C_FACT, 0x0001, C_CCF, [], [], [], [m_fac_init], [m_fac_inst]),
            (C_GACT, 0x0001, C_OGACT, [], [], [], [m_ga_init], [m_ga_create]),
            (C_HDV, 0x0001, C_VIEW, [], [(F_HD_ICONS, 0x0009)],
             [(F_HD_TOUCH, 0x0001), (F_HD_BG, 0x0001), (F_HD_RING, 0x0001), (F_HD_BMPP, 0x0001), (F_HD_RF, 0x0001)],
             [m_hd_init, m_hd_load], [m_hd_draw]),
            (C_FCLK, 0x0001, C_OBJ, [C_OCL], [], [(F_FC_A, 0x0001), (F_FC_B, 0x0001), (F_FC_C, 0x0001)], [m_fc_init], [m_fc_click]),
            (C_ATT, 0x0001, C_THR, [], [], [(F_ATT_P, 0x0001)], [m_att_init], [m_att_run]),
        ]

    # Pass 1: collect all symbols
    define_all(True)

    # Sort tables strictly per DEX spec (MUTF-8 / index order)
    s_list = sorted(strings, key=lambda s: s.encode("utf-8"))
    sym_s = {s: i for i, s in enumerate(s_list)}
    t_list = sorted(types, key=lambda t: sym_s[t])
    sym_t = {t: i for i, t in enumerate(t_list)}
    p_list = sorted(protos, key=lambda p: (sym_t[p[0]], [sym_t[x] for x in p[1]]))
    sym_p = {p: i for i, p in enumerate(p_list)}
    f_list = sorted(fields, key=lambda f: (sym_t[f[0]], sym_s[f[1]], sym_t[f[2]]))
    sym_f = {f: i for i, f in enumerate(f_list)}
    m_list = sorted(methods, key=lambda m: (sym_t[m[0]], sym_s[m[1]], sym_p[(m[2], m[3])]))
    sym_m = {m: i for i, m in enumerate(m_list)}

    # Pass 2: assemble all methods with real indices
    cls_defs = define_all(False)

    # Layout DEX sections
    header_size = 0x70
    str_ids_off = header_size
    type_ids_off = str_ids_off + len(s_list) * 4
    proto_ids_off = type_ids_off + len(t_list) * 4
    field_ids_off = proto_ids_off + len(p_list) * 12
    method_ids_off = field_ids_off + len(f_list) * 8
    class_defs_off = method_ids_off + len(m_list) * 8
    data_off = class_defs_off + len(cls_defs) * 32

    data = bytearray()
    def align4():
        while len(data) % 4: data.append(0)
    def cur_off():
        return data_off + len(data)

    # Type lists (for proto params + class interfaces)
    align4()
    tl_start = cur_off()
    tl_cache = {}
    def write_type_list(ts):
        if not ts: return 0
        key = tuple(ts)
        if key in tl_cache: return tl_cache[key]
        align4()
        off = cur_off()
        data.extend(struct.pack("<I", len(ts)))
        for t in ts:
            data.extend(struct.pack("<H", sym_t[t]))
        tl_cache[key] = off
        return off

    proto_tl_offs = [write_type_list(p[1]) for p in p_list]
    iface_tl_offs = [write_type_list(c[3]) for c in cls_defs]
    tl_count = len(tl_cache)

    # String data items
    sdata_start = cur_off()
    str_data_offs = []
    for s in s_list:
        str_data_offs.append(cur_off())
        data.extend(mutf8(s))

    # Code items
    align4()
    code_start = cur_off()
    code_offs = {}
    code_count = 0
    for c in cls_defs:
        for m in c[6] + c[7]:
            cls, name, ret, ps, af, regs, ins, outs, words, tries = m
            align4()
            code_offs[(cls, name, ret, ps)] = cur_off()
            code_count += 1
            data.extend(struct.pack("<HHHHII", regs, ins, outs, len(tries), 0, len(words)))
            for w in words:
                data.extend(struct.pack("<H", w))
            if tries:
                if len(words) % 2 == 1:
                    data.extend(b"\x00\x00")
                handlers = bytearray()
                handlers.extend(uleb128(len(tries)))
                h_offs = []
                for _, _, h_addr in tries:
                    h_offs.append(len(handlers))
                    handlers.extend(b"\x00")
                    handlers.extend(uleb128(h_addr))
                for (s_addr, cnt, _), hoff in zip(tries, h_offs):
                    data.extend(struct.pack("<IHH", s_addr, cnt, hoff))
                data.extend(handlers)

    # Class data items
    cdata_start = cur_off()
    cdata_offs = []
    for c in cls_defs:
        cdata_offs.append(cur_off())
        sf, inf, dm, vm = c[4], c[5], c[6], c[7]
        sf_sorted = sorted(sf, key=lambda x: sym_f[x[0]])
        inf_sorted = sorted(inf, key=lambda x: sym_f[x[0]])
        dm_sorted = sorted(dm, key=lambda x: sym_m[x[:4]])
        vm_sorted = sorted(vm, key=lambda x: sym_m[x[:4]])
        data.extend(uleb128(len(sf_sorted)))
        data.extend(uleb128(len(inf_sorted)))
        data.extend(uleb128(len(dm_sorted)))
        data.extend(uleb128(len(vm_sorted)))
        for lst in (sf_sorted, inf_sorted):
            prev = 0
            for fkey, faf in lst:
                idx = sym_f[fkey]
                data.extend(uleb128(idx - prev)); prev = idx
                data.extend(uleb128(faf))
        for lst in (dm_sorted, vm_sorted):
            prev = 0
            for m in lst:
                idx = sym_m[m[:4]]
                data.extend(uleb128(idx - prev)); prev = idx
                data.extend(uleb128(m[4]))
                data.extend(uleb128(code_offs[m[:4]]))

    align4()
    map_off = cur_off()
    # Complete ART DexFileVerifier-compliant map_list
    map_items = [
        (0x0000, 1, 0),
        (0x0001, len(s_list), str_ids_off),
        (0x0002, len(t_list), type_ids_off),
        (0x0003, len(p_list), proto_ids_off),
        (0x0004, len(f_list), field_ids_off),
        (0x0005, len(m_list), method_ids_off),
        (0x0006, len(cls_defs), class_defs_off),
        (0x1001, tl_count, tl_start),
        (0x2002, len(s_list), sdata_start),
        (0x2001, code_count, code_start),
        (0x2000, len(cls_defs), cdata_start),
        (0x1000, 1, map_off),
    ]
    data.extend(struct.pack("<I", len(map_items)))
    for mt, mc, mo in map_items:
        data.extend(struct.pack("<HHII", mt, 0, mc, mo))
    align4()

    total_size = data_off + len(data)
    out = bytearray(total_size)
    out[0:8] = b"dex\n035\x00"
    struct.pack_into("<IIIIIIIIIIIIIIIIIIII", out, 32,
        total_size, header_size, 0x12345678,
        0, 0, map_off,
        len(s_list), str_ids_off,
        len(t_list), type_ids_off,
        len(p_list), proto_ids_off,
        len(f_list), field_ids_off,
        len(m_list), method_ids_off,
        len(cls_defs), class_defs_off,
        len(data), data_off)

    for i, o in enumerate(str_data_offs):
        struct.pack_into("<I", out, str_ids_off + i * 4, o)
    for i, t in enumerate(t_list):
        struct.pack_into("<I", out, type_ids_off + i * 4, sym_s[t])
    for i, (ret, ps) in enumerate(p_list):
        struct.pack_into("<III", out, proto_ids_off + i * 12, sym_s[shorty(ret, ps)], sym_t[ret], proto_tl_offs[i])
    for i, (cls, name, typ) in enumerate(f_list):
        struct.pack_into("<HHI", out, field_ids_off + i * 8, sym_t[cls], sym_t[typ], sym_s[name])
    for i, (cls, name, ret, ps) in enumerate(m_list):
        struct.pack_into("<HHI", out, method_ids_off + i * 8, sym_t[cls], sym_p[(ret, ps)], sym_s[name])
    for i, c in enumerate(cls_defs):
        struct.pack_into("<IIIIIIII", out, class_defs_off + i * 32,
            sym_t[c[0]], c[1], sym_t[c[2]], iface_tl_offs[i], 0xFFFFFFFF, 0, cdata_offs[i], 0)
    out[data_off:] = data

    out[12:32] = hashlib.sha1(out[32:]).digest()
    struct.pack_into("<I", out, 8, zlib.adler32(out[12:]) & 0xFFFFFFFF)
    with open(out_path, "wb") as f:
        f.write(out)
    verify_dex(bytes(out), "classes2.dex")


if __name__ == "__main__":
    build_classes2(sys.argv[1] if len(sys.argv) > 1 else
                   os.path.join(os.path.dirname(os.path.abspath(__file__)), "FalloutNVNativeWork", "classes2.dex"))
