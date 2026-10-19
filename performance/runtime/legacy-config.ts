import { readFile, writeFile } from 'node:fs/promises';
import { legacyConfiguration } from '../lib/query-parameters.ts';

const source = new URL('./konifer.conf', import.meta.url);
const destination = new URL('./konifer.legacy.conf', import.meta.url);
await writeFile(destination, legacyConfiguration(await readFile(source, 'utf8')));
