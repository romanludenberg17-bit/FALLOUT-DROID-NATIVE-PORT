"""Emulate libfnv.so AAudio mixer callback (+ patched stub) in unicorn."""
import os, sys, struct, math, lzma, subprocess, tempfile
from unicorn import *
from unicorn.arm64_const import *

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATCHED_SO = os.path.join(ROOT, 'FalloutNVNativeWork/lib/arm64-v8a/libfnv.so')

def load_orig():
    return lzma.decompress(open(os.path.join(ROOT, 'orig/libfnv.so.xz'), 'rb').read())

def plt_map(so):
    """PLT stub address -> imported symbol name (via .rela.plt JUMP_SLOTs)."""
    with tempfile.NamedTemporaryFile(suffix='.so') as f:
        f.write(so); f.flush()
        rel = subprocess.run(['readelf', '-r', '-W', f.name], capture_output=True, text=True, check=True).stdout
    got = {}
    for l in rel.splitlines():
        p = l.split()
        if 'JUMP_SLOT' in l and len(p) >= 5: got[int(p[0], 16)] = p[4]
    res = {}
    for a in range(0x54be0f0, 0x54be0d0 + 0x2cb0, 16):
        w0, w1 = struct.unpack_from('<II', so, a)
        if (w0 & 0x9f00001f) != 0x90000010: continue
        imm = (((w0 >> 5) & 0x7ffff) << 2) | ((w0 >> 29) & 3)
        page = (a & ~0xfff) + (imm << 12)
        slot = page + ((w1 >> 10) & 0xfff) * 8
        if slot in got: res[a] = got[slot]
    return res

PLT = plt_map(load_orig())
CB = 0x5412c9c
VOICES = 0x58c5f18
STATE = 0x58d40c0
STK, BUF, STR, TLS, PCM, RET = 0x60000000, 0x61000000, 0x62000000, 0x63000000, 0x64000000, 0x70000000

class Emu:
    def __init__(self, so, fifo=False, epipe=False, guard_bss=True):
        self.so = so
        u = self.u = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        u.mem_map(0, 0x5900000)
        # PT_LOADs
        phoff, = struct.unpack_from('<Q', so, 0x20)
        phn, = struct.unpack_from('<H', so, 0x38)
        for i in range(phn):
            p = phoff + i * 56
            t, fl, off, va, pa, fs, ms = struct.unpack_from('<IIQQQQQ', so, p)
            if t == 1: u.mem_write(va, bytes(so[off:off + fs]))
        for b in (STK, BUF, STR, TLS, PCM, RET): u.mem_map(b, 0x200000)
        u.reg_write(UC_ARM64_REG_TPIDR_EL0, TLS)
        u.reg_write(UC_ARM64_REG_CPACR_EL1, 3 << 20)  # enable FP/SIMD
        u.mem_write(STR, b'/cache/fnv_pcm.fifo\0')
        self.fifo, self.epipe = fifo, epipe
        self.writes, self.prints, self.calls, self.bad, self.pargs = [], [], [], [], []
        self.h_plt = u.hook_add(UC_HOOK_CODE, self.plt_hook, begin=0x54be0d0, end=0x54c0d80)
        u.hook_add(UC_HOOK_INTR, self.svc)
        if guard_bss:
            u.hook_add(UC_HOOK_MEM_WRITE, self.wguard, begin=0x58d4000, end=0x58d40bf)

    def wguard(self, uc, access, addr, size, value, ud):
        self.bad.append((hex(uc.reg_read(UC_ARM64_REG_PC)), hex(addr)))

    def ret(self, val):
        u = self.u
        u.reg_write(UC_ARM64_REG_X0, val & 0xffffffffffffffff)
        u.reg_write(UC_ARM64_REG_PC, u.reg_read(UC_ARM64_REG_X30))

    def plt_hook(self, uc, addr, size, ud):
        name = PLT.get(addr)
        if name is None: return
        x = [uc.reg_read(UC_ARM64_REG_X0 + i) for i in range(3)]
        n = name.split('@')[0]
        self.calls.append(n)
        if n == 'memset':
            uc.mem_write(x[0], bytes([x[1] & 0xff]) * x[2]); self.ret(x[0])
        elif n in ('pthread_mutex_lock', 'pthread_mutex_unlock'): self.ret(0)
        elif n == 'getenv':
            s = uc.mem_read(x[0], 32).split(b'\0')[0]
            self.ret(STR if (self.fifo and s == b'FNV_PCM_FIFO') else 0)
        elif n == 'open': self.ret(7)
        elif n == 'fprintf':
            self.prints.append(uc.mem_read(x[1], 64).split(b'\0')[0])
            regs = [uc.reg_read(UC_ARM64_REG_X0 + i) & 0xffffffff for i in range(2, 8)]
            dregs = [struct.unpack('<d', struct.pack('<Q', uc.reg_read(UC_ARM64_REG_D0 + i)))[0] for i in range(4)]
            self.pargs.append((self.prints[-1], regs, dregs)); self.ret(0)
        elif n == 'dlsym': self.ret(0)
        else:
            raise RuntimeError('unexpected PLT call ' + name)

    def svc(self, uc, intno, ud):
        nr = uc.reg_read(UC_ARM64_REG_X8)
        x0, x1, x2 = (uc.reg_read(UC_ARM64_REG_X0 + i) for i in range(3))
        if nr == 64:
            if self.epipe: uc.reg_write(UC_ARM64_REG_X0, (-32) & (2**64 - 1)); self.calls.append('write-EPIPE')
            else: self.writes.append(bytes(uc.mem_read(x1, x2))); uc.reg_write(UC_ARM64_REG_X0, x2)
        elif nr in (135, 57): uc.reg_write(UC_ARM64_REG_X0, 0); self.calls.append(f'svc{nr}')
        else: raise RuntimeError(f'syscall {nr}')

    def set_voice(self, i, pcm16_stereo, gl=1.0, gr=1.0, loop=1):
        u = self.u
        addr = PCM + i * 0x40000
        u.mem_write(addr, pcm16_stereo)
        v = VOICES + i * 0x58
        frames = len(pcm16_stereo) // 4
        u.mem_write(v, struct.pack('<I', 1))
        u.mem_write(v + 8, struct.pack('<QIIIIII', addr, frames, 4, 2, 16, loop, 0))
        u.mem_write(v + 0x28, struct.pack('<ddff', 0.0, 1.0, gl, gr))

    def callback(self, frames):
        u = self.u
        u.mem_write(BUF, b'\xAA' * frames * 8)
        u.reg_write(UC_ARM64_REG_SP, STK + 0x100000)
        u.reg_write(UC_ARM64_REG_X0, 0); u.reg_write(UC_ARM64_REG_X1, 0)
        u.reg_write(UC_ARM64_REG_X2, BUF); u.reg_write(UC_ARM64_REG_X3, frames)
        u.reg_write(UC_ARM64_REG_X30, RET)
        u.emu_start(CB, RET, count=50_000_000)
        assert u.reg_read(UC_ARM64_REG_X0) & 0xffffffff == 0
        return struct.unpack(f'<{frames*2}f', u.mem_read(BUF, frames * 8))

    def call_stub(self, addr, samples):
        u = self.u
        frames = len(samples) // 2
        u.mem_write(BUF, struct.pack(f'<{len(samples)}f', *samples))
        u.reg_write(UC_ARM64_REG_SP, STK + 0x100000)
        u.reg_write(UC_ARM64_REG_X19, BUF); u.reg_write(UC_ARM64_REG_X20, frames)
        u.reg_write(UC_ARM64_REG_X30, RET)
        u.emu_start(addr, RET, count=10_000_000)
        return struct.unpack(f'<{len(samples)}f', u.mem_read(BUF, frames * 8))

def sine(freq, amp, frames, sr=48000):
    out = bytearray()
    for n in range(frames):
        s = int(round(amp * 32767 * math.sin(2 * math.pi * freq * n / sr)))
        out += struct.pack('<hh', s, s)
    return bytes(out)
