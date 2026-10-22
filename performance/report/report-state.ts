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

/** Read report selections from URL parameters and the active-tab fragment. */
export function requestedStateFromUrl(url: URL): RequestedReportState {
    return {
        subject: url.searchParams.get('release'),
        environment: url.searchParams.get('environment'),
        profile: url.searchParams.get('profile'),
        tab: url.hash === '#load' ? 'load' : 'benchmarks',
    };
}

/** Copy the URL and encode resolved selections while retaining unrelated parameters. */
export function urlForState(url: URL, state: ReportState): URL {
    const result = new URL(url.href);
    setParameter(result, 'release', state.subject);
    setParameter(result, 'environment', state.environment);
    setParameter(result, 'profile', state.profile);
    result.hash = state.tab === 'load' ? 'load' : 'benchmarks';
    return result;
}

/** Combine benchmark/load subjects, keep their latest completion times, and order newest releases first. */
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

/** Resolve unavailable URL selections against history/catalog defaults, honoring builds without load testing. */
export function resolveReportState(
    history: PerformanceHistory,
    loads: LoadHistory,
    environments: EnvironmentCatalog,
    loadCatalog: LoadCatalog,
    requested: RequestedReportState,
    includeLoadTesting = true,
): ReportState {
    const availableLoads: LoadHistory = includeLoadTesting ? loads : { version: 1, runs: [] };
    const tab = includeLoadTesting ? requested.tab : 'benchmarks';
    const subjects = reportSubjects(history, availableLoads);
    const preferredSubjects = tab === 'load' && availableLoads.runs.length > 0
        ? reportSubjects({ version: 1, releases: [] }, availableLoads)
        : history.releases.length > 0
            ? reportSubjects(history, { version: 1, runs: [] })
            : subjects;
    const subject = subjects.some((value) => value.subject === requested.subject)
        ? requested.subject
        : preferredSubjects[0]?.subject ?? null;
    const availableEnvironments = reportEnvironments(history, availableLoads, subject);
    const environment = choose(
        availableEnvironments,
        requested.environment,
        environments.default,
    );
    const availableProfiles = reportProfiles(availableLoads, subject, environment);
    const profile = choose(availableProfiles, requested.profile, loadCatalog.defaultProfile);
    return { subject, environment, profile, tab };
}

/** List environments with benchmark or load results for the selected subject. */
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

/** List load profiles available for the selected subject and environment. */
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

/** Choose the most recent benchmark run for the resolved subject/environment. */
export function selectedRelease(
    history: PerformanceHistory,
    state: ReportState,
): PerformanceRelease | null {
    return [...history.releases]
        .filter((value) => value.subject === state.subject && value.environment === state.environment)
        .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
        .at(-1) ?? null;
}

/** Choose the most recent load run for the resolved subject/environment/profile. */
export function selectedLoadRun(loads: LoadHistory, state: ReportState): LoadRun | null {
    return [...loads.runs]
        .filter((value) => value.subject === state.subject
            && value.environment === state.environment
            && value.profile === state.profile)
        .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
        .at(-1) ?? null;
}

/** Choose the newest release-ordered load run, optionally scoped to an environment. */
export function newestLoadRun(loads: LoadHistory, environment: string | null): LoadRun | null {
    return [...loads.runs]
        .filter((value) => !environment || value.environment === environment)
        .sort(compareReleaseRecords)
        .at(-1) ?? null;
}

/** Prefer an available requested value, then the catalog default, then the first available value. */
function choose(values: string[], requested: string | null, fallback: string): string | null {
    if (requested && values.includes(requested)) return requested;
    if (values.includes(fallback)) return fallback;
    return values[0] ?? null;
}

/** Set a nonempty selection in the URL or remove its parameter when selection is absent. */
function setParameter(url: URL, name: string, value: string | null): void {
    if (value) url.searchParams.set(name, value);
    else url.searchParams.delete(name);
}
