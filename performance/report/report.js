import { layoutHistoryChart, layoutSparkline, linePath } from './chart.ts';
import {
    formatChange,
    formatMs,
    formatRange,
    environmentsForHistory,
    isHeadline,
    label,
    latestBySeries,
    pointsForSeries,
    workloadDescription,
    workloadLabel,
} from './model.ts';

const report = document.querySelector('#report');
const historyUrl = new URL('../history/releases.json', document.baseURI);
const environmentsUrl = new URL('../config/environments.json', document.baseURI);
const workloadsUrl = new URL('../config/workloads.json', document.baseURI);

try {
    const [history, environments, workloads] = await Promise.all([
        fetchJson(historyUrl),
        fetchJson(environmentsUrl).catch(() => ({ default: '', profiles: {} })),
        fetchJson(workloadsUrl).catch(() => ({ suites: {}, workloads: {} })),
    ]);
    render(history, environments, workloads);
} catch (error) {
    report.replaceChildren(
        element('h1', '', 'Konifer performance'),
        element('div', 'panel error', `Unable to load benchmark history: ${error.message}`),
    );
}

function render(history, environments, workloads) {
    const latest = latestBySeries(history);
    const common = latest.filter(isHeadline);
    const encode = latest.filter((value) => value.workload === 'format.encode');
    const decode = latest.filter((value) => value.workload === 'format.decode');
    const lastRelease = [...(history.releases || [])].sort((left, right) => left.completedAt.localeCompare(right.completedAt)).at(-1);

    report.replaceChildren(
        element('h1', '', 'Konifer performance'),
        element('p', 'lead', 'Release benchmark history for common image workflows. Every result—including regressions and unchanged performance—is retained.'),
        element('p', 'muted', lastRelease ? `Latest published run: ${dateTime(lastRelease.completedAt)}` : 'No release benchmark has been published yet.'),
    );

    if (latest.length === 0) {
        report.append(element('section', 'panel', 'No release benchmark has been published yet.'));
        return;
    }

    report.append(
        environmentSection(history, environments),
        sectionTitle('Common workflows'),
        headlineGrid(history, common, workloads),
        seriesExplorer(history, latest, workloads),
        resultTable('Common workflows', common, workloads),
        resultTable('Format encoding', encode, workloads),
        resultTable('Format decoding', decode, workloads),
        methodology(),
    );
}

async function fetchJson(url) {
    const response = await fetch(url, { cache: 'no-cache' });
    if (!response.ok) throw new Error(`${url.pathname} returned HTTP ${response.status}`);
    return response.json();
}

function environmentSection(history, catalog) {
    const section = document.createElement('section');
    section.append(element('h2', '', 'Benchmark environment'));
    const grid = element('div', 'environment-grid');

    for (const environment of environmentsForHistory(history, catalog)) {
        const card = element('article', 'panel environment');
        const heading = element('h3', '', environment.displayName);
        const id = element('code', 'environment-id', environment.id);
        const description = element(
            'p',
            'environment-description',
            environment.description || 'No descriptive metadata is available for this historical environment.',
        );
        card.append(heading, id, description);

        if (environment.hardware) {
            const facts = [
                ['System', environment.hardware.system],
                ['Processor', environment.hardware.processor],
                ['Architecture', environment.hardware.architecture],
                ['CPU topology', cpuTopology(environment.hardware)],
                ['Memory', environment.hardware.memoryGiB === undefined ? undefined : `${environment.hardware.memoryGiB} GiB`],
            ].filter(([, value]) => value !== undefined);
            if (facts.length > 0) card.append(descriptionList(facts));
        }

        if (environment.interpretation.length > 0) {
            card.append(element('h4', '', 'How to interpret these results'));
            const notes = document.createElement('ul');
            environment.interpretation.forEach((note) => notes.append(element('li', '', note)));
            card.append(notes);
        }
        grid.append(card);
    }

    section.append(grid);
    return section;
}

function cpuTopology(hardware) {
    if (hardware.physicalCores === undefined && hardware.logicalCpus === undefined) return undefined;
    return `${hardware.physicalCores ?? '—'} physical cores / ${hardware.logicalCpus ?? '—'} logical CPUs`;
}

function descriptionList(facts) {
    const list = element('dl', 'environment-facts');
    for (const [name, value] of facts) {
        list.append(element('dt', '', name), element('dd', '', value));
    }
    return list;
}

function sectionTitle(text) {
    return element('h2', '', text);
}

function headlineGrid(history, values, workloads) {
    const grid = element('section', 'grid');
    for (const value of values) {
        const card = element('article', 'panel');
        const change = element('div', value.changePercent > 0 ? 'regression' : 'muted', `${formatChange(value.changePercent)} vs previous`);
        const description = workloadDescription(value, workloads);
        card.append(
            element('div', 'chart-title', workloadLabel(value)),
            element('div', 'result-case', value.case),
            ...(description ? [element('p', 'workload-description', description)] : []),
            element('div', 'chart-value', `${formatMs(value.durationMs.p95)} p95`),
            change,
            sparkline(pointsForSeries(history, value)),
        );
        grid.append(card);
    }
    return grid;
}

function seriesExplorer(history, values, workloads) {
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

    const legend = element('div', 'legend');
    const p50Legend = element('span', '', 'p50');
    p50Legend.style.setProperty('--series-color', 'var(--p50)');
    const p95Legend = element('span', '', 'p95');
    p95Legend.style.setProperty('--series-color', 'var(--p95)');
    legend.append(p50Legend, p95Legend);

    const chartHost = element('div');
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

function historyChart(points) {
    const width = 920;
    const height = 310;
    const bounds = { left: 62, right: 20, top: 20, bottom: 45 };
    const layout = layoutHistoryChart(points, width, height, bounds);
    const svg = svgElement('svg', { class: 'chart', viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': 'p50 and p95 latency by release' });

    for (const gridLine of layout.gridLines) {
        svg.append(
            svgElement('line', { class: 'grid-line', x1: bounds.left, y1: gridLine.y, x2: width - bounds.right, y2: gridLine.y }),
            svgText(bounds.left - 9, gridLine.y + 4, formatMs(gridLine.value), 'end'),
        );
    }

    svg.append(
        svgElement('path', { class: 'trend-p50', d: linePath(layout.points, (value) => value.y50) }),
        svgElement('path', { class: 'trend-p95', d: linePath(layout.points, (value) => value.y95) }),
    );

    layout.points.forEach((point, index) => {
        svg.append(metricPoint(point, 'p50', 'y50'), metricPoint(point, 'p95', 'y95'));
        if (layout.points.length <= 8 || index === 0 || index === layout.points.length - 1) {
            const text = svgText(point.x, height - 17, point.subject, index === 0 ? 'start' : index === layout.points.length - 1 ? 'end' : 'middle');
            svg.append(text);
        }
    });

    return svg;
}

function sparkline(points) {
    const width = 500;
    const height = 90;
    const padding = 8;
    const coordinates = layoutSparkline(points, width, height, padding);
    const svg = svgElement('svg', { class: 'sparkline', viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': 'p95 latency history' });
    svg.append(
        svgElement('line', { class: 'axis', x1: 0, y1: height - 1, x2: width, y2: height - 1 }),
        svgElement('path', { class: 'trend-p95', d: linePath(coordinates, (value) => value.y) }),
    );
    coordinates.forEach((point) => svg.append(metricPoint({ ...point, y95: point.y }, 'p95', 'y95')));
    return svg;
}

function metricPoint(point, metric, yProperty) {
    const circle = svgElement('circle', { class: `point-${metric}`, cx: point.x, cy: point[yProperty], r: 4 });
    const title = svgElement('title');
    const repetitionLabel = point.repetitions === 1 ? '1 repetition' : `${point.repetitions} repetitions`;
    title.textContent = `${point.subject}: ${formatMs(point[metric])} ${metric} · ${repetitionLabel}`;
    circle.append(title);
    return circle;
}

function resultTable(title, results, workloads) {
    if (results.length === 0) return document.createDocumentFragment();
    const section = element('section', 'panel');
    section.append(element('h2', '', title));
    const workloadIds = new Set(results.map((value) => value.workload));
    if (workloadIds.size === 1) {
        const description = workloadDescription(results[0], workloads);
        if (description) section.append(element('p', 'workload-description', description));
    }
    const table = document.createElement('table');
    const headings = ['Operation', 'p50', 'p95', 'p95 range', 'Repetitions', 'Change', 'Operations', 'Errors', 'Dropped', 'Version'];
    const header = document.createElement('tr');
    headings.forEach((value) => header.append(element('th', '', value)));
    const head = document.createElement('thead');
    head.append(header);
    const body = document.createElement('tbody');

    for (const value of results) {
        const row = document.createElement('tr');
        row.append(operationCell(value));
        const cells = [
            { value: formatMs(value.durationMs.p50) },
            { value: formatMs(value.durationMs.p95) },
            { value: formatRange(value) },
            { value: value.repetitions },
            { value: formatChange(value.changePercent), className: value.changePercent > 0 ? 'regression' : '' },
            { value: value.operations },
            { value: value.errors },
            { value: value.droppedIterations },
            { value: value.subject },
        ];
        cells.forEach((cell) => {
            row.append(element('td', cell.className || '', String(cell.value)));
        });
        body.append(row);
    }

    table.append(head, body);
    section.append(table);
    return section;
}

function operationCell(value) {
    const cell = element('td', 'operation');
    cell.append(
        element('div', 'operation-label', workloadLabel(value)),
        element('div', 'result-case', value.case),
    );
    return cell;
}

function methodology() {
    const footer = document.createElement('footer');
    footer.append(
        document.createTextNode('Each result reports its repetition count. With multiple repetitions, latency is the median of the run-level percentiles. Compare results only within the same environment profile. '),
        link('../BENCHMARKING.md', 'Read the benchmark methodology.'),
    );
    return footer;
}

function svgText(x, y, text, anchor) {
    const value = svgElement('text', { class: 'axis-label', x, y, 'text-anchor': anchor });
    value.textContent = text;
    return value;
}

function svgElement(name, attributes = {}) {
    const value = document.createElementNS('http://www.w3.org/2000/svg', name);
    Object.entries(attributes).forEach(([key, attribute]) => value.setAttribute(key, attribute));
    return value;
}

function element(name, className = '', text = undefined) {
    const value = document.createElement(name);
    if (className) value.className = className;
    if (text !== undefined) value.textContent = text;
    return value;
}

function link(href, text) {
    const value = element('a', '', text);
    value.href = href;
    return value;
}

function dateTime(value) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
