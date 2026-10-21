#!/usr/bin/env bash
# Clear benchmark data on the Konifer EC2 host using its deployed manifest.
# Invoked as root by SSM with KONIFER_RESET_BUCKET and KONIFER_RESET_DATABASE from
# the controller. Requires AWS CLI, jq, psql, Docker, and mount inspection tools.
# Drops/recreates the benchmark database, empties its bucket, and deletes scratch
# contents. Retains infrastructure/models. Sourcing defines helpers without reset.
set -euo pipefail

# Refuse deletion unless the fixed scratch path is an ext4 instance-store mount.
# Status: nonzero for symlinks, other backing devices, or nested mounts. Read-only.
validate_reset_scratch() {
  local scratch=/mnt/konifer-scratch device
  [[ $(realpath "$scratch") == "$scratch" && $(findmnt -n -r --mountpoint "$scratch" -o FSTYPE) == ext4 ]] || {
    echo 'Unexpected scratch mount; refusing reset' >&2
    return 1
  }
  device=$(findmnt -n -r --mountpoint "$scratch" -o SOURCE)
  [[ $(lsblk -dn -o MODEL "$device") == *'Instance Storage'* ]] || {
    echo 'Scratch is not EC2 instance storage; refusing reset' >&2
    return 1
  }
  [[ $(findmnt -rn -o TARGET | awk '$0 ~ /^\/mnt\/konifer-scratch\// { count++ } END { print count+0 }') == 0 ]] || {
    echo 'Scratch has nested mounts; refusing reset' >&2
    return 1
  }
}

# Read one S3 listing page and fail unless the target bucket contains zero keys.
# Arguments: verified benchmark bucket name. Uses host credentials; deletes nothing.
verify_empty_object_bucket() {
  local bucket=${1:?Expected object bucket}
  # KeyCount belongs to an individual API page; the CLI's auto-pagination
  # aggregate omits it. An empty first page proves the bucket is empty.
  [[ $(aws s3api list-objects-v2 --bucket "$bucket" --no-paginate --query KeyCount --output text) == 0 ]] || {
    echo 'Object bucket is not empty after reset' >&2
    return 1
  }
}

# Validate controller targets, stop Konifer, then clear database/bucket/scratch.
# Reads KONIFER_RESET_BUCKET/DATABASE and the current manifest; fetches the database
# password into psql's environment. Marks reset-incomplete before the first change;
# the controller clears that marker only after bootstrap and checkpoint succeed.
main() {
  cd /opt/konifer-performance/current
  validate_reset_scratch
  local bucket secret container upload key upload_id
  bucket=$(jq -er .infrastructure.objectBucket manifest.json)
  secret=$(jq -er .infrastructure.secretArn manifest.json)
  # Compare the remote deployment identity to the exact controller-resolved targets.
  [[ $bucket == "${KONIFER_RESET_BUCKET:?}" && $(jq -er .infrastructure.databaseId manifest.json) == "${KONIFER_RESET_DATABASE:?}" ]] || {
    echo 'Reset target differs from deployed stack' >&2; return 1;
  }
  touch /opt/konifer-performance/reset-incomplete
  container=$(docker ps -aq --filter label=com.docker.compose.project=konifer-performance --filter label=com.docker.compose.service=konifer)
  if [[ -n $container ]]; then docker stop "$container"; fi
  export PGHOST PGUSER=konifer_user PGDATABASE=postgres PGSSLMODE=verify-full
  PGHOST=$(jq -er .connections.rdsEndpoint manifest.json)
  export PGSSLROOTCERT="$PWD/generated/rds-ca-bundle.pem" PGPASSWORD
  PGPASSWORD=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text | jq -er .password)
  psql -X --no-password --set ON_ERROR_STOP=1 -c 'DROP DATABASE IF EXISTS konifer WITH (FORCE);'
  psql -X --no-password --set ON_ERROR_STOP=1 -c 'CREATE DATABASE konifer OWNER konifer_user;'
  PGDATABASE=konifer psql -X --no-password --set ON_ERROR_STOP=1 -c 'CREATE EXTENSION ltree;'
  unset PGPASSWORD
  aws s3 rm "s3://$bucket/" --recursive --only-show-errors
  # Abort old incomplete uploads so bytes do not leak across release boundaries.
  aws s3api list-multipart-uploads --bucket "$bucket" --output json | jq -r '.Uploads[]? | [.Key,.UploadId] | @base64' | while IFS= read -r upload; do
    key=$(printf '%s' "$upload" | base64 -d | jq -r '.[0]')
    upload_id=$(printf '%s' "$upload" | base64 -d | jq -r '.[1]')
    aws s3api abort-multipart-upload --bucket "$bucket" --key "$key" --upload-id "$upload_id"
  done
  verify_empty_object_bucket "$bucket"
  # Fixed, validated mount only; do not cross filesystems or remove the mount itself.
  find /mnt/konifer-scratch -xdev -mindepth 1 -delete
  rm -f -- /opt/konifer-performance/initialized-image
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then main "$@"; fi
