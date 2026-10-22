#!/usr/bin/env bash
# Dispatch the AWS lifecycle CLI from any directory; forward arguments and status.
# Requires Node and AWS CLI/OpenTofu for the chosen operation. Defaults AWS_PROFILE
# to the dedicated benchmark profile; the controller owns validation and locking.
set -euo pipefail
runtime_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
export AWS_PROFILE=${AWS_PROFILE:-konifer-perf}
exec node "$runtime_dir/aws.ts" "$@"
