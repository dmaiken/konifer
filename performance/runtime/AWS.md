# Disposable AWS performance environment

Use OpenTofu to provision dedicated Konifer and k6 hosts, RDS, and S3. Run smoke
or release suites on k6, then collect and report results on your machine. Load
testing remains Compose-only. The [main README](../README.md#run-aws-benchmarks)
covers the shortest path from provisioning to teardown.

## Workflow

Run from the repository root on Linux. Install AWS CLI v2, OpenTofu 1.13+,
Docker Buildx, Node 24+, npm, GNU tar, jq, and sha256sum. Install dependencies
with `npm --prefix performance ci` and download the SigLIP2 model pack. Keep
the fixture files in the checkout. Authenticate with your chosen AWS profile.

```bash
export AWS_PROFILE=konifer-perf
# If the profile needs authentication:
aws login --profile "$AWS_PROFILE"

bash performance/runtime/aws.sh prepare --docker-tag 0.14.0
bash performance/runtime/aws.sh plan
```

Review the plan. The next command creates billable resources without another
prompt:

```bash
bash performance/runtime/aws.sh apply

bash performance/run.sh smoke \
  --environment aws-c9gd-v1 \
  --docker-tag 0.14.0 --subject v0.14.0 \
  --use-legacy-query-params

bash performance/run.sh release \
  --environment aws-c9gd-v1 \
  --docker-tag 0.14.0 --subject v0.14.0 \
  --use-legacy-query-params
```

`run.sh` bootstraps and checkpoints the applied stack before testing. To inspect
startup without running a suite, use `aws.sh bootstrap --docker-tag TAG` with the
chosen config/compatibility flags, then `aws.sh checkpoint`.

Registry tags omit the release's `v` prefix. Use the legacy flag for abbreviated
transformation parameters, including 0.14.0 and 0.9.0. `prepare` requires a tag,
resolves its immutable ARM64 digest, and checks offerings, quota headroom, RDS,
the AMI, fixtures, and models without creating resources.

The wrapper defaults `AWS_PROFILE` to `konifer-perf`, region to `us-east-2`, AZs
to `us-east-2a`/`us-east-2b`, and stack to `konifer-perf`. Set `AWS_REGION`,
`KONIFER_AWS_AZ`, `KONIFER_AWS_SECONDARY_AZ`, or `KONIFER_AWS_STACK` before
preparation to override placement. Use separate checkouts/state for different
accounts, regions, or stack names. The harness does not adopt existing resources.

`plan` saves a plan for inspection; `apply` generates a fresh plan and applies it
without another prompt. Collect results before teardown:

```bash
bash performance/runtime/aws.sh destroy
```

`destroy` also runs without another prompt. It deletes stack-owned S3 objects,
staged files, root disks, and RDS without a final snapshot. Local results and
checkpoint evidence remain. Treat remote data as disposable.

Hosts, RDS, public IPv4, Secrets Manager, disks, and S3 storage incur charges.
There is no automatic shutdown. Destroy after testing; stopping a host leaves
other charges in place.

## Local state and evidence

Infrastructure code lives in `performance/infrastructure/aws/`. Commit the provider
lockfile; Git ignores state/backups, plans, local inputs, and provider downloads.
**Back up local state before losing the checkout.** Deleting state leaves AWS
resources running. The `connections` and `deployment` outputs contain no passwords.

Resolved inputs, the manifest, and evidence live in the ignored
`performance/runtime/aws-local/` directory. Evidence includes resource IDs,
AMI/RDS versions, image digest, model/fixture hashes, Git revision, and harness
hash. Deployment uses current local sources, including uncommitted edits. It
excludes local AWS config, dependencies, Git metadata, and results. Models use
a separate archive; hosts check archive SHA-256 before extraction.

## Selecting a Konifer configuration

`run.sh`, `aws.sh bootstrap`, and `aws.sh reset` accept `--config FILE`, defaulting
to `performance/runtime/konifer.conf`. Relative paths start at your invocation
directory; absolute/external paths and spaces also work. Supply a standalone
HOCON file: the harness does not discover or package include trees. Missing or
unreadable files fail before deployment/reset. Konifer rejects malformed HOCON
at startup.

```bash
# Local: use your configuration unchanged, without an environment overlay.
bash performance/run.sh smoke --config /path/to/konifer-0.9.conf --docker-tag 0.9.0 --use-legacy-query-params

# AWS: reset for the selected image/config, then run against that deployment.
bash performance/runtime/aws.sh reset --docker-tag 0.9.0 --config /path/to/konifer-0.9.conf --use-legacy-query-params
bash performance/run.sh smoke --environment aws-c9gd-v1 --docker-tag 0.9.0 --subject v0.9.0 --config /path/to/konifer-0.9.conf --use-legacy-query-params --no-start
```

The harness leaves your file unchanged. With `--config`, the legacy flag changes
requests only; write the config in that release's syntax. Without `--config`,
the flag also translates the bundled default's parameter keys. Local Compose
connection environment variables remain unchanged. `--with-load` forwards the
selected file to the next local load run.

AWS stages the complete source, then applies a generated override layer through
HOCON includes. Property order, outer braces, and `rule-definitions` placement
have no effect on preservation. Konifer's HOCON parser handles merges and
substitutions; the harness adds no parser dependency.

The AWS layer overrides PostgreSQL provider/host/port/user/database/SSL settings,
S3 provider/region/path-style settings, `http.public-url`, and the existing
`paths."/**".object-store.bucket` default. It masks supplied PostgreSQL password
and certificate properties with null; startup supplies the password through
`PG_PASSWORD`. It also masks Garage's endpoint and static S3 keys so AWS uses
the instance role. Conflicts trigger replacement or clearing, not errors.
Unrelated application settings remain intact.

The container mounts the root, source, and override files read-only. Bootstrap
grants the image user access and records source/composition hashes in deployment
metadata and evidence. Local runs write fingerprint metadata to
`configuration.json`. AWS `--no-start` requires matching hashes; omit it to deploy
a changed config or update a deployment that lacks fingerprints.

Only the Konifer bundle contains your selected file. Logs, results, and k6
metadata exclude its contents. Use environment references for secrets: AWS
copies the source to the encrypted staging bucket and Konifer host even for
overridden properties. Do not embed real secrets in the source.

Run `npm run check` from `performance/` for the quality gate. The opt-in
`npm run test:config-hocon` checks composition with the parser in
`ghcr.io/dmaiken/konifer:0.14.0`, using a container with no network access. Set
`KONIFER_CONFIG_TEST_IMAGE` to check another compatible local image.

## Running and collecting results

`run.sh --environment aws-c9gd-v1` requires an applied stack and bootstraps the
selected image/current harness. `--no-start` reuses a deployment with matching
image, compatibility mode, config, and harness hash. Neither mode provisions or
destroys infrastructure. AWS rejects `load` and `--with-load` before remote work.
Local Compose defaults and host-based k6 execution remain unchanged.

`prepare` resolves k6 2.3.0 to an immutable ARM64 digest. Repetitions run in Docker
with 1 CPU, 1536 MiB memory, and no extra swap. The worker verifies limits before
execution and saves them with results. AWS shares local workloads, sampling,
cleanup, and normalized formats. Split selections that exceed SSM's 48-hour
budget, which includes setup and phase timeouts.

The k6 worker uploads a checksummed snapshot after each completed repetition and
on exit, including final status and logs. Its role can upload only under the
staging bucket's `k6/results/*` prefix. The local controller downloads artifacts
at the end of the run into `performance/results/<run-id>/` and generates
`aggregate.json` and `report.md`. Artifacts include raw/normalized JSON, release
HTML, deployment hashes, and checkpoint evidence. Before reporting, collection
verifies checksums, archive paths/types, request identity, the selected result
matrix, schemas, and required files.

The controller prints a run ID and retains its request in
`performance/runtime/aws-local/runs/`.
After a local interruption, retrieve available artifacts with:

```bash
bash performance/runtime/aws.sh collect --run-id RUN_ID
```

Collection retrieves artifacts; it does not resume execution. Running or
operationally failed jobs retain partial results without updating history. If an
active upload changes the checksum, retry after it finishes. You can repeat
completed collection. Complete release runs update `performance/history/releases.json`,
including failed cases as unavailable; smoke and filtered runs do not. Keep local
run records until collection finishes. Teardown deletes remote recovery artifacts
but preserves local results.

The local `runtime/aws-local/operation.lock` serializes operations. After an
interruption, inspect the SSM command ID and wait for completion or cancel it
before removing a stale lock. Use `aws ssm get-command-invocation` or Session
Manager to inspect failures. Polling has bounded retries and propagates errors.
Do not dump full Docker inspect output: its environment contains the database
password. Use selected safe fields, as the checkpoint does.

Bootstrap, checkpoint, reset, and runs also acquire
`/opt/konifer-performance/controller.lock` on k6. The worker holds a host `flock`
through execution, cleanup, and final upload. An abrupt interruption can leave
the directory lease in place to prevent concurrent work. Inspect SSM, the host
lock, and containers labelled `konifer.performance.run` before removing a stale
lease through Session Manager. Do not clear a lease while a run is active.

You can repeat bootstrap with the same image. Both the controller and a host
marker reject image digest changes before migrations on an initialized database.
Use `reset` to switch releases; preparation retains the pinned AMI. Re-bootstrap
to fetch a rotated password before restarting the application.

`reset` preflights the image, fixtures, and models before stopping Konifer. It
recreates the benchmark `konifer` database and ltree extension, empties the object
bucket (including incomplete multipart uploads), and clears validated NVMe scratch.
It preserves hosts, RDS, networking, staged artifacts, and the model cache. Cleared
data has no recovery path. Reset then bootstraps the selected image/config and
runs the checkpoint. An incomplete-reset marker blocks runs until reset succeeds;
resolve the error and retry. Reset does not format disks or remove cached models.

## Resource contract

- Dedicated VPC, one public EC2 subnet, and two private DB subnets across two AZs.
  Both hosts and the RDS primary share the primary AZ. Hosts have public IPv4 and
  outbound internet access, with no public inbound ports or SSH keys. Security
  groups allow private HTTP 8080 from k6 to Konifer and PostgreSQL 5432 from
  Konifer to RDS. The stack has no NAT gateway or load balancer.
- On-Demand c9gd.xlarge for Konifer, limited to 4 CPUs and 6 GiB RAM; separate
  c9g.medium for k6, limited to 1 CPU and 1536 MiB RAM. Preparation pins Ubuntu
  24.04 ARM64 to a concrete AMI.
  Both hosts use encrypted 30 GiB gp3 root disks and IMDSv2 with hop limit 2.
- SSM instance profiles for both hosts. Konifer gets object-bucket permissions
  and access to the benchmark's DB secret. Staging reads use shared and
  host-specific prefixes; k6 gets scoped result-upload access. The harness does
  not send operator credentials to hosts.
- Bootstrap installs Ubuntu Docker/Compose packages, Node 24.19.0, AWS CLI 2.38.2,
  psql, jq, and utilities. Checkpoint evidence records observed tool versions.
- Scratch uses ext4 NVMe instance storage at `/mnt/konifer-scratch`, bound to
  `/app/tmp`. Bootstrap identifies disks by EC2 model rather than device number.
  It formats only a blank, unpartitioned instance-store disk and refuses unexpected
  mounts, EBS, and nonblank unmounted disks. Ownership follows the image UID/GID.
  After reboot, inspect and mount nonblank scratch before retrying bootstrap.
  Stop/termination loses scratch.
- RDS PostgreSQL 17.11, single-AZ db.m7g.large, private endpoint, encrypted 20 GiB
  gp3, no storage scaling or automatic minor upgrades, no backups/final snapshot.
  Database `konifer` uses master owner `konifer_user`. RDS manages its password
  in Secrets Manager; bootstrap retrieves it on-host, creates ltree over verified
  TLS, and starts Konifer's migrations.
- S3 Standard object and staging buckets use SSE-S3, ACLs disabled, Block Public
  Access, HTTPS-only policies, and a gateway endpoint on the EC2 route table.
  Incomplete multipart uploads expire after one day. The stack leaves object
  request metrics and data-event audit logging disabled.

## Historical database compatibility

The harness requests `ssl-mode=disable` and `rds.force_ssl=0` for each Konifer
version. It offers no application TLS selector or certificate mount, permitting
**unencrypted database traffic inside the isolated VPC**. Releases before 0.13.0
hardcode R2DBC TLS off. Older JDBC schedulers may negotiate TLS through driver
defaults; evidence records sessions without adapting config or requiring plaintext
on each connection.

For releases without `/health/ready`, the checkpoint falls back to `/health`
after a 404. It does not bypass an unhealthy modern probe. The upload/download
check verifies database and object access with either route.

The application's `ssl-root-cert` feature remains available outside the harness.
Administrative psql uses `verify-full` with the RDS CA bundle on the host, outside
the application mount. Local Compose defaults and compatibility flags remain unchanged.

The default workload configuration spans releases. Historical images can accept
uploads while ignoring newer preprocessing/rule settings. With the default config,
0.9.0 fails three preprocessing/rules smoke cases but passes original upload and
both variant cases: it expects `edit-rules` instead of `modify-rules` and older
preprocessing options. Reports mark these cases failed/unavailable. Supply
`--config` to express the intended behavior in that release's syntax. The harness
does not infer version-specific config beyond the explicit legacy parameter flag.

## Acceptance

The live checkpoint verifies placement, metadata options, root encryption, RDS
size/version/SSL enforcement, ingress, S3 encryption/public access, and endpoint
routing. It uploads the fixture JPEG, compares downloaded bytes, and deletes its
path. Startup checks container role discovery and scratch writes. Evidence records
container limits, scratch backing, DB TLS sessions, and tool versions.

After deletion, the checkpoint polls for newly added S3 keys to disappear through
background cleanup. It uses 25 attempts with five-second pauses and preserves the
pre-existing object baseline.

Use checkpoint for private networking, RDS/S3 access, and startup; smoke for
workload correctness; release for measured samples. Reset between image digests.
The current benchmark methodology starts a new release baseline while retaining
existing load history. A filtered release can check execution/reporting without
running the full suite or publishing a baseline.

## References

- [RDS PostgreSQL TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html)
- [RDS-managed passwords](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [EC2 metadata options](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html)
- [SSM command status](https://docs.aws.amazon.com/systems-manager/latest/userguide/monitor-commands.html)
