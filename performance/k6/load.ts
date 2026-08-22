import execution from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import type { Options, Scenario, Threshold } from 'k6/options';
import {
    contentType,
    entryUrl,
    header,
    logUnexpectedResponse,
    parseJson,
    requestParameters,
    safe,
    uploadAsset,
    withQuery,
} from './lib/konifer.ts';
import {
    peakRatePerMinute,
    plannedOperations,
    seedPoolSize,
    targetRatePerMinute,
    trafficDurationMilliseconds,
} from './lib/load-profile.ts';
import type {
    AssetReference,
    FixtureManifest,
    KoniferResponseBody,
    LoadCatalog,
    LoadProfile,
    LoadSetupData,
    LoadStreamDefinition,
    OperationContext,
    OperationResult,
    SummaryData,
    SummaryOutput,
    WorkloadCatalog,
    WorkloadCase,
    WorkloadReference,
} from './types.ts';

const workloadCatalog = JSON.parse(open('../config/workloads.json')) as WorkloadCatalog;
const loadCatalog = JSON.parse(open('../config/load.json')) as LoadCatalog;
const fixtures = (JSON.parse(open('../assets/manifest.json')) as FixtureManifest).fixtures;
const profileId = __ENV.LOAD_PROFILE || loadCatalog.defaultProfile;
const profile = loadCatalog.profiles[profileId];
const runId = requiredEnvironment('RUN_ID');
const baseUrl = __ENV.BASE_URL || workloadCatalog.baseUrl;

if (!profile) throw new Error(`Unknown load profile: ${profileId}`);

const fixture = fixtures['photo-medium'];
const sourceFormat = 'jpg';
if (!fixture) throw new Error('Fixture photo-medium is not configured');
const sourceFile = fixture.files[sourceFormat];
if (!sourceFile) throw new Error(`Fixture photo-medium does not contain format ${sourceFormat}`);
const sourceBytes = open(`../assets/${sourceFile.path}`, 'b');
const operationDuration = new Trend('operation_duration', true);
const operationErrors = new Rate('operation_errors');
const operations = new Counter('operations');
const recoveryHealth = new Rate('recovery_health');
const recoveryEagerReady = new Rate('recovery_eager_ready');

const contexts = Object.fromEntries(
    profile.streams.map((stream) => [stream.id, operationContext(stream)]),
) as Record<string, OperationContext>;
const coldStream = requiredStream('cold-variant');
const recoveryDefinition = workloadCatalog.workloads[profile.recovery.workload];
if (!recoveryDefinition) throw new Error(`Unknown recovery workload: ${profile.recovery.workload}`);
const recoveryContext = operationContext({
    workload: profile.recovery.workload,
    case: profile.recovery.case,
});

export const options: Options = buildOptions();

export function setup(): LoadSetupData {
    const cachedOriginal = seedAsset('cached-original', 0, false);
    const cachedVariant = seedAsset('cached-variant', 0, true);
    const cold = [];
    for (let index = 0; index < seedPoolSize(profile, coldStream); index += 1) {
        cold.push(seedAsset('cold-variant', index, false));
    }
    return { cachedOriginal, cachedVariant, cold };
}

export function cachedOriginal(data: LoadSetupData): void {
    execute('cached-original', () => runOriginalDelivery(data.cachedOriginal));
}

export function cachedVariant(data: LoadSetupData): void {
    execute('cached-variant', () => runCachedVariant(data.cachedVariant));
}

export function originalUpload(): void {
    execute('original-upload', () => runOriginalUpload(contexts['original-upload']));
}

export function coldVariant(data: LoadSetupData): void {
    execute('cold-variant', () => runColdVariant(coldAsset(data)));
}

export function eagerUpload(): void {
    execute('eager-upload', () => runEagerAcceptance(contexts['eager-upload']));
}

export function uploadRules(): void {
    execute('upload-rules', () => runUploadRules(contexts['upload-rules']));
}

export function recovery(): void {
    const health = http.get(`${baseUrl}/health`, requestParameters('health', 'health', 'health'));
    const healthPassed = check(health, { 'post-load health returned 200': (value) => value.status === 200 });
    recoveryHealth.add(healthPassed);
    const eager = runEagerReadiness(recoveryContext);
    recoveryEagerReady.add(eager.ok);
    recordOperation('recovery', { ok: healthPassed && eager.ok, durationMs: eager.durationMs });
}

function buildOptions(): Options {
    const scenarios: Record<string, Scenario> = {};
    const thresholds: Record<string, Threshold[]> = {};
    for (const stream of profile.streams) {
        scenarios[scenarioName(stream.id)] = {
            executor: 'ramping-arrival-rate',
            exec: stream.exec,
            startRate: stream.startRate,
            timeUnit: profile.timeUnit,
            preAllocatedVUs: stream.preAllocatedVUs,
            gracefulStop: '1m',
            stages: [
                { duration: profile.rampUpDuration, target: stream.targetRate },
                { duration: profile.steadyDuration, target: stream.targetRate },
                { duration: profile.rampDownDuration, target: 0 },
            ],
            tags: { phase: 'load', stream: stream.id },
        };
        addThresholds(thresholds, stream.id);
    }
    scenarios.recovery = {
        executor: 'per-vu-iterations',
        exec: 'recovery',
        vus: 1,
        iterations: 1,
        startTime: `${trafficDurationMilliseconds(profile)}ms`,
        maxDuration: profile.recoveryTimeout,
        tags: { phase: 'recovery', stream: 'recovery' },
    };
    addThresholds(thresholds, 'recovery');
    thresholds['recovery_health{stream:recovery}'] = ['rate==1'];
    thresholds['recovery_eager_ready{stream:recovery}'] = ['rate==1'];

    return {
        setupTimeout: '30m',
        scenarios,
        thresholds,
        summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    };
}

function addThresholds(thresholds: Record<string, Threshold[]>, stream: string): void {
    thresholds[`operation_duration{stream:${stream}}`] = ['p(95)>=0'];
    thresholds[`operation_errors{stream:${stream}}`] = ['rate==0'];
    thresholds[`operations{stream:${stream}}`] = ['count>0'];
    thresholds[`checks{stream:${stream}}`] = ['rate==1'];
    thresholds[`http_reqs{stream:${stream}}`] = ['count>0'];
    thresholds[`dropped_iterations{stream:${stream}}`] = ['count==0'];
}

function execute(stream: string, handler: () => OperationResult): void {
    const started = Date.now();
    let result;
    try {
        result = handler();
    } catch (error) {
        console.error(`${stream}: ${errorMessage(error)}`);
        check(null, { 'operation completed without exception': () => false });
        result = { ok: false };
    }
    recordOperation(stream, {
        ...result,
        durationMs: result.durationMs === undefined ? Date.now() - started : result.durationMs,
    });
}

function recordOperation(stream: string, result: Required<OperationResult>): void {
    const tags = { stream };
    operationDuration.add(result.durationMs, tags);
    operationErrors.add(!result.ok, tags);
    operations.add(1, tags);
}

function seedAsset(stream: string, index: number, generateVariant: boolean): AssetReference {
    const context = contexts[stream];
    if (!context) throw new Error(`Unknown load stream: ${stream}`);
    const assetPath = `/performance/${safe(runId)}/load/seed/${stream}/${index}`;
    const response = uploadAsset(context, assetPath, { phase: 'setup' });
    const body = parseJson<KoniferResponseBody>(response);
    if (response.status !== 201 || !body || body.entryId === undefined) {
        throw new Error(`Unable to seed ${stream}: HTTP ${response.status}`);
    }
    const contentUrl = entryUrl(baseUrl, assetPath, body.entryId, 'content');
    if (generateVariant) {
        const generated = http.get(withQuery(contentUrl, context.query), requestParameters(
            context.workloadId,
            context.caseId,
            'seed-variant',
            { phase: 'setup' },
        ));
        if (generated.status !== 200 || header(generated, 'K-Cache-Status') !== 'miss') {
            throw new Error(`Unable to seed cached variant for ${stream}`);
        }
    }
    return { assetPath, entryId: body.entryId, contentUrl };
}

function runOriginalDelivery(asset: AssetReference): OperationResult {
    const context = contexts['cached-original'];
    if (!context) throw new Error('Missing cached-original context');
    const response = http.get(asset.contentUrl, parameters(context));
    const ok = check(response, {
        'original returned 200': (value) => value.status === 200,
        'original was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'original content type matches fixture': (value) => contentType(value) === sourceFile.mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runCachedVariant(asset: AssetReference): OperationResult {
    const context = contexts['cached-variant'];
    if (!context) throw new Error('Missing cached-variant context');
    const response = http.get(withQuery(asset.contentUrl, context.query), parameters(context));
    const ok = check(response, {
        'cached variant returned 200': (value) => value.status === 200,
        'cached variant was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'cached variant content type matches': (value) => contentType(value) === fixture.files[String(context.query.format)].mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runOriginalUpload(context: OperationContext): OperationResult {
    const response = uploadAsset(context, uniqueAssetPath(context));
    const body = parseJson<KoniferResponseBody>(response);
    const original = body?.variants?.[0];
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload returned 201': (value) => value.status === 201,
        'upload returned one original': () => body?.variants?.length === 1 && original?.isOriginalVariant === true,
        'original format matches fixture': () => original?.attributes.format === sourceFormat,
        'original dimensions match fixture': () => original?.attributes.width === fixture.width && original.attributes.height === fixture.height,
    });
    return { ok, durationMs: response.timings.duration };
}

function runColdVariant(asset: AssetReference): OperationResult {
    const context = contexts['cold-variant'];
    if (!context) throw new Error('Missing cold-variant context');
    const response = http.get(withQuery(asset.contentUrl, context.query), parameters(context));
    const expectedType = fixture.files[String(context.query.format)].mediaType;
    const ok = check(response, {
        'cold variant returned 200': (value) => value.status === 200,
        'cold variant was a cache miss': (value) => header(value, 'K-Cache-Status') === 'miss',
        'cold variant content type matches': (value) => contentType(value) === expectedType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runEagerAcceptance(context: OperationContext): OperationResult {
    const response = uploadAsset(context, configuredAssetPath(context));
    const body = parseJson<KoniferResponseBody>(response);
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'eager upload returned 201': (value) => value.status === 201,
        'eager upload response contains only original': () => body?.variants?.length === 1 && body.variants[0]?.isOriginalVariant === true,
    });
    return { ok, durationMs: response.timings.duration };
}

function runUploadRules(context: OperationContext): OperationResult {
    const response = uploadAsset(context, configuredAssetPath(context));
    const body = parseJson<KoniferResponseBody>(response);
    const original = body?.variants?.[0];
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload rules returned 201': (value) => value.status === 201,
        'upload rule applied benchmark label': () => body?.labels?.['performance-rule'] === 'matched',
        'upload rules returned one original': () => body?.variants?.length === 1 && original?.isOriginalVariant === true,
        'upload rules original dimensions match': () => original?.attributes.width === fixture.width && original.attributes.height === fixture.height,
    });
    return { ok, durationMs: response.timings.duration };
}

function runEagerReadiness(context: OperationContext): Required<OperationResult> {
    const assetPath = configuredAssetPath(context);
    const upload = uploadAsset(context, assetPath);
    const body = parseJson<KoniferResponseBody>(upload);
    let ok = check(upload, {
        'recovery eager upload returned 201': (value) => value.status === 201,
        'recovery eager upload returned entry': () => body?.entryId !== undefined,
    });
    if (!ok) return { ok: false, durationMs: upload.timings.duration };

    const started = Date.now();
    const recoveryProfiles = recoveryDefinition.profiles ?? [];
    const entryId = body?.entryId;
    if (entryId === undefined) return { ok: false, durationMs: upload.timings.duration };
    const expectedVariantCount = recoveryProfiles.length + 1;
    const infoUrl = entryUrl(baseUrl, assetPath, entryId, 'info');
    let ready = false;
    while ((Date.now() - started) / 1000 < (recoveryDefinition.timeoutSeconds ?? 0)) {
        const info = http.get(infoUrl, parameters(context, 'eager-info'));
        const infoBody = parseJson<KoniferResponseBody>(info);
        ready = info.status === 200 && infoBody?.variants?.length === expectedVariantCount;
        if (ready) break;
        sleep(0.1);
    }
    const durationMs = Date.now() - started;
    ok = check(ready, { 'all recovery eager variants became ready': (value) => value });
    for (const variantProfile of recoveryProfiles) {
        const response = http.get(
            withQuery(entryUrl(baseUrl, assetPath, entryId, 'content'), { profile: variantProfile }),
            parameters(context, 'eager-verify'),
        );
        ok = check(response, {
            [`recovery ${variantProfile} returned 200`]: (value) => value.status === 200,
            [`recovery ${variantProfile} was a cache hit`]: (value) => header(value, 'K-Cache-Status') === 'hit',
        }) && ok;
    }
    return { ok, durationMs };
}

function operationContext(stream: WorkloadReference): OperationContext {
    const definition = workloadCatalog.workloads[stream.workload];
    if (!definition) throw new Error(`Unknown workload in load profile: ${stream.workload}`);
    const selectedCase: WorkloadCase | undefined = definition.cases
        ? definition.cases.find((value) => value.id === stream.case)
        : definition.case ? { id: definition.case } : undefined;
    if (!selectedCase || selectedCase.id !== stream.case) {
        throw new Error(`Unknown case in load profile: ${stream.workload}/${stream.case}`);
    }
    const selectedSourceFormat = selectedCase.sourceFormat || definition.sourceFormat;
    if (!selectedSourceFormat) {
        throw new Error(`Load case does not define a source format: ${stream.workload}/${stream.case}`);
    }
    return {
        baseUrl,
        workloadId: stream.workload,
        caseId: stream.case,
        workload: definition,
        query: {
            ...(definition.query || {}),
            ...(selectedCase.destinationFormat ? { format: selectedCase.destinationFormat } : {}),
        },
        sourceBytes,
        sourceFormat: selectedSourceFormat,
        sourceFile,
    };
}

function parameters(context: OperationContext, operation = context.workloadId) {
    return requestParameters(context.workloadId, context.caseId, operation);
}

function coldAsset(data: LoadSetupData): AssetReference {
    const index = execution.scenario.iterationInTest;
    if (index >= data.cold.length) {
        throw new Error(`Cold seed pool exhausted at iteration ${index}; available=${data.cold.length}`);
    }
    return data.cold[index];
}

function uniqueAssetPath(context: OperationContext): string {
    return `/performance/${safe(runId)}/load/${safe(context.workloadId)}/${execution.scenario.name}/${execution.vu.idInTest}-${execution.scenario.iterationInTest}`;
}

function configuredAssetPath(context: OperationContext): string {
    if (!context.workload.path) throw new Error(`Workload ${context.workloadId} does not define a path`);
    return `${context.workload.path}${uniqueAssetPath(context)}`;
}

function scenarioName(stream: string): string {
    return stream.replaceAll('-', '_');
}

function metricValues(data: SummaryData, name: string): Record<string, number | undefined> {
    return (data.metrics[name] && data.metrics[name].values) || {};
}

function integer(value: number | undefined): number {
    return Math.round(Number(value || 0));
}

function streamResult(data: SummaryData, stream: LoadStreamDefinition) {
    const duration = metricValues(data, `operation_duration{stream:${stream.id}}`);
    const operationValues = metricValues(data, `operations{stream:${stream.id}}`);
    const errorValues = metricValues(data, `operation_errors{stream:${stream.id}}`);
    const checkValues = metricValues(data, `checks{stream:${stream.id}}`);
    const requestValues = metricValues(data, `http_reqs{stream:${stream.id}}`);
    const droppedValues = metricValues(data, `dropped_iterations{stream:${stream.id}}`);
    const result = {
        id: stream.id,
        workload: stream.workload,
        case: stream.case,
        targetRatePerMinute: targetRatePerMinute(profile, stream),
        plannedOperations: plannedOperations(profile, stream),
        operations: integer(operationValues.count),
        requests: integer(requestValues.count),
        errors: integer(errorValues.passes),
        checks: { passed: integer(checkValues.passes), failed: integer(checkValues.fails) },
        droppedIterations: integer(droppedValues.count),
        durationMs: {
            p50: Number(duration.med || 0),
            p90: Number(duration['p(90)'] || 0),
            p95: Number(duration['p(95)'] || 0),
            p99: Number(duration['p(99)'] || 0),
        },
        passed: false,
    };
    result.passed = result.operations > 0
        && result.requests > 0
        && result.errors === 0
        && result.checks.failed === 0
        && result.droppedIterations === 0;
    return result;
}

export function handleSummary(data: SummaryData): SummaryOutput {
    const streams = profile.streams.map((stream) => streamResult(data, stream));
    const targetOperations = streams.reduce((total, stream) => total + stream.plannedOperations, 0);
    const recoveryDuration = metricValues(data, 'operation_duration{stream:recovery}');
    const recoveryChecks = metricValues(data, 'checks{stream:recovery}');
    const recoveryErrors = metricValues(data, 'operation_errors{stream:recovery}');
    const health = metricValues(data, 'recovery_health{stream:recovery}');
    const eager = metricValues(data, 'recovery_eager_ready{stream:recovery}');
    const recoveryResult = {
        healthPassed: integer(health.fails) === 0 && integer(health.passes) > 0,
        eagerReady: integer(eager.fails) === 0 && integer(eager.passes) > 0,
        durationMs: Number(recoveryDuration.med || 0),
        checks: { passed: integer(recoveryChecks.passes), failed: integer(recoveryChecks.fails) },
        passed: integer(recoveryErrors.passes) === 0
            && integer(recoveryChecks.fails) === 0
            && integer(health.passes) > 0
            && integer(health.fails) === 0
            && integer(eager.passes) > 0
            && integer(eager.fails) === 0,
    };
    const result = {
        version: 1,
        runId,
        profile: profileId,
        environment: __ENV.ENVIRONMENT || 'unspecified',
        subject: __ENV.SUBJECT || 'working-tree',
        startedAt: __ENV.STARTED_AT || new Date().toISOString(),
        completedAt: new Date().toISOString(),
        trafficDurationSeconds: trafficDurationMilliseconds(profile) / 1000,
        peakRatePerMinute: peakRatePerMinute(profile, profile.streams),
        targetOperations,
        operations: streams.reduce((total, value) => total + value.operations, 0),
        requests: streams.reduce((total, value) => total + value.requests, 0),
        errors: streams.reduce((total, value) => total + value.errors, 0),
        checks: {
            passed: streams.reduce((total, value) => total + value.checks.passed, 0),
            failed: streams.reduce((total, value) => total + value.checks.failed, 0),
        },
        droppedIterations: streams.reduce((total, value) => total + value.droppedIterations, 0),
        streams,
        recovery: recoveryResult,
        passed: streams.every((value) => value.passed) && recoveryResult.passed,
    };
    return {
        [requiredEnvironment('RAW_RESULT_PATH')]: JSON.stringify(data, null, 2),
        [requiredEnvironment('RESULT_PATH')]: JSON.stringify(result, null, 2),
        stdout: `${profileId}: operations=${result.operations}/${result.targetOperations} errors=${result.errors} dropped=${result.droppedIterations} passed=${result.passed}\n`,
    };
}

function requiredEnvironment(name: string): string {
    const value = __ENV[name];
    if (!value) throw new Error(`Missing required environment variable: ${name}`);
    return value;
}

function requiredStream(id: string): LoadStreamDefinition {
    const stream = profile.streams.find((value) => value.id === id);
    if (!stream) throw new Error(`Load profile ${profileId} does not define stream ${id}`);
    return stream;
}

function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}
