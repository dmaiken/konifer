# Konifer Benchmarking Contract

Use this contract to produce and compare Konifer latency and stability results.
The suite measures customer workflows within a named environment. Laptop results
do not define product capacity.

For run commands, see [README.md](README.md). Configuration lives in:

- `config/workloads.json` for suites, workloads, cases, and traffic profiles;
- `config/environments.json` for environment identity and interpretation notes;
- `assets/manifest.json` for fixture paths, media types, and checksums;
- `runtime/konifer.conf` for default Konifer paths, or a standalone file selected
  with `--config` for a historical release.

## Current benchmark scope

The suite covers common customer workflows:

- original upload and delivery;
- on-demand image transformation, including cold generation and cached delivery;
- upload preprocessing;
- eager variant generation and readiness;
- Upload Rules inference, with and without preprocessing;
- decoding and encoding each supported image format.

Isolated workloads, cases, and repetitions run in sequence with one operation
in flight. The separate `mixed-v1` profile runs concurrent traffic at a fixed
target. Capacity discovery and soak testing remain outside the current scope.

## Comparison contract

Environment, workload, and case IDs identify a chart series. The release subject,
such as `v0.9.0`, labels a point in that series.

Compare a series only while these inputs remain equivalent for measurement:

- host class and container resource limits;
- fixture bytes;
- request path, parameters, and assertions;
- workload timing, warmup, and measurement configuration;
- Konifer path and Upload Rules configuration;
- database, object storage, models, and relevant runtime versions.

Create a new environment, workload, or case ID if a changed input invalidates
comparison. Keep local and AWS results in separate environments.

The harness verifies fixture checksums and container limits. AWS run artifacts
also record resolved image digests and deployment evidence, but curated release
history does not contain a full runtime fingerprint. Local floating image tags
remain a comparison limitation.

## Run lifecycle

The harness uses descriptive transformation parameter names by default. To
backfill a version that expects abbreviated names, pass
`--use-legacy-query-params` to `run.sh`:

```sh
./performance/run.sh release --subject v0.9.0 --docker-tag 0.9.0 --use-legacy-query-params --with-load
```

With the default config, this translates request names and configuration keys,
including eager profiles and preprocessing. Local runs write a separate
`runtime/konifer.legacy.conf`. With `--config FILE`, the flag changes requests
only; supply configuration in the release's syntax. The option applies to smoke,
release, and local load runs, including `--with-load`. With `--no-start`, keep
the running deployment's config compatible with its version. The flag does not
add features absent from older releases.

`run.sh` performs the following work:

1. Parses the configuration, fixture manifest, and result schema.
2. Verifies fixture checksums and required model files.
3. Starts or reuses Compose, or stages the deployment on an applied AWS stack.
4. Ensures storage is ready, checks resource limits, and waits for Konifer health.
   AWS also runs an upload/download checkpoint before the suite.
5. Runs each selected workload, case, and repetition through k6.
6. Seeds run-scoped assets during k6 setup, outside the measured phases.
7. Executes the configured warmup and measurement profiles.
8. Validates normalized results and retains failed benchmark checks/thresholds
   while continuing the suite. Operational failures stop execution.
9. After each repetition, deletes assets created under the run's unique
   benchmark path unless `--keep-assets` was supplied. The exit trap performs
   the same cleanup for a repetition that fails before reaching this step.
10. Collects AWS artifacts to the local machine, then aggregates results and
    updates history for a complete release. Local runs aggregate in place.
    Reports show failed cases as unavailable.

Normal runs isolate assets with unique paths and delete them through Konifer's
API after repetitions. PostgreSQL deletes records in the request; background
jobs remove object content. This preserves database/object consistency without
purging shared volumes. The separate AWS `reset` command clears benchmark data
before switching image digests; see [AWS operation](runtime/AWS.md).

## Measurement semantics

Setup and asset seeding are not included in reported operation latency.

- A smoke run uses a small shared-iterations profile to check workflows. Exclude
  it from published performance history.
- A release run uses one virtual user and runs warmup to completion before
  measurement. Each phase has a fixed operation count, minimum duration, and
  maximum duration. The runner paces fast operations across the minimum duration;
  slow operations extend the phase with no concurrent requests.
- Cold variant cases create a distinct variant during each measured operation.
- Cached cases request a variant that setup has already generated.
- General transformation, preprocessing, and eager-variant cases use WebP as
  their common output format.
- Preprocessing and Upload Rules cases measure the complete upload request.
- Eager acceptance measures the upload request. The readiness workload measures
  the post-upload wait; acceptance latency excludes that wait.

### Single-operation protocol

`single-vu-v1` uses 1,000 measured operations for cached reads and 200 for other
cases, per repetition. Measurement spans at least 30 seconds. Read warmup runs
100 operations; light warmup runs 30; heavy and inference warmup run 20. Warmup
spans at least 10 seconds. The catalog sets the maximum duration for each phase.
The k6 scenario also caps total execution time, and requests use the remaining
phase budget as their timeout.

Warmup and measurement execute in one sequence. A repetition must complete both
phases within their budgets and collect the full measurement count to pass.
The harness stops a phase after a failed operation and retains the result,
including a failure with no measured operations. Setup and seed uploads remain
outside the measurement window; cold cases use a distinct seed for each operation.
One k6 scenario iteration contains both phases; use the tagged `operations`
counter for sample counts, not k6's scenario iteration count. Raw summaries retain
each phase's duration and completion checks.

After each eager upload, the harness waits for the four variants to finish before
starting another operation. Pacing requests have their own metric tag and do not
contribute to acceptance latency. Failure to finish the variants fails the
operation. This applies during warmup as well as measurement.

The release suite retains three repetitions. Each normalized result and aggregate
records its sampling protocol. Aggregation rejects repetitions with different
protocols or a passing result with too few samples.

Calibrate these defaults against longer runs on the same hardware before relying
on their p95 variability. The suite targets one hour; slow versions, inference,
setup, and cleanup can extend runtime.

## Mixed-load semantics

The load suite uses one open-model k6 run. `config/load.json` defines scenarios,
stage durations, and arrival rates; the harness derives totals and seed pool
sizes from that profile. Current traffic includes cached original and
variant delivery, original uploads, cold WebP generation, eager uploads with
four background variants, and Upload Rules inference.

After traffic ramps down, a recovery scenario checks service health, uploads an
eager asset, waits up to 30 seconds for its four variants, and verifies their
delivery as cache hits. Passing requires zero request errors, failed checks, and
dropped iterations across traffic streams, plus a passing recovery scenario.
Use latency for release comparisons; the harness imposes no fixed SLO.

The load profile uses one representative case per operation. Codec matrices,
preprocessing combined with inference, repetitions, saturation ramps, and long
soaks stay in the isolated suite or future profiles. A material change to the
mix, stage timing, or arrival rates requires a new profile ID so historical
series remain comparable.

Single-request latency uses k6 HTTP response timing with fractional milliseconds.
Eager readiness uses wall-clock time across polling requests. History retains
measured values; the dashboard labels changes only when p95 differs by at least
5% and 2 ms.

Aggregation reports medians across repetitions. The report shows the observed
p95 range when a series has more than one repetition.

## Correctness and validity

Treat latency as valid only when the workload's functional checks pass. Checks
include:

- expected HTTP status and media type;
- expected output dimensions and format;
- expected cache miss or hit behavior;
- Upload Rules metadata produced by inference;
- eager variant readiness.

Normalized results must satisfy the JSON schema. Valid latency also requires
passing checks, zero request errors, the full measurement count, completed
warmup/measurement windows, and no dropped iterations. Smoke runs require their
configured iteration count. The suite imposes no latency ceiling; a slow but
valid result remains visible in history.

A failed case remains in aggregates and complete release history with unavailable
latency. Filtered releases remain diagnostic: publication requires the full case
matrix and configured repetition count.

## Fixtures and generated data

Treat fixtures as immutable inputs. The harness checks their SHA-256 values from
`assets/manifest.json` before testing. Changed bytes require a manifest update
and a new case/workload ID if the change affects comparison.

Git ignores raw artifacts under `performance/results/`. Runs include normalized
JSON per case/repetition, k6 HTML per release/load invocation, and aggregate JSON
and Markdown for completed runs. Smoke runs skip HTML export. AWS collection
adds deployment metadata, checkpoint evidence, and worker status/logs.

Customer-facing isolated history lives in `performance/history/releases.json`,
and mixed-load history lives in `performance/history/loads.json`. History
entries must use a release subject such as `v0.9.0`; `lint-history.sh` rejects
commit-like, dirty, or otherwise non-release subjects. The history schema permits
new workloads and cases to appear in later releases, so older releases do not
need fabricated results for tests that did not yet exist.

Add a `notes` array to a release, result, load run, or traffic stream to explain
changes without altering measurements. Report regeneration preserves existing
annotations.

The dashboard joins histories by subject/environment while keeping benchmark
and load views separate. Query parameters and `#load` select shareable historical
views; the bare URL selects the newest release. Selectors, charts, and baselines
use numeric version order so backfilled measurements retain their release position.

## Local environment interpretation

`local-compose-v4` describes Daniel's laptop in `config/environments.json`.
The report includes its hardware, limits, and interpretation notes. Konifer uses
a disk-backed Docker temporary volume so large transformations do not consume
memory through tmpfs. This profile starts a `single-vu-v1` history separate from
arrival-rate results in `local-compose-v2` and `local-compose-v3`.

The environment catalog's `default` selects operation benchmarks. `loadDefault`
keeps mixed-load runs on `local-compose-v3`, since their traffic profile has not
changed. Historical results and environment IDs remain unchanged.

Use local results to track trends and catch regressions on that machine. Laptop
power state, thermal throttling, and host activity affect measurements. Keep
these results separate from AWS comparisons and server-capacity claims.

## Dashboard builds

`npm --prefix performance run build` builds the local dashboard with both the
Benchmark latency and Mixed load tabs and stages a standalone site in
`performance/dist/site/`.

Use `npm --prefix performance run build -- --without-load` for a benchmark-only
site. This excludes load data/config, disables load requests/navigation, and
redirects `#load` selections to benchmarks. The local preview keeps load testing.
GitHub Pages uses the benchmark-only build. Neither build changes source history.

## Planned extensions

Future profiles can reuse the workload and report contracts. Candidates include:

- full runtime fingerprints in curated history, beyond AWS per-run evidence;
- capacity and soak suites;
- cold-process inference measurements.
