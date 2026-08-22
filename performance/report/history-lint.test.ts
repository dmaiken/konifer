import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
    assertHistoryMatchesSchema,
    nonReleaseLoadRuns,
    nonReleaseEntries,
    withoutNonReleaseLoadRuns,
    withoutNonReleaseEntries,
} from './history-lint.ts';
import type { AggregatedResult, LoadHistory, LoadRun, PerformanceHistory, PerformanceRelease } from './types.ts';

const [historySchema, loadHistorySchema, loadResultSchema] = await Promise.all([
    readFile(new URL('../schema/history.schema.json', import.meta.url), 'utf8'),
    readFile(new URL('../schema/load-history.schema.json', import.meta.url), 'utf8'),
    readFile(new URL('../schema/load-result.schema.json', import.meta.url), 'utf8'),
]).then((documents) => documents.map((document) => JSON.parse(document) as Record<string, unknown>));

test('non-release entries can be identified and removed without mutating history', () => {
    const valid = release('release-1', 'v0.9.0');
    const invalid = release('release-2', 'v0.9.0-dirty');
    const history: PerformanceHistory = { version: 1, releases: [valid, invalid] };

    assert.deepEqual(nonReleaseEntries(history), [invalid]);
    assert.deepEqual(withoutNonReleaseEntries(history).releases, [valid]);
    assert.deepEqual(history.releases, [valid, invalid]);
});

test('non-release load runs can be identified and removed without mutating history', () => {
    const valid = loadRun('v0.9.0');
    const invalid = loadRun('working-tree');
    const history: LoadHistory = { version: 1, runs: [valid, invalid] };

    assert.deepEqual(nonReleaseLoadRuns(history), [invalid]);
    assert.deepEqual(withoutNonReleaseLoadRuns(history).runs, [valid]);
    assert.equal(history.runs.length, 2);
});

test('history schema permits workloads to be added in later releases', () => {
    const first = release('release-1', 'v0.9.0');
    const second = release('release-2', 'v0.10.0');
    second.results.push(result('upload.rules', 'jpg-medium-landscape-rule'));
    const history: PerformanceHistory = { version: 1, releases: [first, second] };

    assert.doesNotThrow(() => assertHistoryMatchesSchema(history, historySchema));
    assert.equal(first.results.length, 1);
    assert.equal(second.results.length, 2);
});

test('history schema permits release and result annotations', () => {
    const annotated = release('release-1', 'v0.9.0');
    annotated.notes = ['Native codec dependencies changed in this release.'];
    annotated.results[0].notes = ['The format-specific regression is expected.'];

    assert.doesNotThrow(() => assertHistoryMatchesSchema(
        { version: 1, releases: [annotated] },
        historySchema,
    ));
});

test('history schema permits complete releases with failed benchmark results', () => {
    const incomplete = release('release-1', 'v0.9.0');
    incomplete.results[0].passed = false;
    incomplete.passed = false;

    assert.doesNotThrow(() => assertHistoryMatchesSchema(
        { version: 1, releases: [incomplete] },
        historySchema,
    ));
});

test('history schema rejects empty annotations', () => {
    const invalid = release('release-1', 'v0.9.0');
    invalid.notes = [];

    assert.throws(
        () => assertHistoryMatchesSchema({ version: 1, releases: [invalid] }, historySchema),
        /must NOT have fewer than 1 items/,
    );
});

test('history schema rejects blank annotation text', () => {
    const invalid = release('release-1', 'v0.9.0');
    invalid.notes = ['   '];

    assert.throws(
        () => assertHistoryMatchesSchema({ version: 1, releases: [invalid] }, historySchema),
        /must match pattern/,
    );
});

test('history schema rejects malformed aggregate results', () => {
    const invalid = release('release-1', 'v0.9.0') as PerformanceRelease & {
        unexpected?: boolean;
    };
    invalid.unexpected = true;

    assert.throws(
        () => assertHistoryMatchesSchema({ version: 1, releases: [invalid] }, historySchema),
        /must NOT have additional properties/,
    );
});

test('load history schema permits passing and failed operational results', () => {
    const failed = loadRun('v0.9.0');
    failed.streams[0].passed = false;
    failed.passed = false;
    const history: LoadHistory = { version: 1, runs: [loadRun('v0.8.0'), failed] };

    assert.doesNotThrow(() => assertHistoryMatchesSchema<LoadHistory>(
        history,
        loadHistorySchema,
        [loadResultSchema],
    ));
});

test('load history schema permits run and stream annotations', () => {
    const annotated = loadRun('v0.9.0');
    annotated.notes = ['Eager generation scheduling changed in this release.'];
    annotated.streams[0].notes = ['Cached delivery improved after removing a metadata lookup.'];

    assert.doesNotThrow(() => assertHistoryMatchesSchema<LoadHistory>(
        { version: 1, runs: [annotated] },
        loadHistorySchema,
        [loadResultSchema],
    ));
});

test('load history schema rejects blank annotations', () => {
    const invalid = loadRun('v0.9.0');
    invalid.notes = ['   '];

    assert.throws(
        () => assertHistoryMatchesSchema<LoadHistory>(
            { version: 1, runs: [invalid] },
            loadHistorySchema,
            [loadResultSchema],
        ),
        /must match pattern/,
    );
});

function release(runId: string, subject: string): PerformanceRelease {
    return {
        version: 1,
        runId,
        suite: 'release',
        environment: 'local-compose-v2',
        subject,
        startedAt: '2026-08-05T00:00:00Z',
        completedAt: '2026-08-05T01:00:00Z',
        results: [result('upload.original', 'jpg-medium')],
        passed: true,
    };
}

function result(workload: string, caseId: string): AggregatedResult {
    return {
        workload,
        case: caseId,
        repetitions: 1,
        operations: 10,
        requests: 10,
        errors: 0,
        checks: { passed: 10, failed: 0 },
        droppedIterations: 0,
        durationMs: {
            p50: 50,
            p90: 90,
            p95: 95,
            p99: 99,
            p95Min: 95,
            p95Max: 95,
        },
        passed: true,
    };
}

function loadRun(subject: string): LoadRun {
    return {
        version: 1,
        runId: `load-${subject}`,
        profile: 'mixed-v1',
        environment: 'local-compose-v2',
        subject,
        startedAt: '2026-08-05T00:00:00Z',
        completedAt: '2026-08-05T00:15:00Z',
        trafficDurationSeconds: 840,
        peakRatePerMinute: 1,
        targetOperations: 1,
        operations: 1,
        requests: 1,
        errors: 0,
        checks: { passed: 1, failed: 0 },
        droppedIterations: 0,
        streams: [{
            id: 'cached-original',
            workload: 'delivery.original.cached',
            case: 'jpg-medium',
            targetRatePerMinute: 1,
            plannedOperations: 1,
            operations: 1,
            requests: 1,
            errors: 0,
            checks: { passed: 1, failed: 0 },
            droppedIterations: 0,
            durationMs: { p50: 1, p90: 1, p95: 1, p99: 1 },
            passed: true,
        }],
        recovery: {
            healthPassed: true,
            eagerReady: true,
            durationMs: 1,
            checks: { passed: 1, failed: 0 },
            passed: true,
        },
        passed: true,
    };
}
