import execution from 'k6/execution';
import http from 'k6/http';
import { check as k6Check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import type { Options } from 'k6/options';
import { durationMilliseconds, runBenchmarkPhase, validatePhase } from '../lib/benchmark-profile.ts';
import {
    contentType,
    entryUrl,
    header,
    logUnexpectedResponse,
    parseJson,
    requestParameters as buildRequestParameters,
    safe,
    uploadAsset as postAsset,
    withQuery,
} from './lib/konifer.ts';
import type {
    AssetPools,
    AssetReference,
    FixtureManifest,
    KoniferResponseBody,
    OperationResult,
    QueryParameters,
    SummaryData,
    SummaryOutput,
    Tags,
    WorkloadCatalog,
    WorkloadCase,
    WorkloadDefinition,
} from './types.ts';

const catalog = JSON.parse(open('../config/workloads.json')) as WorkloadCatalog;
const fixtures = (JSON.parse(open('../assets/manifest.json')) as FixtureManifest).fixtures;

const workloadId = requiredEnvironment('WORKLOAD');
const suite = __ENV.SUITE || 'smoke';
const runId = requiredEnvironment('RUN_ID');
const workload = catalog.workloads[workloadId];

if (!workload) {
    throw new Error(`Unknown workload: ${workloadId}`);
}
const profile = catalog.profiles[workload.profile];
if (!profile) throw new Error(`Unknown execution profile: ${workload.profile}`);
const measurementSamples = suite === 'smoke' ? Number(__ENV.SMOKE_ITERATIONS || 1) : profile.measurement.samples;
if (!Number.isInteger(measurementSamples) || measurementSamples < 1) {
    throw new Error('Measurement samples must be a positive integer');
}

const selectedCase = selectCase(workload, __ENV.CASE);
const caseId = requiredString(selectedCase.id || workload.case, `Workload ${workloadId} does not define a case`);
const sourceFormat = requiredString(
    selectedCase.sourceFormat || workload.sourceFormat,
    `Workload ${workloadId}/${caseId} does not define a source format`,
);
const destinationFormat = selectedCase.destinationFormat;
const fixture = fixtures[workload.fixture];

if (!fixture) {
    throw new Error(`Unknown fixture: ${workload.fixture}`);
}

const sourceFile = fixture.files[sourceFormat];
if (!sourceFile) {
    throw new Error(`Fixture ${workload.fixture} does not contain format ${sourceFormat}`);
}

const sourceBytes = open(`../assets/${sourceFile.path}`, 'b');
const baseUrl = __ENV.BASE_URL || catalog.baseUrl;
const query: QueryParameters = {
    ...(workload.query || {}),
    ...(destinationFormat ? { format: destinationFormat } : {}),
};

const operationDuration = new Trend('operation_duration', true);
const operationErrors = new Rate('operation_errors');
const operations = new Counter('operations');
const phaseCompleted = new Counter('phase_completed');
const phaseDuration = new Trend('phase_duration', true);
let activePhase = 'setup';
let operationIndex = 0;
let phaseDeadline: number | null = null;
/** Attach the active phase to correctness checks so setup/pacing checks cannot contaminate measurement metrics. */
const check: typeof k6Check = (value, checks, tags) => k6Check(value, checks, { ...tags, phase: activePhase });

export const options: Options = buildOptions();

/** Seed phase-isolated assets before measurement; cold operations receive one unused asset per sample. */
export function setup(): AssetPools {
    if (suite === 'smoke') {
        return seedForPhases(0, measurementSamples);
    }

    return seedForPhases(
        profile.warmup.samples,
        profile.measurement.samples,
    );
}

/** Run a smoke operation or sequential warmup/measurement phases on one VU, with explicit phase deadlines. */
export default function measurement(data: AssetPools): void {
    if (suite === 'smoke') {
        activePhase = 'measurement';
        operationIndex = execution.scenario.iterationInTest;
        executeWorkload(data, 'measurement');
        return;
    }
    for (const phase of ['warmup', 'measurement'] as const) {
        activePhase = phase;
        phaseDeadline = Date.now() + durationMilliseconds(profile[phase].maxDuration);
        const result = runBenchmarkPhase(profile[phase], {
            now: Date.now,
            sleep: (milliseconds) => sleep(milliseconds / 1000),
            run: (index) => {
                operationIndex = index;
                return executeWorkload(data, phase);
            },
        });
        phaseDuration.add(result.durationMs, { phase });
        phaseCompleted.add(result.completed ? 1 : 0, { phase });
        console.log(`${phase}: ${result.operations}/${profile[phase].samples} operations in ${result.durationMs} ms; completed=${result.completed}`);
        if (!result.completed) return;
    }
}

/** Configure single-VU execution and correctness/completion thresholds, isolating measurement metrics from setup. */
function buildOptions(): Options {
    const thresholds: Record<string, string[]> = {
        'operation_duration{phase:measurement}': ['p(95)>=0'],
        'operation_errors{phase:measurement}': ['rate==0'],
        'operations{phase:measurement}': [`count==${measurementSamples}`],
        'checks{phase:measurement}': ['rate==1'],
        'http_reqs{phase:measurement}': ['count>0'],
        'dropped_iterations{phase:measurement}': ['count==0'],
    };

    if (suite === 'smoke') {
        return {
            setupTimeout: '5m',
            scenarios: {
                measurement: {
                    executor: 'shared-iterations',
                    exec: 'default',
                    vus: 1,
                    iterations: measurementSamples,
                    maxDuration: '2m',
                    tags: { phase: 'measurement' },
                },
            },
            thresholds,
            summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
        };
    }

    validatePhase(profile.warmup);
    validatePhase(profile.measurement);
    thresholds['phase_completed{phase:warmup}'] = ['count==1'];
    thresholds['phase_completed{phase:measurement}'] = ['count==1'];
    for (const phase of ['warmup', 'measurement'] as const) {
        thresholds[`phase_duration{phase:${phase}}`] = [
            `min>=${durationMilliseconds(profile[phase].minDuration)}`,
            `max<${durationMilliseconds(profile[phase].maxDuration)}`,
        ];
    }
    thresholds['operations{phase:warmup}'] = [`count==${profile.warmup.samples}`];
    thresholds['checks{phase:warmup}'] = ['rate==1'];
    thresholds['operation_errors{phase:warmup}'] = ['rate==0'];

    return {
        setupTimeout: '30m',
        scenarios: {
            measurement: {
                executor: 'per-vu-iterations',
                vus: 1,
                iterations: 1,
                maxDuration: `${durationMilliseconds(profile.warmup.maxDuration) + durationMilliseconds(profile.measurement.maxDuration)}ms`,
                gracefulStop: '0s',
            },
        },
        thresholds,
        summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    };
}

/** Choose fresh pools for cold operations, one reusable asset for cache hits, or no seeds for upload workloads. */
function seedForPhases(warmupCount: number, measurementCount: number): AssetPools {
    if (requiresColdPool()) {
        return {
            warmup: seedAssets('warmup', warmupCount, false),
            measurement: seedAssets('measurement', measurementCount, false),
        };
    }

    if (workloadId === 'delivery.original.cached') {
        return {
            warmup: seedAssets('warmup', warmupCount > 0 ? 1 : 0, false),
            measurement: seedAssets('measurement', 1, false),
        };
    }

    if (workloadId === 'variant.deliver.cached') {
        return {
            warmup: seedAssets('warmup', warmupCount > 0 ? 1 : 0, true),
            measurement: seedAssets('measurement', 1, true),
        };
    }

    return { warmup: [], measurement: [] };
}

/** Upload phase-scoped fixtures outside measurement; pre-generate variants only for cached-delivery cases. */
function seedAssets(phase: keyof AssetPools, count: number, generateVariant: boolean): AssetReference[] {
    const assets: AssetReference[] = [];
    for (let index = 0; index < count; index += 1) {
        const assetPath = `/performance/${safe(runId)}/seed/${safe(workloadId)}/${safe(caseId)}/${phase}/${index}`;
        const response = uploadAsset(assetPath, { phase: 'setup', operation: 'seed-upload' });
        const body = parseJson<KoniferResponseBody>(response);
        if (response.status !== 201 || !body || body.entryId === undefined) {
            throw new Error(`Unable to seed ${workloadId}/${caseId}: HTTP ${response.status}`);
        }

        const contentUrl = entryUrl(baseUrl, assetPath, body.entryId, 'content');
        if (generateVariant) {
            const generated = http.get(withQuery(contentUrl, query), {
                tags: { phase: 'setup', operation: 'seed-variant' },
            });
            if (generated.status !== 200 || header(generated, 'K-Cache-Status') !== 'miss') {
                throw new Error(`Unable to seed cached variant for ${workloadId}/${caseId}`);
            }
        }

        assets.push({ assetPath, entryId: body.entryId, contentUrl });
    }
    return assets;
}

/** Run one operation and record phase-tagged latency, errors, and count; convert handler exceptions into failed checks. */
function executeWorkload(data: AssetPools, phase: keyof AssetPools): boolean {
    const started = Date.now();
    let result;
    try {
        result = runHandler(data, phase);
    } catch (error) {
        console.error(`${workloadId}/${caseId}: ${errorMessage(error)}`);
        check(null, { 'operation completed without exception': () => false });
        result = { ok: false };
    }

    const durationMs = result.durationMs === undefined ? Date.now() - started : result.durationMs;
    operationDuration.add(durationMs, { phase });
    operationErrors.add(!result.ok, { phase });
    operations.add(1, { phase });
    return result.ok;
}

/** Dispatch the selected workload against the appropriate phase pool; fail for unimplemented catalog entries. */
function runHandler(data: AssetPools, phase: keyof AssetPools): OperationResult {
    switch (workloadId) {
        case 'upload.original':
            return runOriginalUpload();
        case 'delivery.original.cached':
            return runOriginalDelivery(assetForIteration(data, phase, true));
        case 'variant.generate.cold':
        case 'format.decode':
        case 'format.encode':
            return runColdVariant(assetForIteration(data, phase, false));
        case 'variant.deliver.cached':
            return runCachedVariant(assetForIteration(data, phase, true));
        case 'upload.preprocess':
            return runPreprocessedUpload();
        case 'upload.rules':
            return runUploadRules(false);
        case 'upload.rules.preprocess':
            return runUploadRules(true);
        case 'upload.eager.accept':
            return runEagerAcceptance();
        case 'variant.eager.ready':
            return runEagerReadiness();
        default:
            throw new Error(`No handler implemented for ${workloadId}`);
    }
}

/** Measure upload HTTP latency and verify the returned original's metadata and entry location. */
function runOriginalUpload(): OperationResult {
    const assetPath = uniqueAssetPath();
    const response = uploadAsset(assetPath);
    const body = parseJson<KoniferResponseBody>(response);
    const original = body?.variants?.[0];
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload returned 201': (value) => value.status === 201,
        'upload returned one original': () => body?.variants?.length === 1 && original?.isOriginalVariant === true,
        'original format matches fixture': () => original?.attributes.format === sourceFormat,
        'original dimensions match fixture': () => original?.attributes.width === fixture.width && original.attributes.height === fixture.height,
        'location targets returned entry': (value) => body?.entryId !== undefined && header(value, 'Location').includes(`/-/entry/${body.entryId}`),
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure original delivery from a seeded asset and require a cache hit with the fixture's media type. */
function runOriginalDelivery(asset: AssetReference): OperationResult {
    const response = http.get(asset.contentUrl, requestParameters());
    const ok = check(response, {
        'original returned 200': (value) => value.status === 200,
        'original was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'original content type matches fixture': (value) => contentType(value) === sourceFile.mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure transformation of an unused original and require a cache miss and the requested output format. */
function runColdVariant(asset: AssetReference): OperationResult {
    const response = http.get(withQuery(asset.contentUrl, query), requestParameters());
    logUnexpectedResponse(response, 200);
    const expectedFormat = destinationFormat || String(query.format);
    const expectedType = fixture.files[expectedFormat].mediaType;
    const ok = check(response, {
        'cold variant returned 200': (value) => value.status === 200,
        'cold variant was a cache miss': (value) => header(value, 'K-Cache-Status') === 'miss',
        'cold variant content type matches': (value) => contentType(value) === expectedType,
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure delivery of a pre-generated variant and require a cache hit and expected media type. */
function runCachedVariant(asset: AssetReference): OperationResult {
    const response = http.get(withQuery(asset.contentUrl, query), requestParameters());
    const ok = check(response, {
        'cached variant returned 200': (value) => value.status === 200,
        'cached variant was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'cached variant content type matches': (value) => contentType(value) === fixture.files[String(query.format)].mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure upload latency while verifying the configured preprocessing output. */
function runPreprocessedUpload(): OperationResult {
    const response = uploadAsset(ruleAssetPath());
    const body = parseJson<KoniferResponseBody>(response);
    const original = body?.variants?.[0];
    const expected = requiredExpectedImage();
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'preprocessed upload returned 201': (value) => value.status === 201,
        'preprocessed original format matches': () => original?.attributes.format === expected.format,
        'preprocessed original width matches': () => original?.attributes.width === expected.width,
        'preprocessed original height matches': () => original?.attributes.height === expected.height,
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure a rule-matching upload and validate its label and original, with optional preprocessing expectations. */
function runUploadRules(expectPreprocessing: boolean): OperationResult {
    const response = uploadAsset(ruleAssetPath());
    const body = parseJson<KoniferResponseBody>(response);
    const original = body?.variants?.[0];
    const expected = expectPreprocessing
        ? requiredExpectedImage()
        : { format: sourceFormat, width: fixture.width, height: fixture.height };
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload rules returned 201': (value) => value.status === 201,
        'upload rule applied benchmark label': () => body?.labels?.['performance-rule'] === 'matched',
        'upload rules returned one original': () => body?.variants?.length === 1 && original?.isOriginalVariant === true,
        'upload rules original format matches': () => original?.attributes.format === expected.format,
        'upload rules original width matches': () => original?.attributes.width === expected.width,
        'upload rules original height matches': () => original?.attributes.height === expected.height,
    });
    return { ok, durationMs: response.timings.duration };
}

/** Measure acceptance HTTP latency; wait under pacing tags for background variants before the next upload. */
function runEagerAcceptance(): OperationResult {
    const assetPath = ruleAssetPath();
    const response = uploadAsset(assetPath);
    const body = parseJson<KoniferResponseBody>(response);
    logUnexpectedResponse(response, 201);
    let ok = check(response, {
        'eager upload returned 201': (value) => value.status === 201,
        'eager upload response contains only original': () => body?.variants?.length === 1 && body.variants[0]?.isOriginalVariant === true,
        'eager location targets returned entry': (value) => body?.entryId !== undefined && header(value, 'Location').includes(`/-/entry/${body.entryId}`),
    });
    if (ok && body?.entryId !== undefined) {
        const previousPhase = activePhase;
        activePhase = 'pacing';
        try {
            const ready = waitForEagerVariants(
                entryUrl(baseUrl, assetPath, body.entryId, 'info'),
                (workload.profiles?.length ?? 0) + 1,
            );
            ok = check(ready, { 'eager variants finished before the next upload': (value) => value }) && ok;
        } finally {
            activePhase = previousPhase;
        }
    }
    return { ok, durationMs: response.timings.duration };
}

/** Measure post-upload eager readiness time, then verify the generated profiles through cache-hit downloads. */
function runEagerReadiness(): OperationResult {
    const assetPath = ruleAssetPath();
    const upload = uploadAsset(assetPath);
    const body = parseJson<KoniferResponseBody>(upload);
    let ok = check(upload, {
        'eager readiness upload returned 201': (value) => value.status === 201,
        'eager readiness upload returned entry': () => body?.entryId !== undefined,
    });
    if (!ok) {
        return { ok: false };
    }

    const readinessStarted = Date.now();
    const eagerProfiles = workload.profiles ?? [];
    const entryId = body?.entryId;
    if (entryId === undefined) return { ok: false };
    const expectedVariantCount = eagerProfiles.length + 1;
    const infoUrl = entryUrl(baseUrl, assetPath, entryId, 'info');
    const ready = waitForEagerVariants(infoUrl, expectedVariantCount);
    const readinessDuration = Date.now() - readinessStarted;
    ok = check(ready, { 'all eager variants became ready': (value) => value });

    for (const profile of eagerProfiles) {
        const response = http.get(withQuery(entryUrl(baseUrl, assetPath, entryId, 'content'), { profile }), requestParameters('eager-verify'));
        ok = check(response, {
            [`${profile} returned 200`]: (value) => value.status === 200,
            [`${profile} was a cache hit`]: (value) => header(value, 'K-Cache-Status') === 'hit',
            [`${profile} content type matches`]: (value) => contentType(value) === fixture.files[workload.expectedFormat ?? ''].mediaType,
        }) && ok;
    }
    return { ok, durationMs: readinessDuration };
}

/** Poll entry metadata until all variants exist or the workload/phase deadline expires. */
function waitForEagerVariants(infoUrl: string, expectedVariantCount: number): boolean {
    const deadline = Math.min(Date.now() + (workload.timeoutSeconds ?? 0) * 1000, phaseDeadline ?? Infinity);
    while (Date.now() < deadline) {
        const info = http.get(infoUrl, {
            ...requestParameters('eager-info'),
            timeout: `${Math.max(1, deadline - Date.now())}ms`,
        });
        const body = parseJson<KoniferResponseBody>(info);
        if (info.status === 200 && body?.variants?.length === expectedVariantCount) return true;
        sleep(0.1);
    }
    return false;
}

/** Upload the selected fixture with the active phase tag and remaining phase timeout. */
function uploadAsset(assetPath: string, tags?: Tags) {
    return postAsset({
        baseUrl,
        sourceBytes,
        sourceFormat,
        sourceFile,
        workloadId,
        caseId,
    }, assetPath, { phase: activePhase, ...tags }, requestTimeout());
}

/** Build phase-tagged request options and cap HTTP timeout at the remaining phase duration. */
function requestParameters(operation = workloadId, extraTags?: Tags) {
    const timeoutMs = requestTimeout();
    return {
        ...buildRequestParameters(workloadId, caseId, operation, { phase: activePhase, ...extraTags }),
        ...(timeoutMs === undefined ? {} : { timeout: `${timeoutMs}ms` }),
    };
}

/** Return remaining phase milliseconds, clamped to one; leave smoke requests on the k6 default timeout. */
function requestTimeout(): number | undefined {
    return phaseDeadline === null ? undefined : Math.max(1, phaseDeadline - Date.now());
}

/** Reuse a cached seed or consume the indexed cold seed; reject empty or exhausted phase pools. */
function assetForIteration(data: AssetPools, phase: keyof AssetPools, reuse: boolean): AssetReference {
    const assets = data[phase];
    if (!assets || assets.length === 0) {
        throw new Error(`No ${phase} seed data available`);
    }
    if (reuse) {
        return assets[0];
    }
    const index = operationIndex;
    if (index >= assets.length) {
        throw new Error(`Seed pool exhausted at iteration ${index}; available=${assets.length}`);
    }
    return assets[index];
}

/** Namespace uploads by run, workload, case, scenario, phase, VU, and operation to prevent collisions. */
function uniqueAssetPath(): string {
    return `/performance/${safe(runId)}/${safe(workloadId)}/${safe(caseId)}/${execution.scenario.name}/${activePhase}/${execution.vu.idInTest}-${operationIndex}`;
}

/** Prefix a unique upload path with the configured rule-matching path; reject workloads without one. */
function ruleAssetPath(): string {
    if (!workload.path) throw new Error(`Workload ${workloadId} does not define a path`);
    return `${workload.path}${uniqueAssetPath()}`;
}

/** Resolve the requested or default case for single- and multi-case workloads, rejecting unknown IDs. */
function selectCase(selectedWorkload: WorkloadDefinition, requestedCase?: string): WorkloadCase {
    if (!selectedWorkload.cases) {
        if (requestedCase && requestedCase !== selectedWorkload.case) {
            throw new Error(`Unknown case ${requestedCase} for ${workloadId}`);
        }
        return {
            id: requiredString(
                selectedWorkload.case,
                `Workload ${workloadId} does not define a case`,
            ),
        };
    }
    const id = requestedCase || selectedWorkload.cases[0].id;
    const value = selectedWorkload.cases.find((item) => item.id === id);
    if (!value) {
        throw new Error(`Unknown case ${id} for ${workloadId}`);
    }
    return value;
}

/** Identify transformation workloads that must consume a fresh original for each operation. */
function requiresColdPool(): boolean {
    return workloadId === 'variant.generate.cold' || workloadId === 'format.decode' || workloadId === 'format.encode';
}

/** Read a required k6 environment value or fail before running an incomplete benchmark. */
function requiredEnvironment(name: string): string {
    const value = __ENV[name];
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

/** Read one k6 summary metric's values, using an empty object when k6 omitted the metric. */
function metricValues(data: SummaryData, name: string): Record<string, number | undefined> {
    return (data.metrics[name] && data.metrics[name].values) || {};
}

/** Normalize optional summary counters to whole numbers, treating missing values as zero. */
function integer(value: number | undefined): number {
    return Math.round(Number(value || 0));
}

/** Emit raw and normalized artifacts using measurement-only metrics; require phase completion for non-smoke runs. */
export function handleSummary(data: SummaryData): SummaryOutput {
    const duration = metricValues(data, 'operation_duration{phase:measurement}');
    const operationValues = metricValues(data, 'operations{phase:measurement}');
    const errorValues = metricValues(data, 'operation_errors{phase:measurement}');
    const checkValues = metricValues(data, 'checks{phase:measurement}');
    const requestValues = metricValues(data, 'http_reqs{phase:measurement}');
    const droppedValues = metricValues(data, 'dropped_iterations{phase:measurement}');

    const result = {
        version: 1,
        runId,
        suite,
        workload: workloadId,
        case: caseId,
        repetition: Number(__ENV.REPETITION || 1),
        environment: __ENV.ENVIRONMENT || 'unspecified',
        subject: __ENV.SUBJECT || 'working-tree',
        startedAt: __ENV.STARTED_AT || new Date().toISOString(),
        completedAt: new Date().toISOString(),
        ...(suite === 'smoke' ? {} : {
            benchmark: { methodology: catalog.methodology, ...profile },
        }),
        metrics: {
            operations: integer(operationValues.count),
            requests: integer(requestValues.count),
            errors: integer(errorValues.passes),
            checks: {
                passed: integer(checkValues.passes),
                failed: integer(checkValues.fails),
            },
            droppedIterations: integer(droppedValues.count),
            durationMs: {
                min: Number(duration.min || 0),
                p50: Number(duration.med || 0),
                p90: Number(duration['p(90)'] || 0),
                p95: Number(duration['p(95)'] || 0),
                p99: Number(duration['p(99)'] || 0),
                max: Number(duration.max || 0),
            },
        },
        passed: integer(operationValues.count) === measurementSamples
            && (suite === 'smoke' || (integer(metricValues(data, 'phase_completed{phase:warmup}').count) === 1
                && integer(metricValues(data, 'phase_completed{phase:measurement}').count) === 1))
            && integer(requestValues.count) > 0
            && integer(errorValues.passes) === 0
            && integer(checkValues.fails) === 0
            && integer(droppedValues.count) === 0,
    };

    return {
        [requiredEnvironment('RAW_RESULT_PATH')]: JSON.stringify(data, null, 2),
        [requiredEnvironment('RESULT_PATH')]: JSON.stringify(result, null, 2),
        stdout: `${workloadId}/${caseId}: operations=${result.metrics.operations} p95=${result.metrics.durationMs.p95.toFixed(2)}ms passed=${result.passed}\n`,
    };
}

/** Require expected image metadata for preprocessing correctness checks. */
function requiredExpectedImage() {
    if (!workload.expected) throw new Error(`Workload ${workloadId} does not define expected image metadata`);
    return workload.expected;
}

/** Convert thrown values into a printable benchmark diagnostic. */
function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

/** Require a nonempty catalog field, reporting the caller's context on failure. */
function requiredString(value: string | undefined, message: string): string {
    if (!value) throw new Error(message);
    return value;
}
