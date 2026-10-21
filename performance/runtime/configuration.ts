import { createHash } from 'node:crypto';
import { access, readFile, stat } from 'node:fs/promises';
import { constants } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { legacyConfiguration } from '../lib/query-parameters.ts';

export const defaultConfiguration = fileURLToPath(new URL('./konifer.conf', import.meta.url));

export interface ConfigurationFingerprint {
    sourceName: string;
    explicit: boolean;
    sourceSha256: string;
    applicationSha256: string;
    legacyTranslated: boolean;
}

export interface ConfigurationSelection {
    path: string;
    source: string;
    application: string;
    fingerprint: ConfigurationFingerprint;
}

/** Hash UTF-8 config contents for comparison without storing them in result metadata. */
export function configurationHash(value: string): string {
    return createHash('sha256').update(value).digest('hex');
}

/**
 * Read and snapshot one standalone config before deployment can change anything.
 * Resolve explicit paths against the caller's cwd; translate legacy keys only in
 * the bundled default. Throw on unreadable input and leave the source untouched.
 */
export async function selectConfiguration(file?: string, legacy = false): Promise<ConfigurationSelection> {
    const resolved = file === undefined ? defaultConfiguration : path.resolve(file);
    let source: string;
    try {
        const info = await stat(resolved);
        if (!info.isFile() || (info.mode & 0o444) === 0) throw new Error('Not a readable file');
        await access(resolved, constants.R_OK);
        source = await readFile(resolved, 'utf8');
    } catch {
        throw new Error(`Configuration file is missing or unreadable: ${resolved}`);
    }
    const legacyTranslated = file === undefined && legacy;
    const application = legacyTranslated ? legacyConfiguration(source) : source;
    return {
        path: resolved, source, application,
        fingerprint: {
            sourceName: path.basename(resolved), explicit: file !== undefined,
            sourceSha256: configurationHash(source), applicationSha256: configurationHash(application), legacyTranslated,
        },
    };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    let file: string | undefined;
    let legacy = false;
    const args = process.argv.slice(2);
    for (let index = 0; index < args.length; index++) {
        if (args[index] === '--config' && args[index + 1]) file = args[++index];
        else if (args[index] === '--use-legacy-query-params') legacy = true;
        else throw new Error('Usage: node runtime/configuration.ts [--config FILE] [--use-legacy-query-params]');
    }
    const selection = await selectConfiguration(file, legacy);
    console.log(JSON.stringify(selection.fingerprint));
}
