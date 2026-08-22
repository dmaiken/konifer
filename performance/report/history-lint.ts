import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { isStableRelease } from './release-version.ts';
import type { LoadHistory, LoadRun, PerformanceHistory, PerformanceRelease } from './types.ts';

export function nonReleaseEntries(history: PerformanceHistory): PerformanceRelease[] {
    return history.releases.filter((release) => !isStableRelease(release.subject));
}

export function withoutNonReleaseEntries(history: PerformanceHistory): PerformanceHistory {
    return {
        ...history,
        releases: history.releases.filter((release) => isStableRelease(release.subject)),
    };
}

export function nonReleaseLoadRuns(history: LoadHistory): LoadRun[] {
    return history.runs.filter((run) => !isStableRelease(run.subject));
}

export function withoutNonReleaseLoadRuns(history: LoadHistory): LoadHistory {
    return {
        ...history,
        runs: history.runs.filter((run) => isStableRelease(run.subject)),
    };
}

export function assertHistoryMatchesSchema<T = PerformanceHistory>(
    value: unknown,
    schema: Record<string, unknown>,
    referencedSchemas: Record<string, unknown>[] = [],
): asserts value is T {
    const validator = new Ajv2020({ allErrors: true, strict: true });
    addFormats(validator);
    referencedSchemas.forEach((referencedSchema) => validator.addSchema(referencedSchema));
    const validate = validator.compile(schema);
    if (!validate(value)) {
        const details = validate.errors
            ?.map((error) => `${error.instancePath || '/'} ${error.message}`)
            .join('\n');
        throw new Error(`Performance history does not match schema:\n${details || 'Unknown validation error'}`);
    }
}
