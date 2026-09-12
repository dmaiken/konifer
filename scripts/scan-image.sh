#!/usr/bin/env bash

set -euo pipefail

readonly repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly image="${1:-ghcr.io/dmaiken/konifer:latest}"

if ! command -v trivy >/dev/null 2>&1; then
    echo "Trivy is required to scan ${image}." >&2
    echo "Install it from https://trivy.dev/docs/latest/getting-started/installation/" >&2
    exit 127
fi

exec trivy --config "${repo_root}/trivy.yaml" image "${image}"
