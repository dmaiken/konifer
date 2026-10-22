#!/usr/bin/env bash
# Execute a staged request on the k6 EC2 host: aws-job.sh REQUEST.json.
# The publish subcommand uploads an intermediate snapshot using KONIFER_RESULT_DIR
# and KONIFER_AWS_UPLOAD_PREFIX. Requires the host instance profile, Docker, flock,
# jq, Node, and AWS CLI. Upload only non-secret requests and benchmark artifacts.
set -euo pipefail

# Archive current results, upload the tar and SHA-256, then remove temporary files.
# Reads KONIFER_RESULT_DIR and KONIFER_AWS_UPLOAD_PREFIX. A failed upload returns
# nonzero and leaves source results on the host for recovery with collect.
publish() {
  local archive checksum
  archive=$(mktemp /opt/konifer-performance/results.XXXXXX.tar)
  checksum="$archive.sha256"
  tar -cf "$archive" -C "${KONIFER_RESULT_DIR:?}" .
  sha256sum "$archive" | cut -d ' ' -f 1 > "$checksum"
  aws s3 cp "$archive" "$KONIFER_AWS_UPLOAD_PREFIX/results.tar" --only-show-errors
  aws s3 cp "$checksum" "$KONIFER_AWS_UPLOAD_PREFIX/results.sha256" --only-show-errors
  rm -- "$archive" "$checksum"
}

# Run one request under the host lock and publish a final snapshot on exit.
# Arguments: staged request file. Exports worker/image/run/API settings for run.sh;
# redirects its output to runner.log. Preserves failure status, including signals.
main() {
  local request=${1:?Expected request file} status=0
  export KONIFER_RESULT_DIR
  KONIFER_RESULT_DIR=$(dirname "$request")
  local run_id
  run_id=$(jq -er .runId "$request")
  [[ $run_id =~ ^[[:alnum:]-]+$ ]] || return 1
  export KONIFER_RUN_ID="$run_id" KONIFER_AWS_WORKER=true
  export KONIFER_K6_IMAGE
  KONIFER_K6_IMAGE=$(jq -er .k6Image /opt/konifer-performance/current/manifest.json)
  export BASE_URL
  BASE_URL=$(jq -er '.connections.koniferPrivateIp | "http://\(.):8080"' /opt/konifer-performance/current/manifest.json)
  # flock survives a disconnected local operator and covers cleanup and final upload.
  exec 9>/opt/konifer-performance/runtime.lock
  flock -n 9 || { echo 'Another benchmark or reset is active' >&2; return 1; }
  [[ ! -f /opt/konifer-performance/reset-incomplete ]] || { echo 'Reset incomplete; retry reset' >&2; return 1; }
  local -a arguments
  mapfile -d '' -t arguments < <(node -e 'for (const a of JSON.parse(require("fs").readFileSync(process.argv[1])).args) process.stdout.write(a + "\0")' "$request")
  cd /opt/konifer-performance/current/performance
  # EXIT handler: uses main's dynamically scoped run_id while the shell exits.
  # Remove any remaining k6 container and publish status even after runner failure.
  # A final upload failure turns success into failure without discarding results.
  finish() {
    local exit_status=$?
    trap - EXIT TERM INT
    docker rm -f "k6-$run_id" >/dev/null 2>&1 || true
    jq -n --argjson exitCode "$exit_status" '{exitCode: $exitCode, complete: true}' > "$KONIFER_RESULT_DIR/status.json"
    publish || { echo 'Final upload failed; artifacts remain on host' >&2; exit_status=1; }
    exit "$exit_status"
  }
  trap finish EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  printf '{"complete":false}\n' > "$KONIFER_RESULT_DIR/status.json"
  docker run --rm "$KONIFER_K6_IMAGE" version > "$KONIFER_RESULT_DIR/k6-version.txt"
  grep -q '^k6 v2\.3\.0 ' "$KONIFER_RESULT_DIR/k6-version.txt"
  bash run.sh "${arguments[@]}" > "$KONIFER_RESULT_DIR/runner.log" 2>&1 || status=$?
  exit "$status"
}

if [[ ${1:-} == publish ]]; then publish; else main "$@"; fi
