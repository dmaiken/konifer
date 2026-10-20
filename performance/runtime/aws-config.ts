import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { legacyConfiguration } from '../lib/query-parameters.ts';
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

export function awsKoniferConfiguration(source: string, aws: AwsEnvironment, connections: AwsConnections, legacy = false): string {
    // Reuse only the workload sections, not Compose endpoints or Garage credentials.
    const workloadStart = source.indexOf('\nrule-definitions {');
    if (workloadStart === -1) throw new Error('Shared benchmark rule-definitions section is missing');
    const quote = JSON.stringify;
    const configuration = `# Generated AWS connections; benchmark rules come from runtime/konifer.conf.
data-store {
  provider = postgresql
  postgresql {
    host = ${quote(connections.rdsEndpoint)}
    port = 5432
    user = ${quote(aws.rds.user)}
    database = ${quote(aws.rds.database)}
    ssl-mode = ${quote(aws.rds.sslMode)}
    ssl-root-cert = ${quote(aws.rds.sslRootCert)}
  }
}
object-store {
  provider = s3
  s3 {
    region = ${quote(connections.region)}
    force-path-style = false
  }
}
http.public-url = ${quote(`http://${connections.koniferPrivateIp}:8080`)}
paths."/**".object-store.bucket = ${quote(connections.bucketName)}
${source.slice(workloadStart)}`;
    return legacy ? legacyConfiguration(configuration) : configuration;
}

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
                    './rds-ca-bundle.pem:/app/config/rds-ca-bundle.pem:ro',
                    './models:/app/models:ro',
                    `${aws.scratch.hostPath}:${aws.scratch.containerPath}`,
                ],
                logging: { driver: 'json-file', options: { 'max-size': '10m', 'max-file': '3' } },
            },
        },
    };
}

export async function generateAwsConfiguration(connectionsFile: string, outputDirectory: string, legacy = false): Promise<void> {
    const { aws, connections } = await loadAwsConfiguration(JSON.parse(await readFile(connectionsFile, 'utf8')));
    const source = await readFile(path.join(performanceDirectory, 'runtime/konifer.conf'), 'utf8');
    await mkdir(outputDirectory, { recursive: true });
    await Promise.all([
        writeFile(path.join(outputDirectory, 'konifer.conf'), awsKoniferConfiguration(source, aws, connections, legacy)),
        writeFile(path.join(outputDirectory, 'compose.json'), `${JSON.stringify(awsComposeConfiguration(aws, connections), null, 2)}\n`),
        writeFile(path.join(outputDirectory, 'deployment.json'), `${JSON.stringify({ connections, aws, useLegacyQueryParams: legacy }, null, 2)}\n`),
    ]);
}

async function readJson(relativePath: string): Promise<object> {
    return JSON.parse(await readFile(path.join(performanceDirectory, relativePath), 'utf8')) as object;
}

function isPrivateIpv4(ip: string): boolean {
    const [a, b] = ip.split('.').map(Number);
    return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const [connectionsFile, outputDirectory, flag] = process.argv.slice(2);
    if (!connectionsFile || !outputDirectory || (flag !== undefined && flag !== '--use-legacy-query-params') || process.argv.length > 5) {
        throw new Error('Usage: node runtime/aws-config.ts CONNECTIONS.json OUTPUT_DIRECTORY [--use-legacy-query-params]');
    }
    await generateAwsConfiguration(connectionsFile, outputDirectory, flag !== undefined);
    console.log(`AWS configuration written to ${path.resolve(outputDirectory)}; no resources were created.`);
}
