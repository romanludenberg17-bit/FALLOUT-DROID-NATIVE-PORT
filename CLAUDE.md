# Fallout Droid NV Native — Claude Code Project Guide

## 1. Project Overview
**Fallout Droid NV Native** (current version **v1.2.15 / versionCode 25**) is a native ARM64 Android port/recompilation wrapper for **Fallout: New Vegas** (`lib/arm64-v8a/libfnv.so` + custom Java/DEX launcher & audio/touch layer in `classes2.dex`).
- **Primary test device**: `vivo I2508` (QTI SM8845 / Adreno 829, Android 17 API 37, 8 cores, 7.1 GB RAM).
- **Language**: The user communicates in **Russian** and expects built, signed (v1+v2), ready-to-install APKs.

## 2. Repository Structure (All-in-One, < 25 MB)
- `FalloutNVNativeWork/` — Complete unpacked APK tree (all icons, `res/`, `resources.arsc`, `AndroidManifest.xml`, `assets/`, `classes.dex`, `classes2.dex`).
- `orig/libfnv.so.xz` — Clean unpatched original `libfnv.so` compressed with LZMA (Python stdlib `lzma` decompresses it automatically during build and writes the patched `FalloutNVNativeWork/lib/arm64-v8a/libfnv.so`).
- `build_fnv_native_v1215.py` — **Main single-command build, patch, verify & sign script**.
- `gen_classes2_v127.py` & `gen_classes2_v1214.py` — Pure-Python DEX generators for `classes2.dex`.
- `sign_apk.py` & `.signing_keys/` — Pure-Python APK v1+v2 signer and release signing keys.

## 3. Build, Verify & Sign (One Command)
```bash
python3 build_fnv_native_v1215.py
```
Outputs signed `output/Fallout Droid NV Native.apk` and `output/Fallout Droid NV Native.zip`.

## 4. Critical Technical Invariants (DO NOT BREAK)
1. **AArch64 Code Cave Bounds in `lib/arm64-v8a/libfnv.so`**:
   - Safe code cave is strictly **`0x53fb664 .. 0x53fb9c0`**. NEVER overflow past `0x53fb9c0` — live function at **`0x53fb9ec`** (`0x53fb9c8..0x53fba74`).
2. **XAudio2 Starvation & Audio Crackling Fix (v1.2.15)**:
   - `-6 dB` (`0.5x`) headroom + rational soft limiter (`x / (1 + 0.5*|x|)`), 5 ms (240-frame) linear gain ramp on starvation (`>16` silent frames), 32 KB `AudioTrack` buffer + 120 ms prefill + `Thread.MAX_PRIORITY`, 440 Hz beep removed.
3. **DEX & Signing**: Always recompute DEX SHA-1 (`[12:32]`) and Adler32 (`[8:12]`) and sign with `.signing_keys/release.key.pem`.
