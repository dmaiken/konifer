#!/usr/bin/env bash
# Run on the provisioned Konifer host. This does not provision or format disks.
set -euo pipefail

deployment_dir=$(realpath "${1:?Usage: bash aws-start.sh GENERATED_DIRECTORY}")
deployment="$deployment_dir/deployment.json"
for command_name in jq docker findmnt lsblk psql; do
  command -v "$command_name" >/dev/null || { echo "Required command not found: $command_name" >&2; exit 1; }
done
: "${KONIFER_IMAGE:?Set an ARM64 image containing ssl-root-cert support}"
: "${PG_PASSWORD:?Fetch the benchmark database password into PG_PASSWORD}"

scratch=$(jq -er '.aws.scratch.hostPath' "$deployment")
filesystem=$(findmnt --noheadings --raw --mountpoint "$scratch" --output FSTYPE)
device=$(findmnt --noheadings --raw --mountpoint "$scratch" --output SOURCE)
if [[ $filesystem != ext4 ]] || [[ $(lsblk --nodeps --noheadings --output MODEL "$device") != *"Instance Storage"* ]]; then
  echo "Scratch must be an ext4 mount backed by EC2 NVMe instance storage: $scratch" >&2
  exit 1
fi
for required_file in konifer.conf compose.json rds-ca-bundle.pem models/siglip2-base-patch16-224/vision_model.onnx models/siglip2-base-patch16-224/text_model.onnx models/siglip2-base-patch16-224/tokenizer.json; do
  [[ -s "$deployment_dir/$required_file" ]] || { echo "Missing deployment file: $required_file" >&2; exit 1; }
done

# The provisioner creates the database and its owner first. Konifer runs its own
# migrations at startup; ltree must exist before those migrations run.
PGPASSWORD="$PG_PASSWORD" PGSSLMODE=verify-full PGSSLROOTCERT="$deployment_dir/rds-ca-bundle.pem" \
  psql --no-password --set ON_ERROR_STOP=1 \
    --host "$(jq -er '.connections.rdsEndpoint' "$deployment")" \
    --username "$(jq -er '.aws.rds.user' "$deployment")" \
    --dbname "$(jq -er '.aws.rds.database' "$deployment")" \
    --command 'CREATE EXTENSION IF NOT EXISTS ltree;'

docker compose -f "$deployment_dir/compose.json" up -d konifer
# Verify IMDSv2 from the actual application container, without printing credentials.
docker compose -f "$deployment_dir/compose.json" exec -T konifer sh -ec '
  token=$(curl --noproxy "*" --fail --silent --show-error --connect-timeout 2 --max-time 5 \
    --request PUT --header "X-aws-ec2-metadata-token-ttl-seconds: 60" http://169.254.169.254/latest/api/token)
  curl --noproxy "*" --fail --silent --show-error --connect-timeout 2 --max-time 5 \
    --header "X-aws-ec2-metadata-token: $token" \
    http://169.254.169.254/latest/meta-data/iam/security-credentials/ >/dev/null
  test -w /app/tmp
'
echo "Container started. Run aws-checkpoint.ts from the k6 host to check readiness and an upload/download."
