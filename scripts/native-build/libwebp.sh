#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly DEPENDENCY_NAME="libwebp"
readonly DEPENDENCY_VERSION="$LIBWEBP_VERSION"
readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly SOURCE_URL="https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-${LIBWEBP_VERSION}.tar.gz"
readonly SOURCE_SHA256="$LIBWEBP_SHA256"

native_require_commands cmake curl install ldd mktemp ninja nproc sha256sum tar
native_validate_installer_contract
native_create_work_dir "$DEPENDENCY_NAME"

readonly WORK_DIR="$NATIVE_BUILD_WORK_DIR"
readonly ARCHIVE="$WORK_DIR/libwebp.tar.gz"
readonly SOURCE_DIR="$WORK_DIR/source"
readonly BUILD_DIR="$WORK_DIR/build"

native_download_source \
  "$DEPENDENCY_NAME" \
  "$DEPENDENCY_VERSION" \
  "$SOURCE_URL" \
  "$ARCHIVE"
native_verify_sha256 "$DEPENDENCY_NAME" "$ARCHIVE" "$SOURCE_SHA256"

mkdir -p "$SOURCE_DIR"
tar --extract --gzip --file "$ARCHIVE" --directory "$SOURCE_DIR" --strip-components=1

native_log "Configuring $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR=lib \
  -DBUILD_SHARED_LIBS=ON \
  -DWEBP_BUILD_ANIM_UTILS=OFF \
  -DWEBP_BUILD_CWEBP=OFF \
  -DWEBP_BUILD_DWEBP=OFF \
  -DWEBP_BUILD_EXTRAS=OFF \
  -DWEBP_BUILD_FUZZTEST=OFF \
  -DWEBP_BUILD_GIF2WEBP=OFF \
  -DWEBP_BUILD_IMG2WEBP=OFF \
  -DWEBP_BUILD_LIBWEBPMUX=ON \
  -DWEBP_BUILD_VWEBP=OFF \
  -DWEBP_BUILD_WEBPINFO=OFF \
  -DWEBP_BUILD_WEBPMUX=OFF \
  -DWEBP_BUILD_WEBP_JS=OFF \
  -DWEBP_ENABLE_SIMD=ON \
  -DWEBP_LINK_STATIC=OFF \
  -DWEBP_USE_THREAD=ON

native_log "Building $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --build "$BUILD_DIR" --parallel "${NATIVE_BUILD_JOBS:-$(nproc)}"
native_log "Installing $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --install "$BUILD_DIR" --strip

install -D -m 0644 \
  "$SCRIPT_DIR/native-versions.env" \
  "$INSTALL_PREFIX/share/konifer-native/native-versions.env"

readonly WEBP_LIBRARY="$INSTALL_PREFIX/lib/libwebp.so.7"
readonly SHARPYUV_LIBRARY="$INSTALL_PREFIX/lib/libsharpyuv.so.0"
readonly EXPECTED_LIBRARIES=(
  "$WEBP_LIBRARY"
  "$INSTALL_PREFIX/lib/libwebpdecoder.so.3"
  "$INSTALL_PREFIX/lib/libwebpdemux.so.2"
  "$INSTALL_PREFIX/lib/libwebpmux.so.3"
  "$SHARPYUV_LIBRARY"
)

for library_path in "${EXPECTED_LIBRARIES[@]}"; do
  [[ -e "$library_path" ]] || \
    native_die "Expected shared library was not installed: $library_path"
done

LD_LIBRARY_PATH="$INSTALL_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  ldd "$WEBP_LIBRARY" | grep -F "$SHARPYUV_LIBRARY" >/dev/null || \
  native_die "Installed libwebp does not resolve to libsharpyuv under $INSTALL_PREFIX/lib"

native_log_installed "$DEPENDENCY_NAME" "$DEPENDENCY_VERSION" "$INSTALL_PREFIX"
