#!/usr/bin/env bash

set -euo pipefail

performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
exec node "$performance_dir/report/lint-history.ts" "$@"
