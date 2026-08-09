#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly DEPENDENCY_NAME="cgif"
readonly DEPENDENCY_VERSION="$CGIF_VERSION"
readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly SOURCE_URL="https://github.com/dloebl/cgif/archive/refs/tags/v${CGIF_VERSION}.tar.gz"
readonly SOURCE_SHA256="$CGIF_SHA256"

native_require_commands curl install meson mktemp ninja nproc sha256sum tar
native_validate_installer_contract
native_create_work_dir "$DEPENDENCY_NAME"

readonly WORK_DIR="$NATIVE_BUILD_WORK_DIR"
readonly ARCHIVE="$WORK_DIR/cgif.tar.gz"
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
meson setup "$BUILD_DIR" "$SOURCE_DIR" \
  --buildtype=release \
  --default-library=shared \
  --libdir=lib \
  --prefix="$INSTALL_PREFIX" \
  --strip \
  --wrap-mode=nodownload \
  -Dexamples=false \
  -Dfuzzer=false \
  -Dinstall_examples=false \
  -Dtests=false

native_log "Building $DEPENDENCY_NAME $DEPENDENCY_VERSION"
ninja -C "$BUILD_DIR" -j "${NATIVE_BUILD_JOBS:-$(nproc)}"
native_log "Installing $DEPENDENCY_NAME $DEPENDENCY_VERSION"
meson install -C "$BUILD_DIR" --no-rebuild

install -D -m 0644 \
  "$SCRIPT_DIR/native-versions.env" \
  "$INSTALL_PREFIX/share/konifer-native/native-versions.env"

[[ -e "$INSTALL_PREFIX/lib/libcgif.so.0" ]] || \
  native_die "Expected libcgif v0 ABI was not installed under $INSTALL_PREFIX/lib"

native_log_installed "$DEPENDENCY_NAME" "$DEPENDENCY_VERSION" "$INSTALL_PREFIX"
