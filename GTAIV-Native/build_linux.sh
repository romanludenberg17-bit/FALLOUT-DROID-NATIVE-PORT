#!/bin/bash
# Full from-source build of GTA IV Native (com.rommy.gtaiv) on Linux x86_64.
# Needs: git, python3, cmake>=3.29, ninja, JDK 17+, ANDROID_HOME with
# NDK 29.0.14206865, platform 35, build-tools 35.0.0.
# usage: ANDROID_HOME=/opt/android-sdk ./build_linux.sh [workdir]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${1:-$HERE/work}"
UPSTREAM_COMMIT=f4f6d8ae904d6d781671759d3d83c983768127d6
SRC="$WORK/LibertyRecompAndroid"
mkdir -p "$WORK"
if [ ! -d "$SRC/.git" ]; then
  GIT_LFS_SKIP_SMUDGE=1 git clone https://github.com/vaduur/LibertyRecompAndroid "$SRC"
fi
cd "$SRC"
git checkout -q "$UPSTREAM_COMMIT"
# webrtc (GameNetworkingSockets, desktop only) is not needed on Android.
git submodule update --init --depth 1 thirdparty/GameNetworkingSockets || true
git -C thirdparty/GameNetworkingSockets config submodule.src/external/webrtc.update none || true
python3 tools/setup_repo.py || python3 tools/setup_repo.py --prepare-only
git apply --check "$HERE/patches/gtaiv-native-android.patch" 2>/dev/null && git apply "$HERE/patches/gtaiv-native-android.patch"
mkdir -p os/android/touch-icons && cp "$HERE"/touch-icons/* os/android/touch-icons/

# Host shader tools (Linux builds of DXC and glslang).
H="$SRC/out/host-tools"; mkdir -p "$H/dxc" "$H/glslang"
if [ ! -x "$H/dxc/bin/dxc" ]; then
  curl -fsSL -o "$H/dxc.tgz" https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.8.2505/linux_dxc_2025_05_24.x86_64.tar.gz
  tar xzf "$H/dxc.tgz" -C "$H/dxc"
fi
if [ ! -x "$H/glslang/bin/glslang" ]; then
  curl -fsSL -o "$H/gl.zip" https://github.com/KhronosGroup/glslang/releases/download/16.6.0/glslang-16.6.0-linux-x86_64-release.zip
  unzip -q -o "$H/gl.zip" -d "$H/glslang"
fi
ln -sf glslang "$H/glslang/bin/glslangValidator"

export BUILD_TYPE=Release STRIP=1
bash os/android/scripts/build_native.sh
bash os/android/scripts/build_driver_proxy.sh
rm -rf os/android/app/build/outputs/apk
bash os/android/scripts/build_apk.sh release
echo "APK: $SRC/os/android/app/build/outputs/apk/release/app-release.apk"
