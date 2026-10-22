#!/usr/bin/env bash
# Generate a mixed-load report and publish eligible load history for one run.
# Usage: load-report.sh RESULTS_DIRECTORY. Requires Node and harness packages;
# preserve the caller's working directory when forwarding the results path.

set -euo pipefail

performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $# -ne 1 ]]; then
  echo "Usage: ./performance/load-report.sh performance/results/<run-id>" >&2
  exit 2
fi

node "$performance_dir/report/load-generate.ts" "$1"
