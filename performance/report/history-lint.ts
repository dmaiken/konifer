import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import type { PerformanceHistory, PerformanceRelease } from './types.ts';

const releaseTagPattern = /^v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/;

export function isReleaseTag(subject: string): boolean {
    return releaseTagPattern.test(subject);
}

export function nonReleaseEntries(history: PerformanceHistory): PerformanceRelease[] {
    return history.releases.filter((release) => !isReleaseTag(release.subject));
}

export function withoutNonReleaseEntries(history: PerformanceHistory): PerformanceHistory {
    return {
        ...history,
        releases: history.releases.filter((release) => isReleaseTag(release.subject)),
    };
}

export function assertHistoryMatchesSchema(
    value: unknown,
    schema: Record<string, unknown>,
): asserts value is PerformanceHistory {
    const validator = new Ajv2020({ allErrors: true, strict: true });
    addFormats(validator);
    const validate = validator.compile(schema);
    if (!validate(value)) {
        const details = validate.errors
            ?.map((error) => `${error.instancePath || '/'} ${error.message}`)
            .join('\n');
        throw new Error(`Performance history does not match schema:\n${details || 'Unknown validation error'}`);
    }
}
