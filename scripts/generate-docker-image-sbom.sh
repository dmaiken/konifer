#!/usr/bin/env bash

set -euo pipefail

if (( $# != 2 )); then
  echo "Usage: $0 <syft-image-source> <output.spdx.json>" >&2
  exit 2
fi

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly REPO_ROOT
readonly IMAGE_SOURCE="$1"
readonly OUTPUT_PATH="$2"
readonly SYFT_COMMAND="${SYFT_CMD:-syft}"

# shellcheck source=native-build/native-versions.env
source "$REPO_ROOT/scripts/native-build/native-versions.env"

command -v jq >/dev/null 2>&1 || {
  echo "jq is required to validate the generated SBOM." >&2
  exit 127
}

if [[ "$SYFT_COMMAND" == */* ]]; then
  [[ -x "$SYFT_COMMAND" ]] || {
    echo "Syft is not executable: $SYFT_COMMAND" >&2
    exit 127
  }
else
  command -v "$SYFT_COMMAND" >/dev/null 2>&1 || {
    echo "Syft is required to generate the image SBOM." >&2
    exit 127
  }
fi

OUTPUT_DIRECTORY="$(dirname -- "$OUTPUT_PATH")"
readonly OUTPUT_DIRECTORY
mkdir -p "$OUTPUT_DIRECTORY"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/konifer-image-sbom.XXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT

readonly SYFT_JSON="$work_dir/image.syft.json"
readonly SPDX_JSON="$work_dir/image.spdx.json"

"$SYFT_COMMAND" scan "$IMAGE_SOURCE" \
  --select-catalogers +sbom-cataloger \
  --output "syft-json=$SYFT_JSON" \
  --output "spdx-json@2.3=$SPDX_JSON"

EXPECTED_NATIVE_COMPONENTS="$(
  jq --compact-output --null-input \
    --arg cgif "$CGIF_VERSION" \
    --arg dav1d "$DAV1D_VERSION" \
    --arg highway "$LIBHWY_VERSION" \
    --arg kvazaar "$KVAZAAR_VERSION" \
    --arg libheif "$LIBHEIF_VERSION" \
    --arg libjpeg_turbo "$LIBJPEG_TURBO_VERSION" \
    --arg libjxl "$LIBJXL_VERSION" \
    --arg libpng "$LIBPNG_VERSION" \
    --arg libvips "$VIPS_VERSION" \
    --arg libwebp "$LIBWEBP_VERSION" \
    --arg svt_av1 "$SVT_AV1_VERSION" \
    --arg zlib_ng "$ZLIB_NG_VERSION" \
    '{
      cgif: $cgif,
      dav1d: $dav1d,
      highway: $highway,
      kvazaar: $kvazaar,
      libheif: $libheif,
      "libjpeg-turbo": $libjpeg_turbo,
      libjxl: $libjxl,
      libpng: $libpng,
      libvips: $libvips,
      libwebp: $libwebp,
      "svt-av1": $svt_av1,
      "zlib-ng": $zlib_ng
    }'
)"
readonly EXPECTED_NATIVE_COMPONENTS

if ! jq --exit-status \
  --argjson expected "$EXPECTED_NATIVE_COMPONENTS" \
  '[
    .artifacts[]
    | select(.foundBy == "sbom-cataloger")
    | {key: .name, value: .version}
  ]
  | from_entries as $actual
  | all($expected | to_entries[]; $actual[.key] == .value)' \
  "$SYFT_JSON" >/dev/null; then
  echo "Syft did not import every expected native component and version:" >&2
  jq --raw-output \
    --argjson expected "$EXPECTED_NATIVE_COMPONENTS" \
    '[
      .artifacts[]
      | select(.foundBy == "sbom-cataloger")
      | {key: .name, value: .version}
    ]
    | from_entries as $actual
    | $expected
    | to_entries[]
    | select($actual[.key] != .value)
    | "  \(.key): expected \(.value), found \($actual[.key] // "missing")"' \
    "$SYFT_JSON" >&2
  exit 1
fi

jq --exit-status \
  '.spdxVersion == "SPDX-2.3" and (.packages | length > 0)' \
  "$SPDX_JSON" >/dev/null || {
  echo "Syft generated an invalid or empty SPDX 2.3 SBOM." >&2
  exit 1
}

install -m 0644 "$SPDX_JSON" "$OUTPUT_PATH"
echo "Generated SPDX image SBOM at $OUTPUT_PATH"
