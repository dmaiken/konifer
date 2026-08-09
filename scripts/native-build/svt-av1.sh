#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly DEPENDENCY_NAME="svt-av1"
readonly DEPENDENCY_VERSION="$SVT_AV1_VERSION"
readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly SOURCE_URL="https://gitlab.com/AOMediaCodec/SVT-AV1/-/archive/v${SVT_AV1_VERSION}/SVT-AV1-v${SVT_AV1_VERSION}.tar.gz"
readonly SOURCE_SHA256="$SVT_AV1_SHA256"

native_require_commands cmake curl install mktemp ninja nproc sha256sum tar
native_validate_installer_contract
native_create_work_dir "$DEPENDENCY_NAME"

readonly WORK_DIR="$NATIVE_BUILD_WORK_DIR"
readonly ARCHIVE="$WORK_DIR/svt-av1.tar.gz"
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
  -DBUILD_APPS=OFF \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_TESTING=OFF \
  -DCOMPILE_C_ONLY=OFF \
  -DCOVERAGE=OFF \
  -DEXCLUDE_HASH=ON \
  -DLOG_QUIET=OFF \
  -DMINIMAL_BUILD=OFF \
  -DNATIVE=OFF \
  -DREPRODUCIBLE_BUILDS=ON \
  -DRTC_BUILD=OFF \
  -DSVT_AV1_LTO=ON \
  -DSVT_AV1_PGO=OFF

native_log "Building $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --build "$BUILD_DIR" \
  --target SvtAv1Enc \
  --parallel "${NATIVE_BUILD_JOBS:-$(nproc)}"
native_log "Installing $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --install "$BUILD_DIR" --strip

install -D -m 0644 \
  "$SCRIPT_DIR/native-versions.env" \
  "$INSTALL_PREFIX/share/konifer-native/native-versions.env"

[[ -e "$INSTALL_PREFIX/lib/libSvtAv1Enc.so.4" ]] || \
  native_die "Expected SVT-AV1 v4 ABI was not installed under $INSTALL_PREFIX/lib"

native_log_installed "$DEPENDENCY_NAME" "$DEPENDENCY_VERSION" "$INSTALL_PREFIX"
