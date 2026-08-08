#!/usr/bin/env node

import { readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
    assertHistoryMatchesSchema,
    nonReleaseEntries,
    withoutNonReleaseEntries,
} from './history-lint.ts';

const reportDirectory = path.dirname(fileURLToPath(import.meta.url));
const performanceDirectory = path.dirname(reportDirectory);
const historyPath = path.join(performanceDirectory, 'history/releases.json');
const schemaPath = path.join(performanceDirectory, 'schema/history.schema.json');
const arguments_ = process.argv.slice(2);

if (arguments_.includes('--help') || arguments_.includes('-h')) {
    console.log('Usage: ./performance/lint-history.sh [--fix]');
    process.exit(0);
}
if (arguments_.some((argument) => argument !== '--fix') || arguments_.filter((argument) => argument === '--fix').length > 1) {
    console.error('Usage: ./performance/lint-history.sh [--fix]');
    process.exit(2);
}

const fix = arguments_[0] === '--fix';
let history: unknown;
try {
    const [historyDocument, schemaDocument] = await Promise.all([
        readFile(historyPath, 'utf8'),
        readFile(schemaPath, 'utf8'),
    ]);
    history = JSON.parse(historyDocument);
    const schema = JSON.parse(schemaDocument) as Record<string, unknown>;
    assertHistoryMatchesSchema(history, schema);
} catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
}
const invalid = nonReleaseEntries(history);

if (invalid.length === 0) {
    console.log(`Release history schema and labels are clean: ${historyPath}`);
    process.exit(0);
}

for (const release of invalid) {
    console.error(`Non-release history entry: ${release.subject} (${release.runId})`);
}

if (!fix) {
    console.error(`Release history contains ${invalid.length} non-release entr${invalid.length === 1 ? 'y' : 'ies'}. Run with --fix to remove ${invalid.length === 1 ? 'it' : 'them'}.`);
    process.exit(1);
}

const temporaryPath = `${historyPath}.${process.pid}.tmp`;
await writeFile(temporaryPath, `${JSON.stringify(withoutNonReleaseEntries(history), null, 2)}\n`);
await rename(temporaryPath, historyPath);
console.log(`Removed ${invalid.length} non-release history entr${invalid.length === 1 ? 'y' : 'ies'} from ${historyPath}.`);
console.log('Review the change with Git; tracked history can be recovered from version control.');
