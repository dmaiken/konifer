#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# Configuration
PREFIX="/usr/local"
VIPS_VERSION="8.18.0"
VIPS_URL="https://github.com/libvips/libvips/releases/download"
BUILD_DIR="/tmp/vips-build"

# Flags
INSTALL_DEPS=false
CLEANUP=false

# Parse arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --with-deps) INSTALL_DEPS=true; shift ;;
    --cleanup) CLEANUP=true; shift ;;
    --prefix) PREFIX="$2"; shift 2 ;;
    *) echo "Unknown parameter passed: $1"; exit 1 ;;
  esac
done

echo "Starting VIPS setup. Version: $VIPS_VERSION, Prefix: $PREFIX"

# System Dependencies (Debian/Ubuntu specific)
if [ "$INSTALL_DEPS" = true ]; then
  echo "Installing system dependencies..."
  apt-get update && apt-get install -y \
    build-essential pkg-config ninja-build wget meson \
    glib2.0-dev libexif-dev libexpat1-dev libfftw3-dev \
    libimagequant-dev libjpeg-turbo8-dev liblcms2-dev \
    libpango1.0-dev libpng-dev \
    libwebp-dev libheif-dev libde265-dev \
    libjxl-dev libgif-dev libcgif-dev libaom-dev \
    libheif-plugin-x265 libheif-plugin-aomenc libjemalloc-dev
fi

echo "Downloading and compiling VIPS..."

mkdir -p $BUILD_DIR
cd $BUILD_DIR

wget "${VIPS_URL}/v${VIPS_VERSION}/vips-${VIPS_VERSION}.tar.xz"
tar xf "vips-${VIPS_VERSION}.tar.xz"
cd "vips-${VIPS_VERSION}"

# Configure, Build, Install
# Note: We install to /usr/local. Local devs might need sudo access for this step
# or should run this script with sudo.
LDFLAGS="-ljemalloc" meson setup build \
  --prefix="$PREFIX" \
  --buildtype=release \
  --libdir=lib \
  -Dintrospection=disabled \
  -Dexamples=false \
  -Dmodules=disabled \
  -Dcplusplus=false \
  -Ddeprecated=false
cd build
ninja
ninja install

echo "VIPS installed successfully."

# Cleanup (Docker specific)
if [ "$CLEANUP" = true ]; then
  echo "Cleaning up build tools and artifacts..."
  cd /
  rm -rf $BUILD_DIR

  # Remove build-only dependencies to save space
  apt-get remove -y build-essential pkg-config ninja-build
  apt-get autoremove -y
  rm -rf /var/lib/apt/lists/*
fi
