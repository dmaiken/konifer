#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly DEPENDENCY_NAME="libheif"
readonly DEPENDENCY_VERSION="$LIBHEIF_VERSION"
readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly SOURCE_URL="https://github.com/strukturag/libheif/archive/refs/tags/v${LIBHEIF_VERSION}.tar.gz"
readonly SOURCE_SHA256="$LIBHEIF_SHA256"

native_require_commands cmake curl install ldd mktemp ninja nproc pkg-config sha256sum tar
native_validate_installer_contract

readonly DAV1D_LIBRARY="$INSTALL_PREFIX/lib/libdav1d.so.7"
readonly KVAZAAR_LIBRARY="$INSTALL_PREFIX/lib/libkvazaar.so.7"
readonly SHARPYUV_LIBRARY="$INSTALL_PREFIX/lib/libsharpyuv.so.0"
readonly SVT_AV1_LIBRARY="$INSTALL_PREFIX/lib/libSvtAv1Enc.so.4"

for library_path in \
  "$DAV1D_LIBRARY" \
  "$KVAZAAR_LIBRARY" \
  "$SHARPYUV_LIBRARY" \
  "$SVT_AV1_LIBRARY"; do
  [[ -e "$library_path" ]] || \
    native_die "Required native dependency was not found: $library_path"
done

native_create_work_dir "$DEPENDENCY_NAME"

readonly WORK_DIR="$NATIVE_BUILD_WORK_DIR"
readonly ARCHIVE="$WORK_DIR/libheif.tar.gz"
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
PKG_CONFIG_PATH="$INSTALL_PREFIX/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}" \
  cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
    -DCMAKE_INSTALL_LIBDIR=lib \
    -DCMAKE_PREFIX_PATH="$INSTALL_PREFIX" \
    -DBUILD_DEVELOPMENT_TOOLS=OFF \
    -DBUILD_DOCUMENTATION=OFF \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_TESTING=OFF \
    -DENABLE_COVERAGE=OFF \
    -DENABLE_EXPERIMENTAL_FEATURES=OFF \
    -DENABLE_MULTITHREADING_SUPPORT=ON \
    -DENABLE_PARALLEL_TILE_DECODING=ON \
    -DENABLE_PLUGIN_LOADING=OFF \
    -DWITH_AOM_DECODER=OFF \
    -DWITH_AOM_ENCODER=OFF \
    -DWITH_DAV1D=ON \
    -DWITH_DAV1D_PLUGIN=OFF \
    -DWITH_EXAMPLES=OFF \
    -DWITH_FFMPEG_DECODER=OFF \
    -DWITH_FUZZERS=OFF \
    -DWITH_GDK_PIXBUF=OFF \
    -DWITH_HEADER_COMPRESSION=OFF \
    -DWITH_JPEG_DECODER=OFF \
    -DWITH_JPEG_ENCODER=OFF \
    -DWITH_KVAZAAR=ON \
    -DWITH_KVAZAAR_PLUGIN=OFF \
    -DWITH_LIBDE265=ON \
    -DWITH_LIBDE265_PLUGIN=OFF \
    -DWITH_LIBSHARPYUV=ON \
    -DWITH_LIBSHARPYUV_INTERNAL=OFF \
    -DWITH_OpenH264_DECODER=OFF \
    -DWITH_OPENJPH_ENCODER=OFF \
    -DWITH_OpenJPEG_DECODER=OFF \
    -DWITH_OpenJPEG_ENCODER=OFF \
    -DWITH_RAV1E=OFF \
    -DWITH_REDUCED_VISIBILITY=ON \
    -DWITH_SvtEnc=ON \
    -DWITH_SvtEnc_PLUGIN=OFF \
    -DWITH_UNCOMPRESSED_CODEC=OFF \
    -DWITH_UVG266=OFF \
    -DWITH_VVDEC=OFF \
    -DWITH_VVENC=OFF \
    -DWITH_WEBCODECS=OFF \
    -DWITH_X264=OFF \
    -DWITH_X265=OFF \
    -DWITH_X265_PLUGIN=OFF

native_log "Building $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --build "$BUILD_DIR" \
  --target heif \
  --parallel "${NATIVE_BUILD_JOBS:-$(nproc)}"
native_log "Installing $DEPENDENCY_NAME $DEPENDENCY_VERSION"
cmake --install "$BUILD_DIR" --strip

install -D -m 0644 \
  "$SCRIPT_DIR/native-versions.env" \
  "$INSTALL_PREFIX/share/konifer-native/native-versions.env"

readonly INSTALLED_LIBRARY="$INSTALL_PREFIX/lib/libheif.so.1"
[[ -e "$INSTALLED_LIBRARY" ]] || \
  native_die "Expected libheif v1 ABI was not installed under $INSTALL_PREFIX/lib"

readonly INSTALLED_DEPENDENCIES="$(
  LD_LIBRARY_PATH="$INSTALL_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
    ldd "$INSTALLED_LIBRARY"
)"

for library_path in \
  "$DAV1D_LIBRARY" \
  "$KVAZAAR_LIBRARY" \
  "$SHARPYUV_LIBRARY" \
  "$SVT_AV1_LIBRARY"; do
  grep -F "$library_path" <<<"$INSTALLED_DEPENDENCIES" >/dev/null || \
    native_die "Installed libheif does not resolve to $library_path"
done

grep -F 'libde265.so' <<<"$INSTALLED_DEPENDENCIES" >/dev/null || \
  native_die "Installed libheif does not include its libde265.so backend"

for excluded_codec in libaom.so libx265.so; do
  if grep -F "$excluded_codec" <<<"$INSTALLED_DEPENDENCIES" >/dev/null; then
    native_die "Installed libheif unexpectedly links to $excluded_codec"
  fi
done

native_log_installed "$DEPENDENCY_NAME" "$DEPENDENCY_VERSION" "$INSTALL_PREFIX"
