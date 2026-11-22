#!/usr/bin/env bash
# Build NVForever as libnvforever.so for arm64-v8a (Android 10+, API 29).
# Code for every 64-bit ARM phone (armv8-a baseline: Cortex-A53/A73 included; LSE atomics are
# picked at run time by Rust's outline atomics where the core has them), thin LTO across the
# engine's crates and 8 code generation units (more inlining than the default 16).
# Usage: nvport/build_engine.sh [refs dir]   -> nvport/android/app/src/main/jniLibs/arm64-v8a/
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REFS=${1:-$(cd "$HERE/../.." && pwd)/refs}
NVF_COMMIT=a3e5406
NVF=$REFS/NVForever
NDK=${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/29.0.14206865}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
API=29
if [ ! -d "$NVF/.git" ]; then
  git clone -q https://github.com/LootJunkieAzza/NVForever "$NVF"
  git -C "$NVF" checkout -q "$NVF_COMMIT"
fi
# apply our patches once: applied now, already applied, or stop
for p in "$HERE"/patches/nvforever/*.patch; do
  if git -C "$NVF" apply --check "$p" 2>/dev/null; then git -C "$NVF" apply "$p"; echo "applied $(basename "$p")"
  elif git -C "$NVF" apply --check -R "$p" 2>/dev/null; then echo "already applied $(basename "$p")"
  else echo "error: $(basename "$p") does not apply to $NVF (expected commit $NVF_COMMIT)" >&2; exit 1; fi
done
# android-activity with the GameActivity glue that never blocks the Java main thread on the
# engine's thread (a long frame made Android end the game as "not responding"): the crate
# copied from cargo's registry into the engine's vendor folder, our patch on it.
AA="$NVF/engine/vendor/android-activity"
if [ ! -f "$AA/.nvport-patched" ]; then
  (cd "$NVF/engine" && cargo fetch -q >/dev/null 2>&1 || true)
  SRC=$(ls -d "${CARGO_HOME:-$HOME/.cargo}"/registry/src/*/android-activity-0.6.1 | head -1)
  rm -rf "$AA" && cp -r "$SRC" "$AA" && rm -f "$AA/.cargo_vcs_info.json"
  patch -s -d "$AA" -p1 < "$HERE/patches/android-activity-0.6.1-glue-no-wait.patch"
  touch "$AA/.nvport-patched"
fi
rustup target add aarch64-linux-android >/dev/null
cd "$NVF/engine"
env PATH="$TC:$PATH" ANDROID_NDK_HOME="$NDK" \
  CC_aarch64_linux_android="$TC/aarch64-linux-android$API-clang" \
  CXX_aarch64_linux_android="$TC/aarch64-linux-android$API-clang++" \
  AR_aarch64_linux_android="$TC/llvm-ar" RANLIB_aarch64_linux_android="$TC/llvm-ranlib" \
  CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TC/aarch64-linux-android$API-clang" \
  CARGO_PROFILE_RELEASE_LTO=thin \
  CARGO_PROFILE_RELEASE_CODEGEN_UNITS=8 \
  cargo build --release --target aarch64-linux-android -p nvforever --lib \
    --features bevy/android-game-activity
OUT="$HERE/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUT"
"$TC/llvm-strip" --strip-all -o "$OUT/libnvforever.so" target/aarch64-linux-android/release/libnvforever.so
# libc++_shared.so only when the engine links it dynamically
if "$TC/llvm-readelf" -d "$OUT/libnvforever.so" | grep -q "libc++_shared"; then cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$OUT/"; else rm -f "$OUT/libc++_shared.so"; fi
ls -la "$OUT"
