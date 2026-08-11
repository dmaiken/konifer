import type {
    AggregatedResult,
    NormalizedResult,
    PerformanceHistory,
    PerformanceRelease,
} from './types.ts';

const metricNames = ['p50', 'p90', 'p95', 'p99'] as const;

type AggregateMetricName = typeof metricNames[number];
type RunIdentity = Pick<PerformanceRelease, 'runId' | 'suite' | 'environment' | 'subject'>;

export function aggregateResults(documents: NormalizedResult[]): PerformanceRelease {
    if (documents.length === 0) {
        throw new Error('No normalized result documents were found');
    }

    const identity = pickIdentity(documents[0]);
    for (const document of documents) {
        for (const key of Object.keys(identity) as (keyof RunIdentity)[]) {
            const expected = identity[key];
            if (document[key] !== expected) {
                throw new Error(`Result ${key} mismatch: expected ${expected}, received ${document[key]}`);
            }
        }
    }

    const groups = new Map<string, NormalizedResult[]>();
    for (const document of documents) {
        const key = `${document.workload}\u0000${document.case}`;
        const values = groups.get(key) || [];
        if (values.some((value) => value.repetition === document.repetition)) {
            throw new Error(`Duplicate repetition ${document.repetition} for ${document.workload}/${document.case}`);
        }
        values.push(document);
        groups.set(key, values);
    }

    const results = [...groups.values()].map(aggregateGroup).sort(compareResults);
    return {
        version: 1,
        ...identity,
        startedAt: documents.map((value) => value.startedAt).sort()[0],
        completedAt: documents.map((value) => value.completedAt).sort().at(-1)!,
        results,
        passed: results.every((value) => value.passed),
    };
}

function aggregateGroup(documents: NormalizedResult[]): AggregatedResult {
    documents.sort((left, right) => left.repetition - right.repetition);
    documents.forEach((document, index) => {
        if (document.repetition !== index + 1) {
            throw new Error(`Missing repetition ${index + 1} for ${document.workload}/${document.case}`);
        }
    });
    const durationMs = Object.fromEntries(metricNames.map((name: AggregateMetricName) => [
        name,
        median(documents.map((value) => value.metrics.durationMs[name])),
    ])) as Pick<AggregatedResult['durationMs'], AggregateMetricName>;
    const p95Values = documents.map((value) => value.metrics.durationMs.p95);

    return {
        workload: documents[0].workload,
        case: documents[0].case,
        repetitions: documents.length,
        operations: sum(documents, (value) => value.metrics.operations),
        requests: sum(documents, (value) => value.metrics.requests),
        errors: sum(documents, (value) => value.metrics.errors),
        checks: {
            passed: sum(documents, (value) => value.metrics.checks.passed),
            failed: sum(documents, (value) => value.metrics.checks.failed),
        },
        droppedIterations: sum(documents, (value) => value.metrics.droppedIterations),
        durationMs: {
            ...durationMs,
            p95Min: Math.min(...p95Values),
            p95Max: Math.max(...p95Values),
        },
        passed: documents.every((value) => value.passed),
    };
}

export function updateHistory(history: PerformanceHistory, aggregate: PerformanceRelease): PerformanceHistory {
    const existing = (history.releases || []).find((value) => value.runId === aggregate.runId);
    const annotatedAggregate = preserveNotes(existing, aggregate);
    const releases = (history.releases || []).filter((value) => value.runId !== aggregate.runId);
    releases.push(annotatedAggregate);
    releases.sort((left, right) => left.completedAt.localeCompare(right.completedAt));
    return { version: 1, releases };
}

function preserveNotes(
    existing: PerformanceRelease | undefined,
    aggregate: PerformanceRelease,
): PerformanceRelease {
    if (!existing) return aggregate;
    const existingResults = new Map(existing.results.map((result) => [
        `${result.workload}\u0000${result.case}`,
        result,
    ]));
    return {
        ...aggregate,
        ...(aggregate.notes || !existing.notes ? {} : { notes: existing.notes }),
        results: aggregate.results.map((result) => {
            const previous = existingResults.get(`${result.workload}\u0000${result.case}`);
            return result.notes || !previous?.notes
                ? result
                : { ...result, notes: previous.notes };
        }),
    };
}

export function median(values: number[]): number {
    const sorted = [...values].sort((left, right) => left - right);
    const middle = Math.floor(sorted.length / 2);
    const value = sorted.length % 2 === 0
        ? (sorted[middle - 1] + sorted[middle]) / 2
        : sorted[middle];
    return Math.round(value * 1000) / 1000;
}

function pickIdentity(document: NormalizedResult): RunIdentity {
    return {
        runId: document.runId,
        suite: document.suite,
        environment: document.environment,
        subject: document.subject,
    };
}

function sum(values: NormalizedResult[], selector: (value: NormalizedResult) => number): number {
    return values.reduce((total, value) => total + selector(value), 0);
}

function compareResults(left: AggregatedResult, right: AggregatedResult): number {
    return left.workload.localeCompare(right.workload) || left.case.localeCompare(right.case);
}
