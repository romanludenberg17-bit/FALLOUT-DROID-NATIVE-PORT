#!/usr/bin/env bash
# Fallout Droid (native) — full build: engine -> APK -> sign (v1+v2+v3) -> verify.
#   nvport/build_apk.sh            # rebuild engine if needed, build and sign the APK
#   SKIP_ENGINE=1 nvport/build_apk.sh   # reuse jniLibs/arm64-v8a/libnvforever.so
# Output: nvport/output/FalloutDroid-Native-<versionName>.apk
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
BT=$ANDROID_HOME/build-tools/36.0.0
KEYS=$HERE/android/.signing_keys
SO=$HERE/android/app/src/main/jniLibs/arm64-v8a/libnvforever.so

[ -f "$KEYS/release.key.pem" ] && [ -f "$KEYS/release.cert.pem" ] || { echo "missing signing keys in $KEYS"; exit 1; }
if [ -z "${SKIP_ENGINE:-}" ] || [ ! -f "$SO" ]; then
  "$HERE/build_engine.sh"
fi
NDKBIN=$ANDROID_HOME/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin
"$NDKBIN/llvm-nm" -D --defined-only "$SO" | grep -q "GameActivity_onCreate" || { echo "engine is not a GameActivity build"; exit 1; }
"$NDKBIN/llvm-nm" -D --defined-only "$SO" | grep -q "Java_com_wastelanddroid_engine_NativeInput_key" || { echo "engine lacks NativeInput JNI"; exit 1; }
# A library with initial-exec TLS cannot be dlopen'ed on Android (alpha33: "TLS symbol ...
# using IE access model", the game never started).
if "$NDKBIN/llvm-readelf" -d "$SO" | grep -q STATIC_TLS; then echo "engine has STATIC_TLS (initial-exec TLS): not loadable"; exit 1; fi

# Automatic report upload (ReportUploader.java): {"repo":"owner/name","token":"..."} kept
# beside the signing keys, never in git; without it the APK uploads nothing.
CFG=$HERE/android/app/src/main/assets/report_upload.json
if [ -f "$KEYS/report_upload.json" ]; then cp "$KEYS/report_upload.json" "$CFG"; else rm -f "$CFG"; fi

cd "$HERE/android"
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle --no-daemon -q assembleRelease
UNSIGNED=app/build/outputs/apk/release/app-release-unsigned.apk
VN=$(sed -n 's/^def fdVersionName = "\(.*\)"/\1/p' app/build.gradle)
mkdir -p "$HERE/output"
OUT="$HERE/output/FalloutDroid-Native-$VN.apk"
TMP=$(mktemp -d)
openssl pkcs8 -topk8 -nocrypt -in "$KEYS/release.key.pem" -outform DER -out "$TMP/key.pk8"
"$BT/zipalign" -f -p 4 "$UNSIGNED" "$TMP/aligned.apk"
"$BT/apksigner" sign --key "$TMP/key.pk8" --cert "$KEYS/release.cert.pem" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$TMP/aligned.apk"
rm -rf "$TMP"
"$BT/apksigner" verify --verbose "$OUT" | grep -E "Verified using|Number of signers"
ls -la "$OUT"
