#!/bin/bash

# 1. The list of DEV packages you are using in your build
DEV_PACKAGES=(
  "glib2.0-dev"
  "libexif-dev"
  "libexpat1-dev"
  "libfftw3-dev"
  "libimagequant-dev"
  "libjpeg-turbo8-dev"
  "liblcms2-dev"
  "libpango1.0-dev"
  "libpng-dev"
  "libwebp-dev"
  "libheif-dev"
  "libde265-dev"
  "libjxl-dev"
  "libgif-dev"
  "libcgif-dev"
  "libaom-dev"
  "libheif-plugin-x265"
  "libheif-plugin-aomenc"
  "libjemalloc-dev"
)

# 2. Update apt cache so we can query it (only needed if running in a fresh container)
if [ ! -d "/var/lib/apt/lists/partial" ]; then
    echo "Updating apt cache..."
    apt-get update -qq
fi

echo "Resolving Runtime Libraries for Ubuntu $(cat /etc/os-release | grep VERSION_ID | cut -d'"' -f2)..."
echo "----------------------------------------------------------------"

# 3. Loop through and find the runtime dependency
RUNTIME_LIST="wget ca-certificates tini libheif-plugin-aomenc libheif-plugin-x265"

for pkg in "${DEV_PACKAGES[@]}"; do
    # Get dependencies, filter for "Depends:", clean up lines
    # Logic:
    # 1. Look for packages starting with 'lib'
    # 2. Exclude '-dev' packages (recursive build deps)
    # 3. Exclude '-doc' or '-bin' (usually not needed for linking)
    # 4. Exclude common build toolchain noise (libc, libgcc, libstdc++) which are standard in base

    DEPS=$(apt-cache depends "$pkg" \
        | grep "Depends:" \
        | awk '{print $2}' \
        | grep "^lib" \
        | grep -v "\-dev$" \
        | grep -v "\-doc$" \
        | grep -v "\-bin$" \
        | grep -v "libc6" \
        | grep -v "libgcc" \
        | grep -v "libstdc++" \
        | sort -u)

    # Append to our master list
    RUNTIME_LIST="$RUNTIME_LIST $DEPS"
done

# 4. Print unique, sorted list ready for Dockerfile
echo "COPY THIS INTO RUNTIME STAGE:"
echo ""
echo "RUN apt-get update && apt-get install -y --no-install-recommends \\"
echo "$RUNTIME_LIST" | tr ' ' '\n' | sort -u | grep -v "^$" | sed 's/^/    /' | sed 's/$/ \\/'
echo "    && rm -rf /var/lib/apt/lists/*"
