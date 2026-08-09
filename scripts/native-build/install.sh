#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

readonly INSTALLERS=(
  zlib-ng.sh
  libpng.sh
  libjpeg-turbo.sh
  libhwy.sh
  libjxl.sh
)

native_log "Installing ${#INSTALLERS[@]} native dependencies"
for installer in "${INSTALLERS[@]}"; do
  native_log "Running $installer"
  bash "$SCRIPT_DIR/$installer"
done
native_log "Installed all native dependencies"
