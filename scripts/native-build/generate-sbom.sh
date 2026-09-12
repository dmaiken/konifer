#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR

# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# shellcheck source=native-versions.env
source "$SCRIPT_DIR/native-versions.env"

readonly INSTALL_PREFIX="${NATIVE_INSTALL_PREFIX:-/opt/konifer-native}"
readonly OUTPUT_PATH="${1:-$INSTALL_PREFIX/share/konifer-native/native-components.cdx.json}"

native_require_commands install jq mkdir mktemp mv

readonly REQUIRED_VERSION_VARIABLES=(
  CGIF_VERSION
  DAV1D_VERSION
  LIBJPEG_TURBO_VERSION
  KVAZAAR_VERSION
  LIBHEIF_VERSION
  LIBHWY_VERSION
  LIBJXL_VERSION
  LIBPNG_VERSION
  LIBWEBP_VERSION
  SVT_AV1_VERSION
  VIPS_VERSION
  ZLIB_NG_VERSION
)

for variable_name in "${REQUIRED_VERSION_VARIABLES[@]}"; do
  [[ -n "${!variable_name:-}" ]] || \
    native_die "Native SBOM variable is required: $variable_name"
done

OUTPUT_DIRECTORY="$(dirname -- "$OUTPUT_PATH")"
readonly OUTPUT_DIRECTORY
mkdir -p "$OUTPUT_DIRECTORY"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/konifer-native-sbom.XXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT

readonly COMPONENTS_FILE="$work_dir/components.jsonl"
readonly GENERATED_SBOM="$work_dir/native-components.cdx.json"
: > "$COMPONENTS_FILE"

add_component() {
  local name="$1"
  local version="$2"
  local purl="$3"
  local source_url="$4"
  local source_sha256="${5:-}"

  if [[ -n "$source_sha256" ]]; then
    native_validate_sha256 "$source_sha256"
  fi

  jq --compact-output --null-input \
    --arg name "$name" \
    --arg version "$version" \
    --arg purl "$purl" \
    --arg source_url "$source_url" \
    --arg source_sha256 "$source_sha256" \
    '{
      type: "library",
      "bom-ref": $purl,
      name: $name,
      version: $version,
      scope: "required",
      purl: $purl,
      externalReferences: [
        {
          type: "distribution",
          url: $source_url
        }
        + if $source_sha256 == "" then {}
          else {
            hashes: [
              {
                alg: "SHA-256",
                content: $source_sha256
              }
            ]
          }
          end
      ]
    }' >> "$COMPONENTS_FILE"
}

readonly CGIF_REF="pkg:github/dloebl/cgif@v${CGIF_VERSION}"
readonly DAV1D_REF="pkg:generic/dav1d@${DAV1D_VERSION}"
readonly LIBJPEG_TURBO_REF="pkg:github/libjpeg-turbo/libjpeg-turbo@${LIBJPEG_TURBO_VERSION}"
readonly KVAZAAR_REF="pkg:github/ultravideo/kvazaar@v${KVAZAAR_VERSION}"
readonly LIBHEIF_REF="pkg:github/strukturag/libheif@v${LIBHEIF_VERSION}"
readonly LIBHWY_REF="pkg:github/google/highway@${LIBHWY_VERSION}"
readonly LIBJXL_REF="pkg:github/libjxl/libjxl@v${LIBJXL_VERSION}"
readonly LIBPNG_REF="pkg:github/pnggroup/libpng@v${LIBPNG_VERSION}"
readonly LIBWEBP_REF="pkg:generic/libwebp@${LIBWEBP_VERSION}"
readonly SVT_AV1_REF="pkg:generic/svt-av1@${SVT_AV1_VERSION}"
readonly VIPS_REF="pkg:github/libvips/libvips@v${VIPS_VERSION}"
readonly ZLIB_NG_REF="pkg:github/zlib-ng/zlib-ng@${ZLIB_NG_VERSION}"

add_component \
  cgif \
  "$CGIF_VERSION" \
  "$CGIF_REF" \
  "https://github.com/dloebl/cgif/archive/refs/tags/v${CGIF_VERSION}.tar.gz" \
  "$CGIF_SHA256"
add_component \
  dav1d \
  "$DAV1D_VERSION" \
  "$DAV1D_REF" \
  "https://code.videolan.org/videolan/dav1d/-/archive/${DAV1D_VERSION}/dav1d-${DAV1D_VERSION}.tar.gz" \
  "$DAV1D_SHA256"
add_component \
  libjpeg-turbo \
  "$LIBJPEG_TURBO_VERSION" \
  "$LIBJPEG_TURBO_REF" \
  "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/${LIBJPEG_TURBO_VERSION}/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}.tar.gz" \
  "$LIBJPEG_TURBO_SHA256"
add_component \
  kvazaar \
  "$KVAZAAR_VERSION" \
  "$KVAZAAR_REF" \
  "https://github.com/ultravideo/kvazaar/releases/download/v${KVAZAAR_VERSION}/kvazaar-${KVAZAAR_VERSION}.tar.gz" \
  "$KVAZAAR_SHA256"
add_component \
  libheif \
  "$LIBHEIF_VERSION" \
  "$LIBHEIF_REF" \
  "https://github.com/strukturag/libheif/archive/refs/tags/v${LIBHEIF_VERSION}.tar.gz" \
  "$LIBHEIF_SHA256"
add_component \
  highway \
  "$LIBHWY_VERSION" \
  "$LIBHWY_REF" \
  "https://github.com/google/highway/archive/${LIBHWY_VERSION}/highway-${LIBHWY_VERSION}.tar.gz" \
  "$LIBHWY_SHA256"
add_component \
  libjxl \
  "$LIBJXL_VERSION" \
  "$LIBJXL_REF" \
  "https://github.com/libjxl/libjxl/archive/refs/tags/v${LIBJXL_VERSION}.tar.gz" \
  "$LIBJXL_SHA256"
add_component \
  libpng \
  "$LIBPNG_VERSION" \
  "$LIBPNG_REF" \
  "https://downloads.sourceforge.net/project/libpng/libpng16/${LIBPNG_VERSION}/libpng-${LIBPNG_VERSION}.tar.gz" \
  "$LIBPNG_SHA256"
add_component \
  libwebp \
  "$LIBWEBP_VERSION" \
  "$LIBWEBP_REF" \
  "https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-${LIBWEBP_VERSION}.tar.gz" \
  "$LIBWEBP_SHA256"
add_component \
  svt-av1 \
  "$SVT_AV1_VERSION" \
  "$SVT_AV1_REF" \
  "https://gitlab.com/AOMediaCodec/SVT-AV1/-/archive/v${SVT_AV1_VERSION}/SVT-AV1-v${SVT_AV1_VERSION}.tar.gz" \
  "$SVT_AV1_SHA256"
add_component \
  libvips \
  "$VIPS_VERSION" \
  "$VIPS_REF" \
  "https://github.com/libvips/libvips/releases/download/v${VIPS_VERSION}/vips-${VIPS_VERSION}.tar.xz"
add_component \
  zlib-ng \
  "$ZLIB_NG_VERSION" \
  "$ZLIB_NG_REF" \
  "https://github.com/zlib-ng/zlib-ng/archive/refs/tags/${ZLIB_NG_VERSION}.tar.gz" \
  "$ZLIB_NG_SHA256"

jq --slurp \
  --arg cgif_ref "$CGIF_REF" \
  --arg dav1d_ref "$DAV1D_REF" \
  --arg libjpeg_turbo_ref "$LIBJPEG_TURBO_REF" \
  --arg kvazaar_ref "$KVAZAAR_REF" \
  --arg libheif_ref "$LIBHEIF_REF" \
  --arg libhwy_ref "$LIBHWY_REF" \
  --arg libjxl_ref "$LIBJXL_REF" \
  --arg libpng_ref "$LIBPNG_REF" \
  --arg libwebp_ref "$LIBWEBP_REF" \
  --arg svt_av1_ref "$SVT_AV1_REF" \
  --arg vips_ref "$VIPS_REF" \
  --arg zlib_ng_ref "$ZLIB_NG_REF" \
  '{
    bomFormat: "CycloneDX",
    specVersion: "1.6",
    version: 1,
    components: .,
    dependencies: [
      {ref: $cgif_ref, dependsOn: []},
      {ref: $dav1d_ref, dependsOn: []},
      {ref: $libjpeg_turbo_ref, dependsOn: []},
      {ref: $kvazaar_ref, dependsOn: []},
      {
        ref: $libheif_ref,
        dependsOn: [$dav1d_ref, $kvazaar_ref, $libwebp_ref, $svt_av1_ref]
      },
      {ref: $libhwy_ref, dependsOn: []},
      {ref: $libjxl_ref, dependsOn: [$libhwy_ref]},
      {ref: $libpng_ref, dependsOn: [$zlib_ng_ref]},
      {ref: $libwebp_ref, dependsOn: []},
      {ref: $svt_av1_ref, dependsOn: []},
      {
        ref: $vips_ref,
        dependsOn: [
          $cgif_ref,
          $libheif_ref,
          $libhwy_ref,
          $libjpeg_turbo_ref,
          $libjxl_ref,
          $libpng_ref,
          $libwebp_ref,
          $zlib_ng_ref
        ]
      },
      {ref: $zlib_ng_ref, dependsOn: []}
    ]
  }' "$COMPONENTS_FILE" > "$GENERATED_SBOM"

jq --exit-status \
  '.bomFormat == "CycloneDX"
    and .specVersion == "1.6"
    and (.components | length == 12)' \
  "$GENERATED_SBOM" >/dev/null || \
  native_die "Generated native CycloneDX SBOM failed validation"

install -m 0644 "$GENERATED_SBOM" "$OUTPUT_PATH"
native_log "Generated native CycloneDX SBOM at $OUTPUT_PATH"
