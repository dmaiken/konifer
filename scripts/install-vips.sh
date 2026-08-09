#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# Configuration
PREFIX="/usr/local"
VIPS_VERSION="8.18.5"
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

# Detect OS
OS="$(uname -s)"

echo "Starting VIPS setup. Version: $VIPS_VERSION, Prefix: $PREFIX, OS: $OS"

# System Dependencies
if [ "$INSTALL_DEPS" = true ]; then
  echo "Installing system dependencies..."
  if [ "$OS" = "Darwin" ]; then
    brew install \
      pkg-config ninja meson \
      glib libexif expat \
      libimagequant jpeg-turbo little-cms2 \
      libpng \
      webp libheif libde265 \
      jpeg-xl giflib cgif aom \
      x265
  else
    apt-get update && apt-get install -y \
      build-essential pkg-config ninja-build curl meson \
      glib2.0-dev libexif-dev libexpat1-dev \
      libimagequant-dev libjpeg-turbo8-dev liblcms2-dev \
      libpng-dev \
      libwebp-dev libheif-dev libde265-dev \
      libjxl-dev libcgif-dev libaom-dev \
      libheif-plugin-x265 libheif-plugin-svtenc libheif-plugin-libde265 \
      libheif-plugin-dav1d
  fi
fi

echo "Downloading and compiling VIPS..."

mkdir -p $BUILD_DIR
cd $BUILD_DIR

curl -L "${VIPS_URL}/v${VIPS_VERSION}/vips-${VIPS_VERSION}.tar.xz" -o "vips-${VIPS_VERSION}.tar.xz"
tar xf "vips-${VIPS_VERSION}.tar.xz"
cd "vips-${VIPS_VERSION}"

# Configure, Build, Install
# Note: We install to /usr/local. Local devs might need sudo access for this step
# or should run this script with sudo.
if [ "$OS" = "Darwin" ]; then
  JEMALLOC_PREFIX="$(brew --prefix jemalloc)"
  LDFLAGS="-L${JEMALLOC_PREFIX}/lib -ljemalloc"
else
  LDFLAGS="-ljemalloc"
fi

# auto_features=disabled should prevent magick, but I am
# explicitly disabling it just to be safe
meson setup build \
  --prefix="$PREFIX" \
  --libdir=lib \
  --buildtype=release \
  --default-library=shared \
  --strip \
  --wrap-mode=nodownload \
  -Dauto_features=disabled \
  -Dmagick=disabled \
  -Dmagick-module=disabled \
  -Ddeprecated=false \
  -Dcplusplus=false \
  -Dexamples=false \
  -Ddocs=false \
  -Dcpp-docs=false \
  -Dintrospection=disabled \
  -Dvapi=false \
  -Dmodules=disabled \
  -Djpeg=enabled \
  -Dpng=enabled \
  -Dwebp=enabled \
  -Dheif=enabled \
  -Dheif-module=disabled \
  -Djpeg-xl=enabled \
  -Djpeg-xl-module=disabled \
  -Dcgif=enabled \
  -Dnsgif=true \
  -Dimagequant=enabled \
  -Dexif=enabled \
  -Dlcms=enabled \
  -Dhighway=enabled \
  -Dzlib=enabled \
  -Dfftw=disabled \
  -Dppm=false \
  -Danalyze=false \
  -Dradiance=false
cd build
ninja
ninja install

echo "VIPS installed successfully."

# Cleanup (Docker specific)
if [ "$CLEANUP" = true ]; then
  echo "Cleaning up build tools and artifacts..."
  cd /
  rm -rf $BUILD_DIR

  if [ "$OS" != "Darwin" ]; then
    # Remove build-only dependencies to save space
    apt-get remove -y build-essential pkg-config ninja-build
    apt-get autoremove -y
    rm -rf /var/lib/apt/lists/*
  fi
fi
