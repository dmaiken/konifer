import assert from 'node:assert/strict';
import test from 'node:test';
import { aggregateResults, median, updateHistory } from './aggregate.ts';
import type { NormalizedResult } from './types.ts';

test('median handles odd and even samples', () => {
    assert.equal(median([30, 10, 20]), 20);
    assert.equal(median([40, 10, 30, 20]), 25);
});

test('aggregation uses repetition medians and sums totals', () => {
    const aggregate = aggregateResults([
        result(1, 100, 10),
        result(2, 300, 20),
        result(3, 200, 30),
    ]);

    assert.equal(aggregate.results.length, 1);
    assert.deepEqual(aggregate.results[0].durationMs, {
        p50: 100,
        p90: 180,
        p95: 200,
        p99: 220,
        p95Min: 100,
        p95Max: 300,
    });
    assert.equal(aggregate.results[0].operations, 60);
    assert.equal(aggregate.results[0].repetitions, 3);
    assert.equal(aggregate.passed, true);
});

test('history replaces the same run instead of duplicating it', () => {
    const first = aggregateResults([result(1, 100, 10)]);
    const changed = { ...first, completedAt: '2026-08-04T01:00:00Z' };
    const history = updateHistory(updateHistory({ version: 1, releases: [] }, first), changed);
    assert.equal(history.releases.length, 1);
    assert.equal(history.releases[0].completedAt, changed.completedAt);
});

test('aggregation rejects a gap in repetition numbers', () => {
    assert.throws(
        () => aggregateResults([result(1, 100, 10), result(3, 200, 10), result(4, 300, 10)]),
        /Missing repetition 2/,
    );
});

function result(repetition: number, p95: number, operations: number): NormalizedResult {
    return {
        version: 1,
        runId: 'release-1',
        suite: 'release',
        workload: 'upload.original',
        case: 'jpg-medium',
        repetition,
        environment: 'local-compose-v1',
        subject: 'v1.0.0',
        startedAt: `2026-08-04T00:00:0${repetition}Z`,
        completedAt: `2026-08-04T00:00:1${repetition}Z`,
        metrics: {
            operations,
            requests: operations,
            errors: 0,
            checks: { passed: operations, failed: 0 },
            droppedIterations: 0,
            durationMs: {
                min: p95 / 4,
                p50: p95 / 2,
                p90: p95 * 0.9,
                p95,
                p99: p95 * 1.1,
                max: p95 * 1.2,
            },
        },
        passed: true,
    };
}
