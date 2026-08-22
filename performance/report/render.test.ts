import assert from 'node:assert/strict';
import test from 'node:test';
import { renderRunMarkdown } from './render.ts';
import type { EnvironmentProfile, PerformanceRelease, WorkloadCatalog } from './types.ts';

test('run report displays the actual repetition count without a fixed assumption', () => {
    const markdown = renderRunMarkdown(release(3));

    assert.match(markdown, /\| Repetitions \|/);
    assert.match(markdown, /\| 3 \| 30 \|/);
    assert.doesNotMatch(markdown, /three repetitions/i);
    assert.doesNotMatch(markdown, /\| Change \|/);
});

test('run report identifies completed measurements', () => {
    const markdown = renderRunMarkdown(release(3));

    assert.match(markdown, /- Result: Completed/);
    assert.match(markdown, /\| Operation \| Result \|/);
    assert.match(markdown, /\| Completed \| 50 ms \| 95 ms \|/);
});

test('run report marks a failed benchmark unavailable instead of presenting its latency', () => {
    const failed = release(3);
    failed.results[0].passed = false;
    failed.passed = false;
    const markdown = renderRunMarkdown(failed);

    assert.match(markdown, /- Result: Completed with failed benchmarks/);
    assert.match(markdown, /\| Failed to complete \| — \| — \| — \|/);
    assert.doesNotMatch(markdown, /\| Failed to complete \| 50 ms/);
});

test('run report hides a meaningless single-repetition p95 range', () => {
    const markdown = renderRunMarkdown(release(1));

    assert.match(markdown, /\| 95 ms \| — \| 1 \|/);
    assert.doesNotMatch(markdown, /90 ms–100 ms/);
});

test('run report explains the benchmark environment', () => {
    const environment: EnvironmentProfile = {
        displayName: 'Local developer laptop',
        description: 'Provisional local reference environment.',
        hardware: {
            system: 'Test laptop',
            processor: 'Test CPU',
            architecture: 'x86_64',
            physicalCores: 4,
            logicalCpus: 8,
            memoryGiB: 16,
        },
        interpretation: ['Compare only with the same environment.'],
    };
    const markdown = renderRunMarkdown(release(3), environment);

    assert.match(markdown, /## Benchmark environment/);
    assert.match(markdown, /\*\*Local developer laptop\*\* \(`local-compose-v2`\)/);
    assert.match(markdown, /\| Processor \| Test CPU \|/);
    assert.match(markdown, /4 physical cores \/ 8 logical CPUs/);
    assert.match(markdown, /### How to interpret these results/);
    assert.match(markdown, /Compare only with the same environment/);
});

test('run report explains measured workloads from the workload catalog', () => {
    const workloads: WorkloadCatalog = {
        suites: { release: { repetitions: 1, workloads: ['upload.original'] } },
        workloads: {
            'upload.original': {
                description: 'Stores an original without transformation or inference.',
                case: 'jpg-medium',
            },
        },
    };

    const markdown = renderRunMarkdown(release(1), undefined, workloads);

    assert.match(markdown, /## Workload definitions/);
    assert.match(markdown, /\*\*Original upload\*\* \(`upload\.original`\)/);
    assert.match(markdown, /Stores an original without transformation or inference/);
});

function release(repetitions: number): PerformanceRelease {
    return {
        version: 1,
        runId: 'release-1',
        suite: 'release',
        environment: 'local-compose-v2',
        subject: 'v0.9.0',
        startedAt: '2026-08-05T00:00:00Z',
        completedAt: '2026-08-05T01:00:00Z',
        results: [{
            workload: 'upload.original',
            case: 'jpg-medium',
            repetitions,
            operations: 30,
            requests: 30,
            errors: 0,
            checks: { passed: 30, failed: 0 },
            droppedIterations: 0,
            durationMs: {
                p50: 50,
                p90: 90,
                p95: 95,
                p99: 99,
                p95Min: 90,
                p95Max: 100,
            },
            passed: true,
        }],
        passed: true,
    };
}
