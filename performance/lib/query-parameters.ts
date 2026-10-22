import type { QueryParameters } from '../k6/types.ts';

const legacyNames = new Map([
    ['width', 'w'],
    ['height', 'h'],
    ['quality', 'q'],
    ['gravity', 'g'],
    ['rotate', 'r'],
    ['flip', 'f'],
    ['color-space', 'cs'],
    ['pad-color', 'pad-c'],
]);

/** Encode transformation query values, translating known keys when legacy request names are selected. */
export function queryUrl(url: string, parameters: QueryParameters, useLegacyNames = false): string {
    const values = Object.entries(parameters).map(([key, value]) => {
        const name = useLegacyNames ? (legacyNames.get(key) ?? key) : key;
        return `${encodeURIComponent(name)}=${encodeURIComponent(String(value))}`;
    });
    return values.length === 0 ? url : `${url}?${values.join('&')}`;
}

/** Converts assignment keys in the harness's line-oriented HOCON configuration. */
export function legacyConfiguration(configuration: string): string {
    const assignments = new RegExp(`^([ \\t]*)(${[...legacyNames.keys()].join('|')})([ \\t]*=)`, 'gm');
    return configuration.replace(assignments, (_, indentation: string, name: string, assignment: string) =>
        `${indentation}${legacyNames.get(name)}${assignment}`);
}
