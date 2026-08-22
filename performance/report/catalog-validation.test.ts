import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import type { LoadCatalog } from '../k6/types.ts';
import type { EnvironmentCatalog, WorkloadCatalog, WorkloadDefinition } from './types.ts';

interface FixtureFile {
    path: string;
    mediaType: string;
    bytes: number;
    sha256: string;
}

interface FixtureManifest {
    fixtures: Record<string, {
        width: number;
        height: number;
        files: Record<string, FixtureFile>;
    }>;
}

interface ExecutableWorkload extends WorkloadDefinition {
    profile: string;
    fixture: string;
    sourceFormat?: string;
    expectedFormat?: string;
    query?: Record<string, string | number | boolean>;
    expected?: { format: string; width: number; height: number };
    cases?: Array<{
        id: string;
        sourceFormat?: string;
        destinationFormat?: string;
    }>;
}

interface ExecutableWorkloadCatalog extends Omit<WorkloadCatalog, 'workloads'> {
    profiles: Record<string, {
        preAllocatedVUs: number;
        maxVUs: number;
    }>;
    workloads: Record<string, ExecutableWorkload>;
}

const performanceDirectory = fileURLToPath(new URL('../', import.meta.url));
const assetsDirectory = path.join(performanceDirectory, 'assets');

const [workloads, load, environments, manifest] = await Promise.all([
    readJson<ExecutableWorkloadCatalog>('config/workloads.json'),
    readJson<LoadCatalog>('config/load.json'),
    readJson<EnvironmentCatalog>('config/environments.json'),
    readJson<FixtureManifest>('assets/manifest.json'),
]);

test('catalog documents match their JSON schemas', async (context) => {
    const documents = [
        ['workloads', workloads, 'schema/workloads.schema.json'],
        ['load', load, 'schema/load-config.schema.json'],
        ['environments', environments, 'schema/environments.schema.json'],
        ['fixture manifest', manifest, 'schema/manifest.schema.json'],
    ] as const;

    for (const [name, document, schemaPath] of documents) {
        await context.test(name, async () => {
            const schema = await readJson<Record<string, unknown>>(schemaPath);
            const validator = createValidator().compile(schema);
            assert.equal(validator(document), true, formatValidationErrors(validator.errors));
        });
    }
});

test('published result schemas compile successfully', async () => {
    const [result, history, loadResult, loadHistory, workloadsSchema] = await Promise.all([
        readJson<Record<string, unknown>>('schema/result.schema.json'),
        readJson<Record<string, unknown>>('schema/history.schema.json'),
        readJson<Record<string, unknown>>('schema/load-result.schema.json'),
        readJson<Record<string, unknown>>('schema/load-history.schema.json'),
        readJson<Record<string, unknown>>('schema/workloads.schema.json'),
    ]);

    for (const schema of [result, history, loadResult, workloadsSchema]) {
        assert.doesNotThrow(() => createValidator().compile(schema));
    }
    const validator = createValidator();
    validator.addSchema(loadResult);
    assert.doesNotThrow(() => validator.compile(loadHistory));
});

test('mixed load profile references executable workloads and has valid stream rates', () => {
    const profile = load.profiles[load.defaultProfile];
    assert.ok(profile, `Unknown default load profile ${load.defaultProfile}`);
    assert.equal(new Set(profile.streams.map((stream) => stream.id)).size, profile.streams.length);
    assert.equal(new Set(profile.streams.map((stream) => stream.exec)).size, profile.streams.length);
    for (const stream of profile.streams) {
        assert.ok(stream.targetRate >= stream.startRate, `${stream.id} target rate must not be below its start rate`);
        assertLoadReference(stream.workload, stream.case);
    }
    assertLoadReference(profile.recovery.workload, profile.recovery.case);
});

test('workload and suite references resolve', () => {
    for (const [suiteId, suite] of Object.entries(workloads.suites)) {
        for (const workloadId of suite.workloads) {
            assert.ok(workloads.workloads[workloadId], `${suiteId} references unknown workload ${workloadId}`);
        }
    }

    for (const [profileId, profile] of Object.entries(workloads.profiles)) {
        assert.ok(
            profile.maxVUs >= profile.preAllocatedVUs,
            `${profileId} maxVUs must be greater than or equal to preAllocatedVUs`,
        );
    }

    for (const [workloadId, workload] of Object.entries(workloads.workloads)) {
        assert.ok(workloads.profiles[workload.profile], `${workloadId} references unknown profile ${workload.profile}`);
        const fixture = manifest.fixtures[workload.fixture];
        assert.ok(fixture, `${workloadId} references unknown fixture ${workload.fixture}`);

        const caseIds = workload.cases?.map((value) => value.id) ?? [workload.case!];
        assert.equal(new Set(caseIds).size, caseIds.length, `${workloadId} contains duplicate case IDs`);

        assertFixtureFormat(workloadId, 'sourceFormat', workload.sourceFormat, fixture);
        if (typeof workload.query?.format === 'string') {
            assertFixtureFormat(workloadId, 'query.format', workload.query.format, fixture);
        }
        assertFixtureFormat(workloadId, 'expected.format', workload.expected?.format, fixture);
        assertFixtureFormat(workloadId, 'expectedFormat', workload.expectedFormat, fixture);
        for (const workloadCase of workload.cases ?? []) {
            assertFixtureFormat(`${workloadId}/${workloadCase.id}`, 'sourceFormat', workloadCase.sourceFormat, fixture);
            assertFixtureFormat(`${workloadId}/${workloadCase.id}`, 'destinationFormat', workloadCase.destinationFormat, fixture);
        }
    }
});

test('general transformations use WebP while JPEG XL stays format-specific', () => {
    assert.equal(workloads.workloads['variant.generate.cold'].query?.format, 'webp');
    assert.equal(workloads.workloads['variant.deliver.cached'].query?.format, 'webp');
    assert.equal(workloads.workloads['upload.preprocess'].expected?.format, 'webp');
    assert.equal(workloads.workloads['upload.rules.preprocess'].expected?.format, 'webp');
    assert.equal(workloads.workloads['variant.eager.ready'].expectedFormat, 'webp');

    for (const [workloadId, workload] of Object.entries(workloads.workloads)) {
        if (workloadId === 'format.encode' || workloadId === 'format.decode') continue;
        assert.notEqual(workload.query?.format, 'jxl', `${workloadId} must not transform to JXL`);
        assert.notEqual(workload.expected?.format, 'jxl', `${workloadId} must not preprocess to JXL`);
        assert.notEqual(workload.expectedFormat, 'jxl', `${workloadId} must not expect JXL variants`);
        for (const workloadCase of workload.cases ?? []) {
            assert.notEqual(workloadCase.destinationFormat, 'jxl', `${workloadId}/${workloadCase.id} must not encode JXL`);
        }
    }

    assert.ok(workloads.workloads['format.decode'].cases?.some((value) => value.sourceFormat === 'jxl'));
    assert.ok(workloads.workloads['format.encode'].cases?.some((value) => value.destinationFormat === 'jxl'));
});

test('environment references and service identities are valid', async () => {
    assert.ok(environments.profiles[environments.default], `Unknown default environment ${environments.default}`);

    for (const [environmentId, environment] of Object.entries(environments.profiles)) {
        const containers = environment.services?.map((service) => service.container) ?? [];
        assert.equal(new Set(containers).size, containers.length, `${environmentId} contains duplicate container names`);

        const composeFile = resolveWithin(performanceDirectory, environment.composeFile!);
        assert.ok((await stat(composeFile)).isFile(), `${environmentId} Compose file does not exist: ${composeFile}`);
    }
});

test('fixture files stay inside the asset directory and match manifest metadata', async () => {
    const paths = new Set<string>();

    for (const [fixtureId, fixture] of Object.entries(manifest.fixtures)) {
        for (const [format, file] of Object.entries(fixture.files)) {
            const reference = `${fixtureId}/${format}`;
            const filename = resolveWithin(assetsDirectory, file.path);
            assert.ok(!paths.has(filename), `${reference} reuses fixture path ${file.path}`);
            paths.add(filename);

            const fileStat = await stat(filename);
            assert.ok(fileStat.isFile(), `${reference} is not a file: ${file.path}`);
            assert.equal(fileStat.size, file.bytes, `${reference} byte size does not match the manifest`);
            const hash = createHash('sha256').update(await readFile(filename)).digest('hex');
            assert.equal(hash, file.sha256, `${reference} SHA-256 does not match the manifest`);
        }
    }
});

function createValidator(): Ajv2020 {
    const validator = new Ajv2020({ allErrors: true, strict: true });
    addFormats(validator);
    return validator;
}

async function readJson<T>(relativePath: string): Promise<T> {
    return JSON.parse(await readFile(path.join(performanceDirectory, relativePath), 'utf8')) as T;
}

function assertFixtureFormat(
    workloadId: string,
    field: string,
    format: string | undefined,
    fixture: FixtureManifest['fixtures'][string],
): void {
    if (!format) return;
    assert.ok(fixture.files[format], `${workloadId} ${field} references format ${format} missing from its fixture`);
}

function assertLoadReference(workloadId: string, caseId: string): void {
    const workload = workloads.workloads[workloadId];
    assert.ok(workload, `Load profile references unknown workload ${workloadId}`);
    const cases = workload.cases?.map((value) => value.id) ?? [workload.case!];
    assert.ok(cases.includes(caseId), `Load profile references unknown case ${workloadId}/${caseId}`);
}

function resolveWithin(parent: string, relativePath: string): string {
    const resolvedParent = path.resolve(parent);
    const resolved = path.resolve(resolvedParent, relativePath);
    const relative = path.relative(resolvedParent, resolved);
    assert.ok(relative !== '' && !relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative), `Path escapes ${parent}: ${relativePath}`);
    return resolved;
}

function formatValidationErrors(errors: unknown): string {
    return `JSON Schema validation failed:\n${JSON.stringify(errors, null, 2)}`;
}
