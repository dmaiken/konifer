#!/usr/bin/env bash

set -euo pipefail

performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
run_dir=${1:-}

if [[ -z $run_dir ]]; then
  echo "Usage: ./performance/report.sh performance/results/<run-id>" >&2
  exit 2
fi

if [[ $run_dir != /* ]]; then
  run_dir=$(cd "$(dirname "$run_dir")" && pwd)/$(basename "$run_dir")
fi

node "$performance_dir/report/generate.ts" "$run_dir"
