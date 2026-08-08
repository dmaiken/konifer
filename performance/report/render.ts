import { formatMs, formatRange, label, workloadDescription, workloadLabel } from './model.ts';
import type { AggregatedResult, EnvironmentProfile, PerformanceRelease, WorkloadCatalog } from './types.ts';

export function renderRunMarkdown(
    aggregate: PerformanceRelease,
    environment?: EnvironmentProfile,
    workloads?: WorkloadCatalog,
): string {
    return `# Performance run: ${aggregate.subject}

- Suite: \`${aggregate.suite}\`
- Environment: \`${aggregate.environment}\`
- Completed: ${aggregate.completedAt}

${markdownEnvironment(aggregate.environment, environment)}

${markdownWorkloads(aggregate.results, workloads)}

${markdownTable(aggregate.results)}

Latency values are medians of the run-level percentiles across repetitions. The
p95 range shows the minimum and maximum repetition values. Setup and warmup are
outside the measurement window.
`;
}

function markdownWorkloads(results: AggregatedResult[], catalog?: WorkloadCatalog): string {
    if (!catalog) return '';
    const workloadIds = [...new Set(results.map((result) => result.workload))];
    const definitions = workloadIds.flatMap((workload) => {
        const value = { workload };
        const description = workloadDescription(value, catalog);
        return description
            ? [`- **${workloadLabel(value)}** (\`${workload}\`): ${description}`]
            : [];
    });
    return definitions.length === 0
        ? ''
        : `## Workload definitions\n\n${definitions.join('\n')}`;
}

function markdownEnvironment(id: string, environment?: EnvironmentProfile): string {
    const title = environment?.displayName || id;
    const description = environment?.description || 'No descriptive metadata is available for this environment.';
    const hardware = environment?.hardware;
    const facts = [
        hardware?.system ? ['System', hardware.system] : null,
        hardware?.processor ? ['Processor', hardware.processor] : null,
        hardware?.architecture ? ['Architecture', hardware.architecture] : null,
        hardware?.physicalCores !== undefined || hardware?.logicalCpus !== undefined
            ? ['CPU topology', `${hardware.physicalCores ?? '—'} physical cores / ${hardware.logicalCpus ?? '—'} logical CPUs`]
            : null,
        hardware?.memoryGiB !== undefined ? ['Memory', `${hardware.memoryGiB} GiB`] : null,
    ].filter((value): value is string[] => value !== null);
    const factTable = facts.length === 0
        ? ''
        : `\n\n| Hardware | Value |\n|---|---|\n${facts.map(([name, value]) => `| ${escapeTable(name)} | ${escapeTable(value)} |`).join('\n')}`;
    const interpretation = environment?.interpretation?.length
        ? `\n\n### How to interpret these results\n\n${environment.interpretation.map((note) => `- ${note}`).join('\n')}`
        : '';

    return `## Benchmark environment\n\n**${title}** (\`${id}\`)\n\n${description}${factTable}${interpretation}`;
}

function escapeTable(value: string): string {
    return value.replaceAll('|', '\\|');
}

function markdownTable(results: AggregatedResult[]): string {
    if (results.length === 0) {
        return '_No measurements published._';
    }
    const rows = results.map((value) => `| ${label(value)} | ${formatMs(value.durationMs.p50)} | ${formatMs(value.durationMs.p95)} | ${formatRange(value)} | ${value.repetitions} | ${value.operations} | ${value.errors} | ${value.droppedIterations} |`);
    return `| Operation | p50 | p95 | p95 range | Repetitions | Operations | Errors | Dropped |
|---|---:|---:|---:|---:|---:|---:|---:|
${rows.join('\n')}`;
}
