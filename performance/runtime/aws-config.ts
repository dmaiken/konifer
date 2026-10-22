import { mkdir, readFile, realpath, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { configurationHash, selectConfiguration, type ConfigurationFingerprint, type ConfigurationSelection } from './configuration.ts';
import type { AwsEnvironment, EnvironmentCatalog } from '../report/types.ts';

export interface AwsConnections {
    environment: string;
    region: string;
    availabilityZone: string;
    koniferPrivateIp: string;
    k6PrivateIp: string;
    rdsEndpoint: string;
    bucketName: string;
}

const performanceDirectory = fileURLToPath(new URL('../', import.meta.url));

/** Poll readiness with bounded retries; use historical /health only after a modern 404. */
export async function waitForAwsReadiness(baseUrl: string, attempts = 60, delayMs = 1000): Promise<string> {
    let endpoint = '/health/ready';
    for (let attempt = 0; attempt < attempts; attempt++) {
        try {
            const response = await fetch(`${baseUrl}${endpoint}`, { redirect: 'error', signal: AbortSignal.timeout(3000) });
            await response.arrayBuffer();
            if (response.ok) return endpoint;
            // Early releases exposed /health only. Never bypass a failing modern probe.
            if (response.status === 404 && endpoint === '/health/ready') endpoint = '/health';
        } catch { /* Application may still be starting. */ }
        if (attempt + 1 < attempts) await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    throw new Error(`Konifer did not become ready at ${endpoint}`);
}

/** Validate schemas, private host placement, and resource budgets before generating deployment files. */
export async function loadAwsConfiguration(connections: unknown): Promise<{ aws: AwsEnvironment; connections: AwsConnections }> {
    const [catalog, environmentSchema, connectionSchema] = await Promise.all([
        readJson('config/environments.json') as Promise<EnvironmentCatalog>,
        readJson('schema/environments.schema.json'),
        readJson('schema/aws-connections.schema.json'),
    ]);
    const ajv = new Ajv2020({ allErrors: true, strict: true });
    addFormats(ajv);
    for (const [schema, value] of [[environmentSchema, catalog], [connectionSchema, connections]] as const) {
        const validate = ajv.compile(schema);
        if (!validate(value)) throw new Error(`Invalid AWS configuration: ${ajv.errorsText(validate.errors)}`);
    }
    const resolved = connections as AwsConnections;
    const profile = catalog.profiles[resolved.environment];
    if (profile?.runtime !== 'aws' || !profile.aws) throw new Error(`Not an AWS environment: ${resolved.environment}`);
    if (resolved.availabilityZone.slice(0, -1) !== resolved.region) throw new Error('Availability zone must belong to the configured region');
    for (const ip of [resolved.koniferPrivateIp, resolved.k6PrivateIp]) {
        if (!isPrivateIpv4(ip)) throw new Error('Benchmark instance addresses must be private IPv4 addresses');
    }
    if (resolved.koniferPrivateIp === resolved.k6PrivateIp) throw new Error('Konifer and k6 must use separate instances');
    for (const instance of [profile.aws.konifer, profile.aws.k6]) {
        if (instance.container.cpus > instance.vcpus || instance.container.memoryBytes >= instance.memoryGiB * 1024 ** 3) {
            throw new Error('Container limits must fit the instance and leave memory for the host');
        }
    }
    return { aws: profile.aws, connections: resolved };
}

export interface AwsConfigurationFingerprint extends ConfigurationFingerprint {
    rootSha256: string;
    overridesSha256: string;
    effectiveSha256: string;
}

/** Compose the complete source with a final AWS override layer using Konifer's HOCON parser. */
export function awsKoniferConfiguration(): string {
    // Native HOCON handles braces, object merging, substitutions and property order.
    return `# Generated: the environment owns connection settings; the source owns application behavior.
include required("konifer.source.conf")
include required("konifer.aws.conf")
`;
}

/**
 * Override environment-owned connections and mask stale Garage credentials/TLS settings.
 * Keep application behavior in the source; provide the database secret at startup.
 */
export function awsKoniferOverrides(aws: AwsEnvironment, connections: AwsConnections): string {
    const quote = JSON.stringify;
    return `# Generated AWS connections. Nulls mask stale local connection values.
data-store {
  provider = postgresql
  postgresql {
    host = ${quote(connections.rdsEndpoint)}
    port = 5432
    user = ${quote(aws.rds.user)}
    database = ${quote(aws.rds.database)}
    password = null
    ssl-mode = "disable"
    ssl-root-cert = null
  }
}
object-store {
  provider = s3
  s3 {
    region = ${quote(connections.region)}
    force-path-style = false
    endpoint-url = null
    access-key = null
    secret-key = null
  }
}
http.public-url = ${quote(`http://${connections.koniferPrivateIp}:8080`)}
paths."/**".object-store.bucket = ${quote(connections.bucketName)}
`;
}

/** Build the three config files and hash their composition, without resolving runtime secrets. */
export function awsConfigurationFiles(selection: ConfigurationSelection, aws: AwsEnvironment, connections: AwsConnections): { files: Record<string, string>; fingerprint: AwsConfigurationFingerprint } {
    const root = awsKoniferConfiguration();
    const overrides = awsKoniferOverrides(aws, connections);
    const files = { 'konifer.conf': root, 'konifer.source.conf': selection.application, 'konifer.aws.conf': overrides };
    return {
        files,
        fingerprint: {
            ...selection.fingerprint, rootSha256: configurationHash(root), overridesSha256: configurationHash(overrides),
            effectiveSha256: configurationHash(JSON.stringify(files)),
        },
    };
}

/** Reject --no-start when either the selected source or generated composition has changed. */
export function assertSameConfiguration(previous: AwsConfigurationFingerprint | undefined, next: AwsConfigurationFingerprint): void {
    if (!previous || previous.sourceSha256 !== next.sourceSha256 || previous.effectiveSha256 !== next.effectiveSha256) {
        throw new Error('Konifer configuration changed; omit --no-start to deploy the selected configuration');
    }
}

/** Define the ARM64 application container with pinned limits, private binding, and read-only config/models. */
export function awsComposeConfiguration(aws: AwsEnvironment, connections: AwsConnections): object {
    return {
        services: {
            konifer: {
                image: '${KONIFER_IMAGE:?Set an ARM64 Konifer image}',
                platform: 'linux/arm64',
                ports: [`${connections.koniferPrivateIp}:8080:8080`],
                cpus: aws.konifer.container.cpus,
                mem_limit: aws.konifer.container.memoryBytes,
                memswap_limit: aws.konifer.container.memoryBytes,
                environment: {
                    PG_PASSWORD: '${PG_PASSWORD:?Fetch the benchmark database password before starting}',
                    AWS_REGION: connections.region,
                },
                volumes: [
                    './konifer.conf:/app/config/konifer.conf:ro',
                    './konifer.source.conf:/app/config/konifer.source.conf:ro',
                    './konifer.aws.conf:/app/config/konifer.aws.conf:ro',
                    './models:/app/models:ro',
                    `${aws.scratch.hostPath}:${aws.scratch.containerPath}`,
                ],
                logging: { driver: 'json-file', options: { 'max-size': '10m', 'max-file': '3' } },
            },
        },
    };
}

/**
 * Write configuration, Compose, and deployment metadata into the staging directory.
 * Reuse a supplied snapshot or select the default; reject output aliases that could
 * overwrite the input, including symlinks/hardlinks. Creates no AWS resources.
 */
export async function generateAwsConfiguration(connectionsFile: string, outputDirectory: string, legacy = false, selection?: ConfigurationSelection): Promise<AwsConfigurationFingerprint> {
    const { aws, connections } = await loadAwsConfiguration(JSON.parse(await readFile(connectionsFile, 'utf8')));
    const selected = selection ?? await selectConfiguration(undefined, legacy);
    const { files, fingerprint } = awsConfigurationFiles(selected, aws, connections);
    await mkdir(outputDirectory, { recursive: true });
    // The standalone generator must not overwrite its own input, even through
    // a symlink/hardlink. Deployment generation normally uses a fresh temp dir.
    const sourcePath = await realpath(selected.path).catch(() => selected.path);
    const sourceInfo = await stat(sourcePath).catch(() => undefined);
    const outputPath = await realpath(outputDirectory);
    for (const name of [...Object.keys(files), 'compose.json', 'deployment.json']) {
        const target = path.join(outputDirectory, name);
        const targetPath = await realpath(target).catch(() => path.join(outputPath, name));
        const targetInfo = await stat(target).catch(() => undefined);
        if (targetPath === sourcePath || (sourceInfo && targetInfo && sourceInfo.dev === targetInfo.dev && sourceInfo.ino === targetInfo.ino)) {
            throw new Error('Output directory would overwrite the selected configuration; choose a separate directory');
        }
    }
    await Promise.all([
        ...Object.entries(files).map(([name, contents]) => writeFile(path.join(outputDirectory, name), contents)),
        writeFile(path.join(outputDirectory, 'compose.json'), `${JSON.stringify(awsComposeConfiguration(aws, connections), null, 2)}\n`),
        writeFile(path.join(outputDirectory, 'deployment.json'), `${JSON.stringify({ connections, aws, useLegacyQueryParams: legacy, configuration: fingerprint }, null, 2)}\n`),
    ]);
    return fingerprint;
}

/** Read a JSON resource relative to the harness, independent of the invocation directory. */
async function readJson(relativePath: string): Promise<object> {
    return JSON.parse(await readFile(path.join(performanceDirectory, relativePath), 'utf8')) as object;
}

/** Check RFC 1918 membership after the connection schema has validated IPv4 syntax. */
function isPrivateIpv4(ip: string): boolean {
    const [a, b] = ip.split('.').map(Number);
    return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const [connectionsFile, outputDirectory, ...options] = process.argv.slice(2);
    let configFile: string | undefined;
    let legacy = false;
    for (let index = 0; index < options.length; index++) {
        if (options[index] === '--config' && options[index + 1]) configFile = options[++index];
        else if (options[index] === '--use-legacy-query-params') legacy = true;
        else throw new Error(`Unknown configuration option: ${options[index]}`);
    }
    if (!connectionsFile || !outputDirectory) throw new Error('Usage: node runtime/aws-config.ts CONNECTIONS.json OUTPUT_DIRECTORY [--config FILE] [--use-legacy-query-params]');
    await generateAwsConfiguration(connectionsFile, outputDirectory, legacy, await selectConfiguration(configFile, legacy));
    console.log(`AWS configuration written to ${path.resolve(outputDirectory)}; no resources were created.`);
}
