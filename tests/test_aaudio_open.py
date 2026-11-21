"""AAudio stream-open regression test (builder params, capacity, buffer size).
Run after build:  python3 tests/test_aaudio_open.py"""
import sys, struct
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu_audio import Emu, PLT, STK, RET, STR, PATCHED_SO
from unicorn.arm64_const import *
NEW = open(PATCHED_SO, 'rb').read()
e = Emu(NEW)
log = []
def hook(uc, addr, size, ud):
    name = PLT.get(addr)
    if not name: return
    n = name.split('@')[0]
    x = [uc.reg_read(UC_ARM64_REG_X0 + i) for i in range(4)]
    r = 0
    if n == 'AAudio_createStreamBuilder': uc.mem_write(x[0], struct.pack('<Q', 0xB0B0)); 
    elif n == 'AAudioStreamBuilder_openStream': uc.mem_write(x[1], struct.pack('<Q', 0x5757))
    elif n == 'AAudioStream_getFramesPerBurst': r = 960
    elif n == 'AAudioStream_getSampleRate': r = 48000
    elif n == 'AAudioStream_getBufferSizeInFrames': r = 4096
    elif n == 'dlsym': log.append(('dlsym', bytes(uc.mem_read(x[1], 64)).split(b'\0')[0].decode())); r = 0x71000000
    elif n == 'fprintf': log.append(('fprintf', bytes(uc.mem_read(x[1], 64)).split(b'\0')[0].decode(), x[2], x[3])); 
    if n.startswith('AAudio'): log.append((n, hex(x[0]), x[1] & 0xffffffff))
    uc.reg_write(UC_ARM64_REG_X0, r); uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_X30))
def fake_cap(uc, addr, size, ud):
    log.append(('setBufferCapacityInFrames', hex(uc.reg_read(UC_ARM64_REG_X0)), uc.reg_read(UC_ARM64_REG_X1) & 0xffffffff))
    uc.reg_write(UC_ARM64_REG_X0, 0); uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_X30))
e.u.mem_map(0x71000000, 0x1000); e.u.mem_write(0x71000000, b'\xc0\x03\x5f\xd6')
e.u.hook_add(1 << 2, hook, begin=0x54be0d0, end=0x54c0d80)  # UC_HOOK_CODE, registered first? second hook also runs
e.u.hook_add(1 << 2, fake_cap, begin=0x71000000, end=0x71000003)
e.u.hook_del(e.h_plt)
e.u.reg_write(UC_ARM64_REG_SP, STK + 0x100000); e.u.reg_write(UC_ARM64_REG_X30, RET)
e.u.reg_write(UC_ARM64_REG_X9, 0)
e.u.emu_start(0x5412b0c, RET, count=1000000)
for l in log: print(l)
want = [('AAudioStreamBuilder_setPerformanceMode', '0xb0b0', 10), ('AAudioStreamBuilder_setSharingMode', '0xb0b0', 1),
        ('AAudioStreamBuilder_setUsage', '0xb0b0', 1), ('AAudioStreamBuilder_setContentType', '0xb0b0', 2),
        ('dlsym', 'AAudioStreamBuilder_setBufferCapacityInFrames'), ('setBufferCapacityInFrames', '0xb0b0', 4096),
        ('AAudioStream_setBufferSizeInFrames', '0x5757', 65536)]
ok = all(w in log for w in want) and any(l[0] == 'fprintf' and 'AAudio+lim' in l[1] for l in log)
print('AAUDIO OPEN PASS' if ok else 'AAUDIO OPEN FAIL'); sys.exit(0 if ok else 1)
