import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import Ajv2020 from 'ajv/dist/2020.js';
import { assertSameConfiguration, awsComposeConfiguration, awsConfigurationFiles, awsKoniferConfiguration, awsKoniferOverrides, generateAwsConfiguration, loadAwsConfiguration } from '../runtime/aws-config.ts';
import { selectConfiguration } from '../runtime/configuration.ts';
import { legacyConfiguration } from '../lib/query-parameters.ts';

const connections = JSON.parse(await readFile(new URL('../runtime/aws-connections.example.json', import.meta.url), 'utf8'));
const source = await readFile(new URL('../runtime/konifer.conf', import.meta.url), 'utf8');
const { aws } = await loadAwsConfiguration(connections);

test('AWS composes the complete source with last-applied connection overrides', async () => {
    const selected = await selectConfiguration();
    const composition = awsConfigurationFiles(selected, aws, connections);
    assert.equal(composition.files['konifer.source.conf'], source);
    assert.equal(composition.files['konifer.conf'], awsKoniferConfiguration());
    assert.ok(composition.files['konifer.conf'].indexOf('konifer.source.conf') < composition.files['konifer.conf'].indexOf('konifer.aws.conf'));
    const overrides = awsKoniferOverrides(aws, connections);
    assert.match(overrides, /paths\."\/\*\*"\.object-store.bucket = "replace-me-konifer-performance"/);
    assert.match(overrides, /ssl-mode = "disable"/);
    for (const property of ['ssl-root-cert', 'password', 'access-key', 'secret-key', 'endpoint-url']) assert.ok(overrides.includes(`${property} = null`));
    assert.doesNotMatch(overrides, /perf-postgres|perf-garage/);
    assert.equal(awsConfigurationFiles(await selectConfiguration(undefined, true), aws, connections).files['konifer.source.conf'], legacyConfiguration(source));
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
    for (const file of ['konifer.conf', 'konifer.source.conf', 'konifer.aws.conf']) {
        assert.ok((service.volumes as string[]).includes(`./${file}:/app/config/${file}:ro`));
    }
    assert.deepEqual(Object.keys(service.environment as object).sort(), ['AWS_REGION', 'PG_PASSWORD']);
    assert.doesNotMatch(JSON.stringify(compose), /AWS_ACCESS_KEY|AWS_SECRET|S3_SECRET|\/\.aws(?:\/|:)|tmpfs|rds-ca-bundle|ssl-root-cert/);
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
        { ...profile, aws: { ...profile.aws, rds: { ...profile.aws.rds, sslMode: 'verify-full' } } },
        { ...profile, aws: { ...profile.aws, rds: { ...profile.aws.rds, sslMode: 'require' } } },
        { ...profile, aws: { ...profile.aws, rds: { ...profile.aws.rds, forceSsl: true } } },
        { ...profile, aws: { ...profile.aws, rds: { ...profile.aws.rds, sslRootCert: '/app/config/rds-ca-bundle.pem' } } },
    ]) {
        assert.equal(validate({ default: 'test', profiles: { test: invalid } }), false);
    }
});

test('generator writes usable files without changing the canonical Compose configuration', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'konifer-aws-config-'));
    try {
        const fingerprint = await generateAwsConfiguration(new URL('../runtime/aws-connections.example.json', import.meta.url).pathname, directory, true);
        assert.equal(await readFile(path.join(directory, 'konifer.conf'), 'utf8'), awsKoniferConfiguration());
        assert.equal(await readFile(path.join(directory, 'konifer.source.conf'), 'utf8'), legacyConfiguration(source));
        const deployment = JSON.parse(await readFile(path.join(directory, 'deployment.json'), 'utf8'));
        assert.equal(deployment.useLegacyQueryParams, true);
        assert.equal(deployment.connections.environment, 'aws-c9gd-v1');
        assert.deepEqual(deployment.configuration, fingerprint);
        assert.deepEqual(JSON.parse(await readFile(path.join(directory, 'compose.json'), 'utf8')), awsComposeConfiguration(aws, connections));
        assert.equal(await readFile(new URL('../runtime/konifer.conf', import.meta.url), 'utf8'), source);
    } finally {
        await rm(directory, { recursive: true, force: true });
    }
});

test('custom configs need no rule-definitions section and are not legacy-rewritten', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'konifer-custom-config-'));
    const file = path.join(directory, 'custom config.conf');
    const custom = '{ "before": 1, "width": 42, "after": { "preserved": true } }';
    try {
        await writeFile(file, custom);
        const selection = await selectConfiguration(file, true);
        const fingerprint = await generateAwsConfiguration(new URL('../runtime/aws-connections.example.json', import.meta.url).pathname, path.join(directory, 'generated'), true, selection);
        assert.equal(await readFile(path.join(directory, 'generated/konifer.source.conf'), 'utf8'), custom);
        assert.equal(await readFile(file, 'utf8'), custom);
        assert.equal(fingerprint.legacyTranslated, false);
        assert.equal(fingerprint.sourceSha256, fingerprint.applicationSha256);
        assertSameConfiguration(fingerprint, fingerprint);
        assert.throws(() => assertSameConfiguration(undefined, fingerprint), /configuration changed/);
        await writeFile(file, `${custom}\n`);
        const changed = awsConfigurationFiles(await selectConfiguration(file, true), aws, connections).fingerprint;
        assert.throws(() => assertSameConfiguration(fingerprint, changed), /configuration changed/);
        const changedEnvironment = awsConfigurationFiles(selection, aws, { ...connections, bucketName: 'different-benchmark-bucket' }).fingerprint;
        assert.throws(() => assertSameConfiguration(fingerprint, changedEnvironment), /configuration changed/);
        assert.doesNotMatch(JSON.stringify(fingerprint), /preserved|"width"/);
    } finally { await rm(directory, { recursive: true, force: true }); }
});

test('standalone generator refuses to overwrite the supplied config', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'konifer-config-no-overwrite-'));
    const file = path.join(directory, 'konifer.conf');
    const contents = 'custom.property = original';
    try {
        await writeFile(file, contents);
        await assert.rejects(generateAwsConfiguration(new URL('../runtime/aws-connections.example.json', import.meta.url).pathname, directory, false, await selectConfiguration(file)), /overwrite the selected configuration/);
        assert.equal(await readFile(file, 'utf8'), contents);
    } finally { await rm(directory, { recursive: true, force: true }); }
});
