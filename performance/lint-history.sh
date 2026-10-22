#!/usr/bin/env bash
# Validate release/load history with Node; forward CLI options and exit status.
# Read-only by default. --fix removes non-release entries from history files.

set -euo pipefail

performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
exec node "$performance_dir/report/lint-history.ts" "$@"
