#!/usr/bin/env bash
# Stage 1 audit for the NVForever-based Android port.
#  - reports which Android/Rust tools are present
#  - clones NVForever at the pinned commit (if missing)
#  - type-checks the platform-independent data layer for aarch64-linux-android
#  - if an Android NDK is present, also type-checks the full engine for Android
# Usage: nvport/stage1_check.sh [workdir]   (default: ~/refs)
set -euo pipefail
NVF_REPO=https://github.com/LootJunkieAzza/NVForever
NVF_PIN=a3e5406                       # audited commit (2026-11-21)
WORK=${1:-$HOME/refs}
mkdir -p "$WORK"

echo "== tools"
for t in rustc cargo cmake ninja clang java gradle adb; do
  printf '%-8s %s\n' "$t" "$(command -v $t >/dev/null && ($t --version 2>/dev/null | head -1) || echo MISSING)"
done
NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
printf '%-8s %s\n' NDK "${NDK:-MISSING (set ANDROID_NDK_HOME)}"
printf '%-8s %s\n' SDK "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-MISSING (set ANDROID_HOME)}}"

rustup target add aarch64-linux-android >/dev/null

if [ ! -d "$WORK/NVForever/.git" ]; then
  git clone -q "$NVF_REPO" "$WORK/NVForever"
fi
git -C "$WORK/NVForever" fetch -q --depth 1 origin "$NVF_PIN" 2>/dev/null || true
echo "NVForever at $(git -C "$WORK/NVForever" rev-parse --short HEAD) (pinned $NVF_PIN)"

echo "== data layer for aarch64-linux-android"
( cd "$WORK/NVForever" && cargo check --release --target aarch64-linux-android \
    -p fnv-esm -p fnv-bsa -p fnv-nif -p fnv-dds -p fnv-vfs -p fnv-world -p fnv-script -p fnv-save )

if [ -n "$NDK" ]; then
  echo "== full engine for aarch64-linux-android (NDK: $NDK)"
  TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
  ( cd "$WORK/NVForever/engine" && \
    CC_aarch64_linux_android="$TC/aarch64-linux-android29-clang" \
    CXX_aarch64_linux_android="$TC/aarch64-linux-android29-clang++" \
    AR_aarch64_linux_android="$TC/llvm-ar" \
    CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TC/aarch64-linux-android29-clang" \
    cargo check --release --target aarch64-linux-android -p nvforever --features bevy/android-native-activity )
else
  echo "== full engine: SKIPPED (no Android NDK; C deps such as blake3 need it)"
fi
