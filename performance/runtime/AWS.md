# AWS benchmark configuration

This step defines deployment inputs and prepares Konifer configuration. It does not
create AWS resources or add remote execution to `run.sh`. Keep the local Compose
default until the AWS provisioner and runner exist.

## Environment identity

Use `aws-c9gd-v1` from `config/environments.json`:

- Konifer: `c9gd.xlarge`, ARM64, container limit of 4 CPUs and 6 GiB RAM.
- k6: separate `c9g.medium`, container limit of 1 CPU and 1536 MiB RAM.
- Scratch: ext4 on NVMe instance storage at `/mnt/konifer-scratch`, bound to `/app/tmp`.
- RDS: PostgreSQL 17, single-AZ `db.m7g.large`, 20 GiB encrypted gp3, private endpoint.
- S3: regional S3 Standard, SSE-S3 encryption, Block Public Access, instance-role credentials.

The memory limits leave room for the host OS. Confirm the instance specifications
and offerings in your chosen region before provisioning. Pin the RDS minor version
and AMI during provisioning and record them with the image digest for each run.
Create a new environment ID for changes to hardware, resource limits, scratch,
database sizing/version, or networking that affect performance. Clone the profile
to choose another k6 instance; update its declared capacity and limits too.

## Provisioning contract

The infrastructure step must supply:

- Two ARM64 EC2 hosts and the RDS primary in the same AZ. A DB subnet group still
  needs subnets in at least two AZs; select the primary AZ when creating RDS.
- Private k6-to-Konifer traffic on port 8080, allowed from the k6 security group.
  Allow PostgreSQL 5432 from the Konifer security group. Keep SSH closed and use SSM.
  This benchmark measures private HTTP, without an ALB or TLS termination.
- An S3 gateway endpoint on the Konifer subnet route table, with DNS resolution
  and outbound HTTPS. Plan bootstrap access for the image, models, and SSM too.
- IMDSv2 required, endpoint enabled, response hop limit 2 for bridge containers.
  Attach a dedicated Konifer instance profile with bucket-scoped S3 access. Keep
  operator credentials and static S3 keys out of the container.
- Encrypted EBS root disks and RDS storage. Require `rds.force_ssl=1`, create the
  `konifer` database owned by `konifer_user`, and fetch its password through a
  protected secret. Do not put the password in user data or connection JSON.
- An S3 bucket with ACLs disabled, SSE-S3 encryption, Block Public Access, and
  HTTPS-only access. Scope access to the benchmark role and bucket. Configure
  access logging, CloudTrail data events, and CloudWatch request metrics when you
  need object-level audit records; budget their added cost and keep them consistent
  across comparable runs. Encrypt log destinations.
- A mounted ext4 NVMe instance-store filesystem with space and ownership that let
  the image's `konifer` user write to `/app/tmp`. Identify disks by their EC2 NVMe
  model, not a guessed device number. `aws-start.sh` refuses EBS or an unmounted
  directory and does not format or change ownership of disks.

Keep metadata in RDS and image objects in S3. Instance-store data disappears when
you stop or terminate the host. Delete benchmark objects and tear down idle
resources after testing; RDS, retained disks, logs, and S3 storage can keep accruing
charges after a test ends.

## Generate configuration

Copy `runtime/aws-connections.example.json` to the ignored
`runtime/aws-connections.local.json` and fill in provisioned addresses and names.
The region/AZ in the example are placeholders, not a deployment selection.
From `performance/`, run:

```bash
node runtime/aws-config.ts runtime/aws-connections.local.json runtime/aws-generated
```

For releases with abbreviated transformation parameters, append
`--use-legacy-query-params`. The generator applies translation after combining AWS
connections with the existing rules, paths, and variants from `runtime/konifer.conf`.
It excludes the local Garage endpoint and static credentials.

The output contains `konifer.conf`, `compose.json` for the Konifer host, and
`deployment.json` with resolved connections and requested resource settings. These
files describe the target; they do not attest to actual instance placement or
limits. The provisioner must check those against AWS before running benchmarks.
The full benchmark runner must also enforce the k6 limits and preserve deployment
metadata with results before publishing under this environment ID.

## Startup and live checkpoint

Use an ARM64 Konifer image containing `ssl-root-cert` support from this change.
Both the R2DBC application pool and JDBC scheduler use `verify-full` with the RDS
CA bundle. Older images without that setting need a separate, verified trust-store
setup; legacy transformation translation alone does not add TLS support.

On the Konifer host, put these beside the generated Compose file:

- The RDS CA bundle as `rds-ca-bundle.pem`.
- The model pack under `models/siglip2-base-patch16-224/`.

Set `KONIFER_IMAGE` and fetch `PG_PASSWORD` from your protected secret into the
shell environment. From a checkout on the host, run:

```bash
bash performance/runtime/aws-start.sh /absolute/path/to/aws-generated
```

The host needs Docker Compose, `jq`, PostgreSQL's `psql`, and `findmnt`/`lsblk`.
The script creates `ltree` using the database owner, starts Konifer, then checks
IMDSv2 from inside the application container and scratch write access. Konifer
runs schema migrations at startup. No AWS access keys or host AWS credential
files go into the generated container definition.

On the k6 host, use a checkout with performance dependencies installed and run:

```bash
node performance/runtime/aws-checkpoint.ts /absolute/path/to/aws-connections.local.json
```

The checkpoint waits for `/health/ready`, uploads the existing JPEG fixture,
downloads its original through Konifer, compares the bytes, and deletes the
checkpoint path. It writes no benchmark history. Run it from the k6 host to check
the private network path. Run the normal smoke suite once remote execution exists
to cover inference, transforms, and eager generation.

Configuration tests cover schema compatibility and generated settings. A live
checkpoint requires provisioned resources; generation alone cannot verify
instance-role credentials, RDS TLS, or S3 access.

## References

- [EC2 metadata options](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html)
- [RDS PostgreSQL TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html)
- [RDS DB subnet groups](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [RDS certificate bundles](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)
- [S3 security guidance](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
