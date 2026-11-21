"""Audio regression tests: runs the game's original AAudio mixer callback +
the patched stub in unicorn.  Requires: pip install unicorn.
Run after build:  python3 tests/test_audio.py"""
import sys, math, struct, lzma
import os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emu_audio import Emu, sine, STATE, ROOT, PATCHED_SO, load_orig
NEW = open(PATCHED_SO, 'rb').read()
ORIG = load_orig()
syms = {k: int(v, 16) for k, v in json.load(open(os.path.join(ROOT, 'native/fnv_stubs.json'))).items()}
loop = sine(441, 1.0, 4800)  # 441 Hz -> exact period 108.84.. ; loop buffer 100 ms

def run(so, nvoices, amp, cbs=40, frames=480, **kw):
    e = Emu(so, **kw)
    pcm = sine(441, amp, 4800)
    for i in range(nvoices): e.set_voice(i, pcm)
    out = []
    for _ in range(cbs): out += e.callback(frames)
    return e, out

ok = True
def check(name, cond, info=''):
    global ok
    ok &= bool(cond)
    print(('PASS ' if cond else 'FAIL ') + name, info)

# A: 4 loud voices summed (~3.6 FS) — original clips hard, new must limit
e0, o0 = run(ORIG, 4, 0.9, guard_bss=False)
e1, o1 = run(NEW, 4, 0.9)
clip0 = sum(1 for v in o0 if abs(v) >= 0.9999)
clip1 = sum(1 for v in o1 if abs(v) >= 0.9999)
pk1 = max(abs(v) for v in o1)
print(f'  original: peak {max(abs(v) for v in o0):.3f}, samples at +-1.0: {clip0}/{len(o0)}')
print(f'  v1.3.0  : peak {pk1:.4f}, samples at +-1.0: {clip1}/{len(o1)}')
check('A: no hard clipping with 4 loud voices', clip1 == 0 and pk1 <= 0.9445, f'peak={pk1:.4f}')
check('A: no forbidden BSS writes (0x58d4000..0x58d40bf)', not e1.bad, str(e1.bad[:3]))
check('A: no FIFO activity by default', not e1.writes and 'open' not in e1.calls)

# B: one quiet voice -> exact linear gain 0.7079 (limiter transparent)
e2, o2 = run(NEW, 1, 0.25, cbs=5)
e2o, o2o = run(ORIG, 1, 0.25, cbs=5, guard_bss=False)
ratios = [a / b for a, b in zip(o2, o2o) if abs(b) > 0.05]
check('B: quiet signal is linear (gain -3 dB, no distortion)',
      max(ratios) - min(ratios) < 1e-4 and abs(ratios[0] - 0.70794578) < 1e-4,
      f'gain {min(ratios):.6f}..{max(ratios):.6f}')

# C: NaN / Inf / huge values straight into stub_audio
e3 = Emu(NEW)
nan, inf = float('nan'), float('inf')
o3 = e3.call_stub(syms['stub_audio'], [nan, 0.1, inf, -inf, 5.0, -5.0, 0.1, 0.1] * 4)
check('C: NaN/Inf never reach output', all(math.isfinite(v) and abs(v) <= 1.0 for v in o3), str([round(v, 4) for v in o3[:8]]))
check('C: NaN/Inf lanes become 0', o3[0] == 0.0 and o3[2] == 0.0 and o3[3] == 0.0)
check('C: Inf does not collapse limiter gain', abs(o3[1] - 0.1 * 0.70794578) < 1e-6)

# D: limiter releases back to unity after loud burst
e4 = Emu(NEW)
e4.call_stub(syms['stub_audio'], [3.0, 3.0] * 480)
g_after_attack = 1 - struct.unpack('<f', e4.u.mem_read(STATE, 4))[0]
for _ in range(100): e4.call_stub(syms['stub_audio'], [0.01, 0.01] * 480)   # 1 s quiet
g_after_rel = 1 - struct.unpack('<f', e4.u.mem_read(STATE, 4))[0]
check('D: limiter attack/release', g_after_attack < 0.5 and g_after_rel > 0.999, f'g attack={g_after_attack:.3f} release(1s)={g_after_rel:.5f}')

# E: FIFO mode -> PCM16 to fd, AAudio buffer silenced
e5, o5 = run(NEW, 1, 0.25, cbs=3, fifo=True)
pcm = b''.join(e5.writes)
s16 = struct.unpack(f'<{len(pcm)//2}h', pcm)
exp = [round(max(-1, min(1, v * 0.70794578)) * 32767) for v in o2o[:len(s16)]]
err = max(abs(a - b) for a, b in zip(s16, exp))
check('E: FIFO gets PCM16 of limited mix', len(e5.writes) == 3 and len(pcm) == 3 * 480 * 4 and err <= 1, f'writes={len(e5.writes)} maxerr={err}')
check('E: AAudio output silenced in FIFO mode', all(v == 0.0 for v in o5))
check('E: SIGPIPE blocked before write', 'svc135' in e5.calls)
check('E: FIFO log printed once', sum(1 for p in e5.prints if b'FIFO' in p) == 1)

# F: EPIPE -> fall back to AAudio float output
e6 = Emu(NEW, fifo=True, epipe=True)
e6.set_voice(0, sine(441, 0.25, 4800))
a = e6.callback(480)
st = struct.unpack('<i', e6.u.mem_read(STATE + 4, 4))[0]
b = e6.callback(480)
check('F: EPIPE closes FIFO and falls back to AAudio', st == -1 and 'svc57' in e6.calls and max(abs(v) for v in b) > 0.1, f'state={st}')

print('ALL PASS' if ok else 'SOME TESTS FAILED')
sys.exit(0 if ok else 1)
