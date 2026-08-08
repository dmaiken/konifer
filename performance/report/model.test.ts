import assert from 'node:assert/strict';
import test from 'node:test';
import { environmentsForHistory, formatChange, formatRange, latestBySeries, pointsForSeries, workloadDescription, workloadLabel } from './model.ts';
import type { EnvironmentCatalog, PerformanceRelease, WorkloadCatalog } from './types.ts';

test('latest results calculate changes within the same environment and series', () => {
    const history = {
        version: 1 as const,
        releases: [
            release('2026-01-01T00:00:00Z', 'v1.0.0', 'local', 100),
            release('2026-02-01T00:00:00Z', 'v1.1.0', 'aws', 50),
            release('2026-03-01T00:00:00Z', 'v1.2.0', 'local', 120),
        ],
    };

    const latest = latestBySeries(history);
    const local = latest.find((value) => value.environment === 'local');
    const aws = latest.find((value) => value.environment === 'aws');

    assert.equal(local?.subject, 'v1.2.0');
    assert.equal(local?.changePercent, 20);
    assert.equal(aws?.changePercent, null);
});

test('series points are chronological and environment-specific', () => {
    const history = {
        version: 1 as const,
        releases: [
            release('2026-03-01T00:00:00Z', 'v1.2.0', 'local', 120),
            release('2026-02-01T00:00:00Z', 'v1.1.0', 'aws', 50),
            release('2026-01-01T00:00:00Z', 'v1.0.0', 'local', 100),
        ],
    };
    const selected = latestBySeries(history).find((value) => value.environment === 'local');
    assert.ok(selected);
    const points = pointsForSeries(history, selected);

    assert.deepEqual(points.map((value) => value.subject), ['v1.0.0', 'v1.2.0']);
    assert.deepEqual(points.map((value) => value.p95), [100, 120]);
});

test('change formatting keeps regressions and unchanged results visible', () => {
    assert.equal(formatChange(4.25), '+4.3%');
    assert.equal(formatChange(0), '0.0%');
    assert.equal(formatChange(-3.25), '-3.3%');
    assert.equal(formatChange(null), '—');
});

test('workload labels keep the customer-facing name separate from the case ID', () => {
    assert.equal(workloadLabel({ workload: 'variant.generate.cold' }), 'Cold on-demand variant');
    assert.equal(workloadLabel({ workload: 'custom.workload' }), 'custom.workload');
});

test('workload descriptions come from executable workload metadata', () => {
    const catalog: WorkloadCatalog = {
        suites: { release: { repetitions: 1, workloads: ['delivery.original.cached'] } },
        workloads: {
            'delivery.original.cached': {
                description: 'Retrieves an existing original without image processing.',
                case: 'jpg-medium',
            },
        },
    };

    assert.equal(
        workloadDescription({ workload: 'delivery.original.cached' }, catalog),
        'Retrieves an existing original without image processing.',
    );
    assert.equal(workloadDescription({ workload: 'retired.workload' }, catalog), null);
});

test('p95 range is hidden until multiple repetitions provide a real range', () => {
    const single = release('2026-01-01T00:00:00Z', 'v1.0.0', 'local', 100).results[0];
    const repeated = {
        ...single,
        repetitions: 3,
        durationMs: { ...single.durationMs, p95Min: 90, p95Max: 110 },
    };

    assert.equal(formatRange(single), '—');
    assert.equal(formatRange(repeated), '90 ms–110 ms');
});

test('environment summaries use configured metadata and preserve unknown historical IDs', () => {
    const history = {
        version: 1 as const,
        releases: [
            release('2026-01-01T00:00:00Z', 'v1.0.0', 'local', 100),
            release('2026-02-01T00:00:00Z', 'v1.1.0', 'retired-environment', 110),
        ],
    };
    const catalog: EnvironmentCatalog = {
        default: 'local',
        profiles: {
            local: {
                displayName: 'Local laptop',
                description: 'Local reference environment.',
                hardware: { memoryGiB: 16 },
                interpretation: ['Compare locally.'],
            },
        },
    };

    assert.deepEqual(environmentsForHistory(history, catalog), [
        {
            id: 'local',
            displayName: 'Local laptop',
            description: 'Local reference environment.',
            hardware: { memoryGiB: 16 },
            interpretation: ['Compare locally.'],
        },
        {
            id: 'retired-environment',
            displayName: 'retired-environment',
            description: null,
            hardware: null,
            interpretation: [],
        },
    ]);
});

function release(completedAt: string, subject: string, environment: string, p95: number): PerformanceRelease {
    return {
        version: 1,
        runId: `${environment}-${subject}`,
        suite: 'release',
        startedAt: completedAt,
        completedAt,
        subject,
        environment,
        results: [{
            workload: 'upload.original',
            case: 'jpg-medium',
            repetitions: 1,
            operations: 10,
            requests: 10,
            errors: 0,
            checks: { passed: 10, failed: 0 },
            droppedIterations: 0,
            durationMs: {
                p50: p95 / 2,
                p90: p95 * 0.9,
                p95,
                p99: p95 * 1.1,
                p95Min: p95,
                p95Max: p95,
            },
            passed: true,
        }],
        passed: true,
    };
}
