"""FNV_TRACE audio filter test (trace_pre / trace_post gates).
Run after build:  python3 tests/test_trace_filter.py"""
import os, sys, struct, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu_audio import Emu, PATCHED_SO, ROOT, STK, RET
from unicorn.arm64_const import *
NEW = open(PATCHED_SO, 'rb').read()
syms = {k: int(v, 16) for k, v in json.load(open(os.path.join(ROOT, 'native/fnv_stubs.json'))).items()}
ok = True
def check(n, c, i=''):
    global ok; ok &= bool(c); print(('PASS ' if c else 'FAIL ') + n, i)
e = Emu(NEW); u = e.u
G, CTX, ENT = 0x40000000, 0x3fe00000, 0x3fd00000
u.mem_map(G, 0x100000); u.mem_map(CTX, 0x1000); u.mem_map(ENT, 0x1000)
u.mem_write(0x5646868, struct.pack('<Q', G))
u.mem_write(CTX, struct.pack('<I', 0x1234)); u.mem_write(CTX + 0x10, struct.pack('<I', 0x8000))
u.mem_write(G + 0x8004, struct.pack('<4I', 0xa, 0xb, 0xc, 0xd))
def call(fn, dll, func, flag=1):
    u.mem_write(ENT, dll.encode().ljust(0x40, b'\0') + func.encode().ljust(0x40, b'\0'))
    n = len(e.prints)
    u.reg_write(UC_ARM64_REG_X8, flag); u.reg_write(UC_ARM64_REG_X19, CTX); u.reg_write(UC_ARM64_REG_X20, ENT)
    u.reg_write(UC_ARM64_REG_SP, STK + 0x100000); u.reg_write(UC_ARM64_REG_X30, RET)
    u.emu_start(syms[fn], RET, count=100000)
    return e.prints[n:]
p = call('trace_pre', 'IDirectSoundBuffer8', 'Play')
check('dsound call traced', len(p) == 1 and p[0].startswith(b'[imp] %s!%s'))
check('kernel32 call filtered', call('trace_pre', 'kernel32', 'Sleep') == [])
check('DirectShow traced', len(call('trace_pre', 'IGraphBuilder', 'RenderFile')) == 1)
check('post-print traced', len(call('trace_post', 'dsound', 'DirectSoundCreate8')) == 1)
check('flag off -> nothing', call('trace_pre', 'IDirectSoundBuffer8', 'Play', flag=0) == [])
print('ALL PASS' if ok else 'SOME TESTS FAILED'); sys.exit(0 if ok else 1)
