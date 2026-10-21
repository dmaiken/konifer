#!/usr/bin/env bash
# Start Konifer on its provisioned EC2 host: aws-start.sh GENERATED_DIRECTORY.
# Requires Docker, PostgreSQL client tools, a prepared instance-store mount,
# KONIFER_IMAGE, and PG_PASSWORD from the host bootstrap. Uses verified TLS for
# administrative SQL; application connections use the generated configuration.
# Sourcing defines helpers without starting containers or touching the database.
set -euo pipefail

# Check tools and required environment before reading deployment state.
# Reads KONIFER_IMAGE and PG_PASSWORD; emits diagnostics without secret values.
# Status: nonzero if a required tool or variable is absent.
validate_prerequisites() {
  local command_name
  for command_name in jq docker findmnt lsblk psql; do
    command -v "$command_name" >/dev/null || {
      echo "Required command not found: $command_name" >&2
      return 1
    }
  done
  : "${KONIFER_IMAGE:?Set an immutable ARM64 Konifer image reference}"
  : "${PG_PASSWORD:?Fetch the benchmark database password into PG_PASSWORD}"
}

# Validate scratch backing and deployment files before any SQL or Docker changes.
# Arguments: generated directory. Allows an empty selected HOCON source; rejects
# missing generated files, non-ext4 mounts, and non-instance-store devices.
validate_deployment() {
  local deployment_dir=${1:?Expected generated directory}
  local deployment="$deployment_dir/deployment.json" scratch filesystem device required_file
  scratch=$(jq -er '.aws.scratch.hostPath' "$deployment")
  filesystem=$(findmnt --noheadings --raw --mountpoint "$scratch" --output FSTYPE)
  device=$(findmnt --noheadings --raw --mountpoint "$scratch" --output SOURCE)
  if [[ $filesystem != ext4 ]] || [[ $(lsblk --nodeps --noheadings --output MODEL "$device") != *"Instance Storage"* ]]; then
    echo "Scratch must be an ext4 mount backed by EC2 NVMe instance storage: $scratch" >&2
    return 1
  fi
  for required_file in konifer.conf konifer.aws.conf compose.json rds-ca-bundle.pem models/siglip2-base-patch16-224/vision_model.onnx models/siglip2-base-patch16-224/text_model.onnx models/siglip2-base-patch16-224/tokenizer.json; do
    [[ -s "$deployment_dir/$required_file" ]] || {
      echo "Missing deployment file: $required_file" >&2
      return 1
    }
  done
  # An empty source is valid HOCON; the generated environment layer can stand alone.
  [[ -f "$deployment_dir/konifer.source.conf" && -r "$deployment_dir/konifer.source.conf" ]] || {
    echo "Missing or unreadable selected configuration" >&2
    return 1
  }
}

# Create ltree before Konifer runs its migrations against the provisioned database.
# Arguments: generated directory. Reads PG_PASSWORD; passes it only through the
# psql environment. Requires server certificate verification and fails on SQL errors.
prepare_database() {
  local deployment_dir=${1:?Expected generated directory}
  local deployment="$deployment_dir/deployment.json"
  PGPASSWORD="$PG_PASSWORD" PGSSLMODE=verify-full PGSSLROOTCERT="$deployment_dir/rds-ca-bundle.pem" \
    psql --no-password --set ON_ERROR_STOP=1 \
      --host "$(jq -er '.connections.rdsEndpoint' "$deployment")" \
      --username "$(jq -er '.aws.rds.user' "$deployment")" \
      --dbname "$(jq -er '.aws.rds.database' "$deployment")" \
      --command 'CREATE EXTENSION IF NOT EXISTS ltree;'
}

# Verify IMDSv2 role discovery and scratch writes from inside the running container.
# Arguments: generated directory. Uses the image's JDK because released images
# lack curl. Prints probe success, never credentials; propagates probe failure.
probe_container() {
  local deployment_dir=${1:?Expected generated directory}
  docker compose -f "$deployment_dir/compose.json" exec -T konifer java --source 21 /dev/stdin <<'JAVA'
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class MetadataProbe {
    // Bypass proxies so this probe exercises the container's direct metadata route.
    static HttpURLConnection connection(String path) throws Exception {
        var connection = (HttpURLConnection) URI.create("http://169.254.169.254/latest/" + path).toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(5000);
        return connection;
    }
    public static void main(String[] args) throws Exception {
        var tokenRequest = connection("api/token");
        tokenRequest.setRequestMethod("PUT");
        tokenRequest.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "60");
        if (tokenRequest.getResponseCode() != 200) throw new IllegalStateException("IMDSv2 token request failed");
        String token;
        try (var body = tokenRequest.getInputStream()) { token = new String(body.readAllBytes(), StandardCharsets.UTF_8); }
        tokenRequest.disconnect();
        var roleRequest = connection("meta-data/iam/security-credentials/");
        roleRequest.setRequestProperty("X-aws-ec2-metadata-token", token);
        if (roleRequest.getResponseCode() != 200) throw new IllegalStateException("Instance profile unavailable in container");
        try (var body = roleRequest.getInputStream()) {
            if (body.readAllBytes().length == 0) throw new IllegalStateException("No instance role attached");
        }
        roleRequest.disconnect();
        var scratch = Files.createTempFile(Path.of("/app/tmp"), "aws-probe-", ".tmp");
        Files.delete(scratch);
        System.out.println("Container IMDSv2 and scratch checks passed");
    }
}
JAVA
}

# Validate, prepare the database, start Compose, and verify the container runtime.
# Arguments: generated directory. Leaves the container running if a later probe
# fails so the operator can inspect it; application readiness is a separate checkpoint.
main() {
  local deployment_dir
  deployment_dir=$(realpath "${1:?Usage: bash aws-start.sh GENERATED_DIRECTORY}")
  validate_prerequisites
  validate_deployment "$deployment_dir"
  prepare_database "$deployment_dir"
  docker compose -f "$deployment_dir/compose.json" up -d konifer
  probe_container "$deployment_dir"
  echo "Container started. Run aws-checkpoint.ts from the k6 host to check readiness and an upload/download."
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then main "$@"; fi
