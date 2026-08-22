#!/usr/bin/env bash

set -euo pipefail

performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $# -ne 1 ]]; then
  echo "Usage: ./performance/load-report.sh performance/results/<run-id>" >&2
  exit 2
fi

node "$performance_dir/report/load-generate.ts" "$1"
