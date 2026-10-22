# Performance Benchmarks

Run Konifer benchmarks on local Docker Compose or dedicated AWS hosts, collect
results, and publish release history with this harness. It uses Node.js 24 and
TypeScript 7. See [BENCHMARKING.md](BENCHMARKING.md) for measurement rules and
[runtime/AWS.md](runtime/AWS.md) for AWS resource and recovery details.

Run the commands below from the repository root.

## Prerequisites

Install Node.js 24+, npm, Docker, jq, and sha256sum. Local runs also need k6 and
curl. AWS runs need a Linux checkout, AWS CLI v2, OpenTofu 1.13+, Docker Buildx,
and GNU tar; the harness installs k6 on the remote host.

Install the harness dependencies:

```bash
npm --prefix performance ci
```

Download the SigLIP2 models before running tests, including AWS tests:

```bash
./scripts/download-siglip2-models.sh
```

Local Compose defaults to `ghcr.io/dmaiken/konifer:latest`. To measure local
application changes, build that image first:

```bash
./gradlew :service:shadowJar && docker build -t ghcr.io/dmaiken/konifer:latest .
```

## Quality checks

Run the complete harness quality gate with:

```bash
npm --prefix performance run check
```

This checks TypeScript, ESLint, Bash syntax, history schemas and release labels,
then runs unit tests and builds the dashboard. From `performance/`, use
`npm run lint:fix` for ESLint fixes or `npm run lint:history` to check history.

CI runs this check for performance changes and before tagged image builds.
Performance-only changes skip the application build. CI does not run benchmarks.

## Run local benchmarks

Display the complete command reference with:

```bash
./performance/run.sh --help
```

Run the short development suite with:

```bash
./performance/run.sh smoke
```

The runner starts Konifer, PostgreSQL, and Garage in Compose and runs k6 on your
host. It verifies fixture hashes, container limits, and readiness before testing.
After each repetition, it validates results and deletes run-scoped assets through
Konifer's API. Failed benchmark checks remain in the report; operational failures
stop execution. An exit trap attempts cleanup after an interrupted repetition.

Reuse an already-running stack while iterating:

```bash
./performance/run.sh smoke --no-start
./performance/run.sh smoke --workload variant.eager.ready --no-start
./performance/run.sh smoke --workload format.encode --case jpg-to-webp --no-start
./performance/run.sh smoke --workload upload.rules --no-start
```

`--no-start` skips Compose startup while retaining limit and health checks. To
keep assets for debugging:

```bash
./performance/run.sh smoke \
  --workload variant.generate.cold \
  --keep-assets
```

Run a complete release suite with a stable release label:

```bash
./performance/run.sh release --subject v0.14.0 --docker-tag 0.14.0 --use-legacy-query-params
```

Run the shorter mixed-traffic load profile on its own, or append it to a release:

```bash
./performance/run.sh load --subject v0.14.0 --docker-tag 0.14.0 --use-legacy-query-params
./performance/run.sh release --subject v0.14.0 --docker-tag 0.14.0 --use-legacy-query-params --with-load
```

The `mixed-v1` profile runs concurrent traffic defined in `config/load.json`.
Passing requires no request errors, failed checks, or dropped iterations, plus
a passing post-load health/eager-readiness check. Latency has no fixed ceiling.
Load testing and `--with-load` remain local-only.

Without `--subject`, the runner uses `git describe --tags --always --dirty`.
Published history requires stable `vMAJOR.MINOR.PATCH` labels. Override the
repetition count from `config/workloads.json` for a diagnostic run with:

```bash
./performance/run.sh release --subject v0.14.0 --docker-tag 0.14.0 --use-legacy-query-params --repetitions 2
```

A filtered or shortened release run produces local artifacts. Publication
requires the full configured case and repetition matrix.

`--subject` labels results; `--docker-tag` selects the image. Keep them matched
before publishing.

Use `--config FILE` to select a standalone Konifer configuration; omit it to use
`runtime/konifer.conf`. The harness resolves relative paths from your working
directory and leaves explicit files unchanged. Add `--use-legacy-query-params`
for images that expect abbreviated transformation names. With an explicit
config, that flag changes requests only; write the config in the image's syntax.

Keep the host awake during a long local run. On systemd:

```bash
systemd-inhibit \
  --what=sleep \
  --why="Konifer release benchmark" \
  ./performance/run.sh release --subject v0.14.0 --docker-tag 0.14.0 --use-legacy-query-params
```

## Run AWS benchmarks

AWS uses separate Konifer and k6 EC2 hosts, private RDS PostgreSQL, and S3.
Authenticate with the AWS profile you intend to use. The wrapper defaults to
`konifer-perf`; select another authenticated profile through `AWS_PROFILE`.
The defaults are region `us-east-2` and stack `konifer-perf`. Set region/AZ/stack
overrides before preparation; see [AWS settings](runtime/AWS.md#workflow).

### Provision the disposable stack

```bash
export AWS_PROFILE=konifer-perf
# If this profile needs authentication:
aws login --profile "$AWS_PROFILE"

bash performance/runtime/aws.sh prepare --docker-tag 0.14.0
bash performance/runtime/aws.sh plan
```

`prepare` checks AWS access, offerings, quotas, fixtures, and models, then pins
the ARM64 image digest and deployment inputs. Neither command creates resources.
Review the plan before applying:

```bash
bash performance/runtime/aws.sh apply
```

**`apply` creates billable resources without another prompt.** It generates a
fresh plan before applying. Back up the ignored local OpenTofu state under
`performance/infrastructure/aws/`; losing that state does not delete resources.

### Run smoke, then release

```bash
bash performance/run.sh smoke \
  --environment aws-c9gd-v1 \
  --docker-tag 0.14.0 --subject v0.14.0 \
  --use-legacy-query-params

bash performance/run.sh release \
  --environment aws-c9gd-v1 \
  --docker-tag 0.14.0 --subject v0.14.0 \
  --use-legacy-query-params
```

Registry tags omit the `v` prefix; release subjects include it. The example
uses legacy request names because 0.14.0 expects them. Match that flag to the
image you select.

`run.sh` requires an applied stack. It stages the current harness and selected
config, starts Konifer, checks the deployment, and runs k6 on its dedicated host.
You do not need separate `bootstrap` or `checkpoint` commands for this path.
Add `--workload`/`--case` for a diagnostic subset. Use `--no-start` to reuse a
deployment only if its image, compatibility mode, config, and harness match.

Add `--config /path/to/konifer.conf` for an older release's configuration. AWS
keeps the whole file and overrides environment-owned database/S3 connections.
See [configuration selection](runtime/AWS.md#selecting-a-konifer-configuration)
for precedence, credential handling, and standalone-file requirements.

### Collect results

The local controller collects results at the end of the run. The k6 host uploads
checksummed snapshots to the staging bucket after repetitions and on exit. Your
controller downloads and validates them, then writes:

```text
performance/results/<run-id>/
```

The directory contains raw and normalized JSON, `aggregate.json`, and `report.md`;
release runs also include k6 HTML reports. AWS artifacts include deployment
metadata, checkpoint evidence, logs, and exit status. Complete release runs update
`performance/history/releases.json` under the same publication rules as local runs.

If your local process disconnects, collect using the run ID printed at startup:

```bash
bash performance/runtime/aws.sh collect --run-id RUN_ID
```

Collection retrieves artifacts; it does not resume execution. Keep
`performance/runtime/aws-local/runs/` until collection completes. Incomplete or
operationally failed runs retain partial artifacts without publishing history.
After interruption, inspect SSM commands and locks before retrying; follow the
[recovery instructions](runtime/AWS.md#running-and-collecting-results).

### Backfill another image and tear down

Reset before changing image digests. This clears benchmark data while preserving
the hosts and model cache:

```bash
bash performance/runtime/aws.sh reset \
  --docker-tag 0.9.0 --config /path/to/konifer-0.9.conf \
  --use-legacy-query-params
```

Then run smoke/release with the matching tag, subject, config, and compatibility
flag. You can add `--no-start` after a successful reset if the harness is unchanged.

Collect results before teardown:

```bash
bash performance/runtime/aws.sh destroy
```

**There is no automatic shutdown.** Resources accrue charges until you destroy
them. `destroy` runs without another prompt and deletes stack-owned buckets,
remote artifacts, disks, and RDS without a final snapshot. Local results remain.

## Results and publication

Local and collected AWS runs use this ignored artifact directory:

```text
performance/results/<run-id>/
├── html/          # self-contained k6 time-series reports
├── raw/           # complete k6 summaries
├── normalized/    # one validated document per case and repetition
├── aggregate.json # repetition medians and p95 variability
└── report.md      # readable report for this run
```

Load runs write `normalized/load.json` and `html/load.html`. Release runs write
one HTML report per case and repetition; smoke runs skip HTML export. Open these
self-contained HTML reports in a browser to inspect one-second time-series
samples. The runner disables the live dashboard listener so unattended runs exit.

Regenerate an existing run's aggregate and Markdown report with:

```bash
./performance/report.sh performance/results/RUN_ID
```

For a load run, use its load-specific generator:

```bash
./performance/load-report.sh performance/results/LOAD_RUN_ID
```

Complete release runs update [`history/releases.json`](history/releases.json).
Smoke, filtered releases, and incomplete repetition sets do not update history.
Complete runs retain failed cases as unavailable results. Commit curated history;
keep raw artifacts out of Git.

Schema-valid load results, including failed checks, update
[`history/loads.json`](history/loads.json). The dashboard puts these in the Mixed
load tab. Add optional `notes` arrays to runs or individual results/streams;
regeneration preserves existing notes.

Published history must match its JSON Schema and contain only stable release
subjects:

```bash
./performance/lint-history.sh
```

Remove commit, prerelease, or `-dirty` entries with:

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

Open <http://localhost:8000/performance/report/>. The dashboard reads history at
runtime; publishing a release leaves its HTML and URL unchanged. The bare URL
opens the newest release's Benchmark latency tab. Select a release/environment
to share a deep link; `#load` opens Mixed load. Expand the environment panel for
hardware and interpretation details.

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

These commands return `width`, `height`, `mediaType`, `bytes`, and `sha256` in
order. Use the first field from `sha256sum`. Check MIME types before copying
them: the format ID `jpg` uses `image/jpeg`.

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

Each row contains `path`, `width`, `height`, `mediaType`, `bytes`, and `sha256`.
Files in one fixture must share dimensions because k6 checks fixture-level
values. Fix mismatched encodings or give them separate fixture IDs.

After editing the manifest, check its JSON syntax and run a smoke workload that
uses the fixture:

```bash
jq empty performance/assets/manifest.json
./performance/run.sh smoke --workload WORKLOAD_ID
```

`run.sh` verifies fixture sizes and checksums before testing. Follow
[comparison versioning](BENCHMARKING.md#comparison-contract) when changed bytes
would invalidate historical comparisons.

## Machine-readable contracts

- [`config/workloads.json`](config/workloads.json) defines executable traffic
  profiles, suites, fixtures, cases, request parameters, and expected values.
- [`config/environments.json`](config/environments.json) defines Compose/AWS
  profiles, resource limits, hardware metadata, and interpretation notes.
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

Keep workload descriptions beside their executable definitions. Add a workload
with its description, k6 handler, runtime config, fixture, and result checks.
Put measurement rationale in [BENCHMARKING.md](BENCHMARKING.md).

## Local reference environment

`local-compose-v4` runs single-operation benchmarks on Daniel's laptop, described
in `config/environments.json`. Konifer gets 2 CPUs and 8 GiB; PostgreSQL and Garage
each get 1 CPU and 1 GiB. Konifer uses disk-backed Docker storage for `/app/tmp`
and a read-only SigLIP2 model mount. Mixed-load runs retain `local-compose-v3`;
the catalog keeps `local-compose-v2` for historical MinIO results.

The services and k6 share laptop resources. Compare releases within the same
environment; local results do not measure production capacity or support
cross-environment comparisons with AWS.

`upload.rules` verifies inference through a rule-added label.
`upload.rules.preprocess` also checks WebP preprocessing and dimensions. Paths
without an Upload Rules ruleset skip inference; the model remains resident.
