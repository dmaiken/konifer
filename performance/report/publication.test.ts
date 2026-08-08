import assert from 'node:assert/strict';
import test from 'node:test';
import { missingReleaseCases } from './publication.ts';
import type { AggregatedResult, PerformanceRelease, WorkloadCatalog } from './types.ts';

const catalog: WorkloadCatalog = {
    suites: {
        release: {
            repetitions: 1,
            workloads: ['upload.original', 'format.encode'],
        },
    },
    workloads: {
        'upload.original': { description: 'Uploads an original.', case: 'jpg-medium' },
        'format.encode': {
            description: 'Encodes destination formats.',
            cases: [{ id: 'jpg-to-webp' }, { id: 'jpg-to-avif' }],
        },
    },
};

test('complete release contains every configured workload and case', () => {
    const aggregate = release([
        result('upload.original', 'jpg-medium'),
        result('format.encode', 'jpg-to-webp'),
        result('format.encode', 'jpg-to-avif'),
    ]);

    assert.deepEqual(missingReleaseCases(aggregate, catalog), []);
});

test('filtered release diagnostics are not publication-complete', () => {
    const aggregate = release([result('format.encode', 'jpg-to-webp')]);

    assert.deepEqual(missingReleaseCases(aggregate, catalog), [
        'upload.original/jpg-medium',
        'format.encode/jpg-to-avif',
    ]);
});

function release(results: AggregatedResult[]): PerformanceRelease {
    return {
        version: 1,
        runId: 'release-1',
        suite: 'release',
        environment: 'local-compose-v2',
        subject: 'v0.9.0',
        startedAt: '2026-08-05T00:00:00Z',
        completedAt: '2026-08-05T01:00:00Z',
        results,
        passed: true,
    };
}

function result(workload: string, caseId: string): AggregatedResult {
    return {
        workload,
        case: caseId,
        repetitions: 1,
        operations: 1,
        requests: 1,
        errors: 0,
        checks: { passed: 1, failed: 0 },
        droppedIterations: 0,
        durationMs: {
            p50: 1,
            p90: 1,
            p95: 1,
            p99: 1,
            p95Min: 1,
            p95Max: 1,
        },
        passed: true,
    };
}
