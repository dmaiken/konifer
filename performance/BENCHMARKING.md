# Konifer Benchmarking Contract

This document defines how Konifer performance results are produced and how they
should be interpreted. The goal is a small, repeatable benchmark suite that
shows customer-relevant latency and stability without presenting laptop results
as universal product limits.

For commands and day-to-day operation, see [README.md](README.md). The executable
configuration lives in:

- `config/workloads.json` for suites, workloads, cases, and traffic profiles;
- `config/environments.json` for environment identity and interpretation notes;
- `assets/manifest.json` for fixture paths, media types, and checksums;
- `runtime/konifer.conf` for the Konifer paths used by the workloads.

## Current benchmark scope

The suite covers common customer workflows:

- original upload and delivery;
- on-demand image transformation, including cold generation and cached delivery;
- upload preprocessing;
- eager variant generation and readiness;
- Upload Rules inference, with and without preprocessing;
- decoding and encoding each supported image format.

The isolated profiles measure operation latency with one operation in flight.
Their workloads, cases, and repetitions run sequentially. The separate
`mixed-v1` load profile exercises representative operations concurrently at a
fixed target. Maximum-capacity discovery and soak testing remain outside the
current scope.

## Comparison contract

A chart series is identified by the combination of environment ID, workload ID,
and case ID. The release subject, such as `v0.9.0`, labels a point in that series;
it does not define a new series.

Results in a series are comparable only while these inputs remain materially
equivalent:

- host class and container resource limits;
- fixture bytes;
- request path, parameters, and assertions;
- workload timing, warmup, and measurement configuration;
- Konifer path and Upload Rules configuration;
- database, object storage, models, and relevant runtime versions.

If one of those inputs changes materially, create a new environment, workload,
or case ID instead of silently continuing the old series. In particular, never
compare local and AWS results as though they came from the same environment.

The current harness verifies fixture checksums and configured container limits,
but it does not yet record resolved container image digests or a complete runtime
fingerprint in release history. Floating image tags are therefore a known
limitation of local results.

## Run lifecycle

The harness uses descriptive transformation parameter names by default. To
backfill a version that expects abbreviated names, pass
`--use-legacy-query-params` to `run.sh`:

```sh
./performance/run.sh release --subject v0.9.0 --docker-tag v0.9.0 --use-legacy-query-params --with-load
```

This translates request parameter names and generates
`runtime/konifer.legacy.conf` for Compose, including eager-variant profiles and
upload preprocessing. The option applies to smoke, release, and load suites,
and is forwarded by `--with-load`. With `--no-start`, the running server must
already have configuration appropriate for its version. This option handles
the parameter rename; it does not add features absent from older releases.

`run.sh` performs the following work:

1. Parses the configuration, fixture manifest, and result schema.
2. Verifies fixture checksums and required model files.
3. Starts or reuses the selected Compose environment.
4. When starting the stack, ensures the object-storage bucket exists; then
   checks configured container limits and waits for Konifer to become healthy.
5. Runs each selected workload, case, and repetition through k6.
6. Seeds run-scoped assets during k6 setup, outside the measured phases.
7. Executes the configured warmup and measurement profiles.
8. Validates every normalized result. Failed checks or thresholds are recorded
   without stopping the remaining workloads.
9. After each repetition, deletes assets created under the run's unique
   benchmark path unless `--keep-assets` was supplied. The exit trap performs
   the same cleanup for a repetition that fails before reaching this step.
10. Aggregates the results and, for a complete `release` run, updates release
    history. Failed cases are published as unavailable rather than as latency
    measurements.

The runner does not purge the entire PostgreSQL or object-storage volume. Isolation comes
from unique run-scoped asset paths, followed after each repetition by recursive
deletion through Konifer's API so database and object-storage state remain
consistent and storage use does not accumulate across repetitions.

## Measurement semantics

Setup and asset seeding are not included in reported operation latency.

- A smoke run uses a small shared-iterations profile to catch broken workflows
  quickly. It is a functional confidence check, not publishable performance data.
- A release run uses one virtual user and runs warmup to completion before
  measurement. Each phase has a fixed operation count, minimum duration, and
  maximum duration. Fast operations are paced across the minimum duration;
  slow operations extend the phase without adding concurrent requests.
- Cold variant cases create a distinct variant during each measured operation.
- Cached cases request a variant that setup has already generated.
- General transformation, preprocessing, and eager-variant cases use WebP as
  their common output format.
- Preprocessing and Upload Rules cases measure the complete upload request.
- Eager generation measures upload acceptance; readiness is verified separately
  after the upload and is not folded into upload latency.

### Single-operation protocol

`single-vu-v1` uses 1,000 measured operations for cached reads and 200 for other
cases, per repetition. Measurement spans at least 30 seconds. Read warmup runs
100 operations; light warmup runs 30; heavy and inference warmup run 20. Warmup
spans at least 10 seconds. The catalog sets the maximum duration for each phase.
The k6 scenario also caps total execution time, and requests use the remaining
phase budget as their timeout.

Warmup and measurement execute in one sequence. A repetition must complete both
phases within their budgets and collect the full measurement count to pass.
The harness stops a phase after a failed operation and retains its failed result,
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

These defaults are an initial calibration target. Compare shortened runs against
longer runs on the same hardware before treating their p95 variability as
established. A one-hour suite is a target, not a fixed runtime guarantee; slow
versions, inference, setup, and cleanup add time.

## Mixed-load semantics

The load suite is one open-model k6 run. Its scenarios, stage durations, and
arrival rates come from the selected profile in `config/load.json`; changing a
rate or duration does not require updating separately calculated totals or seed
pool sizes. The current profile concurrently exercises cached original and
variant delivery, original uploads, cold WebP generation, eager uploads with
four background variants, and Upload Rules inference.

After traffic ramps down, a recovery scenario checks service health, uploads an
eager asset, waits up to 30 seconds for its four variants, and verifies their
delivery as cache hits. The run passes only when every traffic stream executes
with zero request errors, failed checks, and dropped iterations and the recovery
scenario passes. Latency remains comparative rather than a fixed SLO.

The load profile uses one representative case per operation. Codec matrices,
preprocessing combined with inference, repetitions, saturation ramps, and long
soaks stay in the isolated suite or future profiles. A material change to the
mix, stage timing, or arrival rates requires a new profile ID so historical
series remain comparable.

Single-request workload latency comes directly from k6's HTTP response timing,
which retains fractional milliseconds. Eager readiness spans several polling
requests, so it remains a wall-clock interval. The dashboard retains every raw
measurement but displays a change indicator only when the p95 difference is
both at least 5% and at least 2 ms.

Aggregation reports medians across repetitions. The report shows the observed
p95 range when a series has more than one repetition.

## Correctness and validity

Performance numbers are accepted only when the workload's functional checks also
pass. Depending on the case, these checks include:

- expected HTTP status and media type;
- expected output dimensions and format;
- expected cache miss or hit behavior;
- Upload Rules metadata produced by inference;
- eager variant readiness.

Each normalized result must satisfy the JSON schema. Its latency qualifies as a
valid measurement only when it reports passing checks, zero request errors,
the full configured measurement count, completed warmup and measurement windows,
and no dropped iterations. Smoke runs require their configured iteration count.
The current suite does
not impose a fixed latency ceiling: regressions remain visible in history
instead of being hidden by a failed publication.

A failed case does not stop aggregation or history publication, but its latency
is not presented as a valid measurement. A filtered release run is still
diagnostic only: publication requires every case configured for the release
suite and the configured number of repetitions for each case.

## Fixtures and generated data

Benchmark fixtures are immutable inputs. Their SHA-256 checksums are stored in
`assets/manifest.json` and verified before a run. Replacing fixture bytes requires
updating the manifest and, when comparability is affected, versioning the relevant
case or workload ID.

Raw run output is written below `performance/results/` and is intentionally
ignored by Git. It includes one normalized JSON result per case and repetition,
one self-contained k6 HTML time-series report per release or mixed-load k6
invocation, plus aggregate JSON and Markdown summaries for a completed run.
Smoke invocations are too short to provide useful time-series reports and skip
the HTML export.

Customer-facing isolated history lives in `performance/history/releases.json`,
and mixed-load history lives in `performance/history/loads.json`. History
entries must use a release subject such as `v0.9.0`; `lint-history.sh` rejects
commit-like, dirty, or otherwise non-release subjects. The history schema permits
new workloads and cases to appear in later releases, so older releases do not
need fabricated results for tests that did not yet exist.

Release and individual-result entries may contain a manually maintained `notes`
array. The dashboard renders these annotations so unusually large changes can
be explained without changing or discarding their measurements. Mixed-load runs
and individual traffic streams support the same annotations, which are preserved
when the run is regenerated.

The customer report joins both histories by release subject and environment.
Its Benchmark latency and Mixed load tabs always keep environments and versioned
load profiles separate. The stable report URL selects the newest release by
default; query parameters and the `#load` fragment provide shareable historical
views without generating per-release HTML. Release selectors, charts, and
comparison baselines use numeric `vMAJOR.MINOR.PATCH` order, so a backfilled run
keeps its release position even though its measurement timestamp is newer.

## Local environment interpretation

The `local-compose-v4` environment represents my laptop described in
`config/environments.json`. Its hardware description and interpretation notes
are rendered at the top of the report. Docker resource limits are part of that
profile, and Konifer uses a Docker-managed, disk-backed temporary volume rather
than tmpfs so large transformations do not consume the container's memory
allocation. It uses `single-vu-v1` and starts a new latency history, separate from
the arrival-rate results in `local-compose-v2` and `local-compose-v3`.

The environment catalog's `default` selects operation benchmarks. `loadDefault`
keeps mixed-load runs on `local-compose-v3`, since their traffic profile has not
changed. Historical results and environment IDs remain unchanged.

Local results are useful for establishing trends, exercising the harness, and
catching large regressions. They are affected by laptop power state, thermal
throttling, and other host activity, so they should not be presented as server
capacity or compared directly with a future AWS environment.

## Dashboard builds

`npm --prefix performance run build` builds the local dashboard with both the
Benchmark latency and Mixed load tabs and stages a standalone site in
`performance/dist/site/`.

Use `npm --prefix performance run build -- --without-load` to stage a site with
only operation benchmarks. This omits load history and configuration from the
site, disables load navigation and data requests, and makes existing `#load`
links show operation benchmarks. The local preview at `performance/report/`
still includes load testing. GitHub Pages publishes the build without load
testing; neither build changes the source histories.

## Planned extensions

Future environment profiles can reuse the same workload and report contracts.
Likely additions are:

- a reproducible AWS environment provisioned with Terraform;
- resolved image digests and a fuller runtime fingerprint in result metadata;
- capacity, mixed-traffic, and soak suites;
- explicit cold-process inference measurements where customer value justifies
  the additional orchestration.
