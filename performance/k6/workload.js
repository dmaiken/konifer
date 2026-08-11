import execution from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const catalog = JSON.parse(open('../config/workloads.json'));
const fixtures = JSON.parse(open('../assets/manifest.json')).fixtures;

const workloadId = requiredEnvironment('WORKLOAD');
const suite = __ENV.SUITE || 'smoke';
const runId = requiredEnvironment('RUN_ID');
const workload = catalog.workloads[workloadId];

if (!workload) {
    throw new Error(`Unknown workload: ${workloadId}`);
}

const selectedCase = selectCase(workload, __ENV.CASE);
const caseId = selectedCase.id || workload.case;
const sourceFormat = selectedCase.sourceFormat || workload.sourceFormat;
const destinationFormat = selectedCase.destinationFormat;
const fixture = fixtures[workload.fixture];

if (!caseId) {
    throw new Error(`Workload ${workloadId} does not define a case`);
}
if (!fixture) {
    throw new Error(`Unknown fixture: ${workload.fixture}`);
}
if (!sourceFormat) {
    throw new Error(`Workload ${workloadId}/${caseId} does not define a source format`);
}

const sourceFile = fixture.files[sourceFormat];
if (!sourceFile) {
    throw new Error(`Fixture ${workload.fixture} does not contain format ${sourceFormat}`);
}

const sourceBytes = open(`../assets/${sourceFile.path}`, 'b');
const baseUrl = __ENV.BASE_URL || catalog.baseUrl;
const query = {
    ...(workload.query || {}),
    ...(destinationFormat ? { format: destinationFormat } : {}),
};

const metadata = JSON.stringify({
    alt: 'Performance benchmark fixture',
    tags: ['performance', 'benchmark'],
    labels: { suite: 'konifer-performance' },
});

const operationDuration = new Trend('operation_duration', true);
const operationErrors = new Rate('operation_errors');
const operations = new Counter('operations');

export const options = buildOptions();

export function setup() {
    if (suite === 'smoke') {
        return seedForPhases(0, 1);
    }

    const profile = catalog.profiles[workload.profile];
    return seedForPhases(
        expectedIterations(profile.warmup),
        expectedIterations(profile.measurement),
    );
}

export function warmup(data) {
    executeWorkload(data, 'warmup');
}

export default function measurement(data) {
    executeWorkload(data, 'measurement');
}

function buildOptions() {
    const thresholds = {
        'operation_duration{phase:measurement}': ['p(95)>=0'],
        'operation_errors{phase:measurement}': ['rate==0'],
        'operations{phase:measurement}': ['count>0'],
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
                    iterations: Number(__ENV.SMOKE_ITERATIONS || 1),
                    maxDuration: '2m',
                    tags: { phase: 'measurement' },
                },
            },
            thresholds,
            summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
        };
    }

    const profile = catalog.profiles[workload.profile];
    if (!profile) {
        throw new Error(`Unknown execution profile: ${workload.profile}`);
    }

    return {
        setupTimeout: '30m',
        scenarios: {
            warmup: arrivalScenario(profile, profile.warmup, '0s', 'warmup'),
            measurement: arrivalScenario(
                profile,
                profile.measurement,
                profile.warmup.duration,
                'default',
            ),
        },
        thresholds,
        summaryTrendStats: ['min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    };
}

function arrivalScenario(profile, phase, startTime, execFunction) {
    return {
        executor: 'constant-arrival-rate',
        exec: execFunction,
        startTime,
        duration: phase.duration,
        rate: phase.rate,
        timeUnit: phase.timeUnit,
        preAllocatedVUs: profile.preAllocatedVUs,
        maxVUs: profile.maxVUs,
        tags: { phase: execFunction === 'default' ? 'measurement' : 'warmup' },
    };
}

function seedForPhases(warmupCount, measurementCount) {
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

function seedAssets(phase, count, generateVariant) {
    const assets = [];
    for (let index = 0; index < count; index += 1) {
        const assetPath = `/performance/${safe(runId)}/seed/${safe(workloadId)}/${safe(caseId)}/${phase}/${index}`;
        const response = uploadAsset(assetPath, { phase: 'setup', operation: 'seed-upload' });
        const body = parseJson(response);
        if (response.status !== 201 || !body || body.entryId === undefined) {
            throw new Error(`Unable to seed ${workloadId}/${caseId}: HTTP ${response.status}`);
        }

        const contentUrl = entryUrl(assetPath, body.entryId, 'content');
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

function executeWorkload(data, phase) {
    const started = Date.now();
    let result;
    try {
        result = runHandler(data, phase);
    } catch (error) {
        console.error(`${workloadId}/${caseId}: ${error.message}`);
        check(null, { 'operation completed without exception': () => false });
        result = { ok: false };
    }

    const durationMs = result.durationMs === undefined ? Date.now() - started : result.durationMs;
    operationDuration.add(durationMs);
    operationErrors.add(!result.ok);
    operations.add(1);
}

function runHandler(data, phase) {
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

function runOriginalUpload() {
    const assetPath = uniqueAssetPath();
    const response = uploadAsset(assetPath);
    const body = parseJson(response);
    const original = body && body.variants && body.variants[0];
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload returned 201': (value) => value.status === 201,
        'upload returned one original': () => body && Array.isArray(body.variants) && body.variants.length === 1 && original.isOriginalVariant,
        'original format matches fixture': () => original && original.attributes.format === sourceFormat,
        'original dimensions match fixture': () => original && original.attributes.width === fixture.width && original.attributes.height === fixture.height,
        'location targets returned entry': (value) => body && header(value, 'Location').includes(`/-/entry/${body.entryId}`),
    });
    return { ok, durationMs: response.timings.duration };
}

function runOriginalDelivery(asset) {
    const response = http.get(asset.contentUrl, requestParameters());
    const ok = check(response, {
        'original returned 200': (value) => value.status === 200,
        'original was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'original content type matches fixture': (value) => contentType(value) === sourceFile.mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runColdVariant(asset) {
    const response = http.get(withQuery(asset.contentUrl, query), requestParameters());
    const expectedFormat = destinationFormat || query.format;
    const expectedType = fixture.files[expectedFormat].mediaType;
    const ok = check(response, {
        'cold variant returned 200': (value) => value.status === 200,
        'cold variant was a cache miss': (value) => header(value, 'K-Cache-Status') === 'miss',
        'cold variant content type matches': (value) => contentType(value) === expectedType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runCachedVariant(asset) {
    const response = http.get(withQuery(asset.contentUrl, query), requestParameters());
    const ok = check(response, {
        'cached variant returned 200': (value) => value.status === 200,
        'cached variant was a cache hit': (value) => header(value, 'K-Cache-Status') === 'hit',
        'cached variant content type matches': (value) => contentType(value) === fixture.files[query.format].mediaType,
    });
    return { ok, durationMs: response.timings.duration };
}

function runPreprocessedUpload() {
    const response = uploadAsset(ruleAssetPath());
    const body = parseJson(response);
    const original = body && body.variants && body.variants[0];
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'preprocessed upload returned 201': (value) => value.status === 201,
        'preprocessed original format matches': () => original && original.attributes.format === workload.expected.format,
        'preprocessed original width matches': () => original && original.attributes.width === workload.expected.width,
        'preprocessed original height matches': () => original && original.attributes.height === workload.expected.height,
    });
    return { ok, durationMs: response.timings.duration };
}

function runUploadRules(expectPreprocessing) {
    const response = uploadAsset(ruleAssetPath());
    const body = parseJson(response);
    const original = body && body.variants && body.variants[0];
    const expected = expectPreprocessing
        ? workload.expected
        : { format: sourceFormat, width: fixture.width, height: fixture.height };
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'upload rules returned 201': (value) => value.status === 201,
        'upload rule applied benchmark label': () => body && body.labels && body.labels['performance-rule'] === 'matched',
        'upload rules returned one original': () => body && Array.isArray(body.variants) && body.variants.length === 1 && original.isOriginalVariant,
        'upload rules original format matches': () => original && original.attributes.format === expected.format,
        'upload rules original width matches': () => original && original.attributes.width === expected.width,
        'upload rules original height matches': () => original && original.attributes.height === expected.height,
    });
    return { ok, durationMs: response.timings.duration };
}

function runEagerAcceptance() {
    const response = uploadAsset(ruleAssetPath());
    const body = parseJson(response);
    logUnexpectedResponse(response, 201);
    const ok = check(response, {
        'eager upload returned 201': (value) => value.status === 201,
        'eager upload response contains only original': () => body && Array.isArray(body.variants) && body.variants.length === 1 && body.variants[0].isOriginalVariant,
        'eager location targets returned entry': (value) => body && header(value, 'Location').includes(`/-/entry/${body.entryId}`),
    });
    return { ok, durationMs: response.timings.duration };
}

function runEagerReadiness() {
    const assetPath = ruleAssetPath();
    const upload = uploadAsset(assetPath);
    const body = parseJson(upload);
    let ok = check(upload, {
        'eager readiness upload returned 201': (value) => value.status === 201,
        'eager readiness upload returned entry': () => body && body.entryId !== undefined,
    });
    if (!ok) {
        return { ok: false };
    }

    const readinessStarted = Date.now();
    const expectedVariantCount = workload.profiles.length + 1;
    const infoUrl = entryUrl(assetPath, body.entryId, 'info');
    let ready = false;
    while ((Date.now() - readinessStarted) / 1000 < workload.timeoutSeconds) {
        const info = http.get(infoUrl, requestParameters('eager-info'));
        const infoBody = parseJson(info);
        ready = info.status === 200 && infoBody && infoBody.variants.length === expectedVariantCount;
        if (ready) {
            break;
        }
        sleep(0.1);
    }
    const readinessDuration = Date.now() - readinessStarted;
    ok = check(ready, { 'all eager variants became ready': (value) => value });

    for (const profile of workload.profiles) {
        const response = http.get(withQuery(entryUrl(assetPath, body.entryId, 'content'), { profile }), requestParameters('eager-verify'));
        ok = check(response, {
            [`${profile} returned 200`]: (value) => value.status === 200,
            [`${profile} was a cache hit`]: (value) => header(value, 'K-Cache-Status') === 'hit',
            [`${profile} content type matches`]: (value) => contentType(value) === fixture.files[workload.expectedFormat].mediaType,
        }) && ok;
    }
    return { ok, durationMs: readinessDuration };
}

function uploadAsset(assetPath, tags = undefined) {
    return http.post(
        `${baseUrl}/assets${assetPath}`,
        {
            metadata,
            asset: http.file(sourceBytes, `fixture.${sourceFormat}`, sourceFile.mediaType),
        },
        requestParameters('upload', tags),
    );
}

function requestParameters(operation = workloadId, extraTags = undefined) {
    return {
        tags: {
            operation,
            workload: workloadId,
            case: caseId,
            ...(extraTags || {}),
        },
    };
}

function assetForIteration(data, phase, reuse) {
    const assets = data[phase];
    if (!assets || assets.length === 0) {
        throw new Error(`No ${phase} seed data available`);
    }
    if (reuse) {
        return assets[0];
    }
    const index = execution.scenario.iterationInTest;
    if (index >= assets.length) {
        throw new Error(`Seed pool exhausted at iteration ${index}; available=${assets.length}`);
    }
    return assets[index];
}

function uniqueAssetPath() {
    return `/performance/${safe(runId)}/${safe(workloadId)}/${safe(caseId)}/${execution.scenario.name}/${execution.vu.idInTest}-${execution.scenario.iterationInTest}`;
}

function ruleAssetPath() {
    return `${workload.path}${uniqueAssetPath()}`;
}

function entryUrl(assetPath, entryId, selector) {
    return `${baseUrl}/assets${assetPath}/-/entry/${entryId}/${selector}`;
}

function withQuery(url, parameters) {
    const values = Object.entries(parameters).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    return values.length === 0 ? url : `${url}?${values.join('&')}`;
}

function parseJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function header(response, name) {
    const expected = name.toLowerCase();
    for (const [key, value] of Object.entries(response.headers)) {
        if (key.toLowerCase() === expected) {
            return value;
        }
    }
    return '';
}

function contentType(response) {
    return header(response, 'Content-Type').split(';')[0].trim().toLowerCase();
}

function logUnexpectedResponse(response, expectedStatus) {
    if (response.status !== expectedStatus) {
        console.error(`Expected HTTP ${expectedStatus}, received ${response.status}: ${response.body}`);
    }
}

function selectCase(selectedWorkload, requestedCase) {
    if (!selectedWorkload.cases) {
        if (requestedCase && requestedCase !== selectedWorkload.case) {
            throw new Error(`Unknown case ${requestedCase} for ${workloadId}`);
        }
        return { id: selectedWorkload.case };
    }
    const id = requestedCase || selectedWorkload.cases[0].id;
    const value = selectedWorkload.cases.find((item) => item.id === id);
    if (!value) {
        throw new Error(`Unknown case ${id} for ${workloadId}`);
    }
    return value;
}

function requiresColdPool() {
    return workloadId === 'variant.generate.cold' || workloadId === 'format.decode' || workloadId === 'format.encode';
}

function expectedIterations(phase) {
    const iterations = (durationSeconds(phase.duration) / durationSeconds(phase.timeUnit)) * phase.rate;
    return Math.ceil(iterations * 1.05) + 1;
}

function durationSeconds(value) {
    const match = /^(\d+)(ms|s|m|h)$/.exec(value);
    if (!match) {
        throw new Error(`Unsupported duration: ${value}`);
    }
    const amount = Number(match[1]);
    return amount * { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
}

function requiredEnvironment(name) {
    const value = __ENV[name];
    if (!value) {
        throw new Error(`Missing required environment variable: ${name}`);
    }
    return value;
}

function safe(value) {
    return value.replace(/[^a-zA-Z0-9_-]/g, '-');
}

function metricValues(data, name) {
    return (data.metrics[name] && data.metrics[name].values) || {};
}

function integer(value) {
    return Math.round(Number(value || 0));
}

export function handleSummary(data) {
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
        passed: integer(operationValues.count) > 0
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
