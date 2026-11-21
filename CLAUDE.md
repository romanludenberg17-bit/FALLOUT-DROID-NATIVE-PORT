# Fallout Droid NV Native — Claude Code Project Guide

## 1. Project Overview
**Fallout Droid NV Native** (current version **v1.3.1 / versionCode 27**) is a native ARM64 Android port wrapper for **Fallout: New Vegas**:
`lib/arm64-v8a/libfnv.so` (static recompilation of FalloutNV.exe + unicorn/QEMU fallback, D3D9 → OpenGL ES 3.2, DirectSound → AAudio) + original `com.fnvport` shell in `classes.dex` + our layer in `classes2.dex`.
- **Primary test device**: `vivo I2508` (QTI SM8845 / Adreno 829, Android 17 API 37).
- **Language**: the user communicates in **Russian** and expects built, signed (v1+v2), ready-to-install APKs. After every change bump `VERSION_NAME` / `VERSION_CODE` in `build_fnv_native.py`.

## 2. Repository Structure
- `FalloutNVNativeWork/` — unpacked APK tree (`res/`, `resources.arsc`, `AndroidManifest.xml`, `assets/`, `classes.dex`, generated `classes2.dex`). `lib/arm64-v8a/libfnv.so` is generated (git-ignored).
- `orig/libfnv.so.xz` — clean original `libfnv.so` (LZMA).
- `native/fnv_stubs.S` — AArch64 source of ALL our native code (linked at `0x58e0000` into a new R+X segment); `native/fnv_stubs.bin/.json` — prebuilt blob + symbols (refreshed by the build when LLVM is present, used as fallback otherwise).
- `gen_classes2.py` — pure-Python DEX generator for `classes2.dex` (hand-written Dalvik bytecode, `Asm` helper; `c4`/`c16` assert literal ranges).
- `build_fnv_native.py` — **single-command build, patch, verify & sign** (version constants at the top).
- `sign_apk.py` + `.signing_keys/` (git-ignored) — APK v1+v2 signer and release keys. The build refuses to run without the keys.
- `tests/` — unicorn-based regression tests of the native audio path (`pip install unicorn`).

## 3. Build, Verify & Sign
```bash
python3 build_fnv_native.py          # -> output/Fallout Droid NV Native.apk (+ .zip)
python3 tests/run_all.py             # unicorn tests: mixer/limiter/FIFO, AAudio open, DirectSound voices
```
Tools: Python 3, `llvm-mc` + `ld.lld` + `llvm-objcopy` + `llvm-nm` (optional, for re-assembling stubs), `readelf`.

## 4. Critical Technical Invariants (DO NOT BREAK)
1. **Native code placement (since v1.3.1)**: all stubs live in a NEW `PT_LOAD R+X` segment at vaddr `0x58e0000` (file offset 16 KiB-aligned after the original end), created by converting the unused `PT_NOTE` program header. Hooks are `bl` from original code (±128 MB range).
   - The old code cave `0x53fb664..0x53fb9a0` is **no longer used** and must stay original; `0x53fb9a0..0x53fb9c8` is LIVE code (`__stack_chk_fail` + FNV_QUIT_AFTER exit), `0x53fb9ec` is a live function. The build asserts `0x53fb000..0x53fc000` is untouched and that **every** changed byte of the original image is in a whitelisted range.
   - Hook sites: `0x54130e0` audio, `0x5412b88` AAudio builder, `0x54134d0` GetCaps, `0x5413cc8` Play sync, `0x5414434` pan clamp, `0x5413d64` voice creation (diagnostics + pool reclaim).
2. **No stub state in existing `.bss`**. `0x58d40a0..0x58d40c0` belongs to live globals (MT19937 init flags, cached `CTR_EL0` for `__clear_cache`, LSE-atomics flag) — v1.2.15 corrupted them. Stub state lives in a `+0x480` extension of the last PT_LOAD at `0x58d40c0` (layout documented at the top of `native/fnv_stubs.S`).
3. **Audio path (v1.3.0)** — hook at `0x54130e0` (AAudio data callback tail):
   - mixer hard clamp at `0x5413040` disabled (`b.lt` → `b`), so the stub sees real peaks;
   - NaN/Inf → 0, master gain −3 dB, stereo-linked peak limiter (threshold −0.5 dBFS, instant attack, 80 ms release), safety clamp ±1;
   - AAudio: PERFORMANCE_MODE_NONE, SHARED, USAGE_MEDIA, CONTENT_MUSIC, capacity 4096 frames (via `dlsym`), buffer = capacity;
   - optional PCM16 FIFO → Java `AudioTrackThread` only when `/sdcard/FalloutDroidNV/use_audiotrack` exists; SIGPIPE blocked on the audio thread, EPIPE falls back to AAudio.
4. **Dalvik literals**: `const/4` is −8..7, `const/16` is −32768..32767 (v1.2.15 shipped `const/4 10` → −6 and `12` → −4, which killed the AudioTrack thread).
5. **Diagnostics**: game log = `<externalFilesDir>/last_run.log`, copied by the launcher (onResume) to `/sdcard/FalloutDroidNV/last_run.log`. Audio lines: `[audio] AAudio+lim …`, `[dsnd] play/FAIL/stats/voice pool full`. Marker files in `/sdcard/FalloutDroidNV/`: `use_audiotrack` (PCM16 FIFO → AudioTrack), `trace_hle` (`FNV_TRACE=1`, full HLE API trace, very slow).
6. **DEX & Signing**: generator recomputes DEX SHA-1 `[12:32]` and Adler32 `[8:12]`; sign only with `.signing_keys/release.key.pem`.
