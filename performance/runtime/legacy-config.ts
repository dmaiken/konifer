import { readFile, writeFile } from 'node:fs/promises';
import { legacyConfiguration } from '../lib/query-parameters.ts';

// Translate only the harness default into a separate file; explicit user configs
// bypass this entry point so historical settings remain under the operator's control.
const source = new URL('./konifer.conf', import.meta.url);
const destination = new URL('./konifer.legacy.conf', import.meta.url);
await writeFile(destination, legacyConfiguration(await readFile(source, 'utf8')));
