#!/usr/bin/env bash

set -euo pipefail

readonly NATIVE_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly VIPS_PREFIX="${VIPS_INSTALL_PREFIX:-/usr/local}"
readonly VIPS_LIBRARY="$VIPS_PREFIX/lib/libvips.so.42"
readonly LIBPNG_LIBRARY="$NATIVE_PREFIX/lib/libpng16.so.16"

export LD_LIBRARY_PATH="$NATIVE_PREFIX/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

#######################################
# Reports a native-runtime verification error and terminates the script.
# Arguments:
#   $@: Error message components, joined with spaces.
# Outputs:
#   Writes the error message to stderr.
# Returns:
#   Exits with status 1.
#######################################
runtime_die() {
  printf '[native-runtime] ERROR: %s\n' "$*" >&2
  exit 1
}

#######################################
# Verifies that a shared object and all of its dependencies can be resolved.
# Arguments:
#   $1: Path to the shared object.
# Outputs:
#   Writes unresolved dependency information to stderr.
# Returns:
#   Exits with status 1 when the shared object is absent or a dependency cannot
#   be resolved.
#######################################
runtime_verify_dependencies_resolve() {
  local library_path="$1"
  local dependencies

  [[ -e "$library_path" ]] || runtime_die "Shared library not found: $library_path"
  dependencies="$(ldd "$library_path")" || \
    runtime_die "Could not inspect shared library dependencies: $library_path"

  if grep -F 'not found' <<<"$dependencies" >&2; then
    runtime_die "Shared library has unresolved dependencies: $library_path"
  fi
}

#######################################
# Verifies that a shared object resolves a dependency to an exact path.
# Arguments:
#   $1: Path to the shared object being inspected.
#   $2: Expected resolved dependency path.
# Outputs:
#   Writes the resolved dependency list to stderr on failure.
# Returns:
#   Exits with status 1 when the expected path is not selected by the loader.
#######################################
runtime_verify_dependency_path() {
  local library_path="$1"
  local expected_path="$2"
  local dependencies

  dependencies="$(ldd "$library_path")" || \
    runtime_die "Could not inspect shared library dependencies: $library_path"

  if ! grep -F "=> $expected_path " <<<"$dependencies" >/dev/null; then
    printf '%s\n' "$dependencies" >&2
    runtime_die "$library_path does not resolve dependency to $expected_path"
  fi
}

runtime_verify_dependencies_resolve "$VIPS_LIBRARY"
runtime_verify_dependencies_resolve "$LIBPNG_LIBRARY"

runtime_verify_dependency_path "$VIPS_LIBRARY" "$NATIVE_PREFIX/lib/libjpeg.so.8"
runtime_verify_dependency_path "$VIPS_LIBRARY" "$LIBPNG_LIBRARY"
runtime_verify_dependency_path "$VIPS_LIBRARY" "$NATIVE_PREFIX/lib/libz.so.1"
runtime_verify_dependency_path "$LIBPNG_LIBRARY" "$NATIVE_PREFIX/lib/libz.so.1"

readonly WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/konifer-native-runtime.XXXXXX")"
trap 'rm -rf -- "$WORK_DIR"' EXIT

# JPEG
vips black "$WORK_DIR/libjpeg-turbo-smoke.jpg" 8 8
[[ "$(vipsheader -f width "$WORK_DIR/libjpeg-turbo-smoke.jpg")" == 8 ]] || \
  runtime_die "libjpeg-turbo smoke image has an unexpected width"

# PNG
vips black "$WORK_DIR/libpng-smoke.png" 8 8
[[ "$(vipsheader -f width "$WORK_DIR/libpng-smoke.png")" == 8 ]] || \
  runtime_die "libpng smoke image has an unexpected width"

printf '[native-runtime] Verified native library linkage and image codecs\n'
