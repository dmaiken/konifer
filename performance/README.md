# Performance Benchmarks

This directory contains Konifer's executable performance harness, curated
release history, and customer-facing report.

The harness is a private npm project using Node.js 24 and TypeScript 7, matching
the Konifer documentation project. Docker Compose provides Konifer, PostgreSQL,
and MinIO; k6 runs on the host.

## Prerequisites

Install Docker, k6, Node.js 24, npm, jq, sha256sum, and the Python `jsonschema`
CLI. Install the report-tooling dependencies from the repository root with:

```bash
npm --prefix performance ci
```

Upload Rules benchmarks require the SigLIP2 model pack. Download it before
starting the harness:

```bash
./scripts/download-siglip2-models.sh
```

The Compose profile uses `ghcr.io/dmaiken/konifer:latest`. To benchmark local
application changes, build that image from the repository root first:

```bash
./gradlew :service:shadowJar && docker build -t ghcr.io/dmaiken/konifer:latest .
```

## Quality checks

Run the complete harness quality gate with:

```bash
npm --prefix performance run check
```

It performs TypeScript checking, ESLint, release-history schema and label
validation, unit tests, and the standalone dashboard build. `npm run lint`,
`npm run lint:fix`, and `npm run lint:history` are also available from the
`performance` directory.

CI runs the same check for changes beneath `performance/`. Performance-only
changes do not trigger the application build, while tagged releases must pass
the performance quality gate before image builds begin. CI does not execute the
benchmarks themselves.

## Run benchmarks

Display the complete command reference with:

```bash
./performance/run.sh --help
```

Run the short development suite with:

```bash
./performance/run.sh smoke
```

The runner starts the Compose stack, verifies configured resource limits and
fixture hashes, waits for Konifer health, executes k6, validates every result,
and after each repetition deletes run-scoped assets through Konifer's recursive
Asset Delete API. A benchmark whose k6 checks or thresholds fail is recorded and
the remaining suite continues. PostgreSQL records are deleted synchronously,
and Konifer reaps the associated object-store content asynchronously. The exit
trap applies the same cleanup if a repetition cannot produce a valid result.

Reuse an already-running stack while iterating:

```bash
./performance/run.sh smoke --no-start
./performance/run.sh smoke --workload variant.eager.ready --no-start
./performance/run.sh smoke --workload format.encode --case jpg-to-webp --no-start
./performance/run.sh smoke --workload upload.rules --no-start
```

`--no-start` skips Compose startup but still verifies container limits and
Konifer health. To retain run assets for debugging, disable cleanup explicitly:

```bash
./performance/run.sh smoke \
  --workload variant.generate.cold \
  --keep-assets
```

Run the complete release suite and assign its future release label with:

```bash
./performance/run.sh release --subject v0.9.0
```

Run the shorter mixed-traffic load profile independently, or append it to a
release run:

```bash
./performance/run.sh load --subject v0.9.0
./performance/run.sh release --subject v0.9.0 --with-load
```

The `mixed-v1` profile concurrently exercises cached original and variant
delivery, original uploads, cold WebP generation, eager background variants,
and Upload Rules inference. Its duration and rates come from `config/load.json`.
Its gate is operational: every stream must execute without request errors,
failed checks, or dropped iterations, and a post-load health/eager-readiness
canary must pass. Latency is trended but does not have a fixed ceiling.

Without `--subject`, the runner uses `git describe --tags --always --dirty`.
Only stable `vMAJOR.MINOR.PATCH` subjects belong in published history. The
release repetition count comes from `config/workloads.json` and can be
overridden for a run:

```bash
./performance/run.sh release --subject v0.9.0 --repetitions 3
```

The configured release repetition count can be changed as the harness evolves.
A filtered release run using `--workload` or `--case` is diagnostic: it
generates local artifacts but is not published unless every configured release
case and repetition is present.

Prevent the host from sleeping during a long local release run. On systems
using systemd, one option is:

```bash
systemd-inhibit \
  --what=sleep \
  --why="Konifer release benchmark" \
  ./performance/run.sh release --subject v0.9.0
```

## Results and publication

Each run writes ignored, reproducible artifacts beneath its run directory:

```text
performance/results/<run-id>/
├── raw/           # complete k6 summaries
├── normalized/    # one validated document per case and repetition
├── aggregate.json # repetition medians and p95 variability
└── report.md      # readable report for this run
```

Load runs use the same directory shape, with one `normalized/load.json`
document. Their Markdown report and aggregate describe the traffic mix rather
than repetition medians.

Regenerate an existing run's aggregate and Markdown report with:

```bash
./performance/report.sh performance/results/<run-id>
```

For a load run, use its load-specific generator:

```bash
./performance/load-report.sh performance/results/<load-run-id>
```

A complete release run is added to
[`history/releases.json`](history/releases.json). Smoke runs, filtered release
diagnostics, and incomplete repetitions do not update published history. Failed
benchmark cases remain in a complete release as unavailable results, while the
valid cases are still published. The results directory remains ignored because
raw runs are noisy and can be reproduced; only curated release history is
committed.

Valid standalone and chained load runs, including failed operational results,
are added to [`history/loads.json`](history/loads.json). The browser dashboard
renders them in a dedicated Mixed load tab so concurrent-load measurements are
not confused with isolated workload latency. Optional `notes` arrays can be
maintained on a load run or traffic stream and are preserved when that release
is regenerated.

Published history must match its JSON Schema and contain only stable release
subjects:

```bash
./performance/lint-history.sh
```

Remove commit, prerelease, or `-dirty` entries explicitly with:

```bash
./performance/lint-history.sh --fix
```

`--fix` changes only curated history. It does not remove ignored run artifacts.

## Preview the customer report

Build the TypeScript-backed browser modules and serve the repository over HTTP:

```bash
npm --prefix performance run build
python3 -m http.server 8000
```

Open <http://localhost:8000/performance/report/>. The stable HTML loads
the release and load histories at runtime, so publishing a release does not
regenerate HTML or change the report URL. The bare URL selects the newest
published release and opens Benchmark latency. Release, environment, and load
profile selectors provide shareable deep links; `#load` opens the Mixed load
tab. The named environment and its interpretation remain visible while full
hardware details are expandable.

## Add or update fixtures

Store fixture files below `performance/assets/` and describe them in
[`assets/manifest.json`](assets/manifest.json). A fixture ID, such as
`photo-medium`, is referenced by the `fixture` field of a workload. Within a
fixture:

- `width` and `height` are the decoded pixel dimensions shared by every encoded
  file;
- each `files` key is the format ID used by the workload configuration;
- `path` is relative to `performance/assets/`;
- `mediaType` is the file's MIME type;
- `bytes` is its exact file size;
- `sha256` is the SHA-256 checksum of its bytes.

From the repository root, inspect one file with:

```bash
fixture_file=performance/assets/medium/medium.jpg

vipsheader -f width "$fixture_file"
vipsheader -f height "$fixture_file"
file --brief --mime-type "$fixture_file"
stat --format='%s' "$fixture_file"
sha256sum "$fixture_file"
```

The output supplies `width`, `height`, `mediaType`, `bytes`, and `sha256`,
respectively. The checksum is the first field printed by `sha256sum`. Review the
detected MIME type before copying it into the manifest; format IDs and media
types are not always spelled alike—for example, the `jpg` format uses
`image/jpeg`.

To inventory every encoding in a fixture directory as tab-separated values:

```bash
for fixture_file in performance/assets/medium/medium.*; do
  printf '%s\t' "${fixture_file#performance/assets/}"
  vipsheader -f width "$fixture_file" | tr '\n' '\t'
  vipsheader -f height "$fixture_file" | tr '\n' '\t'
  file --brief --mime-type "$fixture_file" | tr '\n' '\t'
  stat --format='%s' "$fixture_file" | tr '\n' '\t'
  sha256sum "$fixture_file" | cut -d ' ' -f 1
done
```

Each row is `path`, `width`, `height`, `mediaType`, `bytes`, then `sha256`. All
files grouped under one fixture must report the same dimensions. If they do not,
fix the encodings or model them as separate fixtures because the k6 correctness
checks use the fixture-level dimensions.

After editing the manifest, check its JSON syntax and run a smoke workload that
uses the fixture:

```bash
jq empty performance/assets/manifest.json
./performance/run.sh smoke --workload <workload-id>
```

`run.sh` recalculates the byte size and SHA-256 checksum of every manifest file
before starting a benchmark, so stale or incorrectly copied values fail early.
Changing fixture bytes can invalidate historical comparisons; follow the
versioning guidance in [`BENCHMARKING.md`](BENCHMARKING.md) when the change is
material.

## Machine-readable contracts

- [`config/workloads.json`](config/workloads.json) defines executable traffic
  profiles, suites, fixtures, cases, request parameters, and expected values.
- [`config/environments.json`](config/environments.json) defines the Compose
  file, verified resource limits, stable hardware metadata, and interpretation
  guidance for each environment.
- [`assets/manifest.json`](assets/manifest.json) fixes fixture paths, sizes,
  dimensions, media types, and hashes.
- [`schema/result.schema.json`](schema/result.schema.json) validates one
  normalized k6 repetition before aggregation.
- [`config/load.json`](config/load.json) defines the versioned mixed-traffic
  profile, its concurrent arrival rates, timing, and recovery canary.
- [`schema/load-result.schema.json`](schema/load-result.schema.json) validates a
  normalized mixed-load run before it is reported or published.
- [`schema/history.schema.json`](schema/history.schema.json) validates the
  published aggregate history. Workload and case IDs remain open-ended so later
  releases can add coverage without rewriting earlier releases.
- [`schema/workloads.schema.json`](schema/workloads.schema.json),
  [`schema/environments.schema.json`](schema/environments.schema.json), and
  [`schema/manifest.schema.json`](schema/manifest.schema.json) validate the
  executable catalogs during `npm test`.

Keep each workload's short customer-facing description beside its executable
definition so the report can load it directly. Extended methodology belongs
here or in [`BENCHMARKING.md`](BENCHMARKING.md). A workload should enter the
catalog only with a description, k6 handler, runtime configuration, fixture
definition, and result checks.

## Local reference environment

`local-compose-v2` runs on the developer laptop described in
`config/environments.json`. Konifer is limited to 2 CPUs and 8 GiB; PostgreSQL
and MinIO are each limited to 1 CPU and 1 GiB. Konifer uses a Docker-managed,
disk-backed `/app/tmp` volume, and the SigLIP2 model is mounted read-only.

The services and host-based k6 process compete for the same laptop CPU, memory,
storage, and network stack. Local results are useful for repeatable development
comparisons on this machine, but they are not production-capacity claims and
must not be compared directly with a future AWS profile.

The `upload.rules` workload verifies inference through a deterministic rule-added
label. `upload.rules.preprocess` verifies the same inference plus the
preprocessed WebP format and dimensions. Workloads whose paths do not reference
an Upload Rules ruleset do not perform inference, although the configured model
remains resident in the Konifer process.
