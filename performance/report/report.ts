import { layoutHistoryChart, linePath } from './chart.ts';
import { includeLoadTesting } from './build-options.ts';
import {
    formatCompletion,
    formatRate,
    loadPointsForStream,
    loadRunWithComparisons,
    recoveryPointsForRun,
} from './load.ts';
import {
    changeClass,
    formatDisplayedChange,
    formatMs,
    formatRange,
    isHeadline,
    label,
    latestBySeries,
    pointsForSeries,
    workloadDescription,
    workloadLabel,
} from './model.ts';
import {
    newestLoadRun,
    reportEnvironments,
    reportProfiles,
    reportSubjects,
    requestedStateFromUrl,
    resolveReportState,
    selectedLoadRun,
    selectedRelease,
    type ReportState,
    type RequestedReportState,
    urlForState,
} from './report-state.ts';
import { compareReleaseVersions } from './release-version.ts';
import type {
    AggregatedResult,
    EnvironmentCatalog,
    EnvironmentHardware,
    LatestLoadRun,
    LatestLoadStream,
    LatestSeriesResult,
    LoadCatalog,
    LoadHistory,
    LoadProfileDefinition,
    LoadRun,
    PerformanceHistory,
    PerformanceRelease,
    SeriesPoint,
    WorkloadCatalog,
} from './types.ts';

interface ReportSources {
    history: PerformanceHistory;
    loadHistory: LoadHistory;
    environments: EnvironmentCatalog;
    workloads: WorkloadCatalog;
    loadCatalog: LoadCatalog;
}

interface SelectOption {
    value: string;
    label: string;
}

interface HistoryChartOptions {
    ariaLabel?: string;
    recovery?: boolean;
    runLabel?: string;
}

interface LoadHistoryOption {
    label: string;
    description: string | null;
    points: () => SeriesPoint[];
    recovery: boolean;
}

interface DrawableMetricPoint extends SeriesPoint {
    x: number;
    y50?: number;
    y95: number;
}

type Navigate = (requested: RequestedReportState) => void;

const report = requiredReportElement();
const historyUrl = new URL('../history/releases.json', document.baseURI);
const loadHistoryUrl = new URL('../history/loads.json', document.baseURI);
const environmentsUrl = new URL('../config/environments.json', document.baseURI);
const workloadsUrl = new URL('../config/workloads.json', document.baseURI);
const loadCatalogUrl = new URL('../config/load.json', document.baseURI);

try {
    const [history, loadHistory, environments, workloads, loadCatalog] = await Promise.all([
        fetchJson<PerformanceHistory>(historyUrl),
        includeLoadTesting
            ? fetchJson<LoadHistory>(loadHistoryUrl).catch((): LoadHistory => ({ version: 1, runs: [] }))
            : Promise.resolve<LoadHistory>({ version: 1, runs: [] }),
        fetchJson<EnvironmentCatalog>(environmentsUrl).catch((): EnvironmentCatalog => ({ default: '', profiles: {} })),
        fetchJson<WorkloadCatalog>(workloadsUrl).catch((): WorkloadCatalog => ({
            suites: { release: { repetitions: 0, workloads: [] } },
            workloads: {},
        })),
        includeLoadTesting
            ? fetchJson<LoadCatalog>(loadCatalogUrl).catch((): LoadCatalog => ({ version: 1, defaultProfile: '', profiles: {} }))
            : Promise.resolve<LoadCatalog>({ version: 1, defaultProfile: '', profiles: {} }),
    ]);
    const sources = { history, loadHistory, environments, workloads, loadCatalog };
    /** Resolve the browser URL against available results and rebuild the view after history/hash navigation. */
    const renderFromLocation = () => {
        const requested = requestedStateFromUrl(new URL(window.location.href));
        const state = resolveReportState(history, loadHistory, environments, loadCatalog, requested, includeLoadTesting);
        render(sources, state, (next: RequestedReportState) => navigate(sources, next));
    };
    window.addEventListener('popstate', renderFromLocation);
    window.addEventListener('hashchange', renderFromLocation);
    renderFromLocation();
} catch (error) {
    report.replaceChildren(
        element('h1', '', 'Konifer performance'),
        element('div', 'panel error', `Unable to load benchmark history: ${errorMessage(error)}`),
    );
}

/** Resolve a requested selection, push its URL into browser history, and render the selected report. */
function navigate(sources: ReportSources, requested: RequestedReportState): void {
    const state = resolveReportState(
        sources.history,
        sources.loadHistory,
        sources.environments,
        sources.loadCatalog,
        requested,
        includeLoadTesting,
    );
    const url = urlForState(new URL(window.location.href), state);
    window.history.pushState({}, '', `${url.pathname}${url.search}${url.hash}`);
    render(sources, state, (next: RequestedReportState) => navigate(sources, next));
}

/** Replace the dashboard contents for resolved state, handling empty history and builds without load testing. */
function render(sources: ReportSources, state: ReportState, onNavigate: Navigate): void {
    const { history, loadHistory, environments, workloads, loadCatalog } = sources;
    const release = selectedRelease(history, state);
    const run = selectedLoadRun(loadHistory, state);
    const completion = [release?.completedAt, run?.completedAt].filter(Boolean).sort().at(-1);

    report.replaceChildren(
        element('h1', '', 'Konifer performance'),
        element(
            'p',
            'lead',
            includeLoadTesting
                ? 'Compare operation latency and mixed-load results across releases.'
                : 'Compare operation latency across releases.',
        ),
    );

    const subjects = reportSubjects(history, loadHistory);
    if (subjects.length === 0) {
        report.append(element('section', 'panel', 'No release results.'));
        return;
    }

    report.append(
        reportControls(sources, state, onNavigate),
        element(
            'p',
            'release-summary',
            `Selected release: ${state.subject}${completion ? ` · ${dateTime(completion)}` : ''}`,
        ),
        environmentDisclosure(state.environment, environments),
    );

    if (includeLoadTesting) report.append(tabNavigation(state, onNavigate));

    const benchmarks = includeLoadTesting
        ? tabPanel('benchmarks', state.tab === 'benchmarks')
        : element('section', '');
    benchmarks.append(benchmarkContent(history, release, workloads));
    report.append(benchmarks);

    if (includeLoadTesting) {
        const load = tabPanel('load', state.tab === 'load');
        load.append(loadContent(loadHistory, run, state, environments, workloads, loadCatalog, onNavigate));
        report.append(load);
    }
    report.append(methodology());
}

/** Fetch fresh report data and reject HTTP failures before decoding JSON. */
async function fetchJson<T>(url: URL): Promise<T> {
    const response = await fetch(url, { cache: 'no-cache' });
    if (!response.ok) throw new Error(`${url.pathname} returned HTTP ${response.status}`);
    return response.json() as Promise<T>;
}

/** Build release/environment selectors and reset dependent selections when their parent changes. */
function reportControls(sources: ReportSources, state: ReportState, onNavigate: Navigate): HTMLElement {
    const controls = element('section', 'report-controls panel');
    controls.setAttribute('aria-label', 'Report selection');
    const subjectOptions = reportSubjects(sources.history, sources.loadHistory);
    controls.append(selectControl(
        'Release',
        subjectOptions.map((value) => ({ value: value.subject, label: value.subject })),
        state.subject,
        (subject) => onNavigate({ subject, environment: null, profile: null, tab: state.tab }),
    ));

    const environmentOptions = reportEnvironments(sources.history, sources.loadHistory, state.subject);
    controls.append(selectControl(
        'Environment',
        environmentOptions.map((value) => ({
            value,
            label: sources.environments.profiles[value]?.displayName || value,
        })),
        state.environment,
        (environment) => onNavigate({ ...state, environment, profile: null }),
    ));
    return controls;
}

/** Build a labeled select with its initial value and change callback. */
function selectControl(
    name: string,
    options: SelectOption[],
    selected: string | null,
    onChange: (value: string) => void,
): HTMLLabelElement {
    const control = element('label', 'controls');
    control.append(element('span', '', name));
    const select = document.createElement('select');
    select.setAttribute('aria-label', name);
    for (const value of options) {
        const option = document.createElement('option');
        option.value = value.value;
        option.textContent = value.label;
        option.selected = value.value === selected;
        select.append(option);
    }
    select.addEventListener('change', () => onChange(select.value));
    control.append(select);
    return control;
}

/** Present the selected environment's identity and expandable hardware/interpretation notes. */
function environmentDisclosure(
    environmentId: string | null,
    catalog: EnvironmentCatalog,
): HTMLElement {
    const profile = environmentId ? catalog.profiles[environmentId] : null;
    const section = element('section', 'environment-summary panel');
    section.append(
        element('div', 'eyebrow', 'Test environment'),
        element('h2', '', profile?.displayName || environmentId || 'Unknown environment'),
        element('code', 'environment-id', environmentId || 'unknown'),
        element(
            'p',
            'environment-description',
            profile?.description || 'No environment description for this run.',
        ),
    );

    const details = document.createElement('details');
    details.append(element('summary', '', 'Hardware and test conditions'));
    if (profile?.hardware) {
        const facts: Array<[string, string | undefined]> = [
            ['System', profile.hardware.system],
            ['Processor', profile.hardware.processor],
            ['Architecture', profile.hardware.architecture],
            ['CPU topology', cpuTopology(profile.hardware)],
            ['Memory', profile.hardware.memoryGiB === undefined ? undefined : `${profile.hardware.memoryGiB} GiB`],
        ];
        const definedFacts = facts.filter((fact): fact is [string, string] => fact[1] !== undefined);
        if (definedFacts.length > 0) details.append(descriptionList(definedFacts));
    }
    if (profile?.interpretation?.length) {
        const notes = document.createElement('ul');
        profile.interpretation.forEach((note) => notes.append(element('li', '', note)));
        details.append(notes);
    }
    section.append(details);
    return section;
}

/** Format optional core/thread counts without inventing missing hardware values. */
function cpuTopology(hardware: EnvironmentHardware): string | undefined {
    if (hardware.physicalCores === undefined && hardware.logicalCpus === undefined) return undefined;
    return `${hardware.physicalCores ?? '—'} physical cores / ${hardware.logicalCpus ?? '—'} logical CPUs`;
}

/** Build a semantic definition list for environment hardware facts. */
function descriptionList(facts: Array<[string, string | number]>): HTMLDListElement {
    const list = element('dl', 'environment-facts');
    for (const [name, value] of facts) {
        list.append(element('dt', '', name), element('dd', '', String(value)));
    }
    return list;
}

/** Build accessible benchmark/load tabs with click and arrow/Home/End keyboard navigation. */
function tabNavigation(state: ReportState, onNavigate: Navigate): HTMLElement {
    const tabs = element('div', 'tabs');
    tabs.setAttribute('role', 'tablist');
    tabs.setAttribute('aria-label', 'Performance report');
    const definitions = [
        ['benchmarks', 'Benchmark latency'],
        ['load', 'Mixed load'],
    ] as const;
    const buttons = definitions.map(([id, text]) => {
        const button = element('button', 'tab', text);
        button.type = 'button';
        button.id = `tab-${id}`;
        button.setAttribute('role', 'tab');
        button.setAttribute('aria-controls', `panel-${id}`);
        button.setAttribute('aria-selected', String(state.tab === id));
        button.tabIndex = state.tab === id ? 0 : -1;
        button.addEventListener('click', () => onNavigate({ ...state, tab: id }));
        tabs.append(button);
        return button;
    });
    tabs.addEventListener('keydown', (event: KeyboardEvent) => {
        const current = buttons.findIndex((button) => button === document.activeElement);
        if (current < 0) return;
        let next = null;
        if (event.key === 'ArrowRight') next = (current + 1) % buttons.length;
        if (event.key === 'ArrowLeft') next = (current - 1 + buttons.length) % buttons.length;
        if (event.key === 'Home') next = 0;
        if (event.key === 'End') next = buttons.length - 1;
        if (next === null) return;
        event.preventDefault();
        buttons[next].focus();
        buttons[next].click();
    });
    return tabs;
}

/** Create a tab panel with linked ARIA IDs and selected-state visibility. */
function tabPanel(id: string, selected: boolean): HTMLElement {
    const panel = element('section', 'tab-panel');
    panel.id = `panel-${id}`;
    panel.setAttribute('role', 'tabpanel');
    panel.setAttribute('aria-labelledby', `tab-${id}`);
    panel.hidden = !selected;
    return panel;
}

/** Render selected-release tables and history restricted to the same environment through that release. */
function benchmarkContent(
    history: PerformanceHistory,
    release: PerformanceRelease | null,
    workloads: WorkloadCatalog,
): DocumentFragment | HTMLElement {
    if (!release) {
        return emptyState('No benchmark results for this release and environment.');
    }
    const content = document.createDocumentFragment();
    if (release.notes?.length) content.append(notesPanel('Release notes', release.notes));

    const scopedHistory: PerformanceHistory = {
        version: 1,
        releases: history.releases.filter((value) => value.environment === release.environment
            && compareReleaseVersions(value.subject, release.subject) <= 0),
    };
    const latest = latestBySeries(scopedHistory).filter((value) => value.subject === release.subject);
    const common = latest.filter(isHeadline);
    const encode = latest.filter((value) => value.workload === 'format.encode');
    const decode = latest.filter((value) => value.workload === 'format.decode');

    content.append(
        sectionIntro(
            'Operation latency',
            'Compare releases using the same measurement protocol for each operation. See the environment notes for test conditions.',
        ),
        resultTable('Common operations', common, workloads),
        resultTable('Format encoding', encode, workloads),
        resultTable('Format decoding', decode, workloads),
        ...(latest.length > 0 ? [seriesExplorer(scopedHistory, latest, workloads)] : []),
    );
    return content;
}

/** Build a section heading and its explanatory paragraph. */
function sectionIntro(title: string, description: string): HTMLElement {
    const header = element('header', 'section-intro');
    header.append(element('h2', '', title), element('p', '', description));
    return header;
}

/** Build a workload/case picker and redraw its passing-result chart when selection changes. */
function seriesExplorer(
    history: PerformanceHistory,
    values: LatestSeriesResult[],
    workloads: WorkloadCatalog,
): HTMLElement {
    const panel = element('section', 'panel');
    panel.append(element('h2', '', 'Release history'));
    const controls = element('label', 'controls');
    controls.append(element('span', '', 'Workload and case'));
    const select = document.createElement('select');
    values.forEach((value, index) => {
        const option = document.createElement('option');
        option.value = String(index);
        option.textContent = label(value);
        select.append(option);
    });
    controls.append(select);
    const description = element('p', 'workload-description');
    const legend = chartLegend([['p50', 'var(--p50)'], ['p95', 'var(--p95)']]);
    const chartHost = element('div');
    /** Redraw the selected benchmark series without changing the release/environment selection. */
    const update = () => {
        const selected = values[Number(select.value)];
        const selectedDescription = workloadDescription(selected, workloads);
        description.textContent = selectedDescription || '';
        description.hidden = !selectedDescription;
        chartHost.replaceChildren(historyChart(pointsForSeries(history, selected)));
    };
    select.addEventListener('change', update);
    update();
    panel.append(controls, description, legend, chartHost);
    return panel;
}

/** Render the selected load run, or offer navigation to available results when that selection is empty. */
function loadContent(
    loadHistory: LoadHistory,
    selected: LoadRun | null,
    state: ReportState,
    environments: EnvironmentCatalog,
    workloads: WorkloadCatalog,
    loadCatalog: LoadCatalog,
    onNavigate: Navigate,
): DocumentFragment | HTMLElement {
    if (!selected) {
        const latest = newestLoadRun(loadHistory, state.environment) || newestLoadRun(loadHistory, null);
        const empty = emptyState(`No mixed-load results for ${state.subject} in this environment.`);
        if (latest) {
            const button = element('button', 'button-link', `View ${latest.subject} mixed-load results`);
            button.type = 'button';
            button.addEventListener('click', () => onNavigate({
                subject: latest.subject,
                environment: latest.environment,
                profile: latest.profile,
                tab: 'load',
            }));
            empty.append(button);
        }
        return empty;
    }

    const run = loadRunWithComparisons(loadHistory, selected);
    const profile = loadCatalog.profiles[run.profile];
    const environmentName = environments.profiles[run.environment]?.displayName || run.environment;
    const content = document.createDocumentFragment();
    const profiles = reportProfiles(loadHistory, run.subject, run.environment);
    const intro = sectionIntro(
        'Mixed-load results',
        run.passed
            ? `Konifer completed ${run.operations.toLocaleString()} operations in ${formatMinutes(run.trafficDurationSeconds)} on ${environmentName} with ${run.errors} errors. Peak target: ${formatRate(run.peakRatePerMinute)}.`
            : `The ${run.profile} run on ${environmentName} failed its checks. Review the traffic streams and recovery result.`,
    );
    content.append(intro);

    if (profiles.length > 1) {
        const selector = element('section', 'profile-control panel-subtle');
        selector.append(selectControl(
            'Load profile',
            profiles.map((value) => ({ value, label: value })),
            run.profile,
            (profileId) => onNavigate({ ...state, profile: profileId, tab: 'load' }),
        ));
        content.append(selector);
    }
    if (run.notes?.length) content.append(notesPanel('Load run notes', run.notes));
    content.append(loadSummary(run), trafficMix(run.streams), loadHistoryExplorer(loadHistory, run, workloads));

    const tablePanel = element('section', 'panel');
    tablePanel.append(element('h2', '', 'Traffic streams'), loadStreamTable(run.streams));
    content.append(tablePanel);
    return content;
}

/** Show the run's traffic completion, errors, and post-load recovery alongside its identity. */
function loadSummary(run: LatestLoadRun): HTMLElement {
    const panel = element('section', 'panel load-summary');
    panel.append(
        element('div', 'load-heading', `${run.subject} · ${run.profile}`),
        element('div', 'result-case', `${run.environment} · ${dateTime(run.completedAt)}`),
    );
    const facts = element('div', 'load-facts');
    const values = [
        ['Result', run.passed ? 'Passed' : 'Failed'],
        ['Peak target', formatRate(run.peakRatePerMinute)],
        ['Executed / scheduled', `${run.operations}/${run.targetOperations} (${formatCompletion(run.operations, run.targetOperations)})`],
        ['Duration', formatMinutes(run.trafficDurationSeconds)],
        ['Errors / dropped', `${run.errors} / ${run.droppedIterations}`],
        ['Recovery', run.recovery.passed ? `Passed · ${formatMs(run.recovery.durationMs)}` : 'Failed'],
    ];
    for (const [name, value] of values) {
        const fact = element('div', 'load-fact');
        fact.append(element('span', 'load-fact-name', name), element('strong', '', value));
        facts.append(fact);
    }
    panel.append(facts);
    return panel;
}

/** Show proportional peak target rates with an accessible text description and legend. */
function trafficMix(streams: LatestLoadStream[]): HTMLElement {
    const panel = element('section', 'panel');
    panel.append(
        element('h2', '', 'Traffic mix at peak'),
        element('p', 'workload-description', 'Target rates for each operation at peak load.'),
    );
    const total = streams.reduce((sum, stream) => sum + stream.targetRatePerMinute, 0);
    const bar = element('div', 'traffic-bar');
    bar.setAttribute('role', 'img');
    bar.setAttribute('aria-label', streams.map((stream) => `${workloadLabel(stream)} ${stream.targetRatePerMinute} per minute`).join(', '));
    const legend = element('div', 'traffic-legend');
    streams.forEach((stream, index) => {
        const color = `var(--stream-${(index % 6) + 1})`;
        const segment = element('span', 'traffic-segment');
        segment.style.width = `${(stream.targetRatePerMinute / total) * 100}%`;
        segment.style.background = color;
        segment.title = `${workloadLabel(stream)} · ${stream.targetRatePerMinute}/min`;
        bar.append(segment);
        const item = element('div', 'traffic-key');
        const swatch = element('span', 'traffic-swatch');
        swatch.style.background = color;
        item.append(swatch, element('span', '', workloadLabel(stream)), element('strong', '', `${stream.targetRatePerMinute}/min`));
        legend.append(item);
    });
    panel.append(bar, legend);
    return panel;
}

/** Build a stream/recovery metric picker and charts scoped to comparable load history. */
function loadHistoryExplorer(
    history: LoadHistory,
    run: LatestLoadRun,
    workloads: WorkloadCatalog,
): HTMLElement {
    const panel = element('section', 'panel');
    panel.append(
        element('h2', '', 'Mixed-load history'),
        element('p', 'workload-description', 'Compare passing results within the same environment and load profile.'),
    );
    const options: LoadHistoryOption[] = [
        ...run.streams.map((stream) => ({
            label: `${workloadLabel(stream)} · ${stream.case}`,
            description: workloadDescription(stream, workloads),
            points: () => loadPointsForStream(history, run, stream),
            recovery: false,
        })),
        {
            label: 'Eager readiness after load',
            description: 'After traffic stops, measure the wait from upload acceptance until four eager variants are ready.',
            points: () => recoveryPointsForRun(history, run),
            recovery: true,
        },
    ];
    const control = element('label', 'controls');
    control.append(element('span', '', 'Metric'));
    const select = document.createElement('select');
    options.forEach((value, index) => {
        const option = document.createElement('option');
        option.value = String(index);
        option.textContent = value.label;
        select.append(option);
    });
    control.append(select);
    const description = element('p', 'workload-description');
    const legendHost = element('div');
    const chartHost = element('div');
    /** Switch the metric legend and chart together, including the single-series recovery view. */
    const update = () => {
        const option = options[Number(select.value)];
        description.textContent = option.description || '';
        description.hidden = !option.description;
        legendHost.replaceChildren(option.recovery
            ? chartLegend([['Recovery', 'var(--p95)']])
            : chartLegend([['p50', 'var(--p50)'], ['p95', 'var(--p95)']]));
        chartHost.replaceChildren(historyChart(option.points(), {
            ariaLabel: `${option.label} by release`,
            recovery: option.recovery,
            runLabel: 'load run',
        }));
    };
    select.addEventListener('change', update);
    update();
    panel.append(control, description, legendHost, chartHost);
    return panel;
}

/** Build color-keyed labels for chart metrics. */
function chartLegend(values: Array<[string, string]>): HTMLElement {
    const legend = element('div', 'legend');
    for (const [name, color] of values) {
        const item = element('span', '', name);
        item.style.setProperty('--series-color', color);
        legend.append(item);
    }
    return legend;
}

/** Render p50/p95 history or a single recovery series with shared axes, points, and release labels. */
function historyChart(points: SeriesPoint[], options: HistoryChartOptions = {}): SVGSVGElement {
    const width = 920;
    const height = 310;
    const bounds = { left: 62, right: 20, top: 20, bottom: 45 };
    const layout = layoutHistoryChart(points, width, height, bounds);
    const svg = svgElement('svg', {
        class: 'chart',
        viewBox: `0 0 ${width} ${height}`,
        role: 'img',
        'aria-label': options.ariaLabel || 'p50 and p95 latency by release',
    });
    for (const gridLine of layout.gridLines) {
        svg.append(
            svgElement('line', { class: 'grid-line', x1: bounds.left, y1: gridLine.y, x2: width - bounds.right, y2: gridLine.y }),
            svgText(bounds.left - 9, gridLine.y + 4, formatMs(gridLine.value), 'end'),
        );
    }
    if (!options.recovery) {
        svg.append(svgElement('path', { class: 'trend-p50', d: linePath(layout.points, (value) => value.y50) }));
    }
    svg.append(svgElement('path', { class: 'trend-p95', d: linePath(layout.points, (value) => value.y95) }));
    layout.points.forEach((point, index) => {
        if (!options.recovery) svg.append(metricPoint(point, 'p50', 'y50', options.runLabel));
        svg.append(metricPoint(point, 'p95', 'y95', options.runLabel, options.recovery ? 'recovery' : undefined));
        if (layout.points.length <= 8 || index === 0 || index === layout.points.length - 1) {
            svg.append(svgText(
                point.x,
                height - 17,
                point.subject,
                index === 0 ? 'start' : index === layout.points.length - 1 ? 'end' : 'middle',
            ));
        }
    });
    return svg;
}

/** Build an SVG point with a tooltip identifying its release, latency, and repetition count. */
function metricPoint(
    point: DrawableMetricPoint,
    metric: 'p50' | 'p95',
    yProperty: 'y50' | 'y95',
    itemLabel = 'repetition',
    metricLabel: string = metric,
): SVGCircleElement {
    const y = point[yProperty];
    if (y === undefined) throw new Error(`Missing ${yProperty} chart coordinate`);
    const circle = svgElement('circle', { class: `point-${metric}`, cx: point.x, cy: y, r: 4 });
    const title = svgElement('title');
    const countLabel = point.repetitions === 1 ? `1 ${itemLabel}` : `${point.repetitions} ${itemLabel}s`;
    title.textContent = `${point.subject}: ${formatMs(point[metric])} ${metricLabel} · ${countLabel}`;
    circle.append(title);
    return circle;
}

/** Render benchmark results, suppressing latency/comparisons for failed cases and omitting empty sections. */
function resultTable(
    title: string,
    results: LatestSeriesResult[],
    workloads: WorkloadCatalog,
): DocumentFragment | HTMLElement {
    if (results.length === 0) return document.createDocumentFragment();
    const section = element('section', 'panel');
    section.append(element('h2', '', title));
    const workloadIds = new Set(results.map((value) => value.workload));
    if (workloadIds.size === 1) {
        const description = workloadDescription(results[0], workloads);
        if (description) section.append(element('p', 'workload-description', description));
    }
    const table = document.createElement('table');
    const headings = ['Operation', 'Result', 'p50', 'p95', 'p95 range', 'Repetitions', 'p95 change', 'Operations', 'Errors / dropped'];
    const header = document.createElement('tr');
    headings.forEach((value) => header.append(element('th', '', value)));
    const head = document.createElement('thead');
    head.append(header);
    const body = document.createElement('tbody');
    for (const value of results) {
        const row = document.createElement('tr');
        if (!value.passed) row.classList.add('failed-result');
        const description = workloadIds.size > 1 ? workloadDescription(value, workloads) : null;
        row.append(operationCell(value, description));
        const measurements = value.passed
            ? [formatMs(value.durationMs.p50), formatMs(value.durationMs.p95), formatRange(value)]
            : ['—', '—', '—'];
        const cells: Array<{ value: string | number; className?: string }> = [
            { value: value.passed ? 'Passed' : 'Failed', className: value.passed ? '' : 'regression' },
            ...measurements.map((measurement) => ({ value: measurement })),
            { value: value.repetitions },
            { value: value.passed ? formatDisplayedChange(value) : '—', className: value.passed ? changeClass(value.changePercent) : 'muted' },
            { value: value.operations },
            { value: `${value.errors} / ${value.droppedIterations}` },
        ];
        cells.forEach((cell) => row.append(element('td', cell.className || '', String(cell.value))));
        body.append(row);
    }
    table.append(head, body);
    section.append(table);
    return section;
}

/** Render load stream completion and latency comparisons, hiding latency for failed streams. */
function loadStreamTable(streams: LatestLoadStream[]): HTMLTableElement {
    const table = document.createElement('table');
    const header = document.createElement('tr');
    ['Operation', 'Result', 'Peak target', 'Operations', 'p95', 'p95 change', 'Errors / dropped']
        .forEach((value) => header.append(element('th', '', value)));
    const head = document.createElement('thead');
    head.append(header);
    const body = document.createElement('tbody');
    for (const stream of streams) {
        const row = document.createElement('tr');
        if (!stream.passed) row.classList.add('failed-result');
        row.append(operationCell(stream));
        const cells = [
            { value: stream.passed ? 'Passed' : 'Failed', className: stream.passed ? '' : 'regression' },
            { value: `${stream.targetRatePerMinute}/min` },
            { value: `${stream.operations}/${stream.plannedOperations}` },
            { value: stream.passed ? formatMs(stream.durationMs.p95) : '—' },
            { value: stream.passed ? formatDisplayedChange(stream) : '—', className: stream.passed ? changeClass(stream.changePercent) : 'muted' },
            { value: `${stream.errors} / ${stream.droppedIterations}` },
        ];
        cells.forEach((cell) => row.append(element('td', cell.className || '', String(cell.value))));
        body.append(row);
    }
    table.append(head, body);
    return table;
}

/** Render workload/case identity, optional description, and operator annotations as text. */
function operationCell(
    value: Pick<AggregatedResult, 'workload' | 'case' | 'notes'>,
    description: string | null = null,
): HTMLTableCellElement {
    const cell = element('td', 'operation');
    cell.append(
        element('div', 'operation-label', workloadLabel(value)),
        element('div', 'result-case', value.case),
        ...(description ? [element('p', 'operation-description', description)] : []),
        ...(value.notes?.length ? [notesList(value.notes)] : []),
    );
    return cell;
}

/** Build a consistent no-results panel for an unavailable report selection. */
function emptyState(message: string): HTMLElement {
    const section = element('section', 'panel empty-state');
    section.append(element('h2', '', 'Results not available'), element('p', '', message));
    return section;
}

/** Render comparison/measurement guidance and a link to the benchmark protocol. */
function methodology(): HTMLElement {
    const footer = document.createElement('footer');
    footer.append(
        document.createTextNode(includeLoadTesting
            ? 'Compare results within the same environment and versioned workload or load profile. '
            : 'Compare results within the same environment and versioned workload. '),
        document.createTextNode('For benchmarks, read p50 and p95 as medians across repetitions. '),
        document.createTextNode(includeLoadTesting
            ? 'A load test passes with successful requests and checks, no dropped iterations, and a passing recovery check. It has no latency limit. '
            : ''),
        document.createTextNode('Compare p95 with the last passing result. Change labels require differences of at least 5% and 2 ms. '),
        link('../BENCHMARKING.md', 'Benchmark methodology.'),
    );
    return footer;
}

/** Wrap operator notes in a titled panel. */
function notesPanel(title: string, notes: string[]): HTMLElement {
    const panel = element('section', 'panel notes-panel');
    panel.append(element('h2', '', title), notesList(notes));
    return panel;
}

/** Render operator notes as text nodes rather than interpreting their contents as HTML. */
function notesList(notes: string[]): HTMLUListElement {
    const list = element('ul', 'result-notes');
    notes.forEach((note) => list.append(element('li', '', note)));
    return list;
}

/** Format traffic duration in minutes with at most one decimal place. */
function formatMinutes(seconds: number): string {
    const minutes = seconds / 60;
    return `${Number(minutes.toFixed(1))} min`;
}

/** Build an SVG axis label at the supplied coordinates and text anchor. */
function svgText(
    x: number,
    y: number,
    text: string,
    anchor: 'start' | 'middle' | 'end',
): SVGTextElement {
    const value = svgElement('text', { class: 'axis-label', x, y, 'text-anchor': anchor });
    value.textContent = text;
    return value;
}

/** Create a typed SVG element in the SVG namespace and apply stringified attributes. */
function svgElement<K extends keyof SVGElementTagNameMap>(
    name: K,
    attributes: Record<string, string | number> = {},
): SVGElementTagNameMap[K] {
    const value = document.createElementNS('http://www.w3.org/2000/svg', name);
    Object.entries(attributes).forEach(([key, attribute]) => value.setAttribute(key, String(attribute)));
    return value;
}

/** Create a typed HTML element with optional CSS classes and text content. */
function element<K extends keyof HTMLElementTagNameMap>(
    name: K,
    className = '',
    text?: string,
): HTMLElementTagNameMap[K] {
    const value = document.createElement(name);
    if (className) value.className = className;
    if (text !== undefined) value.textContent = text;
    return value;
}

/** Create a text-only anchor with the supplied destination. */
function link(href: string, text: string): HTMLAnchorElement {
    const value = element('a', '', text);
    value.href = href;
    return value;
}

/** Format an ISO completion timestamp using the browser's locale and time zone. */
function dateTime(value: string): string {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

/** Convert a fetch/render failure into a printable dashboard error. */
function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

/** Require the report host element before attaching dashboard content. */
function requiredReportElement(): HTMLElement {
    const value = document.querySelector<HTMLElement>('#report');
    if (!value) throw new Error('Performance report container was not found');
    return value;
}
