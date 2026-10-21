#!/usr/bin/env bash
# Bootstrap one EC2 host as root through SSM: aws-host.sh ROLE BUNDLE_DIRECTORY.
# Requires an extracted deployment bundle and the host's instance profile. Installs
# system tools and starts Docker; the Konifer role may format one verified blank
# instance-store disk. Sourcing exposes helpers without bootstrapping the host.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

# Install the pinned host tools and mark completion for subsequent bootstraps.
# Side effects: apt packages, /usr/local tools, Docker service, and the ready marker.
# Status: nonzero on installation/checksum failure; leave the marker absent.
install_dependencies() {
  apt-get update -qq
  apt-get install -y -qq docker.io docker-compose-v2 postgresql-client jq unzip curl xz-utils
  systemctl enable --now docker
  local download_dir
  download_dir=$(mktemp -d)
  curl --fail --silent --show-error --location --retry 3 \
    https://awscli.amazonaws.com/awscli-exe-linux-aarch64-2.38.2.zip -o "$download_dir/aws.zip"
  unzip -q "$download_dir/aws.zip" -d "$download_dir"
  "$download_dir/aws/install" --update
  curl --fail --silent --show-error --location --retry 3 \
    https://nodejs.org/dist/v24.19.0/node-v24.19.0-linux-arm64.tar.xz -o "$download_dir/node-v24.19.0-linux-arm64.tar.xz"
  curl --fail --silent --show-error --location --retry 3 \
    https://nodejs.org/dist/v24.19.0/SHASUMS256.txt -o "$download_dir/SHASUMS256.txt"
  (cd "$download_dir" && sha256sum --check --ignore-missing SHASUMS256.txt)
  tar -xJf "$download_dir/node-v24.19.0-linux-arm64.tar.xz" -C /usr/local --strip-components=1
  # Only remove our freshly created temporary directory.
  rm -r -- "$download_dir"
  touch /opt/konifer-performance/dependencies-ready
}

# Mount verified EC2 instance storage and grant the image user ownership.
# Arguments: numeric image UID and GID. Formats only a single blank, unpartitioned
# instance-store disk; refuses other mounts, partitions, or existing signatures.
# Status: nonzero on unsafe storage or command failure. Emits tool diagnostics.
prepare_scratch() {
  local scratch=/mnt/konifer-scratch device filesystem signature
  if mountpoint -q "$scratch"; then
    filesystem=$(findmnt -n -r --mountpoint "$scratch" -o FSTYPE)
    device=$(findmnt -n -r --mountpoint "$scratch" -o SOURCE)
    [[ $filesystem == ext4 && $(lsblk -dn -o MODEL "$device") == *"Instance Storage"* ]] || {
      echo "Unexpected scratch mount; refusing to modify it" >&2
      return 1
    }
  else
    # Identify instance storage by its model, not an NVMe number: EBS and local
    # devices can enumerate in different orders. Existing unmounted signatures
    # are ambiguous, so only a blank device is eligible for first-time setup.
    device=$(lsblk --json --nodeps --output PATH,MODEL | jq -er '
      [.blockdevices[] | select((.model // "") | contains("Instance Storage")) | .path]
      | if length == 1 then .[0] else error("Expected exactly one EC2 instance-store disk") end')
    [[ $(lsblk -nr -o PATH "$device" | wc -l) -eq 1 ]] || {
      echo "Instance-store disk has partitions; refusing to format" >&2
      return 1
    }
    signature=$(wipefs --no-act --json "$device" | jq -r '.signatures // [] | length')
    [[ $signature == 0 ]] || {
      echo "Unmounted instance-store disk is not blank; refusing to format" >&2
      return 1
    }
    mkfs.ext4 -F "$device"
    mkdir -p "$scratch"
    mount "$device" "$scratch"
  fi
  local uid=${1:?Expected UID} gid=${2:?Expected GID}
  chown "$uid:$gid" "$scratch"
}

# Print the released image's numeric UID:GID, or fail before changing ownership.
# Arguments: immutable image reference. Runs two short-lived containers; stdout
# contains only the identity so the caller can reuse it across bind mounts.
image_identity() {
  local image=${1:?Expected image reference} uid gid
  # Bash clears errexit inside command substitutions; propagate each probe's
  # failure even when a failing container printed a numeric value first.
  uid=$(docker run --rm --entrypoint id "$image" -u) || return
  gid=$(docker run --rm --entrypoint id "$image" -g) || return
  [[ $uid =~ ^[0-9]+$ && $gid =~ ^[0-9]+$ ]] || {
    echo "Image user and group must be numeric" >&2
    return 1
  }
  printf '%s:%s\n' "$uid" "$gid"
}

# Grant the image user access to the complete, composed HOCON configuration.
# Arguments: UID, GID, generated directory. Changes ownership of three files;
# config contents and permissions remain unchanged. Fails if a file is missing.
prepare_configuration_permissions() {
  local uid=${1:?Expected UID} gid=${2:?Expected GID} directory=${3:?Expected config directory} configuration
  for configuration in konifer.conf konifer.source.conf konifer.aws.conf; do
    chown "$uid:$gid" "$directory/$configuration"
  done
}

# Prepare storage, bind mounts, and database credentials, then start Konifer.
# Arguments: deployment bundle directory; caller must cd to its performance tree.
# Exports AWS_DEFAULT_REGION, KONIFER_IMAGE, and a temporary PG_PASSWORD for startup.
# Refuses image changes on an initialized database; marks before migrations so a
# failed startup cannot make a later image switch appear safe.
prepare_konifer() {
  local bundle=${1:?Expected deployment directory}
  export AWS_DEFAULT_REGION
  AWS_DEFAULT_REGION=$(jq -er '.connections.region' "$bundle/generated/deployment.json")
  export KONIFER_IMAGE
  KONIFER_IMAGE=$(jq -er '.image.reference' "$bundle/manifest.json")
  local marker=/opt/konifer-performance/initialized-image
  if [[ -f $marker && $(< "$marker") != "$KONIFER_IMAGE" ]]; then
    echo "Image digest changed on an initialized database. Use aws.sh reset --docker-tag TAG." >&2
    return 1
  fi
  docker pull "$KONIFER_IMAGE"
  local identity uid gid
  identity=$(image_identity "$KONIFER_IMAGE")
  IFS=: read -r uid gid <<< "$identity"
  prepare_scratch "$uid" "$gid"
  curl --fail --silent --show-error --location --retry 3 \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o "$bundle/generated/rds-ca-bundle.pem"
  # Local preparation uses umask 077. Bind mounts must be readable by the
  # released image's non-root user, including privately downloaded model files.
  prepare_configuration_permissions "$uid" "$gid" "$bundle/generated"
  chown -R "$uid:$gid" "$(realpath "$bundle/generated/models")"
  local secret_arn
  secret_arn=$(jq -er '.infrastructure.secretArn' "$bundle/manifest.json")
  export PG_PASSWORD
  PG_PASSWORD=$(aws secretsmanager get-secret-value --secret-id "$secret_arn" --query SecretString --output text | jq -er .password)
  # Mark before migrations: a failed startup may already have modified the schema.
  printf '%s\n' "$KONIFER_IMAGE" > "$marker"
  bash runtime/aws-start.sh "$bundle/generated"
  unset PG_PASSWORD
}

# Install common dependencies, then prepare the requested host role.
# Arguments: konifer|k6 and deployment bundle directory. Changes working directory;
# the k6 role pulls its pinned image but never reads the database secret or models.
main() {
  local role=${1:?Expected host role} bundle=${2:?Expected deployment directory}
  [[ $role == konifer || $role == k6 ]] || return 1
  mkdir -p /opt/konifer-performance
  if [[ ! -f /opt/konifer-performance/dependencies-ready ]]; then
    install_dependencies
  fi
  cd "$bundle/performance"
  npm ci --ignore-scripts --no-audit --no-fund
  if [[ $role == k6 ]]; then
    docker pull "$(jq -er .k6Image "$bundle/manifest.json")"
    return 0
  fi
  prepare_konifer "$bundle"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then main "$@"; fi
