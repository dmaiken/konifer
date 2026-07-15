#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

MODEL_REPO="${MODEL_REPO:-onnx-community/siglip2-base-patch16-224-ONNX}"
REVISION="${REVISION:-main}"
OUTPUT_DIR="${OUTPUT_DIR:-${REPO_ROOT}/models/siglip2-base-patch16-224}"
FORCE=false

usage() {
  cat <<EOF
Download the SigLIP2 ONNX model pack used by Konifer.

Usage:
  scripts/download-siglip2-models.sh [options]

Options:
  --output-dir DIR   Destination directory. Default: ${OUTPUT_DIR}
  --repo REPO        Hugging Face repo. Default: ${MODEL_REPO}
  --revision REV     Hugging Face revision, branch, or commit. Default: ${REVISION}
  --force            Re-download files that already exist.
  -h, --help         Show this help.

Environment:
  HF_TOKEN           Optional Hugging Face token for higher rate limits/private repos.
  MODEL_REPO         Default repo override.
  REVISION           Default revision override.
  OUTPUT_DIR         Default output directory override.
EOF
}

display_path() {
  case "$1" in
    "${REPO_ROOT}"/*)
      printf '%s\n' "${1#"${REPO_ROOT}/"}"
      ;;
    *)
      printf '%s\n' "$1"
      ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --repo)
      MODEL_REPO="$2"
      shift 2
      ;;
    --revision)
      REVISION="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to download SigLIP2 model files." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

curl_args=(
  --fail
  --location
  --retry 5
  --retry-delay 2
  --connect-timeout 30
)

if [[ -n "${HF_TOKEN:-}" ]]; then
  curl_args+=(--header "Authorization: Bearer ${HF_TOKEN}")
fi

download_file() {
  local remote_path="$1"
  local output_name="$2"
  local url="https://huggingface.co/${MODEL_REPO}/resolve/${REVISION}/${remote_path}"
  local destination="${OUTPUT_DIR}/${output_name}"
  local partial="${destination}.part"

  if [[ -f "$destination" && "$FORCE" != true ]]; then
    echo "exists  ${destination}"
    return
  fi

  echo "fetch   ${url}"
  echo "write   ${destination}"
  curl "${curl_args[@]}" --continue-at - --output "$partial" "$url"
  mv "$partial" "$destination"
}

download_file "onnx/vision_model.onnx" "vision_model.onnx"
download_file "onnx/text_model.onnx" "text_model.onnx"
download_file "tokenizer.json" "tokenizer.json"

mount_source="$(display_path "$OUTPUT_DIR")"

cat <<EOF

SigLIP2 model pack is ready:
  ${OUTPUT_DIR}

Mount it into Docker read-only, for example:
  - ./${mount_source}:/app/models/siglip2-base-patch16-224:ro

Expected Konifer model paths:
  /app/models/siglip2-base-patch16-224/vision_model.onnx
  /app/models/siglip2-base-patch16-224/text_model.onnx
  /app/models/siglip2-base-patch16-224/tokenizer.json
EOF
