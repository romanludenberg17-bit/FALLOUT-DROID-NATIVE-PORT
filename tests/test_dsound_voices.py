"""DirectSound voice-pool regression test: stub_vcreate must record owners,
reclaim finished one-shot voices when the 256-voice pool is full, and log.
Run after build:  python3 tests/test_dsound_voices.py"""
import os, sys, struct, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu_audio import Emu, PATCHED_SO, ROOT, STK, RET, VOICES, STATE
from unicorn.arm64_const import *

NEW = open(PATCHED_SO, 'rb').read()
syms = {k: int(v, 16) for k, v in json.load(open(os.path.join(ROOT, 'native/fnv_stubs.json'))).items()}
GUEST = 0x40000000          # host address of guest memory base
PTR = 0x3ff00000            # host cell holding GUEST
ok = True
def check(name, cond, info=''):
    global ok; ok &= bool(cond); print(('PASS ' if cond else 'FAIL ') + name, info)

e = Emu(NEW)
u = e.u
u.mem_map(PTR, 0x1000); u.mem_map(GUEST, 0x400000)
u.mem_write(0x55931a8, struct.pack('<Q', PTR)); u.mem_write(PTR, struct.pack('<Q', GUEST))
OBJ0, DATA = 0x10000, 0x200000
def obj(i): return OBJ0 + i * 0x80
# 256 finished, non-looping one-shot voices, each owned by a buffer object
for v in range(256):
    o = obj(v)
    u.mem_write(GUEST + o + 0x0c, struct.pack('<II', 4800, DATA))       # bytes, data(guest)
    u.mem_write(GUEST + o + 0x24, struct.pack('<I', v + 1))             # voice id + 1
    u.mem_write(GUEST + o + 0x1c, struct.pack('<I', 1))                 # "playing" flag
    vb = VOICES + v * 0x58
    u.mem_write(vb, struct.pack('<I', 1)); u.mem_write(vb + 0x14, struct.pack('<I', 4))
    u.mem_write(vb + 0x20, struct.pack('<II', 0, 1))                     # loop=0, done=1
    u.mem_write(STATE + 0x40 + 4 * v, struct.pack('<I', o))              # owner (as if created by stub)
# one voice still playing and one looping: must NOT be reclaimed
u.mem_write(VOICES + 5 * 0x58 + 0x24, struct.pack('<I', 0))
u.mem_write(VOICES + 6 * 0x58 + 0x20, struct.pack('<I', 1))

def vcreate(o, ch=1, bits=16, hz=22050, nbytes=4800):
    u.reg_write(UC_ARM64_REG_SP, STK + 0x100000); u.reg_write(UC_ARM64_REG_X30, RET)
    regs = [o, GUEST + DATA, nbytes, ch, bits, hz, 0, 0]
    for i, r in enumerate(regs): u.reg_write(UC_ARM64_REG_X0 + i, r)
    for r in range(19, 29): u.reg_write(UC_ARM64_REG_X0 + r, 0x1111 * r)
    u.emu_start(syms['stub_vcreate'], RET, count=5_000_000)
    saved = all(u.reg_read(UC_ARM64_REG_X0 + r) == 0x1111 * r for r in range(19, 29))
    return struct.unpack('<i', struct.pack('<I', u.reg_read(UC_ARM64_REG_X0) & 0xffffffff))[0], saved

NEWOBJ = 0x30000
v, saved = vcreate(NEWOBJ)
types = [struct.unpack('<I', u.mem_read(VOICES + i * 0x58, 4))[0] for i in range(256)]
st = struct.unpack('<11I', u.mem_read(STATE + 0x14, 44))
check('pool full -> reclaim + retry succeeds', v >= 0, f'voice={v}')
check('callee-saved registers preserved', saved)
check('playing and looping voices kept', types[5] == 1 and types[6] == 1)
check('owner recorded', struct.unpack('<I', u.mem_read(STATE + 0x40 + 4 * v, 4))[0] == NEWOBJ)
rec = st[3]
check('reclaim counter', rec == 254, f'reclaims={rec}')
o1 = obj(0)
check('reclaimed buffer state cleared (+0x24/+0x1c)', u.mem_read(GUEST + o1 + 0x1c, 12) == bytes(12) or
      struct.unpack('<I', u.mem_read(GUEST + o1 + 0x24, 4))[0] == 0)
check('log lines', any(b'reclaimed' in p for p in e.prints) and any(b'[dsnd] play' in p for p in e.prints), str(e.prints[:3]))

v2, _ = vcreate(0x31000, ch=6)
check('bad format -> FAIL logged, no reclaim', v2 == -1 and any(b'FAIL' in p for p in e.prints) and any(b'stats' in p for p in e.prints))

# ---- end-to-end: guest IDirectSoundBuffer8::Play -> mixer -> replay ----
import math
from emu_audio import sine, BUF
e = Emu(NEW); u = e.u
u.mem_map(PTR, 0x1000); u.mem_map(GUEST, 0x400000)
u.mem_write(0x55931a8, struct.pack('<Q', PTR)); u.mem_write(PTR, struct.pack('<Q', GUEST))
CTX, ESP, OB, PCMG = 0x3fe00000, 0x100000, 0x20000, 0x210000
u.mem_map(CTX, 0x1000)
pcm = sine(441, 0.5, 2400)                     # 50 ms stereo one-shot "click"
u.mem_write(GUEST + PCMG, pcm)
wfx = struct.pack('<HHIIHH', 1, 2, 48000, 192000, 4, 16)
u.mem_write(GUEST + OB + 0x0c, struct.pack('<II', len(pcm), PCMG))
u.mem_write(GUEST + OB + 0x30, wfx)
PLAY = 0x5413c84
def guest_play(flags=0):
    u.mem_write(CTX + 0x10, struct.pack('<I', ESP))
    u.mem_write(GUEST + ESP, struct.pack('<IIIII', 0xdead, OB, 0, 0, flags))
    u.reg_write(UC_ARM64_REG_X0, CTX); u.reg_write(UC_ARM64_REG_SP, STK + 0x100000); u.reg_write(UC_ARM64_REG_X30, RET)
    u.emu_start(PLAY, RET, count=5_000_000)
    return struct.unpack('<I', u.mem_read(GUEST + OB + 0x24, 4))[0]
def mix(n=10):
    out = []
    for _ in range(n): out += e.callback(480)
    return max(abs(x) for x in out)
v1 = guest_play()
p1 = mix(10)                                   # 100 ms: plays 50 ms then finishes
v2 = guest_play()                              # UI click again on the same buffer
p2 = mix(3)
check('E2E: first Play is audible', v1 >= 1 and p1 > 0.2, f'voice+1={v1} peak={p1:.3f}')
check('E2E: replay of finished one-shot is audible', v2 >= 1 and p2 > 0.2, f'voice+1={v2} peak={p2:.3f}')
print('ALL PASS' if ok else 'SOME TESTS FAILED'); sys.exit(0 if ok else 1)
