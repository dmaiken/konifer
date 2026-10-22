import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { chmod, cp, mkdir, mkdtemp, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertSameConfiguration, awsConfigurationFiles, generateAwsConfiguration, loadAwsConfiguration, type AwsConfigurationFingerprint, type AwsConnections } from './aws-config.ts';
import { selectConfiguration, type ConfigurationSelection } from './configuration.ts';
import { runRequest, validateRun, type RunRequest } from './aws-run.ts';

const performanceDirectory = fileURLToPath(new URL('../', import.meta.url));
const repository = path.dirname(performanceDirectory);
const infrastructureDirectory = path.join(performanceDirectory, 'infrastructure/aws');
const localDirectory = path.join(performanceDirectory, 'runtime/aws-local');
const inputsFile = path.join(infrastructureDirectory, 'deployment.local.tfvars.json');
const preparedFile = path.join(localDirectory, 'prepared.json');
const manifestFile = path.join(localDirectory, 'manifest.json');
const planFile = path.join(infrastructureDirectory, 'deployment.tfplan');
const imageRepository = 'ghcr.io/dmaiken/konifer';

interface Inputs {
    account_id: string;
    region: string;
    availability_zone: string;
    secondary_availability_zone: string;
    ami_id: string;
    stack_name: string;
    postgres_version: string;
}
interface Image { tag: string; digest: string; reference: string }
interface Prepared { inputs: Inputs; image: Image; k6Image: string; preparedAt: string }
interface Infrastructure {
    accountId: string;
    region: string;
    stackName: string;
    availabilityZone: string;
    amiId: string;
    vpcId: string;
    subnetId: string;
    s3EndpointId: string;
    routeTableId: string;
    instances: { konifer: string; k6: string };
    securityGroups: { konifer: string; k6: string; database: string };
    databaseId: string;
    postgresVersion: string;
    secretArn: string;
    stagingBucket: string;
    objectBucket: string;
}
interface Manifest {
    infrastructure: Infrastructure;
    connections: AwsConnections;
    image: Image;
    useLegacyQueryParams: boolean;
    harness: { revision: string; sha256: string };
    models: Record<string, string>;
    fixtures: Record<string, string>;
    createdAt: string;
    bundleKey: string;
    k6Image: string;
    k6Version: string;
    configuration: AwsConfigurationFingerprint;
}

/** Quote one literal shell argument, including embedded quotes and command-substitution syntax. */
export function shellQuote(value: string): string {
    return `'${value.replaceAll("'", "'\\''")}'`;
}

/** Select a Linux ARM64 digest from an OCI index; reject invalid tags and missing platform manifests. */
export function arm64Image(tag: string, index: { manifests?: Array<{ digest: string; platform?: { os: string; architecture: string } }> }): Image {
    if (!/^[\w][\w.-]{0,127}$/.test(tag)) throw new Error('Invalid Docker tag');
    const manifest = index.manifests?.find((entry) => entry.platform?.os === 'linux' && entry.platform.architecture === 'arm64');
    if (!manifest || !/^sha256:[a-f0-9]{64}$/.test(manifest.digest)) throw new Error(`No linux/arm64 image for ${tag}`);
    return { tag, digest: manifest.digest, reference: `${imageRepository}@${manifest.digest}` };
}

/** Require an explicit reset before switching images on a database that may contain migrations. */
export function assertSameImage(previous: string, next: string): void {
    if (previous !== next) throw new Error('Image digest changed on an initialized database. Use aws.sh reset --docker-tag TAG. Destroy and recreate is also supported.');
}

/** Classify SSM invocation states, treating unknown terminal states as failures. */
export function commandStatus(status: string): 'pending' | 'success' | 'failure' {
    if (status === 'Success') return 'success';
    if (['Pending', 'InProgress', 'Delayed', 'Cancelling'].includes(status)) return 'pending';
    return 'failure';
}

/**
 * Spawn a tool without a shell, capture stdout, and optionally stream its output.
 * Disable interactive AWS/Tofu prompts; terminate on timeout and reject nonzero
 * exits with a bounded diagnostic tail. Callers must avoid secret-bearing output.
 */
async function run(command: string, args: string[], options: { cwd?: string; stream?: boolean; timeout?: number } = {}): Promise<string> {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, {
            cwd: options.cwd ?? repository,
            env: { ...process.env, AWS_PAGER: '', AWS_CLI_AUTO_PROMPT: 'off', TF_IN_AUTOMATION: '1' },
            stdio: ['ignore', 'pipe', 'pipe'],
        });
        let stdout = '';
        let stderr = '';
        child.stdout.on('data', (data: Buffer) => {
            stdout += data.toString();
            if (options.stream) process.stdout.write(data);
        });
        child.stderr.on('data', (data: Buffer) => {
            stderr += data.toString();
            if (options.stream) process.stderr.write(data);
        });
        const timeout = setTimeout(() => child.kill('SIGTERM'), options.timeout ?? 120_000);
        child.on('error', (error) => {
            clearTimeout(timeout);
            reject(error);
        });
        child.on('close', (code) => {
            clearTimeout(timeout);
            if (code === 0) {
                resolve(stdout);
            } else {
                reject(new Error(`${command} failed (${code ?? 'terminated'}): ${stderr.slice(-4000) || stdout.slice(-4000)}`));
            }
        });
    });
}

/** Read local JSON state; callers validate the fields required by their operation. */
async function json<T>(file: string): Promise<T> {
    return JSON.parse(await readFile(file, 'utf8')) as T;
}

/** Replace a local JSON record through a private temporary file and atomic rename. */
async function save(file: string, value: unknown): Promise<void> {
    await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
    await writeFile(`${file}.tmp`, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
    await rename(`${file}.tmp`, file);
}
/** Stream a file into SHA-256 without loading model/result archives into memory. */
async function hash(file: string): Promise<string> {
    const digest = createHash('sha256');
    for await (const chunk of createReadStream(file)) digest.update(chunk);
    return digest.digest('hex');
}
/** Invoke the AWS CLI in the selected region and parse its JSON response. */
async function aws<T>(region: string, ...args: string[]): Promise<T> {
    return JSON.parse(await run('aws', ['--region', region, ...args, '--output', 'json'])) as T;
}
/** Run OpenTofu in the harness infrastructure directory with a one-hour process timeout. */
async function tofu(args: string[], stream = true): Promise<string> {
    return run('tofu', [`-chdir=${infrastructureDirectory}`, ...args], { stream, timeout: 3_600_000 });
}
/** Verify pinned inputs and the active AWS account before using existing deployment state. */
async function initialized(): Promise<Prepared> {
    const prepared = await json<Prepared>(preparedFile);
    const actual = await json<Inputs>(inputsFile);
    if (JSON.stringify(prepared.inputs) !== JSON.stringify(actual)) throw new Error('Prepared inputs changed. Run prepare again.');
    const identity = await aws<{ Account: string }>(actual.region, 'sts', 'get-caller-identity');
    if (identity.Account !== actual.account_id) throw new Error('AWS account differs from prepared deployment');
    console.log(`AWS account ${actual.account_id}, region ${actual.region}, stack ${actual.stack_name}`);
    return prepared;
}

/** Hash manifest fixtures and reject missing, changed, or assets-directory-escaping paths. */
export async function fixtures(directory = performanceDirectory): Promise<Record<string, string>> {
    const manifest = await json<{ fixtures: Record<string, { files: Record<string, { path: string; sha256: string }> }> }>(path.join(directory, 'assets/manifest.json'));
    const hashes: Record<string, string> = {};
    for (const file of Object.values(manifest.fixtures).flatMap((fixture) => Object.values(fixture.files))) {
        const fixture = path.resolve(directory, 'assets', file.path);
        if (!fixture.startsWith(`${path.join(directory, 'assets')}/`)) throw new Error('Invalid fixture path');
        hashes[file.path] = await hash(fixture);
        if (hashes[file.path] !== file.sha256) throw new Error(`Fixture hash mismatch: ${file.path}`);
    }
    return hashes;
}
/** Hash the required local inference files for content-addressed deployment and provenance. */
async function models(): Promise<Record<string, string>> {
    const hashes: Record<string, string> = {};
    for (const file of ['vision_model.onnx', 'text_model.onnx', 'tokenizer.json']) {
        hashes[file] = await hash(path.join(repository, 'models/siglip2-base-patch16-224', file));
    }
    return hashes;
}
/** Resolve a release tag to an immutable ARM64 Konifer reference through Docker's registry client. */
async function image(tag: string): Promise<Image> {
    if (!/^[\w][\w.-]{0,127}$/.test(tag)) throw new Error('Invalid Docker tag');
    return arm64Image(tag, JSON.parse(await run('docker', ['buildx', 'imagetools', 'inspect', `${imageRepository}:${tag}`, '--raw'])));
}

/** Resolve the harness's pinned k6 version to an immutable Linux ARM64 reference. */
async function k6Image(): Promise<string> {
    const index = JSON.parse(await run('docker', ['buildx', 'imagetools', 'inspect', 'grafana/k6:2.3.0', '--raw'])) as Parameters<typeof arm64Image>[1];
    const resolved = arm64Image('2.3.0', index);
    return `grafana/k6@${resolved.digest}`;
}

/**
 * Check AWS identity, placement, quota, pinned service availability, and local assets.
 * Save deployment inputs/image digests and invalidate an old plan; preserve an
 * existing pinned AMI. Performs AWS reads but provisions no resources.
 */
async function prepare(tag: string): Promise<void> {
    const region = process.env.AWS_REGION ?? process.env.AWS_DEFAULT_REGION ?? 'us-east-2';
    const az = process.env.KONIFER_AWS_AZ ?? `${region}a`;
    const secondaryAz = process.env.KONIFER_AWS_SECONDARY_AZ ?? `${region}b`;
    const stack = process.env.KONIFER_AWS_STACK ?? 'konifer-perf';
    if (!/^[a-z][a-z0-9-]{2,23}$/.test(stack)) throw new Error('Invalid stack name');
    const [identity, resolvedImage, resolvedK6] = await Promise.all([aws<{ Account: string }>(region, 'sts', 'get-caller-identity'), image(tag), k6Image()]);
    const offerings = await aws<{ InstanceTypeOfferings: Array<{ InstanceType: string; Location: string }> }>(region,
        'ec2', 'describe-instance-type-offerings', '--location-type', 'availability-zone', '--filters', 'Name=instance-type,Values=c9gd.xlarge,c9g.medium');
    for (const type of ['c9gd.xlarge', 'c9g.medium']) {
        if (!offerings.InstanceTypeOfferings.some((entry) => entry.InstanceType === type && entry.Location === az)) throw new Error(`${type} unavailable in ${az}`);
    }
    const types = await aws<{ InstanceTypes: Array<{ InstanceType: string; VCpuInfo: { DefaultVCpus: number }; MemoryInfo: { SizeInMiB: number }; ProcessorInfo: { SupportedArchitectures: string[] } }> }>(region,
        'ec2', 'describe-instance-types', '--instance-types', 'c9gd.xlarge', 'c9g.medium');
    for (const entry of types.InstanceTypes) {
        if (!entry.ProcessorInfo.SupportedArchitectures.includes('arm64') || entry.VCpuInfo.DefaultVCpus !== (entry.InstanceType === 'c9gd.xlarge' ? 4 : 1)
            || entry.MemoryInfo.SizeInMiB !== (entry.InstanceType === 'c9gd.xlarge' ? 8192 : 2048)) throw new Error('Instance specifications differ from benchmark profile');
    }
    const [quota, usage] = await Promise.all([
        aws<{ Quota: { Value: number } }>(region, 'service-quotas', 'get-service-quota', '--service-code', 'ec2', '--quota-code', 'L-1216C47A'),
        aws<{ Reservations: Array<{ Instances: Array<{ InstanceType: string; InstanceLifecycle?: string; CpuOptions: { CoreCount: number; ThreadsPerCore: number }; Tags?: Array<{ Key: string; Value: string }> }> }> }>(region,
            'ec2', 'describe-instances', '--filters', 'Name=instance-state-name,Values=pending,running'),
    ]);
    const used = usage.Reservations.flatMap((reservation) => reservation.Instances)
        .filter((instance) => /^[acdhimrtz]/.test(instance.InstanceType) && !instance.InstanceLifecycle
            && !instance.Tags?.some((tag) => tag.Key === 'Stack' && tag.Value === stack))
        .reduce((sum, instance) => sum + instance.CpuOptions.CoreCount * instance.CpuOptions.ThreadsPerCore, 0);
    if (used + 5 > quota.Quota.Value) throw new Error(`Insufficient standard On-Demand vCPU quota: ${used} used + 5 required > ${quota.Quota.Value}`);
    const postgres = await aws<{ OrderableDBInstanceOptions: Array<{ StorageType: string; AvailabilityZones: Array<{ Name: string }> }> }>(region,
        'rds', 'describe-orderable-db-instance-options', '--engine', 'postgres', '--engine-version', '17.11', '--db-instance-class', 'db.m7g.large');
    if (!postgres.OrderableDBInstanceOptions.some((option) => option.StorageType === 'gp3' && option.AvailabilityZones.some((entry) => entry.Name === az))) throw new Error('Pinned RDS configuration unavailable');
    const zones = await aws<{ AvailabilityZones: Array<{ ZoneName: string }> }>(region, 'ec2', 'describe-availability-zones', '--filters', 'Name=state,Values=available');
    if (az === secondaryAz || !zones.AvailabilityZones.some((entry) => entry.ZoneName === secondaryAz)) throw new Error('A different available secondary AZ is required');
    let amiId: string;
    try {
        const existing = await json<Inputs>(inputsFile);
        if (existing.account_id !== identity.Account || existing.region !== region || existing.stack_name !== stack) throw new Error('Use a separate checkout/state directory for a different account, region, or stack');
        amiId = existing.ami_id;
    } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
        const ami = await aws<{ Parameter: { Value: string } }>(region, 'ssm', 'get-parameter', '--name', '/aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id');
        amiId = ami.Parameter.Value;
    }
    const ami = await aws<{ Images: Array<{ Architecture: string; State: string; OwnerId: string }> }>(region, 'ec2', 'describe-images', '--image-ids', amiId);
    if (ami.Images[0]?.Architecture !== 'arm64' || ami.Images[0]?.State !== 'available' || ami.Images[0]?.OwnerId !== '099720109477') throw new Error('Prepared AMI is not an available Canonical ARM64 image');
    await Promise.all([fixtures(), models()]);
    const inputs: Inputs = { account_id: identity.Account, region, availability_zone: az, secondary_availability_zone: secondaryAz, ami_id: amiId, stack_name: stack, postgres_version: '17.11' };
    await save(inputsFile, inputs);
    await save(preparedFile, { inputs, image: resolvedImage, k6Image: resolvedK6, preparedAt: new Date().toISOString() } satisfies Prepared);
    // A previous saved plan must never survive changed inputs.
    await rm(planFile, { force: true });
    console.log(`Prepared ${identity.Account}/${region}/${az}, AMI ${amiId}, ${resolvedImage.reference}. No resources created.`);
}

/** Read applied OpenTofu outputs and validate the connection contract before using them. */
async function outputs(): Promise<{ infrastructure: Infrastructure; connections: AwsConnections }> {
    const result = JSON.parse(await tofu(['output', '-json'], false)) as { deployment: { value: Infrastructure }; connections: { value: AwsConnections } };
    await loadAwsConfiguration(result.connections.value);
    return { infrastructure: result.deployment.value, connections: result.connections.value };
}

/** Poll SSM registration for up to 120 attempts with five-second pauses. */
async function waitForSsm(region: string, instance: string): Promise<void> {
    for (let attempt = 0; attempt < 120; attempt++) {
        const result = await aws<{ InstanceInformationList: Array<{ PingStatus: string }> }>(region, 'ssm', 'describe-instance-information', '--filters', `Key=InstanceIds,Values=${instance}`);
        if (result.InstanceInformationList[0]?.PingStatus === 'Online') return;
        if (attempt % 6 === 0) console.log(`Waiting for SSM on ${instance}...`);
        await new Promise((resolve) => setTimeout(resolve, 5000));
    }
    throw new Error(`SSM did not become ready: ${instance}`);
}

/** Poll one SSM invocation through injected readers/delays; reject terminal failures and exhausted attempts. */
export async function pollCommand(
    read: () => Promise<{ Status: string; ResponseCode: number; StandardOutputContent: string; StandardErrorContent: string }>,
    pause: () => Promise<void>, attempts = 360,
): Promise<string> {
    for (let attempt = 0; attempt < attempts; attempt++) {
        const invocation = await read();
        const status = commandStatus(invocation.Status);
        if (status === 'success' && invocation.ResponseCode === 0) return invocation.StandardOutputContent;
        if (status === 'failure' || status === 'success') throw new Error(`Remote command ${invocation.Status} (${invocation.ResponseCode}): ${invocation.StandardErrorContent}\n${invocation.StandardOutputContent}`);
        await pause();
    }
    throw new Error('Remote command timed out; inspect the command ID before retrying');
}

/** Wait for checkpoint-created S3 keys to disappear while allowing pre-existing objects to remain. */
export async function waitForObjectCleanup(baseline: Set<string>, readKeys: () => Promise<string[]>, pause: () => Promise<void>, attempts = 25): Promise<void> {
    for (let attempt = 0; attempt < attempts; attempt++) {
        const remaining = (await readKeys()).filter((key) => !baseline.has(key));
        if (remaining.length === 0) return;
        if (attempt + 1 < attempts) await pause();
    }
    throw new Error('Checkpoint objects remain in S3 after waiting for background cleanup');
}

/** List current benchmark object keys using the AWS CLI's automatic pagination. */
async function objectKeys(infrastructure: Infrastructure): Promise<string[]> {
    const result = await aws<{ Contents?: Array<{ Key: string }> }>(infrastructure.region, 's3api', 'list-objects-v2', '--bucket', infrastructure.objectBucket);
    return result.Contents?.map((object) => object.Key) ?? [];
}

/**
 * Run a root shell script on one role through SSM and return its stdout.
 * Allow initial invocation propagation, log the command ID for recovery, and
 * request cancellation on failure. Scripts/output must exclude secret values.
 */
async function remote(infrastructure: Infrastructure, role: 'konifer' | 'k6', script: string, timeoutSeconds = 1800): Promise<string> {
    const instance = infrastructure.instances[role];
    await waitForSsm(infrastructure.region, instance);
    const result = await aws<{ Command: { CommandId: string } }>(infrastructure.region, 'ssm', 'send-command', '--instance-ids', instance,
        '--document-name', 'AWS-RunShellScript', '--timeout-seconds', '600', '--parameters', JSON.stringify({ commands: ['set -eu', script], executionTimeout: [String(timeoutSeconds)] }));
    const id = result.Command.CommandId;
    console.log(`SSM ${role}: ${id}`);
    let ticks = 0;
    try {
        return await pollCommand(async () => {
            try {
                return await aws(infrastructure.region, 'ssm', 'get-command-invocation', '--command-id', id, '--instance-id', instance);
            } catch (error) {
                if (String(error).includes('InvocationDoesNotExist')) return { Status: 'Pending', ResponseCode: -1, StandardOutputContent: '', StandardErrorContent: '' };
                throw error;
            }
        }, async () => {
            if (ticks++ % 6 === 0) console.log(`Waiting for ${role} command ${id}...`);
            await new Promise((resolve) => setTimeout(resolve, 5000));
        }, Math.ceil((timeoutSeconds + 600) / 5));
    } catch (error) {
        await aws(infrastructure.region, 'ssm', 'cancel-command', '--command-id', id, '--instance-ids', instance).catch(() => undefined);
        throw error;
    }
}

/** Upload a content-addressed artifact unless its S3 key exists; allow two hours for model uploads. */
async function upload(infrastructure: Infrastructure, source: string, key: string): Promise<void> {
    try {
        await aws(infrastructure.region, 's3api', 'head-object', '--bucket', infrastructure.stagingBucket, '--key', key);
        return;
    } catch (error) {
        if (!/404|NotFound|NoSuchKey/.test(String(error))) throw error;
    }
    // The initial model archive is ~1.4 GiB; allow slower developer uplinks.
    await run('aws', ['--region', infrastructure.region, 's3', 'cp', source, `s3://${infrastructure.stagingBucket}/${key}`, '--only-show-errors'], { timeout: 7_200_000 });
}

/** Create a deterministic worker bundle with an explicit file allowlist, excluding the selected private config. */
export async function harnessArchive(destination: string, selection: ConfigurationSelection): Promise<void> {
    const relativeSource = path.relative(performanceDirectory, selection.path);
    const exclusions = !relativeSource.startsWith('..') && !path.isAbsolute(relativeSource) ? [`--exclude=${relativeSource}`] : [];
    await run('tar', ['--sort=name', '--mtime=@0', '--owner=0', '--group=0', '--numeric-owner', '--no-wildcards', ...exclusions, '-cf', destination, '-C', performanceDirectory,
        'assets', 'config/workloads.json', 'config/load.json', 'config/environments.json', 'schema', 'lib', 'report', 'k6', 'package.json', 'package-lock.json', 'run.sh',
        'runtime/configuration.ts', 'runtime/aws.sh', 'runtime/aws.ts',
        'runtime/aws-start.sh', 'runtime/aws-host.sh', 'runtime/aws-config.ts', 'runtime/aws-checkpoint.ts',
        'runtime/aws-job.sh', 'runtime/aws-run.ts', 'runtime/aws-reset.sh', 'runtime/validate-result.ts']);
}

/** Separate private application config from shared k6 metadata and return content-addressed archive keys. */
export async function deploymentArchives(directory: string): Promise<{ application: { file: string; hash: string; key: string }; metadata: { file: string; hash: string; key: string } }> {
    const options = ['--sort=name', '--mtime=@0', '--owner=0', '--group=0', '--numeric-owner'];
    const applicationFile = path.join(directory, 'deployment.tar');
    await run('tar', [...options, '-cf', applicationFile, '-C', directory, 'generated', 'manifest.json']);
    const applicationHash = await hash(applicationFile);
    const metadataFile = path.join(directory, 'metadata.tar');
    await run('tar', [...options, '-cf', metadataFile, '-C', directory, 'manifest.json', 'generated/deployment.json']);
    const metadataHash = await hash(metadataFile);
    return {
        application: { file: applicationFile, hash: applicationHash, key: `konifer/deployments/${applicationHash}.tar` },
        metadata: { file: metadataFile, hash: metadataHash, key: `shared/deployments/${metadataHash}.tar` },
    };
}

/**
 * Stage verified harness/models/config bundles and bootstrap both provisioned hosts.
 * Enforce image continuity before host changes and use role-specific archives so
 * k6 cannot fetch private config. Save the deployment manifest after host setup.
 */
async function bootstrap(tag: string, legacy: boolean, selection: ConfigurationSelection): Promise<void> {
    const prepared = await initialized();
    const resolvedImage = await image(tag);
    assertSameImage(prepared.image.digest, resolvedImage.digest);
    const { infrastructure, connections } = await outputs();
    // Guard before staging or changing either host. The remote marker also survives local manifest loss.
    try {
        const previous = await json<Manifest>(manifestFile);
        if (previous.infrastructure.databaseId === infrastructure.databaseId && previous.infrastructure.instances.konifer === infrastructure.instances.konifer) assertSameImage(previous.image.digest, resolvedImage.digest);
    } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-aws-'));
    try {
        const fixtureHashes = await fixtures();
        const modelHashes = await models();
        const harnessFile = path.join(temporary, 'harness.tar');
        const modelArchive = path.join(temporary, 'models.tar');
        const commonTar = ['--sort=name', '--mtime=@0', '--owner=0', '--group=0', '--numeric-owner'];
        await harnessArchive(harnessFile, selection);
        await run('tar', [...commonTar, '-cf', modelArchive, '-C', path.join(repository, 'models'), 'siglip2-base-patch16-224'], { timeout: 300_000 });
        const harnessHash = await hash(harnessFile);
        const modelHash = await hash(modelArchive);
        const harnessKey = `shared/harness/${harnessHash}.tar`;
        const modelKey = `konifer/models/${modelHash}.tar`;
        const configuration = await generateAwsConfiguration(await writeConnections(temporary, connections), path.join(temporary, 'generated'), legacy, selection);
        const revision = (await run('git', ['rev-parse', 'HEAD'])).trim();
        const manifest: Manifest = {
            infrastructure, connections, image: resolvedImage, useLegacyQueryParams: legacy,
            harness: { revision, sha256: harnessHash }, models: modelHashes, fixtures: fixtureHashes,
            createdAt: new Date().toISOString(), bundleKey: '',
            k6Image: prepared.k6Image ?? await k6Image(),
            k6Version: '2.3.0',
            configuration,
        };
        // Stable Compose project: repeat bootstrap replaces the same application container.
        const composeFile = path.join(temporary, 'generated/compose.json');
        await save(composeFile, { ...await json<object>(composeFile), name: 'konifer-performance' });
        await save(path.join(temporary, 'manifest.json'), manifest);
        const { application, metadata } = await deploymentArchives(temporary);
        const deploymentHash = application.hash;
        manifest.bundleKey = application.key;
        console.log('Uploading content-addressed harness, models and configuration...');
        await Promise.all([upload(infrastructure, harnessFile, harnessKey), upload(infrastructure, modelArchive, modelKey), upload(infrastructure, application.file, application.key), upload(infrastructure, metadata.file, metadata.key)]);
        const destination = `/opt/konifer-performance/deployments/${deploymentHash}`;
        for (const role of ['konifer', 'k6'] as const) {
            // CLI is installed by the initial command before S3 downloads, without operator credentials.
            const prefix = [
                'export DEBIAN_FRONTEND=noninteractive',
                'apt-get update -qq && apt-get install -y -qq curl unzip',
                'if ! command -v aws >/dev/null; then',
                '  download_dir=$(mktemp -d)',
                '  curl -fsSL --retry 3 https://awscli.amazonaws.com/awscli-exe-linux-aarch64-2.38.2.zip -o "$download_dir/aws.zip"',
                '  unzip -q "$download_dir/aws.zip" -d "$download_dir"',
                '  "$download_dir/aws/install"',
                '  rm -r -- "$download_dir"',
                'fi',
                `export AWS_DEFAULT_REGION=${shellQuote(infrastructure.region)}`,
                `mkdir -p ${shellQuote(`${destination}/performance`)}`,
            ];
            const modelCache = `/opt/konifer-performance/models/${modelHash}`;
            const downloads = [
                [harnessKey, harnessHash, `${destination}/performance`],
                role === 'konifer' ? [application.key, application.hash, destination] : [metadata.key, metadata.hash, destination],
                ...(role === 'konifer' ? [[modelKey, modelHash, modelCache]] : []),
            ];
            for (const [key, checksum, directory] of downloads) {
                if (key === modelKey) prefix.push(`if [ ! -f ${shellQuote(`${modelCache}/.ready`)} ]; then`);
                prefix.push(`mkdir -p ${shellQuote(directory)}`,
                    `aws s3 cp ${shellQuote(`s3://${infrastructure.stagingBucket}/${key}`)} ${shellQuote(`${destination}/download.tar`)} --only-show-errors`,
                    `printf '%s\\n' ${shellQuote(`${checksum}  ${destination}/download.tar`)} | sha256sum -c -`,
                    `tar -xf ${shellQuote(`${destination}/download.tar`)} -C ${shellQuote(directory)}`);
                if (key === modelKey) prefix.push(`touch ${shellQuote(`${modelCache}/.ready`)}`, 'fi',
                    `ln -sfn ${shellQuote(modelCache)} ${shellQuote(`${destination}/generated/models`)}`);
            }
            prefix.push(`bash ${shellQuote(`${destination}/performance/runtime/aws-host.sh`)} ${shellQuote(role)} ${shellQuote(destination)}`,
                `ln -sfn ${shellQuote(destination)} /opt/konifer-performance/current`);
            await remote(infrastructure, role, prefix.join('\n'));
        }
        await save(manifestFile, manifest);
        console.log('Bootstrap complete. Run aws.sh checkpoint.');
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
}

/** Write connection metadata into a private staging directory and return its filename. */
async function writeConnections(directory: string, connections: AwsConnections): Promise<string> {
    const file = path.join(directory, 'connections.json');
    await save(file, connections);
    return file;
}

/** Verify live placement/limits and an upload/download/cleanup round trip, then save non-secret evidence. */
async function checkpoint(): Promise<string> {
    await initialized();
    const manifest = await json<Manifest>(manifestFile);
    const { infrastructure, connections } = await outputs();
    if (infrastructure.instances.konifer !== manifest.infrastructure.instances.konifer || infrastructure.instances.k6 !== manifest.infrastructure.instances.k6) throw new Error('Stack changed; bootstrap it before checkpoint');
    const observed = await observe(infrastructure);
    const baseline = new Set(await objectKeys(infrastructure));
    const output = await remote(infrastructure, 'k6', [
        'cd /opt/konifer-performance/current/performance',
        `printf '%s\\n' ${shellQuote(JSON.stringify(connections))} > /opt/konifer-performance/current/connections.json`,
        'node runtime/aws-checkpoint.ts /opt/konifer-performance/current/connections.json',
    ].join('\n'));
    const runtime = await remote(infrastructure, 'konifer', [
        'cd /opt/konifer-performance/current/generated',
        // Select safe fields: docker inspect includes the password in Config.Env.
        'container=$(docker ps --filter label=com.docker.compose.project=konifer-performance --filter label=com.docker.compose.service=konifer --format \'{{.ID}}\')',
        'docker inspect "$container" --format \'{"nanoCpus":{{.HostConfig.NanoCpus}},"memory":{{.HostConfig.Memory}},"memorySwap":{{.HostConfig.MemorySwap}},"image":{{json .Config.Image}}}\'',
        'findmnt -n -r --mountpoint /mnt/konifer-scratch -o FSTYPE,SOURCE',
        'lsblk -dn -o MODEL "$(findmnt -n -r --mountpoint /mnt/konifer-scratch -o SOURCE)"',
        'secret=$(jq -er .infrastructure.secretArn ../manifest.json)',
        'export PGPASSWORD=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text | jq -er .password)',
        `export PGHOST=${shellQuote(connections.rdsEndpoint)} PGUSER=konifer_user PGDATABASE=konifer PGSSLMODE=verify-full PGSSLROOTCERT="$PWD/rds-ca-bundle.pem"`,
        'psql -X --no-password --set ON_ERROR_STOP=1 --csv -c "SELECT a.usename,a.application_name,s.ssl FROM pg_stat_activity a JOIN pg_stat_ssl s USING(pid) WHERE a.datname=current_database() AND a.pid <> pg_backend_pid();"',
        'unset PGPASSWORD',
        'docker --version && docker compose version && node --version && aws --version',
    ].join('\n'));
    const limitsLine = runtime.split('\n').find((line) => line.startsWith('{"nanoCpus"'));
    if (!limitsLine) throw new Error('Missing observed container settings');
    const limits = JSON.parse(limitsLine) as { nanoCpus: number; memory: number; memorySwap: number; image: string };
    if (limits.nanoCpus !== 4_000_000_000 || limits.memory !== 6 * 1024 ** 3 || limits.memorySwap !== limits.memory || limits.image !== manifest.image.reference) throw new Error('Observed container settings differ from profile');
    if (!runtime.includes('ext4 /dev/') || !runtime.includes('Instance Storage')) throw new Error('Scratch backing does not match profile');
    console.log('Verifying background S3 cleanup...');
    await waitForObjectCleanup(baseline, () => objectKeys(infrastructure), () => new Promise((resolve) => setTimeout(resolve, 5000)));
    const evidence = path.join(localDirectory, 'evidence', `${new Date().toISOString().replaceAll(':', '-')}.json`);
    await save(evidence, { manifest, observed, runtime, checkpoint: output, cleanup: { baselineObjects: baseline.size, additionalObjectsRemaining: 0 }, controllerSha256: await hash(fileURLToPath(import.meta.url)), completedAt: new Date().toISOString(), passed: true });
    console.log(`${output.trim()}\nEvidence: ${evidence}`);
    return evidence;
}

/** Read live AWS settings and reject drift from the benchmark's compute, database, network, and storage contract. */
async function observe(infrastructure: Infrastructure): Promise<unknown> {
    const region = infrastructure.region;
    const [instances, database, groups, endpoint, buckets] = await Promise.all([
        aws<{ Reservations: Array<{ Instances: Array<{ InstanceId: string; InstanceType: string; ImageId: string; Placement: { AvailabilityZone: string }; MetadataOptions: { HttpTokens: string; HttpPutResponseHopLimit: number }; BlockDeviceMappings: Array<{ Ebs: { VolumeId: string } }> }> }> }>(region,
            'ec2', 'describe-instances', '--instance-ids', infrastructure.instances.konifer, infrastructure.instances.k6),
        aws<{ DBInstances: Array<{ EngineVersion: string; DBInstanceClass: string; AvailabilityZone: string; PubliclyAccessible: boolean; MultiAZ: boolean; StorageEncrypted: boolean; StorageType: string; AllocatedStorage: number; DBParameterGroups: Array<{ DBParameterGroupName: string; ParameterApplyStatus: string }> }> }>(region,
            'rds', 'describe-db-instances', '--db-instance-identifier', infrastructure.databaseId),
        aws<{ SecurityGroups: Array<{ GroupId: string; IpPermissions: Array<{ FromPort?: number; ToPort?: number; IpRanges: unknown[]; Ipv6Ranges: unknown[]; UserIdGroupPairs: Array<{ GroupId: string }> }> }> }>(region,
            'ec2', 'describe-security-groups', '--group-ids', ...Object.values(infrastructure.securityGroups)),
        aws<{ VpcEndpoints: Array<{ State: string; VpcId: string; RouteTableIds: string[]; VpcEndpointType: string }> }>(region,
            'ec2', 'describe-vpc-endpoints', '--vpc-endpoint-ids', infrastructure.s3EndpointId),
        Promise.all([infrastructure.objectBucket, infrastructure.stagingBucket].map(async (bucket) => ({
            bucket,
            encryption: await aws<{ ServerSideEncryptionConfiguration: { Rules: Array<{ ApplyServerSideEncryptionByDefault: { SSEAlgorithm: string } }> } }>(region, 's3api', 'get-bucket-encryption', '--bucket', bucket),
            publicAccess: await aws<{ PublicAccessBlockConfiguration: Record<string, boolean> }>(region, 's3api', 'get-public-access-block', '--bucket', bucket),
        }))),
    ]);
    const hosts = instances.Reservations.flatMap((reservation) => reservation.Instances);
    for (const role of ['konifer', 'k6'] as const) {
        const host = hosts.find((entry) => entry.InstanceId === infrastructure.instances[role]);
        if (!host || host.InstanceType !== (role === 'konifer' ? 'c9gd.xlarge' : 'c9g.medium') || host.ImageId !== infrastructure.amiId
            || host.Placement.AvailabilityZone !== infrastructure.availabilityZone || host.MetadataOptions.HttpTokens !== 'required' || host.MetadataOptions.HttpPutResponseHopLimit !== 2) throw new Error('Observed EC2 settings differ from profile');
    }
    const volumes = await aws<{ Volumes: Array<{ Encrypted: boolean }> }>(region, 'ec2', 'describe-volumes', '--volume-ids', ...hosts.flatMap((host) => host.BlockDeviceMappings.map((device) => device.Ebs.VolumeId)));
    if (volumes.Volumes.some((volume) => !volume.Encrypted)) throw new Error('EC2 root volume is not encrypted');
    const db = database.DBInstances[0];
    if (db.EngineVersion !== '17.11' || db.DBInstanceClass !== 'db.m7g.large' || db.AvailabilityZone !== infrastructure.availabilityZone || db.PubliclyAccessible || db.MultiAZ
        || !db.StorageEncrypted || db.StorageType !== 'gp3' || db.AllocatedStorage !== 20 || db.DBParameterGroups.some((group) => group.ParameterApplyStatus !== 'in-sync')) throw new Error('Observed RDS settings differ from profile');
    const parameters = await aws<{ Parameters: Array<{ ParameterName: string; ParameterValue: string }> }>(region, 'rds', 'describe-db-parameters', '--db-parameter-group-name', db.DBParameterGroups[0].DBParameterGroupName);
    if (parameters.Parameters.find((entry) => entry.ParameterName === 'rds.force_ssl')?.ParameterValue !== '0') throw new Error('RDS does not permit historical plaintext clients');
    for (const [role, id] of Object.entries(infrastructure.securityGroups)) {
        const rules = groups.SecurityGroups.find((group) => group.GroupId === id)?.IpPermissions;
        const expectedPort = role === 'konifer' ? 8080 : role === 'database' ? 5432 : undefined;
        const source = role === 'database' ? infrastructure.securityGroups.konifer : infrastructure.securityGroups.k6;
        if (!rules || rules.length !== (expectedPort === undefined ? 0 : 1)
            || rules.some((rule) => rule.FromPort !== expectedPort || rule.ToPort !== expectedPort || rule.IpRanges.length || rule.Ipv6Ranges.length || rule.UserIdGroupPairs.length !== 1 || rule.UserIdGroupPairs[0].GroupId !== source)) throw new Error('Observed ingress differs from benchmark security groups');
    }
    const s3 = endpoint.VpcEndpoints[0];
    if (s3.State !== 'available' || s3.VpcId !== infrastructure.vpcId || s3.VpcEndpointType !== 'Gateway' || !s3.RouteTableIds.includes(infrastructure.routeTableId)) throw new Error('S3 gateway endpoint is not routed to hosts');
    for (const bucket of buckets) {
        if (Object.values(bucket.publicAccess.PublicAccessBlockConfiguration).some((blocked) => !blocked)
            || bucket.encryption.ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm !== 'AES256') throw new Error('S3 encryption/public access differs from profile');
    }
    return { instances, database, groups, endpoint, buckets, volumes };
}

/** Hold a remote controller lease across an operation; retain it if a worker may still be running. */
async function hostOperation<T>(infrastructure: Infrastructure, operation: () => Promise<T>): Promise<T> {
    // A directory lease deliberately remains after abrupt controller death. Never
    // infer that a benchmark stopped merely because the local process disappeared.
    await remote(infrastructure, 'k6', [
        'mkdir -p /opt/konifer-performance',
        "if command -v docker >/dev/null && [ -n \"$(docker ps -q --filter label=konifer.performance.run)\" ]; then echo 'A k6 container is still active; inspect it before retrying' >&2; exit 1; fi",
        "flock -n /opt/konifer-performance/runtime.lock sh -c 'mkdir /opt/konifer-performance/controller.lock' || { echo 'Remote operation active or stale controller.lock; inspect SSM before retrying' >&2; exit 1; }",
    ].join('\n'));
    try {
        return await operation();
    } finally {
        // Refuse to release the lease while an interrupted worker still owns flock.
        await remote(infrastructure, 'k6', "if command -v docker >/dev/null; then test -z \"$(docker ps -q --filter label=konifer.performance.run)\" || exit 1; fi; flock -n /opt/konifer-performance/runtime.lock rmdir /opt/konifer-performance/controller.lock").catch((error: unknown) => console.error(`Remote lease retained: ${String(error)}`));
    }
}

/** Clear only verified benchmark data, bootstrap the selected image/config, and checkpoint before permitting runs. */
async function reset(tag: string, legacy: boolean, selection: ConfigurationSelection): Promise<void> {
    // Resolve everything before stopping the application or clearing any data.
    await prepare(tag);
    const { infrastructure } = await outputs();
    await hostOperation(infrastructure, async () => {
        const script = await readFile(path.join(performanceDirectory, 'runtime/aws-reset.sh'), 'utf8');
        await remote(infrastructure, 'k6', 'touch /opt/konifer-performance/reset-incomplete');
        await remote(infrastructure, 'konifer', [
            `export KONIFER_RESET_BUCKET=${shellQuote(infrastructure.objectBucket)} KONIFER_RESET_DATABASE=${shellQuote(infrastructure.databaseId)}`,
            `printf '%s' ${shellQuote(script)} > /opt/konifer-performance/reset.sh`,
            'bash /opt/konifer-performance/reset.sh',
        ].join('\n'));
        // The host marker was removed only after all clearing succeeded. Keeping
        // reset-incomplete through startup/checkpoint prevents accidental runs.
        await rm(manifestFile, { force: true });
        await bootstrap(tag, legacy, selection);
        await checkpoint();
        await remote(infrastructure, 'konifer', 'rm -f -- /opt/konifer-performance/reset-incomplete');
        await remote(infrastructure, 'k6', 'rm -f -- /opt/konifer-performance/reset-incomplete');
    });
}

/** Reject run IDs that could escape their local or remote artifact directories. */
function assertRunId(id: string): void {
    if (!/^[A-Za-z0-9-]{1,100}$/.test(id)) throw new Error('Invalid run ID');
}

/**
 * Download and verify a run's archive, paths, request identity, and expected results.
 * Retain partial evidence without publication on operational failure; preserve
 * completed local reports and allow repeat collection of the same run.
 */
async function collect(id: string): Promise<void> {
    assertRunId(id);
    const record = await json<{ infrastructure: Infrastructure; request: RunRequest }>(path.join(localDirectory, 'runs', `${id}.json`));
    const { infrastructure, request } = record;
    const current = await initialized();
    if (current.inputs.account_id !== infrastructure.accountId || current.inputs.region !== infrastructure.region) throw new Error('Run belongs to another AWS account or region');
    const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-results-'));
    const destination = path.join(performanceDirectory, 'results', id);
    try {
        const prefix = `s3://${infrastructure.stagingBucket}/k6/results/${id}`;
        for (const file of ['results.tar', 'results.sha256']) {
            await run('aws', ['--region', infrastructure.region, 's3', 'cp', `${prefix}/${file}`, path.join(temporary, file), '--only-show-errors'], { timeout: 600_000 });
        }
        const expectedHash = (await readFile(path.join(temporary, 'results.sha256'), 'utf8')).trim();
        if (!/^[a-f0-9]{64}$/.test(expectedHash) || await hash(path.join(temporary, 'results.tar')) !== expectedHash) throw new Error('Result archive checksum mismatch; retry collection if an upload was active');
        const names = (await run('tar', ['-tf', path.join(temporary, 'results.tar')])).trim().split('\n');
        if (names.some((name) => !/^\.\/(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]*\/?$/.test(name) || name.split('/').includes('..'))) throw new Error('Unsafe result archive path');
        const listing = await run('tar', ['-tvf', path.join(temporary, 'results.tar')]);
        if (listing.trim().split('\n').some((line) => !['-', 'd'].includes(line[0]))) throw new Error('Result archive contains links or special files');
        const extracted = path.join(temporary, 'extracted');
        await mkdir(extracted);
        await run('tar', ['--no-same-owner', '-xf', path.join(temporary, 'results.tar'), '-C', extracted]);
        const remoteRequest = await json<RunRequest>(path.join(extracted, 'request.json'));
        if (JSON.stringify(remoteRequest) !== JSON.stringify(request)) throw new Error('Remote request differs from the local run record');
        // Refuse to overwrite a completed report. Repeated collect is idempotent.
        const previous: string[] = await readdir(destination, { encoding: 'utf8' }).catch((error: NodeJS.ErrnoException) => {
            if (error.code === 'ENOENT') return [] as string[];
            throw error;
        });
        if (previous.includes('report.md')) {
            await validateRun(destination, request, path.join(performanceDirectory, 'schema/result.schema.json'));
            await run('bash', [path.join(performanceDirectory, 'report.sh'), destination], { stream: true });
            console.log(`Already collected: ${destination}`);
            return;
        }
        if (previous.length) await rename(destination, `${destination}-partial-${Date.now()}`);
        await mkdir(path.dirname(destination), { recursive: true });
        await cp(extracted, destination, { recursive: true, force: false, errorOnExist: true });
        console.log(`Collected artifacts: ${destination}`);
        const status = await json<{ complete: boolean; exitCode?: number }>(path.join(destination, 'status.json'));
        if (!status.complete || status.exitCode !== 0) throw new Error(`Run incomplete or failed operationally (${status.exitCode ?? 'running'}); retained artifacts without publishing history`);
        await validateRun(destination, request, path.join(performanceDirectory, 'schema/result.schema.json'));
        await run('bash', [path.join(performanceDirectory, 'report.sh'), destination], { stream: true });
    } finally {
        await rm(temporary, { recursive: true, force: true });
    }
}

/** Orchestrate selection, deployment reuse checks, checkpoint, one remote suite, and local artifact collection. */
async function benchmark(args: string[]): Promise<void> {
    const config = await json<Parameters<typeof runRequest>[1]>(path.join(performanceDirectory, 'config/workloads.json'));
    const subject = (await run('git', ['describe', '--tags', '--always', '--dirty']).catch(() => 'working-tree')).trim();
    const id = `${new Date().toISOString().replace(/[-:.]/g, '')}-${createHash('sha256').update(String(Math.random())).digest('hex').slice(0, 8)}`;
    const request = runRequest(args, config, id, subject);
    const selection = await selectConfiguration(request.configFile, request.legacy);
    delete request.configFile;
    const prepared = await initialized();
    const resolved = await image(request.tag);
    assertSameImage(prepared.image.digest, resolved.digest);
    const { infrastructure } = await outputs();
    await hostOperation(infrastructure, async () => {
        await remote(infrastructure, 'k6', "test ! -f /opt/konifer-performance/reset-incomplete || { echo 'Reset incomplete; retry reset' >&2; exit 1; }");
        const previous = await json<Manifest>(manifestFile).catch((error: NodeJS.ErrnoException) => {
            if (error.code === 'ENOENT') return undefined;
            throw error;
        });
        if (previous) assertSameImage(previous.image.digest, resolved.digest);
        if (!request.noStart) await bootstrap(request.tag, request.legacy, selection);
        const manifest = await json<Manifest>(manifestFile);
        if (manifest.infrastructure.instances.konifer !== infrastructure.instances.konifer || manifest.useLegacyQueryParams !== request.legacy || !manifest.k6Image) throw new Error('Deployment differs from run options; bootstrap the matching deployment');
        if (request.noStart) {
            const { aws } = await loadAwsConfiguration(manifest.connections);
            const expectedConfiguration = awsConfigurationFiles(selection, aws, manifest.connections).fingerprint;
            assertSameConfiguration(manifest.configuration, expectedConfiguration);
            const temporary = await mkdtemp(path.join(os.tmpdir(), 'konifer-harness-check-'));
            try {
                const archive = path.join(temporary, 'harness.tar');
                await harnessArchive(archive, selection);
                if (await hash(archive) !== manifest.harness.sha256) throw new Error('Harness changed; omit --no-start to stage current sources');
            } finally {
                await rm(temporary, { recursive: true, force: true });
            }
        }
        // This also verifies actual AWS placement, limits and application access.
        const evidence = await checkpoint();
        const staging = await mkdtemp(path.join(os.tmpdir(), 'konifer-request-'));
        try {
            await save(path.join(staging, 'request.json'), request);
            await save(path.join(staging, 'deployment.json'), manifest);
            await save(path.join(staging, 'evidence.json'), await json(evidence));
            const archive = path.join(staging, 'request.tar');
            await run('tar', ['-cf', archive, '-C', staging, 'request.json', 'deployment.json', 'evidence.json']);
            const checksum = await hash(archive);
            const key = `k6/requests/${id}.tar`;
            await upload(infrastructure, archive, key);
            await save(path.join(localDirectory, 'runs', `${id}.json`), { infrastructure, request });
            const resultDirectory = `/opt/konifer-performance/results/${id}`;
            console.log(`AWS run ${id}; recovery: aws.sh collect --run-id ${id}`);
            let failure: unknown;
            try {
                await remote(infrastructure, 'k6', [
                    `export AWS_DEFAULT_REGION=${shellQuote(infrastructure.region)}`,
                    `export KONIFER_AWS_UPLOAD_PREFIX=${shellQuote(`s3://${infrastructure.stagingBucket}/k6/results/${id}`)}`,
                    `mkdir -p ${shellQuote(resultDirectory)}`,
                    `aws s3 cp ${shellQuote(`s3://${infrastructure.stagingBucket}/${key}`)} ${shellQuote(`${resultDirectory}/request.tar`)} --only-show-errors`,
                    `printf '%s\\n' ${shellQuote(`${checksum}  ${resultDirectory}/request.tar`)} | sha256sum -c -`,
                    `tar -xf ${shellQuote(`${resultDirectory}/request.tar`)} -C ${shellQuote(resultDirectory)}`,
                    `rm -- ${shellQuote(`${resultDirectory}/request.tar`)}`,
                    `bash /opt/konifer-performance/current/performance/runtime/aws-job.sh ${shellQuote(`${resultDirectory}/request.json`)}`,
                ].join('\n'), request.timeoutSeconds);
            } catch (error) {
                failure = error;
            }
            // Preserve a remote failure as the primary error while still attempting
            // collection so partial diagnostics remain available to the operator.
            try {
                await collect(id);
            } catch (error) {
                if (!failure) throw error;
                console.error(String(error));
            }
            if (failure) throw failure;
        } finally {
            await rm(staging, { recursive: true, force: true });
        }
    });
}

const usage = `Usage: bash performance/runtime/aws.sh COMMAND [options]
  prepare --docker-tag TAG                 Check AWS, fixtures, models and ARM64 image; pin inputs
  plan                                     Save an OpenTofu plan for the prepared account
  apply                                    Apply a freshly generated OpenTofu plan
  bootstrap --docker-tag TAG [--config FILE] [--use-legacy-query-params]
                                           Stage files and start the image on provisioned hosts
  checkpoint                               Verify deployment and upload/download from k6 host
  reset --docker-tag TAG [--config FILE] [--use-legacy-query-params]
                                           Clear benchmark data and start another image
  run smoke|release [run.sh options]        Execute benchmarks on the dedicated k6 host
  collect --run-id ID                       Download artifacts and report completed runs
  destroy                                  Destroy disposable resources and all benchmark data
Environment: AWS_PROFILE (default konifer-perf), AWS_REGION (default us-east-2),
KONIFER_AWS_AZ, KONIFER_AWS_SECONDARY_AZ, KONIFER_AWS_STACK.
Reports and history are generated locally after successful collection. State is local; back it up.`;

/** Dispatch validated lifecycle commands under a local lock; snapshot configs before reset can clear data. */
async function main(args: string[]): Promise<void> {
    const [command, ...options] = args;
    if (!command || command === '--help' || command === '-h') {
        console.log(usage);
        return;
    }
    let tag: string | undefined;
    let legacy = false;
    let runId: string | undefined;
    let configFile: string | undefined;
    const runOptions = command === 'run' ? options : [];
    const parsedOptions = command === 'run' ? [] : options;
    for (let i = 0; i < parsedOptions.length; i++) {
        if (parsedOptions[i] === '--docker-tag' && parsedOptions[i + 1]) tag = parsedOptions[++i];
        else if (parsedOptions[i] === '--run-id' && parsedOptions[i + 1]) runId = parsedOptions[++i];
        else if (parsedOptions[i] === '--config' && parsedOptions[i + 1]) configFile = parsedOptions[++i];
        else if (parsedOptions[i] === '--use-legacy-query-params') legacy = true;
        else throw new Error(`Unknown option ${parsedOptions[i]}\n${usage}`);
    }
    if (!['prepare', 'plan', 'apply', 'bootstrap', 'checkpoint', 'destroy', 'run', 'collect', 'reset'].includes(command)) throw new Error(usage);
    if (['prepare', 'bootstrap', 'reset'].includes(command) !== (tag !== undefined) || (legacy && !['bootstrap', 'reset'].includes(command)) || (command === 'collect') !== (runId !== undefined)) throw new Error(usage);
    if (configFile !== undefined && !['bootstrap', 'reset'].includes(command)) throw new Error(usage);
    // Snapshot once, before locks/preparation and before reset can clear any data.
    const selection = ['bootstrap', 'reset'].includes(command) ? await selectConfiguration(configFile, legacy) : undefined;
    process.umask(0o077);
    await mkdir(localDirectory, { recursive: true, mode: 0o700 });
    // Lock the entire workflow, not just individual tofu operations.
    const lock = path.join(localDirectory, 'operation.lock');
    try {
        await mkdir(lock);
    } catch {
        throw new Error(`Another AWS operation is active. If its process exited, remove ${lock}.`);
    }
    try {
        if (command === 'prepare') return await prepare(tag!);
        if (command === 'run') return await benchmark(runOptions);
        const prepared = await initialized();
        if (command === 'collect') return await collect(runId!);
        if (command === 'reset') return await reset(tag!, legacy, selection!);
        if (command === 'bootstrap' || command === 'checkpoint') {
            const { infrastructure } = await outputs();
            await hostOperation(infrastructure, async () => {
                if (command === 'bootstrap') {
                    await remote(infrastructure, 'k6', "test ! -f /opt/konifer-performance/reset-incomplete || { echo 'Reset incomplete; retry reset' >&2; exit 1; }");
                    await bootstrap(tag!, legacy, selection!);
                }
                else await checkpoint();
            });
            return;
        }
        await tofu(['init', '-input=false']);
        if (command === 'destroy') {
            console.log('Destroying the disposable stack, including ALL bucket objects and RDS without a final snapshot.');
            await tofu(['destroy', '-input=false', '-auto-approve', `-var-file=${inputsFile}`]);
            await rm(manifestFile, { force: true });
            await rm(planFile, { force: true });
            console.log('Stack destroyed; local evidence and state backups retained.');
            return;
        }
        await tofu(['validate']);
        await tofu(['plan', '-input=false', `-var-file=${inputsFile}`, `-out=${planFile}`]);
        await chmod(planFile, 0o600);
        if (command === 'apply') {
            await tofu(['apply', '-input=false', planFile]);
            const result = await outputs();
            if (result.infrastructure.accountId !== prepared.inputs.account_id) throw new Error('Applied account differs from preparation');
            await save(path.join(localDirectory, 'infrastructure.json'), result);
        }
    } finally {
        await rm(lock, { recursive: true, force: true });
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main(process.argv.slice(2)).catch((error: unknown) => {
        console.error(error instanceof Error ? error.message : error);
        process.exitCode = 1;
    });
}
