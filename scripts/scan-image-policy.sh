#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly repo_root
readonly image="${1:-ghcr.io/dmaiken/konifer:latest}"

if (( $# > 2 )); then
    echo "Usage: $0 [image-or-tar] [json-report]" >&2
    exit 2
fi
if ! command -v trivy >/dev/null 2>&1; then
    echo "Trivy is required to scan ${image}." >&2
    exit 127
fi

report="${2:-}"
if [[ -z "$report" ]]; then
    report="$(mktemp)"
    trap 'rm -f "$report"' EXIT
fi

# Archives let CI inspect exactly the image produced by the build, including on PRs.
source_args=("$image")
if [[ -f "$image" ]]; then
    source_args=(--input "$image")
fi
trivy --config "${repo_root}/trivy-report.yaml" image --output "$report" "${source_args[@]}"
trivy --config "${repo_root}/trivy-report.yaml" convert --scanners license,misconfig --format table "$report"
