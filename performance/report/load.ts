import { formatMs, significantChange, workloadLabel } from './model.ts';
import { compareReleaseRecords, compareReleaseVersions } from './release-version.ts';
import type {
    LatestLoadRun,
    LatestLoadStream,
    LoadHistory,
    LoadRun,
    LoadStreamResult,
    SeriesPoint,
    WorkloadCatalog,
} from './types.ts';

export function updateLoadHistory(history: LoadHistory, run: LoadRun): LoadHistory {
    const existing = history.runs.find((value) => sameLoadIdentity(value, run));
    const annotatedRun = preserveLoadNotes(existing, run);
    const runs = history.runs.filter((value) => !sameLoadIdentity(value, run));
    runs.push(annotatedRun);
    runs.sort(compareReleaseRecords);
    return { version: 1, runs };
}

export function latestLoadRuns(history: LoadHistory): LatestLoadRun[] {
    const latest = new Map<string, LoadRun>();
    const runs = [...history.runs].sort(compareReleaseRecords);
    for (const run of runs) {
        latest.set(loadProfileKey(run), run);
    }
    return [...latest.values()].map((run) => loadRunWithComparisons(history, run)).sort((left, right) => (
        left.environment.localeCompare(right.environment) || left.profile.localeCompare(right.profile)
    ));
}

export function loadRunWithComparisons(history: LoadHistory, selected: LoadRun): LatestLoadRun {
    const previousStreams = new Map<string, LoadStreamResult>();
    const runs = [...history.runs].sort(compareReleaseRecords);

    for (const run of runs) {
        if (run.runId === selected.runId) break;
        if (run.environment !== selected.environment || run.profile !== selected.profile) continue;
        for (const stream of run.streams) {
            if (stream.passed) previousStreams.set(loadStreamKey(run, stream), stream);
        }
    }

    return {
        ...selected,
        streams: selected.streams.map((stream): LatestLoadStream => {
            const previous = previousStreams.get(loadStreamKey(selected, stream));
            const change = stream.passed && previous
                ? significantChange(previous.durationMs.p95, stream.durationMs.p95)
                : null;
            return {
                ...stream,
                hasPrevious: previous !== undefined,
                changePercent: change?.percent ?? null,
            };
        }),
    };
}

export function loadPointsForStream(
    history: LoadHistory,
    selected: LoadRun,
    stream: Pick<LoadStreamResult, 'workload' | 'case'>,
): SeriesPoint[] {
    return comparableRuns(history, selected).flatMap((run) => {
        const result = run.streams.find((value) => (
            value.workload === stream.workload && value.case === stream.case
        ));
        return !result?.passed ? [] : [{
            completedAt: run.completedAt,
            subject: run.subject,
            environment: run.environment,
            repetitions: 1,
            p50: result.durationMs.p50,
            p95: result.durationMs.p95,
        }];
    });
}

export function recoveryPointsForRun(history: LoadHistory, selected: LoadRun): SeriesPoint[] {
    return comparableRuns(history, selected).flatMap((run) => !run.recovery.passed ? [] : [{
        completedAt: run.completedAt,
        subject: run.subject,
        environment: run.environment,
        repetitions: 1,
        p50: run.recovery.durationMs,
        p95: run.recovery.durationMs,
    }]);
}

export function renderLoadMarkdown(
    run: LoadRun,
    workloads: WorkloadCatalog,
): string {
    const completion = formatCompletion(run.operations, run.targetOperations);
    const rows = run.streams.map((stream) => {
        const latency = stream.passed ? formatMs(stream.durationMs.p95) : '—';
        return `| ${workloadLabel(stream)} · ${stream.case} | ${stream.passed ? 'Completed' : 'Failed to complete'} | ${stream.targetRatePerMinute}/min | ${stream.operations}/${stream.plannedOperations} | ${latency} | ${stream.errors} | ${stream.droppedIterations} |`;
    });
    const definitions = [...new Set(run.streams.map((stream) => stream.workload))]
        .map((workload) => `- **${workloadLabel({ workload })}**: ${workloads.workloads[workload]?.description || workload}`)
        .join('\n');

    return `# Mixed load run: ${run.subject}

- Profile: \`${run.profile}\`
- Environment: \`${run.environment}\`
- Completed: ${run.completedAt}
- Result: ${run.passed ? 'Completed' : 'Completed with failed load checks'}
- Peak target: ${formatRate(run.peakRatePerMinute)}
- Scheduled operations completed: ${run.operations}/${run.targetOperations} (${completion})
- Errors: ${run.errors}
- Dropped iterations: ${run.droppedIterations}
- Recovery: ${run.recovery.passed ? `Passed (${formatMs(run.recovery.durationMs)} eager readiness)` : 'Failed'}

## Traffic streams

| Operation | Result | Peak target | Operations | p95 | Errors | Dropped |
|---|---|---:|---:|---:|---:|---:|
${rows.join('\n')}

## Workload definitions

${definitions}

Latency is reported only for streams whose operational checks passed. This
fixed mixed-load profile is intended for release-over-release comparison in the
same environment, not as a production capacity claim.
`;
}

export function formatCompletion(operations: number, target: number): string {
    return target === 0 ? '—' : `${((operations / target) * 100).toFixed(1)}%`;
}

export function formatRate(ratePerMinute: number): string {
    return `${Number((ratePerMinute / 60).toFixed(1))} ops/s`;
}

function sameLoadIdentity(left: LoadRun, right: LoadRun): boolean {
    return left.subject === right.subject
        && left.environment === right.environment
        && left.profile === right.profile;
}

function comparableRuns(history: LoadHistory, selected: LoadRun): LoadRun[] {
    return history.runs
        .filter((run) => run.environment === selected.environment
            && run.profile === selected.profile
            && compareReleaseVersions(run.subject, selected.subject) <= 0)
        .sort(compareReleaseRecords);
}

function preserveLoadNotes(existing: LoadRun | undefined, run: LoadRun): LoadRun {
    if (!existing) return run;
    const streams = new Map(existing.streams.map((stream) => [
        `${stream.workload}\u0000${stream.case}`,
        stream,
    ]));
    return {
        ...run,
        ...(run.notes || !existing.notes ? {} : { notes: existing.notes }),
        streams: run.streams.map((stream) => {
            const previous = streams.get(`${stream.workload}\u0000${stream.case}`);
            return stream.notes || !previous?.notes
                ? stream
                : { ...stream, notes: previous.notes };
        }),
    };
}

function loadProfileKey(run: Pick<LoadRun, 'environment' | 'profile'>): string {
    return `${run.environment}\u0000${run.profile}`;
}

function loadStreamKey(
    run: Pick<LoadRun, 'environment' | 'profile'>,
    stream: Pick<LoadStreamResult, 'workload' | 'case'>,
): string {
    return `${loadProfileKey(run)}\u0000${stream.workload}\u0000${stream.case}`;
}
