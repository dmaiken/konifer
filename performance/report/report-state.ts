import type {
    EnvironmentCatalog,
    LoadCatalog,
    LoadHistory,
    LoadRun,
    PerformanceHistory,
    PerformanceRelease,
} from './types.ts';
import { compareReleaseRecords, compareReleaseVersions } from './release-version.ts';

export type ReportTab = 'benchmarks' | 'load';

export interface RequestedReportState {
    subject: string | null;
    environment: string | null;
    profile: string | null;
    tab: ReportTab;
}

export interface ReportState extends RequestedReportState {}

export interface ReportSubject {
    subject: string;
    completedAt: string;
}

export function requestedStateFromUrl(url: URL): RequestedReportState {
    return {
        subject: url.searchParams.get('release'),
        environment: url.searchParams.get('environment'),
        profile: url.searchParams.get('profile'),
        tab: url.hash === '#load' ? 'load' : 'benchmarks',
    };
}

export function urlForState(url: URL, state: ReportState): URL {
    const result = new URL(url.href);
    setParameter(result, 'release', state.subject);
    setParameter(result, 'environment', state.environment);
    setParameter(result, 'profile', state.profile);
    result.hash = state.tab === 'load' ? 'load' : 'benchmarks';
    return result;
}

export function reportSubjects(history: PerformanceHistory, loads: LoadHistory): ReportSubject[] {
    const subjects = new Map<string, string>();
    for (const value of [...history.releases, ...loads.runs]) {
        const previous = subjects.get(value.subject);
        if (!previous || previous.localeCompare(value.completedAt) < 0) {
            subjects.set(value.subject, value.completedAt);
        }
    }
    return [...subjects]
        .map(([subject, completedAt]) => ({ subject, completedAt }))
        .sort((left, right) => compareReleaseVersions(right.subject, left.subject));
}

export function resolveReportState(
    history: PerformanceHistory,
    loads: LoadHistory,
    environments: EnvironmentCatalog,
    loadCatalog: LoadCatalog,
    requested: RequestedReportState,
): ReportState {
    const subjects = reportSubjects(history, loads);
    const preferredSubjects = requested.tab === 'load' && loads.runs.length > 0
        ? reportSubjects({ version: 1, releases: [] }, loads)
        : history.releases.length > 0
            ? reportSubjects(history, { version: 1, runs: [] })
            : subjects;
    const subject = subjects.some((value) => value.subject === requested.subject)
        ? requested.subject
        : preferredSubjects[0]?.subject ?? null;
    const availableEnvironments = reportEnvironments(history, loads, subject);
    const environment = choose(
        availableEnvironments,
        requested.environment,
        environments.default,
    );
    const availableProfiles = reportProfiles(loads, subject, environment);
    const profile = choose(availableProfiles, requested.profile, loadCatalog.defaultProfile);
    const tab = requested.tab;
    return { subject, environment, profile, tab };
}

export function reportEnvironments(
    history: PerformanceHistory,
    loads: LoadHistory,
    subject: string | null,
): string[] {
    if (!subject) return [];
    return [...new Set([
        ...history.releases.filter((value) => value.subject === subject).map((value) => value.environment),
        ...loads.runs.filter((value) => value.subject === subject).map((value) => value.environment),
    ])].sort();
}

export function reportProfiles(
    loads: LoadHistory,
    subject: string | null,
    environment: string | null,
): string[] {
    if (!subject || !environment) return [];
    return [...new Set(loads.runs
        .filter((value) => value.subject === subject && value.environment === environment)
        .map((value) => value.profile))]
        .sort();
}

export function selectedRelease(
    history: PerformanceHistory,
    state: ReportState,
): PerformanceRelease | null {
    return [...history.releases]
        .filter((value) => value.subject === state.subject && value.environment === state.environment)
        .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
        .at(-1) ?? null;
}

export function selectedLoadRun(loads: LoadHistory, state: ReportState): LoadRun | null {
    return [...loads.runs]
        .filter((value) => value.subject === state.subject
            && value.environment === state.environment
            && value.profile === state.profile)
        .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
        .at(-1) ?? null;
}

export function newestLoadRun(loads: LoadHistory, environment: string | null): LoadRun | null {
    return [...loads.runs]
        .filter((value) => !environment || value.environment === environment)
        .sort(compareReleaseRecords)
        .at(-1) ?? null;
}

function choose(values: string[], requested: string | null, fallback: string): string | null {
    if (requested && values.includes(requested)) return requested;
    if (values.includes(fallback)) return fallback;
    return values[0] ?? null;
}

function setParameter(url: URL, name: string, value: string | null): void {
    if (value) url.searchParams.set(name, value);
    else url.searchParams.delete(name);
}
