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

The current profiles measure nominal, controlled request arrival rather than
maximum capacity. Workloads, cases, and repetitions run sequentially so that a
slow case does not contaminate another case's measurements. Capacity, mixed
traffic, and soak testing are intentionally outside the current scope.

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

The runner does not purge the entire PostgreSQL or MinIO volume. Isolation comes
from unique run-scoped asset paths, followed after each repetition by recursive
deletion through Konifer's API so database and object-storage state remain
consistent and storage use does not accumulate across repetitions.

## Measurement semantics

Setup and asset seeding are not included in reported operation latency.

- A smoke run uses a small shared-iterations profile to catch broken workflows
  quickly. It is a functional confidence check, not publishable performance data.
- A release run uses explicit warmup and measurement phases. Its default profile
  sends requests at a controlled arrival rate so slow responses may overlap.
- Cold variant cases create a distinct variant during each measured operation.
- Cached cases request a variant that setup has already generated.
- General transformation, preprocessing, and eager-variant cases use WebP as
  their common output format.
- Preprocessing and Upload Rules cases measure the complete upload request.
- Eager generation measures upload acceptance; readiness is verified separately
  after the upload and is not folded into upload latency.

Single-request workload latency comes directly from k6's HTTP response timing,
which retains fractional milliseconds. Eager readiness spans several polling
requests, so it remains a wall-clock interval. The dashboard retains every raw
measurement but displays a change indicator only when the p95 difference is
both at least 5% and at least 2 ms.

The configured release repetition count is currently one to keep development
runs short. Increase it before relying on cross-release variability: aggregation
reports medians across repetitions, and the report shows the observed p95 range
only when a series has more than one repetition.

## Correctness and validity

Performance numbers are accepted only when the workload's functional checks also
pass. Depending on the case, these checks include:

- expected HTTP status and media type;
- expected output dimensions and format;
- expected cache miss or hit behavior;
- Upload Rules metadata produced by inference;
- eager variant readiness.

Each normalized result must satisfy the JSON schema. Its latency qualifies as a
valid measurement only when it reports passing checks, zero request errors, at
least one measured operation, and no dropped iterations. The current suite does
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
plus aggregate JSON and Markdown summaries for a completed run.

Customer-facing history lives in `performance/history/releases.json`. History
entries must use a release subject such as `v0.9.0`; `lint-history.sh` rejects
commit-like, dirty, or otherwise non-release subjects. The history schema permits
new workloads and cases to appear in later releases, so older releases do not
need fabricated results for tests that did not yet exist.

Release and individual-result entries may contain a manually maintained `notes`
array. The dashboard renders these annotations so unusually large changes can
be explained without changing or discarding their measurements.

## Local environment interpretation

The `local-compose-v2` environment represents the developer laptop described in
`config/environments.json`. Its hardware description and interpretation notes
are rendered at the top of the report. Docker resource limits are part of that
profile, and Konifer uses a Docker-managed, disk-backed temporary volume rather
than tmpfs so large transformations do not consume the container's memory
allocation.

Local results are useful for establishing trends, exercising the harness, and
catching large regressions. They are affected by laptop power state, thermal
throttling, and other host activity, so they should not be presented as server
capacity or compared directly with a future AWS environment.

## Planned extensions

Future environment profiles can reuse the same workload and report contracts.
Likely additions are:

- a reproducible AWS environment provisioned with Terraform;
- resolved image digests and a fuller runtime fingerprint in result metadata;
- capacity, mixed-traffic, and soak suites;
- explicit cold-process inference measurements where customer value justifies
  the additional orchestration.
