#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly repo_root

if (( $# > 1 )); then
    echo "Usage: $0 [report-directory]" >&2
    exit 2
fi
if ! command -v trivy >/dev/null 2>&1; then
    echo "Trivy is required to scan Dockerfiles." >&2
    exit 127
fi

staging="$(mktemp -d)"
readonly staging
trap 'rm -rf "$staging"' EXIT
readonly report_directory="${1:-${staging}/reports}"
mkdir -p "$report_directory" "$staging/source"
# Stage only the intended build definitions; do not scan test fixtures or build outputs.
cp "${repo_root}/Dockerfile" "${repo_root}/Dockerfile.base" "$staging/source/"
trivy --config "${repo_root}/trivy-report.yaml" config \
    --misconfig-scanners dockerfile --file-patterns 'dockerfile:Dockerfile.base' \
    --output "$report_directory/dockerfiles.json" "$staging/source"
trivy --config "${repo_root}/trivy-report.yaml" convert --scanners misconfig --format table "$report_directory/dockerfiles.json"
