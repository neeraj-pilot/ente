#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$MODULE_DIR/../../../../.." && pwd)"
FFI_DIR="$REPO_ROOT/rust/ffi"
JNI_LIBS_DIR="$MODULE_DIR/src/main/jniLibs"
JAVA_OUT_DIR="$MODULE_DIR/src/main/java"
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/ndk/27.0.12077973}}"

if [[ ! -d "$NDK_HOME" ]]; then
  echo "Android NDK not found at $NDK_HOME" >&2
  exit 1
fi

rm -rf "$JNI_LIBS_DIR"
mkdir -p "$JNI_LIBS_DIR"

export ANDROID_NDK_HOME="$NDK_HOME"
export ANDROID_NDK_ROOT="$NDK_HOME"

pushd "$FFI_DIR" >/dev/null

cargo ndk \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86_64 \
  -o "$JNI_LIBS_DIR" \
  build \
  --release

cargo run \
  --features bindgen \
  --bin uniffi-bindgen \
  -- generate "$FFI_DIR/src/ente_ffi.udl" \
  --language kotlin \
  --out-dir "$JAVA_OUT_DIR"

# UniFFI's generated Android binding hardcodes `uniffi_ente_ffi`, but this
# crate ships `libente_ffi.so`; rewrite the fallback load name after bindgen.
perl -0pi -e 's/return "uniffi_ente_ffi"/return componentName/' \
  "$JAVA_OUT_DIR/uniffi/ente_ffi/ente_ffi.kt"

popd >/dev/null
