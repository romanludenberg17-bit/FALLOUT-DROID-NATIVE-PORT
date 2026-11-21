"""Run all regression tests (after python3 build_fnv_native.py)."""
import os, subprocess, sys
here = os.path.dirname(os.path.abspath(__file__))
rc = 0
for t in ("test_audio.py", "test_aaudio_open.py", "test_dsound_voices.py", "test_trace_filter.py"):
    r = subprocess.run([sys.executable, os.path.join(here, t)], capture_output=True, text=True)
    last = (r.stdout.strip().splitlines() or ["(no output)"])[-1]
    print(f"{t:24s} {'OK ' if r.returncode == 0 else 'FAIL'} {last}")
    if r.returncode: print(r.stdout[-3000:], r.stderr[-3000:]); rc = 1
sys.exit(rc)
