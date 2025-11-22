#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# Configuration
VIPS_VERSION="${VIPS_VERSION:-8.17.1}"
VIPS_URL="https://github.com/libvips/libvips/releases/download"
BUILD_DIR="/tmp/vips-build"

# Flags
INSTALL_DEPS=false
CLEANUP=false

# Parse arguments
for arg in "$@"
do
  case $arg in
    --with-deps)
    INSTALL_DEPS=true
    shift
    ;;
    --cleanup)
    CLEANUP=true
    shift
    ;;
  esac
done

echo "Starting VIPS setup. Version: $VIPS_VERSION"

# System Dependencies (Debian/Ubuntu specific) ---
if [ "$INSTALL_DEPS" = true ]; then
  echo "Installing system dependencies..."
  # specific to the eclipse-temurin (Ubuntu/Debian) base image
  apt-get update && apt-get install -y \
    build-essential pkg-config ninja-build wget meson \
    glib2.0-dev libarchive-dev libexif-dev libexpat1 libfftw3-dev \
    libimagequant-dev libjpeg62 liblcms2-dev liborc-0.4-dev \
    libpango1.0-dev libpng-dev libpoppler-glib-dev librsvg2-dev \
    librsvg2-2 libtiff5-dev libwebp-dev libheif-dev libde265-dev \
    x265 libraw-dev libjxl-dev libgif-dev libaom-dev libheif-plugin-x265 libheif-plugin-aomenc
fi

# Download and Compile VIPS (Universal) ---
echo "Downloading and compiling VIPS..."

mkdir -p $BUILD_DIR
cd $BUILD_DIR

wget "${VIPS_URL}/v${VIPS_VERSION}/vips-${VIPS_VERSION}.tar.xz"
tar xf "vips-${VIPS_VERSION}.tar.xz"
cd "vips-${VIPS_VERSION}"

# Configure, Build, Install
# Note: We install to /usr/local. Local devs might need sudo access for this step
# or should run this script with sudo.
meson build --buildtype=release --libdir=lib
cd build
ninja
ninja install

echo "VIPS installed successfully."

# Cleanup (Docker specific) ---
if [ "$CLEANUP" = true ]; then
  echo "Cleaning up build tools and artifacts..."
  cd /
  rm -rf $BUILD_DIR

  # Remove build-only dependencies to save space
  apt-get remove -y build-essential pkg-config ninja-build
  apt-get autoremove -y
  rm -rf /var/lib/apt/lists/*
fi

# Refresh shared library cache
ldconfig
