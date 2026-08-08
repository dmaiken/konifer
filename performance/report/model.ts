import type {
    AggregatedResult,
    EnvironmentCatalog,
    EnvironmentSummary,
    LatestSeriesResult,
    PerformanceHistory,
    SeriesPoint,
    WorkloadCatalog,
} from './types.ts';

export const headlineSeries = [
    ['upload.original', 'jpg-medium'],
    ['variant.generate.cold', 'jpg-to-webp-400'],
    ['variant.deliver.cached', 'jpg-to-webp-400'],
    ['upload.preprocess', 'jpg-medium-to-jxl'],
    ['upload.rules', 'jpg-medium-landscape-rule'],
    ['upload.rules.preprocess', 'jpg-medium-landscape-rule-to-jxl'],
    ['variant.eager.ready', 'jpg-medium-four-profiles'],
];

const workloadLabels: Record<string, string> = {
    'upload.original': 'Original upload',
    'delivery.original.cached': 'Cached original delivery',
    'variant.generate.cold': 'Cold on-demand variant',
    'variant.deliver.cached': 'Cached variant delivery',
    'upload.preprocess': 'Preprocessed upload',
    'upload.rules': 'Upload Rules evaluation',
    'upload.rules.preprocess': 'Upload Rules with preprocessing',
    'upload.eager.accept': 'Eager upload acceptance',
    'variant.eager.ready': 'Eager variant readiness',
    'format.decode': 'Source format decode',
    'format.encode': 'Destination format encode',
};

export function latestBySeries(history: PerformanceHistory): LatestSeriesResult[] {
    const values = new Map<string, LatestSeriesResult>();
    const releases = [...(history.releases || [])].sort((left, right) => left.completedAt.localeCompare(right.completedAt));

    for (const release of releases) {
        for (const result of release.results) {
            const key = seriesId({ ...result, environment: release.environment });
            const previous = values.get(key);
            values.set(key, {
                ...result,
                subject: release.subject,
                environment: release.environment,
                completedAt: release.completedAt,
                changePercent: previous ? percentChange(previous.durationMs.p95, result.durationMs.p95) : null,
            });
        }
    }

    return [...values.values()].sort(compareSeries);
}

export function environmentsForHistory(
    history: PerformanceHistory,
    catalog: EnvironmentCatalog,
): EnvironmentSummary[] {
    const ids = [...new Set(history.releases.map((release) => release.environment))].sort();
    return ids.map((id) => {
        const profile = catalog.profiles[id];
        return {
            id,
            displayName: profile?.displayName || id,
            description: profile?.description || null,
            hardware: profile?.hardware || null,
            interpretation: profile?.interpretation || [],
        };
    });
}

export function pointsForSeries(history: PerformanceHistory, selected: Pick<LatestSeriesResult, 'environment' | 'workload' | 'case'>): SeriesPoint[] {
    return [...(history.releases || [])]
        .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
        .flatMap((release) => {
            if (release.environment !== selected.environment) return [];
            return release.results
                .filter((value) => seriesKey(value) === seriesKey(selected))
                .map((value) => ({
                    completedAt: release.completedAt,
                    subject: release.subject,
                    environment: release.environment,
                    repetitions: value.repetitions,
                    p50: value.durationMs.p50,
                    p95: value.durationMs.p95,
                }));
        });
}

export function isHeadline(value: Pick<AggregatedResult, 'workload' | 'case'>): boolean {
    return headlineSeries.some(([workload, caseId]) => value.workload === workload && value.case === caseId);
}

export function seriesId(value: Pick<LatestSeriesResult, 'environment' | 'workload' | 'case'>): string {
    return `${value.environment}\u0000${seriesKey(value)}`;
}

export function seriesKey(value: Pick<AggregatedResult, 'workload' | 'case'>): string {
    return `${value.workload}\u0000${value.case}`;
}

export function label(value: Pick<AggregatedResult, 'workload' | 'case'>): string {
    return `${workloadLabel(value)} · ${value.case}`;
}

export function workloadLabel(value: Pick<AggregatedResult, 'workload'>): string {
    return workloadLabels[value.workload] || value.workload;
}

export function workloadDescription(
    value: Pick<AggregatedResult, 'workload'>,
    catalog: WorkloadCatalog,
): string | null {
    return catalog.workloads[value.workload]?.description || null;
}

export function formatRange(value: Pick<AggregatedResult, 'durationMs' | 'repetitions'>): string {
    if (value.repetitions === 1) return '—';
    return `${formatMs(value.durationMs.p95Min)}–${formatMs(value.durationMs.p95Max)}`;
}

export function percentChange(previous: number, current: number): number | null {
    return previous === 0 ? null : ((current - previous) / previous) * 100;
}

export function formatChange(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : `${value > 0 ? '+' : ''}${value.toFixed(1)}%`;
}

export function formatMs(value: number): string {
    return value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${Math.round(value)} ms`;
}

function compareSeries(left: LatestSeriesResult, right: LatestSeriesResult): number {
    return left.environment.localeCompare(right.environment)
        || left.workload.localeCompare(right.workload)
        || left.case.localeCompare(right.case);
}
