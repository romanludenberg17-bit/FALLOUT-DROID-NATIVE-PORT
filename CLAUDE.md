# Fallout Droid NV Native — Claude Code Project Guide

## 1. Project Overview
**Fallout Droid NV Native** (current version **v1.3.0 / versionCode 26**) is a native ARM64 Android port wrapper for **Fallout: New Vegas**:
`lib/arm64-v8a/libfnv.so` (static recompilation of FalloutNV.exe + unicorn/QEMU fallback, D3D9 → OpenGL ES 3.2, DirectSound → AAudio) + original `com.fnvport` shell in `classes.dex` + our layer in `classes2.dex`.
- **Primary test device**: `vivo I2508` (QTI SM8845 / Adreno 829, Android 17 API 37).
- **Language**: the user communicates in **Russian** and expects built, signed (v1+v2), ready-to-install APKs. After every change bump `VERSION_NAME` / `VERSION_CODE` in `build_fnv_native.py`.

## 2. Repository Structure
- `FalloutNVNativeWork/` — unpacked APK tree (`res/`, `resources.arsc`, `AndroidManifest.xml`, `assets/`, `classes.dex`, generated `classes2.dex`). `lib/arm64-v8a/libfnv.so` is generated (git-ignored).
- `orig/libfnv.so.xz` — clean original `libfnv.so` (LZMA).
- `native/fnv_stubs.S` — AArch64 source of all code-cave stubs; `native/fnv_stubs.bin/.json` — prebuilt blob + symbols (refreshed by the build when LLVM is present, used as fallback otherwise).
- `gen_classes2.py` — pure-Python DEX generator for `classes2.dex` (hand-written Dalvik bytecode, `Asm` helper; `c4`/`c16` assert literal ranges).
- `build_fnv_native.py` — **single-command build, patch, verify & sign** (version constants at the top).
- `sign_apk.py` + `.signing_keys/` (git-ignored) — APK v1+v2 signer and release keys. The build refuses to run without the keys.
- `tests/` — unicorn-based regression tests of the native audio path (`pip install unicorn`).

## 3. Build, Verify & Sign
```bash
python3 build_fnv_native.py          # -> output/Fallout Droid NV Native.apk (+ .zip)
python3 tests/test_audio.py          # mixer + limiter + FIFO emulation tests
python3 tests/test_aaudio_open.py    # AAudio stream parameters
```
Tools: Python 3, `llvm-mc` + `ld.lld` + `llvm-objcopy` + `llvm-nm` (optional, for re-assembling stubs), `readelf`.

## 4. Critical Technical Invariants (DO NOT BREAK)
1. **Code cave in `libfnv.so`: `0x53fb664 .. 0x53fb9a0`** (the dead FNV_INPUT_SCRIPT handler; its entry `cbnz` at `0x53fb218` is NOP'ed).
   - `0x53fb9a0..0x53fb9c8` is **LIVE** code (`__stack_chk_fail` call + FNV_QUIT_AFTER exit, branched to from `0x53fb640` / `0x53fb2ec`). The old limit `0x53fb9c0` was wrong.
   - Live function at `0x53fb9ec` (`0x53fb9c8..0x53fba74`). The build asserts `0x53fb9a0..0x53fba74` is byte-identical to the original and that **every** changed byte is in a whitelisted range.
2. **No stub state in existing `.bss`**. `0x58d40a0..0x58d40c0` belongs to live globals (MT19937 init flags, cached `CTR_EL0` for `__clear_cache`, LSE-atomics flag) — v1.2.15 corrupted them. Stub state lives in a `+0x40` extension of the last PT_LOAD at `0x58d40c0` (`[+0]` limiter attenuation, `[+4]` FIFO fd/state).
3. **Audio path (v1.3.0)** — hook at `0x54130e0` (AAudio data callback tail):
   - mixer hard clamp at `0x5413040` disabled (`b.lt` → `b`), so the stub sees real peaks;
   - NaN/Inf → 0, master gain −3 dB, stereo-linked peak limiter (threshold −0.5 dBFS, instant attack, 80 ms release), safety clamp ±1;
   - AAudio: PERFORMANCE_MODE_NONE, SHARED, USAGE_MEDIA, CONTENT_MUSIC, capacity 4096 frames (via `dlsym`), buffer = capacity;
   - optional PCM16 FIFO → Java `AudioTrackThread` only when `/sdcard/FalloutDroidNV/use_audiotrack` exists; SIGPIPE blocked on the audio thread, EPIPE falls back to AAudio.
4. **Dalvik literals**: `const/4` is −8..7, `const/16` is −32768..32767 (v1.2.15 shipped `const/4 10` → −6 and `12` → −4, which killed the AudioTrack thread).
5. **DEX & Signing**: generator recomputes DEX SHA-1 `[12:32]` and Adler32 `[8:12]`; sign only with `.signing_keys/release.key.pem`.
