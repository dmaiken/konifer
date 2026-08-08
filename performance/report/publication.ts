import type { PerformanceRelease, WorkloadCatalog } from './types.ts';

export function missingReleaseCases(
    aggregate: PerformanceRelease,
    catalog: WorkloadCatalog,
): string[] {
    const actual = new Set(
        aggregate.results.map((result) => seriesKey(result.workload, result.case)),
    );
    const missing: string[] = [];

    for (const workloadId of catalog.suites.release.workloads) {
        const workload = catalog.workloads[workloadId];
        if (!workload) {
            throw new Error(`Release suite references unknown workload: ${workloadId}`);
        }
        const caseIds = workload.cases?.map((value) => value.id)
            ?? (workload.case ? [workload.case] : []);
        if (caseIds.length === 0) {
            throw new Error(`Release workload does not define any cases: ${workloadId}`);
        }
        for (const caseId of caseIds) {
            if (!actual.has(seriesKey(workloadId, caseId))) {
                missing.push(`${workloadId}/${caseId}`);
            }
        }
    }

    return missing;
}

function seriesKey(workload: string, caseId: string): string {
    return `${workload}\u0000${caseId}`;
}
