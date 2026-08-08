import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
    assertHistoryMatchesSchema,
    isReleaseTag,
    nonReleaseEntries,
    withoutNonReleaseEntries,
} from './history-lint.ts';
import type { AggregatedResult, PerformanceHistory, PerformanceRelease } from './types.ts';

const historySchema = JSON.parse(
    await readFile(new URL('../schema/history.schema.json', import.meta.url), 'utf8'),
) as Record<string, unknown>;

test('release subjects must be stable semantic version tags', () => {
    assert.equal(isReleaseTag('v0.9.0'), true);
    assert.equal(isReleaseTag('v12.34.56'), true);
    assert.equal(isReleaseTag('v0.9.0-dirty'), false);
    assert.equal(isReleaseTag('v0.9.0-rc.1'), false);
    assert.equal(isReleaseTag('0.9.0'), false);
    assert.equal(isReleaseTag('abc1234'), false);
});

test('non-release entries can be identified and removed without mutating history', () => {
    const valid = release('release-1', 'v0.9.0');
    const invalid = release('release-2', 'v0.9.0-dirty');
    const history: PerformanceHistory = { version: 1, releases: [valid, invalid] };

    assert.deepEqual(nonReleaseEntries(history), [invalid]);
    assert.deepEqual(withoutNonReleaseEntries(history).releases, [valid]);
    assert.deepEqual(history.releases, [valid, invalid]);
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
