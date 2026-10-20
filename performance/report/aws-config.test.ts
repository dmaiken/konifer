import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import Ajv2020 from 'ajv/dist/2020.js';
import { awsComposeConfiguration, awsKoniferConfiguration, generateAwsConfiguration, loadAwsConfiguration } from '../runtime/aws-config.ts';
import { legacyConfiguration } from '../lib/query-parameters.ts';

const connections = JSON.parse(await readFile(new URL('../runtime/aws-connections.example.json', import.meta.url), 'utf8'));
const source = await readFile(new URL('../runtime/konifer.conf', import.meta.url), 'utf8');
const { aws } = await loadAwsConfiguration(connections);

test('AWS configuration preserves workload rules without local endpoints or credentials', () => {
    const configuration = awsKoniferConfiguration(source, aws, connections);
    const workloadSections = source.slice(source.indexOf('\nrule-definitions {'));
    assert.ok(configuration.endsWith(workloadSections));
    assert.match(configuration, /paths\."\/\*\*"\.object-store.bucket = "replace-me-konifer-performance"/);
    assert.match(configuration, /ssl-mode = "verify-full"/);
    assert.match(configuration, /ssl-root-cert = "\/app\/config\/rds-ca-bundle.pem"/);
    assert.doesNotMatch(configuration, /perf-postgres|perf-garage|access-key|secret-key|endpoint-url|password/);
    assert.equal(awsKoniferConfiguration(source, aws, connections, true), legacyConfiguration(configuration));
    assert.match(awsKoniferConfiguration(source, aws, connections, true), /\n    w = 200/);
    assert.throws(() => awsKoniferConfiguration('', aws, connections), /rule-definitions/);
});

test('AWS deployment has only Konifer, fixed limits, private binding, NVMe and no static AWS credentials', () => {
    const compose = awsComposeConfiguration(aws, connections) as { services: Record<string, Record<string, unknown>> };
    assert.deepEqual(Object.keys(compose.services), ['konifer']);
    const service = compose.services.konifer;
    assert.equal(service.platform, 'linux/arm64');
    assert.equal(service.cpus, 4);
    assert.equal(service.mem_limit, 6 * 1024 ** 3);
    assert.equal(service.memswap_limit, service.mem_limit);
    assert.deepEqual(service.ports, ['10.0.1.10:8080:8080']);
    assert.ok((service.volumes as string[]).includes('/mnt/konifer-scratch:/app/tmp'));
    assert.deepEqual(Object.keys(service.environment as object).sort(), ['AWS_REGION', 'PG_PASSWORD']);
    assert.doesNotMatch(JSON.stringify(compose), /AWS_ACCESS_KEY|AWS_SECRET|S3_SECRET|\.aws|tmpfs/);
});

test('connections reject public or shared hosts, wrong AZ, local profiles, and embedded credentials', async () => {
    for (const invalid of [
        { ...connections, koniferPrivateIp: '8.8.8.8' },
        { ...connections, k6PrivateIp: connections.koniferPrivateIp },
        { ...connections, availabilityZone: 'us-west-2a' },
        { ...connections, environment: 'local-compose-v4' },
        { ...connections, password: 'not-allowed' },
        { ...connections, rdsEndpoint: 'localhost' },
        { ...connections, bucketName: 'injected"\n' },
        { ...connections, koniferPrivateIp: '10.999.1.1' },
    ]) {
        await assert.rejects(loadAwsConfiguration(invalid));
    }
});

test('environment schema keeps legacy Compose profiles valid and rejects hybrid or incomplete profiles', async () => {
    const schema = JSON.parse(await readFile(new URL('../schema/environments.schema.json', import.meta.url), 'utf8'));
    const catalog = JSON.parse(await readFile(new URL('../config/environments.json', import.meta.url), 'utf8'));
    const validate = new Ajv2020({ strict: true }).compile(schema);
    assert.equal(validate(catalog), true);
    const profile = catalog.profiles['aws-c9gd-v1'];
    for (const invalid of [
        { ...profile, composeFile: 'runtime/docker-compose.yml' },
        { ...profile, runtime: 'compose' },
        { ...profile, aws: undefined },
        { ...profile, aws: { ...profile.aws, scratch: { ...profile.aws.scratch, type: 'tmpfs' } } },
        { ...profile, aws: { ...profile.aws, rds: { ...profile.aws.rds, publiclyAccessible: true } } },
    ]) {
        assert.equal(validate({ default: 'test', profiles: { test: invalid } }), false);
    }
});

test('generator writes usable files without changing the canonical Compose configuration', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'konifer-aws-config-'));
    try {
        await generateAwsConfiguration(new URL('../runtime/aws-connections.example.json', import.meta.url).pathname, directory, true);
        assert.equal(await readFile(path.join(directory, 'konifer.conf'), 'utf8'), awsKoniferConfiguration(source, aws, connections, true));
        const deployment = JSON.parse(await readFile(path.join(directory, 'deployment.json'), 'utf8'));
        assert.equal(deployment.useLegacyQueryParams, true);
        assert.equal(deployment.connections.environment, 'aws-c9gd-v1');
        assert.deepEqual(JSON.parse(await readFile(path.join(directory, 'compose.json'), 'utf8')), awsComposeConfiguration(aws, connections));
        assert.equal(await readFile(new URL('../runtime/konifer.conf', import.meta.url), 'utf8'), source);
    } finally {
        await rm(directory, { recursive: true, force: true });
    }
});
