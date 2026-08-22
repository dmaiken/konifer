import type {
    AggregatedResult,
    EnvironmentCatalog,
    EnvironmentSummary,
    LatestSeriesResult,
    PerformanceHistory,
    SeriesPoint,
    WorkloadCatalog,
} from './types.ts';
import { compareReleaseRecords } from './release-version.ts';

export const headlineSeries = [
    ['upload.original', 'jpg-medium'],
    ['variant.generate.cold', 'jpg-to-webp-400'],
    ['variant.deliver.cached', 'jpg-to-webp-400'],
    ['upload.preprocess', 'jpg-medium-to-webp'],
    ['upload.rules', 'jpg-medium-landscape-rule'],
    ['upload.rules.preprocess', 'jpg-medium-landscape-rule-to-webp'],
    ['variant.eager.ready', 'jpg-medium-four-webp-profiles'],
];

export const significantChangeMinimumPercent = 5;
export const significantChangeMinimumMs = 2;

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
    const latestPassing = new Map<string, AggregatedResult>();
    const releases = [...(history.releases || [])].sort(compareReleaseRecords);

    for (const release of releases) {
        for (const result of release.results) {
            const key = seriesId({ ...result, environment: release.environment });
            const previous = latestPassing.get(key);
            const change = result.passed && previous
                ? significantChange(previous.durationMs.p95, result.durationMs.p95)
                : null;
            values.set(key, {
                ...result,
                subject: release.subject,
                environment: release.environment,
                completedAt: release.completedAt,
                hasPrevious: previous !== undefined,
                changeMs: change?.milliseconds ?? null,
                changePercent: change?.percent ?? null,
            });
            if (result.passed) latestPassing.set(key, result);
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
        .sort(compareReleaseRecords)
        .flatMap((release) => {
            if (release.environment !== selected.environment) return [];
            return release.results
                .filter((value) => value.passed && seriesKey(value) === seriesKey(selected))
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

export function significantChange(
    previous: number,
    current: number,
): { milliseconds: number; percent: number } | null {
    const percent = percentChange(previous, current);
    const milliseconds = current - previous;
    if (percent === null
        || Math.abs(percent) < significantChangeMinimumPercent
        || Math.abs(milliseconds) < significantChangeMinimumMs) {
        return null;
    }
    return { milliseconds, percent };
}

export function formatDisplayedChange(
    value: Pick<LatestSeriesResult, 'changePercent' | 'hasPrevious'>,
): string {
    if (!value.hasPrevious) return 'No previous release';
    if (value.changePercent === null) return 'No significant change';
    return `${formatChange(value.changePercent)} vs previous`;
}

export function changeClass(changePercent: number | null): string {
    if (changePercent === null || changePercent === 0) return 'muted';
    return changePercent > 0 ? 'regression' : 'improvement';
}

export function formatChange(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : `${value > 0 ? '+' : ''}${value.toFixed(1)}%`;
}

export function formatMs(value: number): string {
    if (value >= 1000) return `${(value / 1000).toFixed(2)} s`;
    if (value >= 100) return `${Math.round(value)} ms`;
    if (value >= 10) return `${Number(value.toFixed(1))} ms`;
    return `${value.toFixed(2)} ms`;
}

function compareSeries(left: LatestSeriesResult, right: LatestSeriesResult): number {
    return left.environment.localeCompare(right.environment)
        || left.workload.localeCompare(right.workload)
        || left.case.localeCompare(right.case);
}
