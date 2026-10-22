#!/usr/bin/env bash
# Run smoke/release benchmarks or mixed load; see --help for the CLI contract.
# Requires Node, jq, fixtures, and the selected runtime's tools (Docker/curl/k6
# locally, AWS tooling for remote dispatch). Run from any working directory.
# Writes results and eligible history; cleans only this run's API asset paths.

set -euo pipefail

invocation_dir=$PWD
performance_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$performance_dir"

suite=smoke
selected_workload=""
selected_case=""
repetitions=""
subject_override=""
docker_tag=latest
start_runtime=true
keep_assets=false
with_load=false
use_legacy_query_params=false
# EXIT cleanup shares these variables with the suite loop. Set pending before
# invoking k6 so failed measurements still trigger asset deletion.
cleanup_pending=false
cleanup_delay_seconds=0
run_id=""
failed_repetitions=0
environment_override=""
config_file=""
configuration_metadata=""
remote_worker=${KONIFER_AWS_WORKER:-false}
original_arguments=("$@")

# Print supported suites, options, and publication rules to stdout.
usage() {
  cat <<'EOF'
Usage:
  ./performance/run.sh [smoke|release|load] [options]

Suites:
  smoke                 Run the short functional development suite (default).
                        Smoke results are never added to release history.
  release               Run the complete benchmark suite. A complete run is
                        published when every configured case is present;
                        failed cases are marked unavailable in the report.
  load                  Run the mixed-v1 concurrent load profile once and
                        publish it in the load section of the report.

Options:
  --environment ID      Select a configured Compose or AWS environment.
  --config FILE         Use a standalone Konifer config (relative to your current
                        directory). Defaults to runtime/konifer.conf. Local runs
                        use it unchanged; AWS overrides connection settings.
  --workload ID         Run one workload ID from config/workloads.json instead
                        of every workload in the suite.
  --case ID             Run one case ID within --workload instead of all of its
                        cases. This option requires --workload.
  --repetitions N       Run every selected workload/case N times and aggregate
                        their metrics. N must be a positive integer. A release
                        with fewer repetitions than configured is not published.
  --subject LABEL       Label the measured Konifer build, for example v0.9.0.
                        The default is `git describe --tags --always --dirty`;
                        published history requires vMAJOR.MINOR.PATCH.
  --docker-tag TAG      Run ghcr.io/dmaiken/konifer:TAG. Defaults to latest.
  --use-legacy-query-params
                        Use abbreviated transformation names for older images,
                        in requests and, only for the default config, configuration.
  --no-start            Reuse an already-running deployment. Container
                        limits and Konifer health are still verified.
  --keep-assets         Skip per-repetition recursive deletion of assets.
                        Intended only for debugging disk or database state.
  --with-load           After a release suite, run the mixed load profile with
                        the same subject, image, runtime, and cleanup setting.
  -h, --help            Show this help text.

Examples:
  ./performance/run.sh smoke
  ./performance/run.sh smoke --docker-tag local
  ./performance/run.sh smoke --workload format.encode --case jpg-to-webp
  ./performance/run.sh smoke --workload upload.rules --no-start
  ./performance/run.sh release --subject v0.9.0
  ./performance/run.sh release --subject v0.9.0 --repetitions 3
  ./performance/run.sh load --subject v0.9.0
  ./performance/run.sh release --subject v0.9.0 --with-load
  ./performance/run.sh release --subject v0.9.0 --docker-tag v0.9.0 --use-legacy-query-params

Output:
  Results are written to performance/results/<run-id>/. Complete release runs
  are also added to performance/history/releases.json. A failed benchmark
  repetition is recorded and does not stop the remaining suite.
EOF
}

if [[ ${1:-} == "smoke" || ${1:-} == "release" || ${1:-} == "load" ]]; then
  suite=$1
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config)
      config_file=${2:?Missing configuration file}
      if [[ $config_file != /* ]]; then config_file="$invocation_dir/$config_file"; fi
      shift 2
      ;;
    --environment)
      environment_override=${2:?Missing environment ID}
      shift 2
      ;;
    --workload)
      selected_workload=${2:?Missing workload ID}
      shift 2
      ;;
    --case)
      selected_case=${2:?Missing case ID}
      shift 2
      ;;
    --repetitions)
      repetitions=${2:?Missing repetition count}
      shift 2
      ;;
    --subject)
      subject_override=${2:?Missing benchmark subject}
      shift 2
      ;;
    --docker-tag)
      docker_tag=${2:?Missing Docker tag}
      shift 2
      ;;
    --no-start)
      start_runtime=false
      shift
      ;;
    --use-legacy-query-params)
      use_legacy_query_params=true
      shift
      ;;
    --keep-assets)
      keep_assets=true
      shift
      ;;
    --with-load)
      with_load=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n $selected_case && -z $selected_workload ]]; then
  echo "--case requires --workload" >&2
  usage >&2
  exit 2
fi

if [[ $with_load == true && $suite != "release" ]]; then
  echo "--with-load is available only with the release suite" >&2
  exit 2
fi

if [[ $suite == "load" && ( -n $selected_workload || -n $selected_case || -n $repetitions ) ]]; then
  echo "--workload, --case, and --repetitions do not apply to the load suite" >&2
  exit 2
fi

if [[ ! $docker_tag =~ ^[[:alnum:]_][[:alnum:]_.-]{0,127}$ ]]; then
  echo "Invalid Docker tag: $docker_tag" >&2
  exit 2
fi

export KONIFER_IMAGE="ghcr.io/dmaiken/konifer:$docker_tag"

for command_name in jq node; do
  command -v "$command_name" >/dev/null || {
    echo "Required command not found: $command_name" >&2
    exit 1
  }
done

jq empty config/workloads.json config/load.json config/environments.json assets/manifest.json schema/result.schema.json schema/load-result.schema.json
if [[ $suite != "load" ]]; then
  jq -e --arg suite "$suite" '.suites[$suite] != null' config/workloads.json >/dev/null || {
    echo "Unknown suite: $suite" >&2
    exit 2
  }
fi

environment=$(jq -r '.default' config/environments.json)
if [[ $suite == "load" ]]; then
  environment=$(jq -r '.loadDefault // .default' config/environments.json)
fi
environment=${environment_override:-$environment}
jq -e --arg environment "$environment" '.profiles[$environment] != null' config/environments.json >/dev/null || {
  echo "Unknown environment: $environment" >&2; exit 2;
}
compose_file=$(jq -r --arg environment "$environment" '.profiles[$environment].composeFile' config/environments.json)
runtime=$(jq -r --arg environment "$environment" '.profiles[$environment].runtime // "compose"' config/environments.json)
if [[ $runtime == "aws" ]]; then
  if [[ $suite == load || $with_load == true ]]; then
    echo "AWS supports smoke and release only; load testing remains local" >&2; exit 2
  fi
  if [[ $remote_worker != true ]]; then
    aws_arguments=()
    for ((index = 0; index < ${#original_arguments[@]}; index += 1)); do
      if [[ ${original_arguments[index]} == --config ]]; then
        aws_arguments+=(--config "$config_file")
        index=$((index + 1))
      else
        aws_arguments+=("${original_arguments[index]}")
      fi
    done
    exec bash runtime/aws.sh run "${aws_arguments[@]}"
  fi
elif [[ $remote_worker == true ]]; then
  echo "AWS worker requires an AWS environment" >&2; exit 2
fi
if [[ $remote_worker != true ]]; then
  configuration_arguments=()
  if [[ -n $config_file ]]; then configuration_arguments+=(--config "$config_file"); fi
  if [[ $use_legacy_query_params == true ]]; then configuration_arguments+=(--use-legacy-query-params); fi
  configuration_metadata=$(node runtime/configuration.ts "${configuration_arguments[@]}")
fi
for command_name in curl docker sha256sum; do
  command -v "$command_name" >/dev/null || { echo "Required command not found: $command_name" >&2; exit 1; }
done
if [[ $remote_worker != true ]]; then command -v k6 >/dev/null; fi
base_url=${BASE_URL:-$(jq -r '.baseUrl' config/workloads.json)}
if [[ $suite != "load" ]]; then
  methodology=$(jq -r '.methodology' config/workloads.json)
  environment_methodology=$(jq -r --arg environment "$environment" '.profiles[$environment].benchmarkMethodology // "arrival-rate-v1"' config/environments.json)
  if [[ $methodology != "$environment_methodology" ]]; then
    echo "Benchmark methodology $methodology does not match environment $environment ($environment_methodology)" >&2
    exit 1
  fi
fi

# Refuse measurements if actual Compose container limits differ from the profile.
# Reads environment; reports mismatches on stderr and exits before running k6.
validate_runtime() {
  local container expected_cpus expected_memory actual_memory actual_nano_cpus expected_nano_cpus
  while IFS=$'\t' read -r container expected_cpus expected_memory; do
    actual_memory=$(docker inspect "$container" --format '{{.HostConfig.Memory}}')
    actual_nano_cpus=$(docker inspect "$container" --format '{{.HostConfig.NanoCpus}}')
    expected_nano_cpus=$((expected_cpus * 1000000000))
    if [[ $actual_memory -ne $expected_memory || $actual_nano_cpus -ne $expected_nano_cpus ]]; then
      echo "Container resource mismatch for $container" >&2
      echo "expected cpus=$expected_cpus memory=$expected_memory; actual nano_cpus=$actual_nano_cpus memory=$actual_memory" >&2
      exit 1
    fi
  done < <(jq -r --arg environment "$environment" '.profiles[$environment].services[] | [.container, (.cpus | tostring), (.memoryBytes | tostring)] | @tsv' config/environments.json)
}

# Verify fixture sizes and SHA-256 values against the checked-in asset manifest.
# Runs from performance_dir; missing or changed fixtures terminate the run.
validate_fixtures() {
  local relative_path expected_bytes expected_hash asset_path actual_bytes actual_hash
  while IFS=$'\t' read -r relative_path expected_bytes expected_hash; do
    asset_path="assets/$relative_path"
    [[ -f $asset_path ]] || {
      echo "Fixture is missing: $asset_path" >&2
      exit 1
    }
    actual_bytes=$(stat -c %s "$asset_path")
    actual_hash=$(sha256sum "$asset_path" | cut -d ' ' -f 1)
    if [[ $actual_bytes -ne $expected_bytes || $actual_hash != "$expected_hash" ]]; then
      echo "Fixture does not match manifest: $asset_path" >&2
      exit 1
    fi
  done < <(jq -r '.fixtures[].files[] | [.path, (.bytes | tostring), .sha256] | @tsv' assets/manifest.json)
}

# Require the local inference model files before starting the Compose runtime.
# Reads performance_dir; prints the download command and exits if a file is absent.
validate_models() {
  local model_dir="$performance_dir/../models/siglip2-base-patch16-224"
  local model_file
  for model_file in vision_model.onnx text_model.onnx tokenizer.json; do
    [[ -f "$model_dir/$model_file" ]] || {
      echo "Required Upload Rules model is missing: $model_dir/$model_file" >&2
      echo "Run ./scripts/download-siglip2-models.sh from the repository root." >&2
      exit 1
    }
  done
}

# Wait up to 60 attempts for base_url readiness; use /health only after a 404.
# Status: zero when ready, nonzero after timeout. Never mask a modern probe's 503.
wait_for_konifer() {
  local attempt status
  for attempt in {1..60}; do
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/health/ready") || status=000
    if [[ $status == 200 ]]; then
      return 0
    fi
    if [[ $status == 404 ]] && curl --silent --fail --output /dev/null "$base_url/health"; then return 0; fi
    sleep 1
  done
  echo "Konifer did not become ready at $base_url/health/ready" >&2
  return 1
}

# Execute k6 and return its status; configure dashboard export outside smoke runs.
# Arguments: HTML output path, then k6 arguments. Reads suite/runtime/result state.
# AWS workers create a pinned container, verify its CPU/memory/swap limits before
# starting it, and remove it after completion; local runs use the installed k6.
run_k6() {
  local html_report_path=$1
  shift
  set -- -e "BASE_URL=$base_url" -e "USE_LEGACY_QUERY_PARAMS=$use_legacy_query_params" "$@"

  if [[ $remote_worker == true ]]; then
    local dashboard=false
    [[ $suite == smoke ]] || dashboard=true
    docker create --name "k6-$run_id" --label "konifer.performance.run=$run_id" --cpus=1 --memory=1536m --memory-swap=1536m \
      --user "$(id -u):$(id -g)" --workdir "$performance_dir" \
      --mount "type=bind,src=$performance_dir,dst=$performance_dir,readonly" \
      --mount "type=bind,src=$result_dir,dst=$result_dir" \
      -e "K6_WEB_DASHBOARD=$dashboard" -e K6_WEB_DASHBOARD_PORT=-1 \
      -e K6_WEB_DASHBOARD_PERIOD=1s -e "K6_WEB_DASHBOARD_EXPORT=$html_report_path" \
      "${KONIFER_K6_IMAGE:?Missing pinned k6 image}" run "$@" >/dev/null || return 1
    if ! docker inspect "k6-$run_id" --format '{{json .HostConfig.NanoCpus}} {{json .HostConfig.Memory}} {{json .HostConfig.MemorySwap}}' \
      | tee -a "$result_dir/k6-limits.txt" | grep -qx '1000000000 1610612736 1610612736'; then
      echo "k6 container limits differ from the AWS profile; refusing measurement" >&2
      docker rm "k6-$run_id" >/dev/null
      return 1
    fi
    local status=0
    docker start -a "k6-$run_id" || status=$?
    docker rm "k6-$run_id" >/dev/null || return 1
    return "$status"
  fi

  if [[ $suite == "smoke" ]]; then
    k6 run "$@"
    return
  fi

  K6_WEB_DASHBOARD=true \
    K6_WEB_DASHBOARD_PORT=-1 \
    K6_WEB_DASHBOARD_PERIOD=1s \
    K6_WEB_DASHBOARD_EXPORT="$html_report_path" \
    k6 run "$@"
}

validate_fixtures
if [[ $remote_worker != true ]]; then validate_models; fi

if [[ $start_runtime == true && $remote_worker != true ]]; then
  export KONIFER_CONFIG=${config_file:-"$performance_dir/runtime/konifer.conf"}
  if [[ $use_legacy_query_params == true && -z $config_file ]]; then
    node runtime/legacy-config.ts
    export KONIFER_CONFIG="$performance_dir/runtime/konifer.legacy.conf"
  fi
  echo "Starting performance runtime with $KONIFER_IMAGE"
  # Garage creates the benchmark access key and bucket during single-node startup.
  docker compose -f "$compose_file" up -d --wait perf-postgres perf-garage
  docker compose -f "$compose_file" up -d --wait perf-konifer
fi

if [[ $remote_worker != true ]]; then validate_runtime; fi
wait_for_konifer

if [[ $suite != "load" ]]; then
  if [[ -n $selected_workload ]]; then
    jq -e --arg workload "$selected_workload" '.workloads[$workload] != null' config/workloads.json >/dev/null || {
      echo "Unknown workload: $selected_workload" >&2
      exit 2
    }
    workloads=("$selected_workload")
  else
    mapfile -t workloads < <(jq -r --arg suite "$suite" '.suites[$suite].workloads[]' config/workloads.json)
  fi

  if [[ -z $repetitions ]]; then
    repetitions=$(jq -r --arg suite "$suite" '.suites[$suite].repetitions' config/workloads.json)
  fi
  [[ $repetitions =~ ^[1-9][0-9]*$ ]] || {
    echo "Repetitions must be a positive integer" >&2
    exit 2
  }
fi

run_id=${KONIFER_RUN_ID:-"$(date -u +%Y%m%dT%H%M%SZ)-$suite"}
if [[ -n $subject_override ]]; then
  subject=$subject_override
else
  subject=$(git describe --tags --always --dirty 2>/dev/null || echo working-tree)
fi
echo "Benchmark subject: $subject"
result_dir=${KONIFER_RESULT_DIR:-"$performance_dir/results/$run_id"}
mkdir -p "$result_dir/raw" "$result_dir/normalized"
if [[ $remote_worker != true ]]; then printf '%s\n' "$configuration_metadata" > "$result_dir/configuration.json"; fi
if [[ $suite != "smoke" ]]; then
  mkdir -p "$result_dir/html"
fi

# Delete this run's assets from default and workload-specific API paths.
# Reads run_id, base_url, keep_assets, cleanup_delay_seconds, and workload catalog.
# Status: nonzero if any recursive delete fails. The eager-work delay prevents
# asynchronous processing from recreating assets after deletion.
cleanup_assets() {
  local cleanup_failed=false
  local cleanup_path
  local cleanup_paths=("/performance/$run_id")

  if [[ $keep_assets == true ]]; then
    echo "Keeping benchmark assets for run $run_id"
    return 0
  fi

  if [[ $cleanup_delay_seconds -gt 0 ]]; then
    echo "Waiting ${cleanup_delay_seconds}s for eager background work before cleanup"
    sleep "$cleanup_delay_seconds"
  fi

  echo "Deleting benchmark assets for run $run_id"
  while IFS= read -r cleanup_path; do
    cleanup_paths+=("${cleanup_path%/}/performance/$run_id")
  done < <(jq -r '[.workloads[].path? | select(type == "string")] | unique[]' config/workloads.json)

  for cleanup_path in "${cleanup_paths[@]}"; do
    if ! delete_cleanup_path "$cleanup_path"; then
      cleanup_failed=true
    fi
  done

  [[ $cleanup_failed == false ]]
}

# Retry one recursive asset deletion until HTTP 204 or 30 failed attempts.
# Arguments: API asset path. Reads base_url; reports the final HTTP status on stderr.
delete_cleanup_path() {
  local cleanup_path=$1
  local http_status=""
  local attempt

  for attempt in {1..30}; do
    http_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --request DELETE "$base_url/assets$cleanup_path/-/recursive") || http_status="000"
    if [[ $http_status == 204 ]]; then
      return 0
    fi
    sleep 1
  done

  echo "Cleanup failed for $cleanup_path after 30 attempts (last HTTP status: $http_status)" >&2
  return 1
}

# Preserve the incoming exit status while cleaning a pending run on EXIT.
# Reads shared run/cleanup state; cleanup failure changes a successful exit to 1.
# Remove the trap before exiting to avoid recursive cleanup.
cleanup_on_exit() {
  local status=$?
  trap - EXIT
  if [[ -n $run_id && $cleanup_pending == true ]]; then
    if ! cleanup_assets; then
      echo "Benchmark asset cleanup did not complete successfully" >&2
      if [[ $status -eq 0 ]]; then
        status=1
      fi
    fi
  fi
  exit "$status"
}

trap cleanup_on_exit EXIT

if [[ $suite == "load" ]]; then
  load_profile=$(jq -r '.defaultProfile' config/load.json)
  html_report_path="$result_dir/html/load.html"
  normalized_path="$result_dir/normalized/load.json"
  raw_path="$result_dir/raw/load.json"
  started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  cleanup_delay_seconds=5
  cleanup_pending=true
  k6_status=0

  echo "Running load profile $load_profile"
  run_k6 "$html_report_path" \
    -e "LOAD_PROFILE=$load_profile" \
    -e "RUN_ID=$run_id" \
    -e "ENVIRONMENT=$environment" \
    -e "SUBJECT=$subject" \
    -e "STARTED_AT=$started_at" \
    -e "RESULT_PATH=$normalized_path" \
    -e "RAW_RESULT_PATH=$raw_path" \
    k6/load.ts || k6_status=$?

  node runtime/validate-result.ts "$normalized_path" schema/load-result.schema.json
  if jq -e '.passed == true' "$normalized_path" >/dev/null; then
    if [[ $k6_status -ne 0 ]]; then
      echo "k6 exited with status $k6_status despite producing a passing load result" >&2
      exit 1
    fi
  else
    echo "Mixed load checks failed; publishing the failed result" >&2
  fi

  if cleanup_assets; then
    cleanup_pending=false
  else
    cleanup_pending=false
    echo "Benchmark asset cleanup did not complete successfully" >&2
    exit 1
  fi

  "$performance_dir/load-report.sh" "$result_dir"
  echo "Load results: $result_dir"
  exit 0
fi

for workload in "${workloads[@]}"; do
  if [[ -n $selected_case ]]; then
    cases=("$selected_case")
  else
    mapfile -t cases < <(
      jq -r --arg workload "$workload" '
        .workloads[$workload]
        | if .cases then .cases[].id else .case end
      ' config/workloads.json
    )
  fi

  for case_id in "${cases[@]}"; do
    for ((repetition = 1; repetition <= repetitions; repetition += 1)); do
      cleanup_delay_seconds=0
      if [[ $workload == "upload.eager.accept" || $workload == "variant.eager.ready" ]]; then
        cleanup_delay_seconds=5
      fi

      safe_workload=${workload//./-}
      result_name="$safe_workload--$case_id--$repetition.json"
      html_report_path="$result_dir/html/${result_name%.json}.html"
      normalized_path="$result_dir/normalized/$result_name"
      raw_path="$result_dir/raw/$result_name"
      started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

      echo "Running $suite $workload/$case_id repetition $repetition/$repetitions"
      cleanup_pending=true
      k6_status=0
      run_k6 "$html_report_path" \
        -e "SUITE=$suite" \
        -e "WORKLOAD=$workload" \
        -e "CASE=$case_id" \
        -e "RUN_ID=$run_id" \
        -e "REPETITION=$repetition" \
        -e "ENVIRONMENT=$environment" \
        -e "SUBJECT=$subject" \
        -e "STARTED_AT=$started_at" \
        -e "RESULT_PATH=$normalized_path" \
        -e "RAW_RESULT_PATH=$raw_path" \
        k6/workload.ts || k6_status=$?

      # A failed benchmark is valid evidence; a missing/invalid result or a nonzero
      # k6 exit with passing checks is an operational failure and stops the suite.
      node runtime/validate-result.ts "$normalized_path" schema/result.schema.json
      if jq -e '.passed == true' "$normalized_path" >/dev/null; then
        if [[ $k6_status -ne 0 ]]; then
          echo "k6 exited with status $k6_status despite producing a passing result for $workload/$case_id" >&2
          exit 1
        fi
      else
        failed_repetitions=$((failed_repetitions + 1))
        echo "Benchmark failed for $workload/$case_id repetition $repetition; continuing with the remaining suite" >&2
      fi

      if cleanup_assets; then
        cleanup_pending=false
      else
        cleanup_pending=false
        echo "Benchmark asset cleanup did not complete successfully" >&2
        exit 1
      fi
      if [[ $remote_worker == true ]]; then bash runtime/aws-job.sh publish; fi
    done
  done
done

if [[ $remote_worker != true ]]; then "$performance_dir/report.sh" "$result_dir"; fi

if [[ $failed_repetitions -gt 0 ]]; then
  echo "Completed with $failed_repetitions failed benchmark repetition(s); see report.md for unavailable results"
fi
echo "Performance results: $result_dir"

if [[ $with_load == true ]]; then
  load_arguments=(load --subject "$subject" --docker-tag "$docker_tag" --no-start)
  if [[ -n $config_file ]]; then load_arguments+=(--config "$config_file"); fi
  if [[ -n $environment_override ]]; then load_arguments+=(--environment "$environment_override"); fi
  if [[ $use_legacy_query_params == true ]]; then
    load_arguments+=(--use-legacy-query-params)
  fi
  if [[ $keep_assets == true ]]; then
    load_arguments+=(--keep-assets)
  fi
  "$performance_dir/run.sh" "${load_arguments[@]}"
fi
