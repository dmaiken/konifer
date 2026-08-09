#!/usr/bin/env bash

# The temporary directory created by native_create_work_dir(). Dependency
# installers may read this value after calling that function.
NATIVE_BUILD_WORK_DIR=""

#######################################
# Writes a consistently formatted native-build lifecycle message.
# Arguments:
#   $@: Message components, joined with spaces.
# Outputs:
#   Writes the message to stdout.
#######################################
native_log() {
  printf '[native-build] %s\n' "$*"
}

#######################################
# Reports an error and terminates the current installer.
# Arguments:
#   $@: Error message components, joined with spaces.
# Outputs:
#   Writes the error message to stderr.
# Returns:
#   Exits with status 1.
#######################################
native_die() {
  printf '[native-build] ERROR: %s\n' "$*" >&2
  exit 1
}

#######################################
# Verifies that every command required by an installer is available.
# Arguments:
#   $@: Command names to locate through PATH.
# Outputs:
#   Writes an error to stderr when a command is unavailable.
# Returns:
#   Exits with status 1 if any command is unavailable.
#######################################
native_require_commands() {
  local command_name

  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 || \
      native_die "Required command is not installed: $command_name"
  done
}

#######################################
# Validates the variables that form the native-installer contract.
# Globals:
#   DEPENDENCY_NAME
#   DEPENDENCY_VERSION
#   INSTALL_PREFIX
#   SOURCE_SHA256
#   SOURCE_URL
# Outputs:
#   Writes an error to stderr when a required variable is empty or invalid.
# Returns:
#   Exits with status 1 when the installer contract is not satisfied.
#######################################
native_validate_installer_contract() {
  local variable_name

  for variable_name in \
    DEPENDENCY_NAME \
    DEPENDENCY_VERSION \
    INSTALL_PREFIX \
    SOURCE_SHA256 \
    SOURCE_URL; do
    [[ -n ${!variable_name:-} ]] || \
      native_die "Installer variable is required: $variable_name"
  done

  [[ "$DEPENDENCY_NAME" =~ ^[[:alnum:]._-]+$ ]] || \
    native_die "DEPENDENCY_NAME contains unsupported characters: $DEPENDENCY_NAME"

  native_validate_sha256 "$SOURCE_SHA256"
}

#######################################
# Validates a SHA-256 checksum encoded as 64 hexadecimal characters.
# Arguments:
#   $1: SHA-256 checksum.
# Outputs:
#   Writes an error to stderr when the checksum is malformed.
# Returns:
#   Exits with status 1 when the checksum is malformed.
# Notes:
#   This validates hexadecimal encoding, not Base64. Upstream release checksum
#   files conventionally publish SHA-256 digests in hexadecimal form.
#######################################
native_validate_sha256() {
  local checksum="${1:-}"

  [[ "$checksum" =~ ^[[:xdigit:]]{64}$ ]] || \
    native_die "SOURCE_SHA256 must be a 64-character hexadecimal SHA-256 digest"
}

#######################################
# Creates and registers a temporary workspace for the current installer.
# Arguments:
#   $1: Dependency name used in the temporary-directory prefix.
# Globals:
#   NATIVE_BUILD_WORK_DIR: Set to the newly created directory.
#   TMPDIR: Optional parent directory; defaults to /tmp.
# Outputs:
#   Writes an error to stderr if a workspace was already registered.
# Returns:
#   Exits with status 1 if called more than once in the same installer.
# Notes:
#   Registers native_cleanup as the EXIT trap for the calling shell.
#######################################
native_create_work_dir() {
  local dependency_name="${1:-}"

  [[ -z "$NATIVE_BUILD_WORK_DIR" ]] || \
    native_die "A native-build workspace is already registered: $NATIVE_BUILD_WORK_DIR"
  [[ "$dependency_name" =~ ^[[:alnum:]._-]+$ ]] || \
    native_die "Invalid dependency name for temporary workspace: $dependency_name"

  NATIVE_BUILD_WORK_DIR="$(
    mktemp -d "${TMPDIR:-/tmp}/konifer-native-${dependency_name}.XXXXXX"
  )"
  trap native_cleanup EXIT
}

#######################################
# Removes the temporary workspace registered by native_create_work_dir().
# Globals:
#   NATIVE_BUILD_WORK_DIR: Directory to remove, then clear.
# Outputs:
#   Writes an error to stderr if the registered path fails safety validation.
# Returns:
#   Returns 0 when no workspace exists or cleanup succeeds.
#   Returns 1 when the registered path is not a recognized build workspace.
#######################################
native_cleanup() {
  local work_dir="$NATIVE_BUILD_WORK_DIR"

  [[ -n "$work_dir" ]] || return 0
  if [[ "${work_dir##*/}" != konifer-native-* ]]; then
    printf '[native-build] ERROR: Refusing to remove unexpected path: %s\n' \
      "$work_dir" >&2
    return 1
  fi

  rm -rf -- "$work_dir"
  NATIVE_BUILD_WORK_DIR=""
}

#######################################
# Downloads a dependency source archive with retries.
# Arguments:
#   $1: Dependency name used for lifecycle logging.
#   $2: Dependency version used for lifecycle logging.
#   $3: Source URL.
#   $4: Destination archive path.
# Outputs:
#   Writes lifecycle information to stdout and curl errors to stderr.
# Returns:
#   Returns curl's non-zero status if the download fails.
#######################################
native_download_source() {
  local dependency_name="$1"
  local dependency_version="$2"
  local source_url="$3"
  local destination="$4"

  native_log "Downloading $dependency_name $dependency_version"
  curl \
    --fail \
    --location \
    --retry 3 \
    --retry-all-errors \
    --silent \
    --show-error \
    --output "$destination" \
    "$source_url"
}

#######################################
# Verifies a downloaded file against an expected SHA-256 checksum.
# Arguments:
#   $1: Dependency name used in error messages.
#   $2: Path to the downloaded file.
#   $3: Expected hexadecimal SHA-256 checksum.
# Outputs:
#   Writes verification lifecycle information to stdout.
#   Writes expected and actual checksums to stderr on mismatch.
# Returns:
#   Exits with status 1 when the checksum is malformed or does not match.
#######################################
native_verify_sha256() {
  local dependency_name="$1"
  local file_path="$2"
  local expected_sha256="${3,,}"
  local actual_sha256

  native_validate_sha256 "$expected_sha256"
  actual_sha256="$(sha256sum "$file_path")"
  actual_sha256="${actual_sha256%% *}"

  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    printf '[native-build] ERROR: %s archive checksum mismatch\n' \
      "$dependency_name" >&2
    printf 'Expected: %s\n' "$expected_sha256" >&2
    printf 'Actual:   %s\n' "$actual_sha256" >&2
    exit 1
  fi

  native_log "Verified $dependency_name source checksum"
}

#######################################
# Logs successful installation of a native dependency.
# Arguments:
#   $1: Dependency name.
#   $2: Dependency version.
#   $3: Installation prefix.
# Outputs:
#   Writes the installation lifecycle message to stdout.
#######################################
native_log_installed() {
  local dependency_name="$1"
  local dependency_version="$2"
  local install_prefix="$3"

  native_log "Installed $dependency_name $dependency_version to $install_prefix"
}
