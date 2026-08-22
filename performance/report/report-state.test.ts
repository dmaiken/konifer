import assert from 'node:assert/strict';
import test from 'node:test';
import {
    newestLoadRun,
    reportEnvironments,
    reportProfiles,
    reportSubjects,
    requestedStateFromUrl,
    resolveReportState,
    selectedLoadRun,
    selectedRelease,
    urlForState,
} from './report-state.ts';
import type {
    EnvironmentCatalog,
    LoadCatalog,
    LoadHistory,
    LoadRun,
    PerformanceHistory,
    PerformanceRelease,
} from './types.ts';

test('report subjects combine both histories and sort by newest measurement', () => {
    const histories = fixtureHistories();
    assert.deepEqual(reportSubjects(histories.releases, histories.loads).map((value) => value.subject), [
        'v1.2.0',
        'v1.1.0',
        'v1.0.0',
    ]);
});

test('report state defaults to the newest release and configured environment', () => {
    const histories = fixtureHistories();
    const state = resolveReportState(
        histories.releases,
        histories.loads,
        environments(),
        loadCatalog(),
        { subject: null, environment: null, profile: null, tab: 'benchmarks' },
    );

    assert.deepEqual(state, {
        subject: 'v1.2.0',
        environment: 'local',
        profile: null,
        tab: 'benchmarks',
    });
    assert.equal(selectedRelease(histories.releases, state)?.subject, 'v1.2.0');
    assert.equal(selectedLoadRun(histories.loads, state), null);
});

test('report state accepts a load-only release and selects its profile', () => {
    const histories = fixtureHistories();
    const state = resolveReportState(
        histories.releases,
        histories.loads,
        environments(),
        loadCatalog(),
        { subject: 'v1.1.0', environment: 'aws', profile: null, tab: 'load' },
    );

    assert.deepEqual(state, {
        subject: 'v1.1.0',
        environment: 'aws',
        profile: 'mixed-v1',
        tab: 'load',
    });
    assert.equal(selectedRelease(histories.releases, state), null);
    assert.equal(selectedLoadRun(histories.loads, state)?.subject, 'v1.1.0');
});

test('bare tabs choose the newest release that has their report type', () => {
    const histories = fixtureHistories();
    histories.loads.runs.push(loadRun('v1.3.0', 'local', 'mixed-v1', '2026-04-01T00:00:00Z'));

    const benchmarks = resolveReportState(
        histories.releases,
        histories.loads,
        environments(),
        loadCatalog(),
        { subject: null, environment: null, profile: null, tab: 'benchmarks' },
    );
    const load = resolveReportState(
        histories.releases,
        histories.loads,
        environments(),
        loadCatalog(),
        { subject: null, environment: null, profile: null, tab: 'load' },
    );

    assert.equal(benchmarks.subject, 'v1.2.0');
    assert.equal(load.subject, 'v1.3.0');
});

test('invalid URL selections fall back without mixing environments or profiles', () => {
    const histories = fixtureHistories();
    const state = resolveReportState(
        histories.releases,
        histories.loads,
        environments(),
        loadCatalog(),
        { subject: 'v9.9.9', environment: 'missing', profile: 'mixed-v9', tab: 'load' },
    );

    assert.equal(state.subject, 'v1.1.0');
    assert.equal(state.environment, 'aws');
    assert.equal(state.profile, 'mixed-v1');
    assert.equal(state.tab, 'load');
});

test('load tab remains addressable when no load history exists', () => {
    const histories = fixtureHistories();
    const state = resolveReportState(
        histories.releases,
        { version: 1, runs: [] },
        environments(),
        loadCatalog(),
        { subject: null, environment: null, profile: null, tab: 'load' },
    );

    assert.equal(state.tab, 'load');
    assert.equal(state.subject, 'v1.2.0');
});

test('available environments and profiles are scoped to the selected release', () => {
    const histories = fixtureHistories();
    assert.deepEqual(reportEnvironments(histories.releases, histories.loads, 'v1.0.0'), ['aws', 'local']);
    assert.deepEqual(reportProfiles(histories.loads, 'v1.0.0', 'local'), ['mixed-v1', 'mixed-v2']);
    assert.equal(newestLoadRun(histories.loads, 'local')?.profile, 'mixed-v2');
});

test('report state round-trips through a shareable GitHub Pages URL', () => {
    const requested = requestedStateFromUrl(new URL(
        'https://example.github.io/imagek/performance/report/?release=v1.0.0&environment=local&profile=mixed-v2#load',
    ));
    assert.deepEqual(requested, {
        subject: 'v1.0.0',
        environment: 'local',
        profile: 'mixed-v2',
        tab: 'load',
    });

    const url = urlForState(new URL('https://example.github.io/imagek/performance/report/'), requested);
    assert.equal(
        url.href,
        'https://example.github.io/imagek/performance/report/?release=v1.0.0&environment=local&profile=mixed-v2#load',
    );
});

function fixtureHistories(): { releases: PerformanceHistory; loads: LoadHistory } {
    return {
        releases: {
            version: 1,
            releases: [
                release('v1.0.0', 'local', '2026-01-01T00:00:00Z'),
                release('v1.0.0', 'aws', '2026-01-02T00:00:00Z'),
                release('v1.2.0', 'local', '2026-03-01T00:00:00Z'),
            ],
        },
        loads: {
            version: 1,
            runs: [
                loadRun('v1.0.0', 'local', 'mixed-v1', '2026-01-01T01:00:00Z'),
                loadRun('v1.0.0', 'local', 'mixed-v2', '2026-01-01T02:00:00Z'),
                loadRun('v1.1.0', 'aws', 'mixed-v1', '2026-02-01T00:00:00Z'),
            ],
        },
    };
}

function release(subject: string, environment: string, completedAt: string): PerformanceRelease {
    return {
        version: 1,
        runId: `${subject}-${environment}`,
        suite: 'release',
        environment,
        subject,
        startedAt: completedAt,
        completedAt,
        results: [],
        passed: true,
    };
}

function loadRun(subject: string, environment: string, profile: string, completedAt: string): LoadRun {
    return {
        version: 1,
        runId: `${subject}-${environment}-${profile}`,
        profile,
        environment,
        subject,
        startedAt: completedAt,
        completedAt,
        trafficDurationSeconds: 840,
        peakRatePerMinute: 816,
        targetOperations: 100,
        operations: 100,
        requests: 100,
        errors: 0,
        checks: { passed: 100, failed: 0 },
        droppedIterations: 0,
        streams: [],
        recovery: {
            healthPassed: true,
            eagerReady: true,
            durationMs: 500,
            checks: { passed: 2, failed: 0 },
            passed: true,
        },
        passed: true,
    };
}

function environments(): EnvironmentCatalog {
    return {
        default: 'local',
        profiles: { local: {}, aws: {} },
    };
}

function loadCatalog(): LoadCatalog {
    return {
        version: 1,
        defaultProfile: 'mixed-v1',
        profiles: {},
    };
}
