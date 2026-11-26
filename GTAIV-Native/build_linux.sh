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
[ -f "$HOME/.android/debug.keystore" ] || { mkdir -p "$HOME/.android"; keytool -genkeypair -keystore "$HOME/.android/debug.keystore" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -validity 10000 -dname "CN=Android Debug"; }

# Final APK: the 4NMP v502 UI shell + this engine (see build_from_4nmp.py).
ASSETS="$WORK/engine_assets"; rm -rf "$ASSETS"; mkdir -p "$ASSETS"
cp -r os/android/app/src/main/assets/. "$ASSETS/"; rm -rf "$ASSETS/Resources/touch-icons"
for f in default_args.txt default_args_mali.txt; do
  grep -q multiplayer_backend "$ASSETS/$f" || echo "--gta4_multiplayer_backend=lan" >> "$ASSETS/$f"
done
cp "$ASSETS/default_args_mali.txt" "$ASSETS/default_args_g99.txt"
python3 -I "$HERE/build_from_4nmp.py" "$HERE/shell/4NMP-v502-ui-only.apk" \
  os/android/app/src/main/jniLibs/arm64-v8a "$ASSETS" "$WORK/unsigned.apk"
BT="$ANDROID_HOME/build-tools/35.0.0"
"$BT/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
if [ -n "${KEYSTORE:-}" ]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "${KS_ALIAS:-gtaiv}" \
    --out "$WORK/GTAIV-Native.apk" "$WORK/aligned.apk"
else
  "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
    --out "$WORK/GTAIV-Native.apk" "$WORK/aligned.apk"
fi
echo "APK: $WORK/GTAIV-Native.apk"
