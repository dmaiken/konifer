import assert from 'node:assert/strict';
import test from 'node:test';
import {
    peakRatePerMinute,
    plannedOperations,
    seedPoolSize,
    targetRatePerMinute,
    trafficDurationMilliseconds,
} from '../k6/lib/load-profile.ts';
import {
    latestLoadRuns,
    loadPointsForStream,
    loadRunWithComparisons,
    recoveryPointsForRun,
    renderLoadMarkdown,
    updateLoadHistory,
} from './load.ts';
import type { LoadHistory, LoadRun, WorkloadCatalog } from './types.ts';

test('planned operations integrate ramp-up, steady, and ramp-down traffic', () => {
    const schedule = {
        timeUnit: '1m',
        rampUpDuration: '2m',
        steadyDuration: '10m',
        rampDownDuration: '2m',
    };

    assert.equal(plannedOperations(schedule, { startRate: 90, targetRate: 360 }), 4410);
    assert.equal(plannedOperations(schedule, { startRate: 4, targetRate: 15 }), 184);
    assert.equal(seedPoolSize(schedule, { startRate: 4, targetRate: 15 }), 195);
    assert.equal(trafficDurationMilliseconds(schedule), 840_000);
    assert.equal(targetRatePerMinute(schedule, { startRate: 4, targetRate: 15 }), 15);
    assert.equal(peakRatePerMinute(schedule, [
        { startRate: 90, targetRate: 360 },
        { startRate: 4, targetRate: 15 },
    ]), 375);
});

test('load history replaces the same subject, environment, and profile', () => {
    const first = loadRun('v1.0.0', 'local', 'mixed-v1', 100);
    const replacement = { ...loadRun('v1.0.0', 'local', 'mixed-v1', 120), runId: 'replacement' };
    const history = updateLoadHistory(updateLoadHistory(emptyHistory(), first), replacement);

    assert.equal(history.runs.length, 1);
    assert.equal(history.runs[0].runId, 'replacement');
});

test('load history retains distinct environments and profiles', () => {
    let history = emptyHistory();
    history = updateLoadHistory(history, loadRun('v1.0.0', 'local', 'mixed-v1', 100));
    history = updateLoadHistory(history, loadRun('v1.0.0', 'aws', 'mixed-v1', 100));
    history = updateLoadHistory(history, loadRun('v1.0.0', 'local', 'mixed-v2', 100));

    assert.equal(history.runs.length, 3);
});

test('regenerating a load run preserves manually maintained run and stream notes', () => {
    const annotated = loadRun('v1.0.0', 'local', 'mixed-v1', 100);
    annotated.notes = ['Background generation was redesigned.'];
    annotated.streams[0].notes = ['Cached delivery now avoids a database lookup.'];
    const regenerated = { ...loadRun('v1.0.0', 'local', 'mixed-v1', 90), runId: 'regenerated' };

    const history = updateLoadHistory(updateLoadHistory(emptyHistory(), annotated), regenerated);

    assert.deepEqual(history.runs[0].notes, annotated.notes);
    assert.deepEqual(history.runs[0].streams[0].notes, annotated.streams[0].notes);
});

test('latest load comparison ignores failed latency and uses the last passing baseline', () => {
    const first = loadRun('v1.0.0', 'local', 'mixed-v1', 100);
    const failed = loadRun('v1.1.0', 'local', 'mixed-v1', 900, false);
    const recovered = loadRun('v1.2.0', 'local', 'mixed-v1', 120);

    const failedLatest = latestLoadRuns({ version: 1, runs: [first, failed] })[0];
    assert.equal(failedLatest.subject, 'v1.1.0');
    assert.equal(failedLatest.streams[0].changePercent, null);

    const recoveredLatest = latestLoadRuns({ version: 1, runs: [first, failed, recovered] })[0];
    assert.equal(recoveredLatest.streams[0].changePercent, 20);
});

test('selected load comparisons and charts stop at the selected release', () => {
    const first = { ...loadRun('v1.0.0', 'local', 'mixed-v1', 100), completedAt: '2026-01-01T00:15:00Z' };
    const failed = { ...loadRun('v1.1.0', 'local', 'mixed-v1', 900, false), completedAt: '2026-02-01T00:15:00Z' };
    const selected = { ...loadRun('v1.2.0', 'local', 'mixed-v1', 80), completedAt: '2026-03-01T00:15:00Z' };
    const future = { ...loadRun('v1.3.0', 'local', 'mixed-v1', 60), completedAt: '2026-04-01T00:15:00Z' };
    const history = { version: 1 as const, runs: [first, failed, selected, future] };

    const compared = loadRunWithComparisons(history, selected);
    assert.equal(compared.streams[0].changePercent, -20);
    assert.deepEqual(
        loadPointsForStream(history, selected, selected.streams[0]).map((value) => value.subject),
        ['v1.0.0', 'v1.2.0'],
    );
    assert.deepEqual(
        recoveryPointsForRun(history, selected).map((value) => value.subject),
        ['v1.0.0', 'v1.2.0'],
    );
});

test('load Markdown suppresses failed stream latency', () => {
    const failed = loadRun('v1.0.0', 'local', 'mixed-v1', 900, false);
    const catalog: WorkloadCatalog = {
        suites: { release: { repetitions: 1, workloads: ['delivery.original.cached'] } },
        workloads: {
            'delivery.original.cached': { description: 'Retrieves a cached original.', case: 'jpg-medium' },
        },
    };
    const markdown = renderLoadMarkdown(failed, catalog);

    assert.match(markdown, /Completed with failed load checks/);
    assert.match(markdown, /Failed to complete \| 360\/min \| 90\/100 \| —/);
    assert.doesNotMatch(markdown, /Failed to complete[^\n]*900 ms/);
});

function emptyHistory(): LoadHistory {
    return { version: 1, runs: [] };
}

function loadRun(
    subject: string,
    environment: string,
    profile: string,
    p95: number,
    passed = true,
): LoadRun {
    return {
        version: 1,
        runId: `${environment}-${profile}-${subject}`,
        profile,
        environment,
        subject,
        startedAt: '2026-08-01T00:00:00Z',
        completedAt: '2026-08-01T00:15:00Z',
        trafficDurationSeconds: 840,
        peakRatePerMinute: 360,
        targetOperations: 100,
        operations: passed ? 100 : 90,
        requests: passed ? 100 : 90,
        errors: passed ? 0 : 1,
        checks: { passed: 100, failed: passed ? 0 : 1 },
        droppedIterations: 0,
        streams: [{
            id: 'cached-original',
            workload: 'delivery.original.cached',
            case: 'jpg-medium',
            targetRatePerMinute: 360,
            plannedOperations: 100,
            operations: passed ? 100 : 90,
            requests: passed ? 100 : 90,
            errors: passed ? 0 : 1,
            checks: { passed: 100, failed: passed ? 0 : 1 },
            droppedIterations: 0,
            durationMs: { p50: p95 / 2, p90: p95 * 0.9, p95, p99: p95 * 1.1 },
            passed,
        }],
        recovery: {
            healthPassed: true,
            eagerReady: passed,
            durationMs: 500,
            checks: { passed: 2, failed: passed ? 0 : 1 },
            passed,
        },
        passed,
    };
}
