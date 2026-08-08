#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly DEPENDENCY_NAME="libpng"
readonly DEPENDENCY_VERSION="$LIBPNG_VERSION"
readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly SOURCE_URL="https://downloads.sourceforge.net/project/libpng/libpng16/${LIBPNG_VERSION}/libpng-${LIBPNG_VERSION}.tar.gz"
readonly SOURCE_SHA256="$LIBPNG_SHA256"

native_require_commands cmake curl install ldd mktemp ninja nproc sha256sum tar
native_validate_installer_contract

readonly ZLIB_LIBRARY="$INSTALL_PREFIX/lib/libz.so"
readonly ZLIB_INCLUDE_DIR="$INSTALL_PREFIX/include"

[[ -e "$ZLIB_LIBRARY" ]] || \
  native_die "zlib-ng must be installed before libpng: $ZLIB_LIBRARY was not found"
[[ -f "$ZLIB_INCLUDE_DIR/zlib.h" ]] || \
  native_die "zlib-ng headers were not found under $ZLIB_INCLUDE_DIR"

native_create_work_dir "$DEPENDENCY_NAME"

readonly WORK_DIR="$NATIVE_BUILD_WORK_DIR"
readonly ARCHIVE="$WORK_DIR/libpng.tar.gz"
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
  -DPNG_HARDWARE_OPTIMIZATIONS=ON \
  -DPNG_SHARED=ON \
  -DPNG_STATIC=OFF \
  -DPNG_TESTS=OFF \
  -DPNG_TOOLS=OFF \
  -DZLIB_INCLUDE_DIR="$ZLIB_INCLUDE_DIR" \
  -DZLIB_LIBRARY="$ZLIB_LIBRARY" \
  -DZLIB_ROOT="$INSTALL_PREFIX"

native_log "Building $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --build "$BUILD_DIR" --parallel "${NATIVE_BUILD_JOBS:-$(nproc)}"
native_log "Installing $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --install "$BUILD_DIR" --strip

install -D -m 0644 \
  "$SCRIPT_DIR/native-versions.env" \
  "$INSTALL_PREFIX/share/konifer-native/native-versions.env"

readonly INSTALLED_LIBRARY="$INSTALL_PREFIX/lib/libpng16.so.16"
[[ -e "$INSTALLED_LIBRARY" ]] || \
  native_die "Expected libpng16 ABI was not installed under $INSTALL_PREFIX/lib"

LD_LIBRARY_PATH="$INSTALL_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  ldd "$INSTALLED_LIBRARY" | grep -F "$INSTALL_PREFIX/lib/libz.so.1" >/dev/null || \
  native_die "Installed libpng does not resolve to zlib-ng under $INSTALL_PREFIX/lib"

native_log_installed "$DEPENDENCY_NAME" "$DEPENDENCY_VERSION" "$INSTALL_PREFIX"
